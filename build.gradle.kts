import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Verifies fresh complete task outputs, including missing reports and skipped mandatory tests. */
abstract class VerifyNetworkProtectionReportsTask : DefaultTask() {
    /** Root containing the test-task outputs produced by this task's gate dependencies. */
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    /** Distinct test output paths; a missing or empty task report is a failure. */
    @get:Input abstract val reportDirectories: ListProperty<String>
    /** XML report inputs, owned by the prerequisite test tasks. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection
    /** Newly required regression suites that cannot silently disappear from successful task outputs. */
    @get:Input abstract val requiredSuites: ListProperty<String>
    /** Requirement state is distinct from test-task execution evidence. */
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ledger: RegularFileProperty
    /** Machine-readable counts for this exact gate run; module count does not imply full requirements closure. */
    @get:OutputFile abstract val summaryFile: RegularFileProperty

    /** Rejects missing evidence, test failures and mandatory skipped tests before publishing the count. */
    @TaskAction fun verify() {
        val root = repositoryDirectory.get().asFile
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder()
        val suites = mutableSetOf<String>()
        var total = 0
        val counts = reportDirectories.get().distinct().map { relative ->
            val files = root.resolve(relative).listFiles { file -> file.name.startsWith("TEST-") && file.extension == "xml" }.orEmpty()
            check(files.isNotEmpty()) { "Mandatory test-task reports are missing: $relative" }
            var count = 0
            files.forEach { file ->
                val suite = parser.parse(file).documentElement
                suites += suite.getAttribute("name")
                // Kotlin multiplatform appends target display suffixes to suite names. JUnit's testcase
                // classname is the executed source identity and stays stable across JVM target labels.
                val cases = suite.getElementsByTagName("testcase")
                for (index in 0 until cases.length) {
                    suites += (cases.item(index) as org.w3c.dom.Element).getAttribute("classname")
                }
                val tests = suite.getAttribute("tests").toInt()
                listOf("failures", "errors", "skipped").forEach { attribute ->
                    check(suite.getAttribute(attribute).ifEmpty { "0" }.toInt() == 0) {
                        "${file.name} has non-zero $attribute; the feature gate is incomplete."
                    }
                }
                count += tests
            }
            check(count > 0) { "Mandatory test-task output contains no executed cases: $relative" }
            total += count
            "    {\"path\":\"$relative\",\"tests\":$count}"
        }
        check(requiredSuites.get().all { it in suites }) { "Required regression suites missing: ${requiredSuites.get().filterNot { it in suites }}" }
        val output = summaryFile.get().asFile
        val softwareOpen = ledger.get().asFile.readLines().drop(1).filter(String::isNotBlank).count { line ->
            val columns = line.split('\t')
            QualificationKind.valueOf(columns[2]) in setOf(QualificationKind.SOFTWARE, QualificationKind.MIXED) &&
                QualificationStatus.valueOf(columns[3]) != QualificationStatus.VERIFIED
        }
        output.parentFile.mkdirs()
        output.writeText("{\n  \"tests\":$total,\n  \"failures\":0,\n  \"skipped\":0,\n  \"softwareRequirementsComplete\":${softwareOpen == 0},\n  \"softwareRequirementsOpen\":$softwareOpen,\n  \"modules\":[\n${counts.joinToString(",\n")}\n  ]\n}\n")
        logger.lifecycle("Network protection gate: $total tests, no failures/skips, ${counts.size} distinct task outputs. Requirements closure is tracked separately.")
    }
}

/** Closed evidence statuses used by the requirements ledger, independent of a passing module test count. */
enum class QualificationStatus { PENDING, BASELINE_PARTIAL, VERIFIED, PHYSICAL_PENDING, SUPERSEDED }

/** Whether acceptance is executable in software, requires hardware, or has been replaced by an approved decision. */
enum class QualificationKind { SOFTWARE, PHYSICAL, MIXED, RETIRED }

/** Checks that every accepted requirement remains tracked and that completion never hides missing software work. */
abstract class VerifyNetworkProtectionLedgerTask : DefaultTask() {
    /** Root used to resolve evidence and test-source links. */
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    /** Versioned machine-readable acceptance ledger. */
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ledger: RegularFileProperty
    /** Original requirement documents, tracked so additions cannot bypass the ledger. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val requirementDocuments: ConfigurableFileCollection
    /** Strict release closure mode; normal development validation permits explicitly pending work. */
    @get:Input abstract val requireSoftwareComplete: Property<Boolean>

    /** Validates identity, ownership, status and retained source/evidence references. */
    @TaskAction fun verify() {
        val root = repositoryDirectory.get().asFile
        val lines = ledger.get().asFile.readLines()
        val columns = listOf("id", "owner", "kind", "status", "title", "requirement", "source", "tests", "evidence")
        check(lines.first().split('\t') == columns) { "Unexpected network protection ledger schema." }
        val entries = lines.drop(1).filter(String::isNotBlank).map { line ->
            val values = line.split('\t')
            check(values.size == columns.size) { "Malformed qualification ledger row: ${values.firstOrNull()}" }
            columns.zip(values).toMap()
        }
        val ids = entries.map { it.getValue("id") }
        check(ids.size == ids.toSet().size) { "Qualification requirement IDs must be unique." }
        val networkSource = root.resolve("docs/network_conditions_test_gap_report.md").readText()
        val expectedNetwork = Regex("(?m)^\\| (NC-[A-Z]+-\\d{3}) \\|").findAll(networkSource)
            .map { it.groupValues[1] }.toSet()
        check(ids.filter { it.startsWith("NC-") }.toSet() == expectedNetwork) { "Network Conditions ledger IDs differ from the acceptance document." }
        val networkRequirements = networkSource.lineSequence().filter { Regex("^\\| NC-[A-Z]+-\\d{3} \\|").containsMatchIn(it) }
            .associate { line -> line.split('|').map(String::trim).let { it[1] to it[4] } }
        entries.filter { it.getValue("id") in expectedNetwork }.forEach { entry ->
            check(entry.getValue("requirement") == networkRequirements[entry.getValue("id")]) {
                "Acceptance text changed for ${entry.getValue("id")}; update/review its ledger evidence."
            }
        }
        val smartSource = root.resolve("docs/smart_compatibility_implementation_plan.md").readText()
            .substringAfter("## Required automated test matrix").substringBefore("\nThe implemented")
        val expectedSmart = smartSource.lineSequence().count { it.startsWith("| ") && !it.startsWith("| Area |") }
        check(ids.count { it.startsWith("SC-") } == expectedSmart) { "Smart acceptance categories are missing from the ledger." }
        val protectedSource = root.resolve("docs/protected_traffic_passthrough_implementation_plan.md").readText()
            .substringAfter("## Automated acceptance gates").substringBefore("\n## ")
        check(ids.count { it.startsWith("PT-") } == Regex("(?m)^- ").findAll(protectedSource).count()) {
            "Protected Traffic acceptance assertions are missing from the ledger."
        }
        check((1..6).all { "AUDIT-A$it" in ids }) { "All six readiness findings must remain tracked." }
        val pending = mutableListOf<String>()
        entries.forEach { entry ->
            val id = entry.getValue("id")
            check(entry.getValue("owner") in (0..12).map { "C" + it.toString().padStart(2, '0') }) { "Unknown work package for $id" }
            val kind = QualificationKind.valueOf(entry.getValue("kind"))
            val status = QualificationStatus.valueOf(entry.getValue("status"))
            check(entry.getValue("title").isNotBlank() && entry.getValue("requirement").isNotBlank()) { "Empty acceptance requirement for $id" }
            check(root.resolve(entry.getValue("source").substringBefore('#')).isFile) { "Missing requirement source for $id" }
            check(status != QualificationStatus.PHYSICAL_PENDING || kind in setOf(QualificationKind.PHYSICAL, QualificationKind.MIXED)) {
                "$id cannot defer a software-only requirement as physical testing."
            }
            check((status == QualificationStatus.SUPERSEDED) == (kind == QualificationKind.RETIRED)) { "Invalid retired requirement $id" }
            if (status == QualificationStatus.VERIFIED) {
                listOf("tests", "evidence").forEach { column ->
                    val references = entry.getValue(column).split(';').filter(String::isNotBlank)
                    check(references.isNotEmpty() && references.all { root.resolve(it.substringBefore('#')).isFile }) {
                        "Verified requirement $id needs existing $column references."
                    }
                }
            }
            if (kind in setOf(QualificationKind.SOFTWARE, QualificationKind.MIXED) && status != QualificationStatus.VERIFIED) pending += id
        }
        check(!requireSoftwareComplete.get() || pending.isEmpty()) {
            "Software acceptance remains open for ${pending.size} requirements: ${pending.joinToString()}"
        }
        logger.lifecycle("Validated ${entries.size} network protection requirements; ${pending.size} software requirements remain open.")
    }
}

/** Requirements validation; strict closure is requested with -PrequireNetworkProtectionComplete=true. */
val verifyNetworkProtectionLedger by tasks.registering(VerifyNetworkProtectionLedgerTask::class) {
    group = "verification"
    description = "Checks complete requirement tracking and prevents software gaps being deferred as physical tests."
    repositoryDirectory.set(layout.projectDirectory)
    ledger.set(layout.projectDirectory.file("docs/qualification/network_protection_cases.tsv"))
    requirementDocuments.from(
        "docs/network_conditions_test_gap_report.md",
        "docs/protected_traffic_passthrough_implementation_plan.md",
        "docs/smart_compatibility_implementation_plan.md",
    )
    requireSoftwareComplete.set(providers.gradleProperty("requireNetworkProtectionComplete").map(String::toBooleanStrict).orElse(false))
}

/** Verifies that all configured module ownership documents exist. */
abstract class VerifyModuleDocumentationTask : DefaultTask() {
    /** Repository directory used to resolve configured relative paths. */
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    /** Relative ownership-document paths keyed by Gradle project path. */
    @get:Input
    abstract val documentationPaths: MapProperty<String, String>

    /** Ownership documents tracked as task inputs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationFiles: ConfigurableFileCollection

    /** Performs the documentation coverage check. */
    @TaskAction
    fun verify() {
        val repositoryRoot = repositoryDirectory.get().asFile
        val undocumentedModules = documentationPaths.get()
            .filterValues { relativePath -> !repositoryRoot.resolve(relativePath).isFile }
            .keys
            .sorted()

        check(undocumentedModules.isEmpty()) {
            "Missing MODULE.md ownership contract for: ${undocumentedModules.joinToString()}"
        }
    }
}

/** Verifies direct project dependencies declared by guarded architecture modules. */
abstract class VerifyModuleBoundariesTask : DefaultTask() {
    /** Repository directory used to resolve guarded build scripts. */
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    /** Relative build-script paths keyed by guarded Gradle project path. */
    @get:Input
    abstract val guardedBuildScriptPaths: MapProperty<String, String>

    /** Allowed direct project paths, encoded as sorted comma-separated values. */
    @get:Input
    abstract val allowedDependencies: MapProperty<String, String>

    /** Guarded build scripts tracked as task inputs. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val guardedBuildScripts: ConfigurableFileCollection

    /** Performs the dependency-boundary check. */
    @TaskAction
    fun verify() {
        val repositoryRoot = repositoryDirectory.get().asFile
        val projectDependencyPattern = Regex("project\\(\"(:[^\"]+)\"\\)")
        val buildScriptsByModule: Map<String, String> = guardedBuildScriptPaths.get()
        val approvedDependenciesByModule: Map<String, String> = allowedDependencies.get()
        val violations: List<String> = buildScriptsByModule.entries.flatMap { entry ->
            val modulePath = entry.key
            val relativeBuildScriptPath = entry.value
            val directDependencies = projectDependencyPattern
                .findAll(repositoryRoot.resolve(relativeBuildScriptPath).readText())
                .map { match -> match.groupValues[1] }
                .toSet()
            val approvedDependencies = approvedDependenciesByModule.getValue(modulePath)
                .split(',')
                .filter(String::isNotBlank)
                .toSet()

            (directDependencies - approvedDependencies).map { dependencyPath ->
                "$modulePath must not depend on $dependencyPath"
            }
        }

        check(violations.isEmpty()) {
            violations.sorted().joinToString(
                prefix = "Target architecture dependency violations:\n",
                separator = "\n",
            )
        }
    }
}

/** Prevents presentation modules from importing concrete runtime, storage, or platform adapters. */
abstract class VerifyUiRuntimeIsolationTask : DefaultTask() {
    /** Production Kotlin sources under UI modules. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    /** UI Gradle build scripts. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildScripts: ConfigurableFileCollection

    /** Engine packages deliberately approved as presentation-facing typed protocol APIs. */
    @get:Input
    abstract val allowedEngineImportPrefixes: ListProperty<String>

    /** Engine projects deliberately approved as presentation-facing typed protocol APIs. */
    @get:Input
    abstract val allowedEngineProjects: ListProperty<String>

    @TaskAction
    fun verify() {
        val allowedImports = allowedEngineImportPrefixes.get()
        val sourceViolations = productionSources.files.sortedBy { it.path }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val imported = line.trim().removePrefix("import ").takeIf { line.trim().startsWith("import ") }
                    ?: return@mapIndexedNotNull null
                if (
                    imported.startsWith("com.devuloopers.knet.engine.") &&
                    allowedImports.none(imported::startsWith)
                ) "${source.relativeTo(project.rootDir)}:${index + 1} imports $imported" else null
            }
        }
        val projectPattern = Regex("project\\(\"(:engine:[^\"]+)\"\\)")
        val allowedProjects = allowedEngineProjects.get().toSet()
        val buildViolations = buildScripts.files.sortedBy { it.path }.flatMap { script ->
            projectPattern.findAll(script.readText())
                .map { match -> match.groupValues[1] }
                .filterNot(allowedProjects::contains)
                .map { dependency -> "${script.relativeTo(project.rootDir)} depends on $dependency" }
                .toList()
        }
        val violations = sourceViolations + buildViolations
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "UI concrete-runtime dependency violations:\n",
                separator = "\n",
            )
        }
    }
}

/** Keeps dependency-injection binding declarations in executable product composition roots. */
abstract class VerifyCompositionOwnershipTask : DefaultTask() {
    /** Production sources outside executable products. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val bindingImports = listOf(
            "import org.koin.dsl.module",
            "import org.koin.core.module.dsl.",
        )
        val violations = productionSources.files.sortedBy { it.path }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (bindingImports.any(trimmed::startsWith)) {
                    "${source.relativeTo(project.rootDir)}:${index + 1} declares Koin bindings outside a product composition root"
                } else {
                    null
                }
            }
        }

        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Dependency-injection composition ownership violations:\n",
                separator = "\n",
            )
        }
    }
}

/** Keeps portable and production Kotlin sources on the approved Kotlin-first platform boundary. */
abstract class VerifyKotlinFirstSourcesTask : DefaultTask() {
    /** Repository directory used to report stable relative source paths. */
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    /** Kotlin sources compiled as portable common code. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val commonSources: ConfigurableFileCollection

    /** Kotlin sources compiled into production artifacts. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSources: ConfigurableFileCollection

    /** Every repository Kotlin source, including tests. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allKotlinSources: ConfigurableFileCollection

    /** Kotlin sources in modules that rely on Kotlin's default public visibility. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val implicitApiSources: ConfigurableFileCollection

    /** JVM platform adapters allowed to use AWT clipboard or native-window event subscription. */
    @get:Input
    abstract val allowedAwtPlatformAdapterPaths: ListProperty<String>

    /** Verifies common-source portability and prohibited production Java/coroutine shortcuts. */
    @TaskAction
    fun verify() {
        val repositoryRoot = repositoryDirectory.get().asFile
        val commonViolations = commonSources.files.sortedBy { it.path }.flatMap { source ->
            val relativePath = source.relativeTo(repositoryRoot).invariantSeparatorsPath
            val sourceLines = source.codeLines()
            val lineViolations = sourceLines.mapNotNull { (lineNumber, line) ->
                when {
                    line.startsWith("import java.") || line.startsWith("import javax.") ->
                        "$relativePath:$lineNumber imports a JVM API from commonMain"
                    line.startsWith("import android.") || line.startsWith("import platform.") ->
                        "$relativePath:$lineNumber imports a native platform API from commonMain"
                    JVM_QUALIFIED_REFERENCE.containsMatchIn(line) ->
                        "$relativePath:$lineNumber references a JVM API from commonMain"
                    SYSTEM_REFERENCE.containsMatchIn(line) ->
                        "$relativePath:$lineNumber references System from commonMain"
                    relativePath.startsWith(COMPANION_COMMON_SOURCE_PREFIX) &&
                        (
                            COMPANION_NATIVE_CONTEXT_REFERENCE.containsMatchIn(line) ||
                                COMPANION_OPAQUE_ANY_DECLARATION.containsMatchIn(line)
                        ) ->
                        "$relativePath:$lineNumber exposes a native context or opaque Any bridge from commonMain"
                    else -> null
                }
            }
            val factoryConstructorViolation = if (
                relativePath == COMPANION_EXPECT_FACTORY_PATH &&
                sourceLines.joinToString("\n") { (_, line) -> line }
                    .contains("expect class PlatformCompanionAdapterFactory(")
            ) {
                listOf("$relativePath declares a common constructor for the native platform factory")
            } else {
                emptyList()
            }
            lineViolations + factoryConstructorViolation
        }

        val approvedClipboardPaths = allowedAwtPlatformAdapterPaths.get().toSet()
        val productionViolations = productionSources.files.sortedBy { it.path }.flatMap { source ->
            val relativePath = source.relativeTo(repositoryRoot).invariantSeparatorsPath
            source.codeLines().flatMap { (lineNumber, line) ->
                buildList {
                    if (RUN_BLOCKING_REFERENCE.containsMatchIn(line)) {
                        add("$relativePath:$lineNumber uses runBlocking in production")
                    }
                    if (JAVA_UUID_REFERENCE.containsMatchIn(line)) {
                        add("$relativePath:$lineNumber uses java.util.UUID")
                    }
                    if (AVOIDABLE_JAVA_UTILITY_IMPORT.containsMatchIn(line)) {
                        add("$relativePath:$lineNumber imports an avoidable Java utility")
                    }
                    if (DEPRECATED_COMPOSE_REFERENCE.containsMatchIn(line)) {
                        add("$relativePath:$lineNumber uses a deprecated Compose API")
                    }
                    if (NON_MIRRORED_DIRECTIONAL_ICON.containsMatchIn(line)) {
                        add("$relativePath:$lineNumber uses a non-auto-mirrored directional icon")
                    }
                    if (
                        AWT_CLIPBOARD_REFERENCE.containsMatchIn(line) &&
                        relativePath !in approvedClipboardPaths
                    ) {
                        add("$relativePath:$lineNumber uses AWT Toolkit/clipboard outside a platform adapter")
                    }
                }
            }
        }

        val visibilityViolations = implicitApiSources.files.sortedBy { it.path }.flatMap { source ->
            val relativePath = source.relativeTo(repositoryRoot).invariantSeparatorsPath
            source.codeLines().mapNotNull { (lineNumber, line) ->
                if (REDUNDANT_PUBLIC_VISIBILITY.containsMatchIn(line)) {
                    "$relativePath:$lineNumber declares redundant public visibility outside an explicit-API module"
                } else {
                    null
                }
            }
        }

        val javaTimeViolations = allKotlinSources.files.sortedBy { it.path }.flatMap { source ->
            val relativePath = source.relativeTo(repositoryRoot).invariantSeparatorsPath
            source.codeLines().mapNotNull { (lineNumber, line) ->
                if (JAVA_TIME_REFERENCE.containsMatchIn(line)) {
                    "$relativePath:$lineNumber uses prohibited java.time; use kotlin.time or kotlinx-datetime"
                } else {
                    null
                }
            }
        }

        val violations = commonViolations + productionViolations + visibilityViolations + javaTimeViolations
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Kotlin-first source-boundary violations:\n",
                separator = "\n",
            )
        }
    }

    /** Returns non-comment source lines with their one-based line numbers. */
    private fun java.io.File.codeLines(): List<Pair<Int, String>> {
        var insideBlockComment = false
        return readLines().mapIndexedNotNull { index, rawLine ->
            val line = rawLine.trim()
            if (insideBlockComment) {
                if (line.contains("*/")) insideBlockComment = false
                return@mapIndexedNotNull null
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) insideBlockComment = true
                return@mapIndexedNotNull null
            }
            if (line.isBlank() || line.startsWith("//")) null else index + 1 to line
        }
    }

    private companion object {
        val JVM_QUALIFIED_REFERENCE = Regex("""\b(?:java|javax)\.""")
        val SYSTEM_REFERENCE = Regex("""(?<!Clock\.)\bSystem\.""")
        const val COMPANION_COMMON_SOURCE_PREFIX = "connectivity/companion/src/commonMain/"
        const val COMPANION_EXPECT_FACTORY_PATH =
            "connectivity/companion/src/commonMain/kotlin/com/devuloopers/knet/companion/connectivity/platform/PlatformCompanionAdapterFactory.kt"
        val COMPANION_NATIVE_CONTEXT_REFERENCE = Regex("""\b(?:[A-Za-z_][A-Za-z0-9_]*\.)*(?:Context|[A-Z][A-Za-z0-9_]*Context)\b""")
        val COMPANION_OPAQUE_ANY_DECLARATION = Regex("""\b[A-Za-z_][A-Za-z0-9_]*\s*:\s*Any\??\s*[,)=]""")
        val RUN_BLOCKING_REFERENCE = Regex("""(?:import kotlinx\.coroutines\.runBlocking|\brunBlocking\s*[({])""")
        val JAVA_UUID_REFERENCE = Regex("""\bjava\.util\.UUID\b""")
        val JAVA_TIME_REFERENCE = Regex("""\bjava\.time(?:\.|\b)""")
        val AVOIDABLE_JAVA_UTILITY_IMPORT = Regex(
            """^import (?:java\.util\.(?:Base64|ArrayDeque|Collections|LinkedHashMap)|java\.net\.(?:URLDecoder|URLEncoder)|java\.nio\.charset\.StandardCharsets|java\.text\.SimpleDateFormat)\b""",
        )
        val DEPRECATED_COMPOSE_REFERENCE = Regex(
            """(?:androidx\.compose\.desktop\.ui\.tooling\.preview\.Preview|androidx\.compose\.ui\.res\.painterResource)""",
        )
        val NON_MIRRORED_DIRECTIONAL_ICON = Regex(
            """Icons\.(?:Default|Filled)\.(?:ArrowBack|ArrowForward|KeyboardArrowLeft|KeyboardArrowRight|NavigateBefore|NavigateNext|ChevronLeft|ChevronRight|HelpOutline|OpenInNew)\b""",
        )
        val AWT_CLIPBOARD_REFERENCE = Regex(
            """\bjava\.awt\.(?:Toolkit|datatransfer\.(?:DataFlavor|StringSelection))\b""",
        )
        val REDUNDANT_PUBLIC_VISIBILITY = Regex(
            """\bpublic\s+(?=(?:(?:actual|expect|data|sealed|enum|value|annotation|open|abstract|const|override|suspend|operator|infix|inline|tailrec|external|final|lateinit)\s+)*(?:class|interface|object|fun|val|var|typealias|constructor|companion)\b)""",
        )
    }
}

/** Prevents the Android companion product from regaining a second XML/View screen implementation. */
abstract class VerifyCompanionComposeUiOwnershipTask : DefaultTask() {
    /** Android layout resources, which must remain empty for the companion product. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val layoutResources: ConfigurableFileCollection

    /** Android product Kotlin sources checked for legacy View layout inflation. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productSources: ConfigurableFileCollection

    /** Shared companion Compose sources checked for a second feature-owned theme palette. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedUiSources: ConfigurableFileCollection

    /** Enforces shared Compose ownership while leaving manifest/theme/icon XML available to Android packaging. */
    @TaskAction
    fun verify() {
        val layoutViolations = layoutResources.files.sortedBy { it.path }.map { layout ->
            "${layout.relativeTo(project.rootDir)} is an Android View layout"
        }
        val legacyTokens = listOf("setContentView(", "R.layout.")
        val sourceViolations = productSources.files.sortedBy { it.path }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val token = legacyTokens.firstOrNull(line::contains) ?: return@mapIndexedNotNull null
                "${source.relativeTo(project.rootDir)}:${index + 1} uses $token"
            }
        }
        val companionPaletteTokens = listOf(
            "darkColorScheme(",
            "lightColorScheme(",
            "Color(0x",
            "Color.White",
            "Color.Black",
            "Color.Red",
            "Color.Green",
            "Color.Blue",
            "Color.Yellow",
            "Color.Gray",
        )
        val paletteViolations = sharedUiSources.files.sortedBy { it.path }.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                val token = companionPaletteTokens.firstOrNull(line::contains) ?: return@mapIndexedNotNull null
                "${source.relativeTo(project.rootDir)}:${index + 1} owns palette token $token instead of using :ui:core"
            }
        }
        val violations = layoutViolations + sourceViolations + paletteViolations
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix = "Android companion UI must be hosted from :ui:companion:sharedUi:\n",
                separator = "\n",
            )
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

val allowedArchitectureDependencies = mapOf(
    ":application:desktop" to setOf(
        ":core:traffic",
        ":core:scripting",
        ":core:domain",
        ":core:connectivity",
        ":core:companion",
        ":core:identity",
        ":core:pairing",
    ),
    ":core:domain" to setOf(":core:logger", ":core:traffic", ":core:scripting"),
    ":core:scripting" to emptySet(),
    ":core:traffic" to emptySet(),
    ":core:identity" to emptySet(),
    ":core:pairing" to setOf(":core:identity"),
    ":core:connectivity" to emptySet(),
    ":core:companion" to setOf(
        ":core:connectivity",
        ":core:identity",
        ":core:pairing",
    ),
    ":application:companion" to setOf(
        ":core:companion",
        ":core:identity",
        ":core:logger",
        ":core:pairing",
    ),
    ":data:companion" to setOf(
        ":application:companion",
        ":core:companion",
        ":core:identity",
        ":core:logger",
        ":core:pairing",
    ),
    ":ui:companion:presentation" to setOf(
        ":application:companion",
        ":core:companion",
        ":core:logger",
    ),
    ":ui:companion:sharedUi" to setOf(
        ":core:companion",
        ":ui:core",
        ":ui:companion:presentation",
    ),
    ":connectivity:companion" to setOf(
        ":application:companion",
        ":core:companion",
        ":core:logger",
    ),
    ":products:companion:di" to setOf(
        ":application:companion",
        ":connectivity:companion",
        ":data:companion",
        ":ui:companion:presentation",
    ),
    ":products:companion:androidApp" to setOf(
        ":application:companion",
        ":connectivity:companion",
        ":core:companion",
        ":core:logger",
        ":data:companion",
        ":products:companion:di",
        ":ui:companion:presentation",
        ":ui:companion:sharedUi",
    ),
    ":products:companion:iosApp" to setOf(
        ":application:companion",
        ":connectivity:companion",
        ":core:companion",
        ":data:companion",
        ":products:companion:di",
        ":ui:core",
        ":ui:companion:presentation",
        ":ui:companion:sharedUi",
    ),
    ":connectivity:desktop" to setOf(
        ":application:desktop",
        ":core:companion",
        ":core:traffic",
        ":core:connectivity",
        ":core:identity",
        ":core:logger",
        ":core:pairing",
    ),
).mapValues { (_, paths) -> paths.sorted().joinToString(",") }

val moduleDocumentationPaths = subprojects.associate { module ->
    module.path to module.file("MODULE.md").relativeTo(rootDir).invariantSeparatorsPath
}

val guardedBuildScriptFilesByModule = allowedArchitectureDependencies.keys.associateWith { modulePath ->
    project(modulePath).buildFile.relativeTo(rootDir).invariantSeparatorsPath
}

val verifyModuleDocumentation by tasks.registering(VerifyModuleDocumentationTask::class) {
    group = "verification"
    description = "Verifies that every Gradle module has a root MODULE.md ownership contract."
    repositoryDirectory.set(layout.projectDirectory)
    documentationPaths.set(moduleDocumentationPaths)
    documentationFiles.from(moduleDocumentationPaths.values.map(rootDir::resolve))
}

val verifyModuleBoundaries by tasks.registering(VerifyModuleBoundariesTask::class) {
    group = "verification"
    description = "Verifies dependency direction for the target architecture foundation modules."
    repositoryDirectory.set(layout.projectDirectory)
    this.allowedDependencies.set(allowedArchitectureDependencies)
    guardedBuildScriptPaths.set(guardedBuildScriptFilesByModule)
    guardedBuildScripts.from(guardedBuildScriptFilesByModule.values.map(rootDir::resolve))
}

val verifyUiRuntimeIsolation by tasks.registering(VerifyUiRuntimeIsolationTask::class) {
    group = "verification"
    description = "Verifies that UI production code does not depend on concrete KNet runtimes or storage."
    productionSources.from(fileTree("ui") {
        include("**/src/commonMain/**/*.kt", "**/src/jvmMain/**/*.kt")
        exclude("**/build/**")
    })
    buildScripts.from(fileTree("ui") {
        include("**/build.gradle.kts")
        exclude("**/build/**")
    })
    allowedEngineImportPrefixes.set(
        listOf(
            "com.devuloopers.knet.engine.formatter.",
        ),
    )
    allowedEngineProjects.set(listOf(":engine:formatter"))
}

val verifyCompositionOwnership by tasks.registering(VerifyCompositionOwnershipTask::class) {
    group = "verification"
    description = "Verifies that Koin binding declarations live only in executable product roots."
    productionSources.from(fileTree(".") {
        include(
            "**/src/main/**/*.kt",
            "**/src/commonMain/**/*.kt",
            "**/src/jvmMain/**/*.kt",
        )
        exclude("products/**", "**/build/**")
    })
}

val verifyKotlinFirstSources by tasks.registering(VerifyKotlinFirstSourcesTask::class) {
    group = "verification"
    description = "Verifies Kotlin-first common and production source boundaries."
    repositoryDirectory.set(layout.projectDirectory)
    commonSources.from(fileTree(".") {
        include("**/src/commonMain/**/*.kt")
        exclude("**/build/**")
    })
    allKotlinSources.from(fileTree(".") {
        include("**/*.kt")
        exclude("**/build/**", ".gradle/**")
    })
    productionSources.from(fileTree(".") {
        include(
            "**/src/main/**/*.kt",
            "**/src/commonMain/**/*.kt",
            "**/src/androidMain/**/*.kt",
            "**/src/jvmMain/**/*.kt",
        )
        exclude("**/build/**", "**/src/test/**", "**/src/commonTest/**", "**/src/jvmTest/**")
    })
    implicitApiSources.from(fileTree(".") {
        include(
            "core/domain/src/**/*.kt",
            "core/http/src/**/*.kt",
            "core/logger/src/**/*.kt",
            "core/pairing/src/**/*.kt",
            "core/serialization/src/**/*.kt",
            "data/src/**/*.kt",
            "data/desktop/src/**/*.kt",
            "engine/src/**/*.kt",
            "engine/*/src/**/*.kt",
            "products/**/src/**/*.kt",
            "storage/src/**/*.kt",
            "testingServer/src/**/*.kt",
            "ui/src/**/*.kt",
            "ui/core/src/**/*.kt",
            "ui/desktop/*/src/**/*.kt",
        )
        exclude(
            "**/build/**",
            "products/companion/di/src/**/*.kt",
            "products/companion/iosPacketTunnel/src/**/*.kt",
        )
    })
    allowedAwtPlatformAdapterPaths.set(
        listOf(
            "ui/core/src/jvmMain/kotlin/com/devuloopers/knet/ui/core/foundation/clipboard/ClipboardText.jvm.kt",
            // Toolkit is used only for native window events in this product/platform adapter.
            "products/desktop/src/jvmMain/kotlin/com/devuloopers/knet/products/desktop/window/MacDeviceTitleBar.kt",
        ),
    )
}

val verifyCompanionComposeUiOwnership by tasks.registering(VerifyCompanionComposeUiOwnershipTask::class) {
    group = "verification"
    description = "Verifies that Android companion screens remain owned by shared Compose Multiplatform UI."
    layoutResources.from(fileTree("products/companion/androidApp/src/main/res/layout") {
        include("**/*.xml")
    })
    productSources.from(fileTree("products/companion/androidApp/src/main") {
        include("**/*.kt")
        exclude("**/build/**")
    })
    sharedUiSources.from(fileTree("ui/companion/sharedUi/src/commonMain") {
        include("**/*.kt")
        exclude("**/build/**")
    })
}

val verifyArchitectureFoundation by tasks.registering {
    group = "verification"
    description = "Runs module ownership and target dependency-boundary checks."
    dependsOn(
        verifyModuleDocumentation,
        verifyModuleBoundaries,
        verifyUiRuntimeIsolation,
        verifyCompositionOwnership,
        verifyKotlinFirstSources,
        verifyCompanionComposeUiOwnership,
    )
}

/** Portable and Android companion gate. It is safe to run on Linux CI and never launches an app. */
tasks.register("companionSharedQualification") {
    group = "verification"
    description = "Runs companion models, workflows, persistence, presentation, Android adapters, and pairing crypto."
    dependsOn(
        verifyArchitectureFoundation,
        ":application:desktop:test",
        ":connectivity:desktop:jvmTest",
        ":core:companion:jvmTest",
        ":application:companion:jvmTest",
        ":data:companion:jvmTest",
        ":ui:companion:presentation:jvmTest",
        ":ui:companion:sharedUi:jvmTest",
        ":ui:core:jvmTest",
        ":ui:core:compileAndroidMain",
        ":connectivity:companion:testAndroidHostTest",
        ":data:companion:testAndroidHostTest",
        ":products:companion:di:testAndroidHostTest",
        ":data:companion:compileAndroidMain",
        ":ui:companion:presentation:compileAndroidMain",
        ":ui:companion:sharedUi:compileAndroidMain",
    )
}

/** Kotlin/Native companion gate. It must run on macOS and never launches or signs an app. */
tasks.register("companionIosFoundationQualification") {
    group = "verification"
    description = "Compiles and tests companion iOS consumers for device and Simulator targets."
    dependsOn(
        verifyArchitectureFoundation,
        ":ui:core:compileKotlinIosSimulatorArm64",
        ":products:companion:di:iosSimulatorArm64Test",
        ":connectivity:companion:compileKotlinIosArm64",
        ":connectivity:companion:compileKotlinIosSimulatorArm64",
        ":connectivity:companion:iosSimulatorArm64Test",
        ":core:identity:compileKotlinIosSimulatorArm64",
        ":core:pairing:compileKotlinIosSimulatorArm64",
        ":core:connectivity:compileKotlinIosSimulatorArm64",
        ":core:companion:compileKotlinIosSimulatorArm64",
        ":application:companion:compileKotlinIosSimulatorArm64",
        ":data:companion:compileKotlinIosSimulatorArm64",
        ":ui:companion:presentation:compileKotlinIosSimulatorArm64",
        ":ui:companion:sharedUi:compileKotlinIosSimulatorArm64",
    )
}

/** Complete companion foundation gate for macOS development and release qualification. */
tasks.register("companionFoundationQualification") {
    group = "verification"
    description = "Runs portable, Android, and iOS companion foundation qualification."
    dependsOn(
        "companionSharedQualification",
        "companionIosFoundationQualification",
    )
}

/** Installable Android companion product gate. It assembles the APK but never installs or launches it. */
tasks.register("companionAndroidProductQualification") {
    group = "verification"
    description = "Runs companion foundations, Android product composition tests/lint, and debug APK assembly."
    dependsOn(
        "companionSharedQualification",
        ":products:companion:androidApp:testDebugUnitTest",
        ":products:companion:androidApp:lintDebug",
        ":products:companion:androidApp:assembleDebug",
    )
}

/** Installable iOS companion product gate. It links the simulator framework but never launches an app. */
tasks.register("companionIosProductQualification") {
    group = "verification"
    description = "Runs companion foundations and links the iOS product framework for the simulator."
    dependsOn(
        "companionFoundationQualification",
        ":products:companion:iosApp:linkDebugFrameworkIosSimulatorArm64",
        ":products:companion:iosPacketTunnel:iosSimulatorArm64Test",
        ":products:companion:iosPacketTunnel:linkDebugFrameworkIosSimulatorArm64",
        ":products:companion:iosPacketTunnel:compileKotlinIosArm64",
    )
}

/** Cross-module HTTP/2 qualification gate used locally and by the desktop operating-system CI matrix. */
tasks.register("http2Qualification") {
    group = "verification"
    description = "Runs HTTP/2 transport, capture, persistence, API Studio, Traffic, and protocol-lab tests."
    dependsOn(
        verifyArchitectureFoundation,
        ":core:http:jvmTest",
        ":core:traffic:jvmTest",
        ":engine:proxy:test",
        ":data:desktop:jvmTest",
        ":storage:jvmTest",
        ":testingServer:test",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":products:desktop:test",
    )
}

/** Cross-module gRPC qualification gate. It never launches the desktop application. */
tasks.register("grpcQualification") {
    group = "verification"
    description = "Runs gRPC framing, persistence, breakpoints, API Studio, Traffic, and protocol-lab tests."
    dependsOn(
        verifyArchitectureFoundation,
        ":application:desktop:test",
        ":core:traffic:jvmTest",
        ":engine:grpc:test",
        ":engine:proxy:test",
        ":data:desktop:jvmTest",
        ":storage:jvmTest",
        ":testingServer:test",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:breakpointManager:jvmTest",
        ":ui:desktop:apiStudio:grpc:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":products:desktop:test",
    )
}

/** Cross-module WebSocket qualification gate. It never launches the desktop application. */
tasks.register("webSocketQualification") {
    group = "verification"
    description = "Runs WebSocket relay, framing, capture, breakpoints, API Studio, Traffic, and protocol-lab tests."
    dependsOn(
        verifyArchitectureFoundation,
        ":application:desktop:test",
        ":core:traffic:jvmTest",
        ":engine:proxy:test",
        ":engine:websocket:test",
        ":data:desktop:jvmTest",
        ":storage:jvmTest",
        ":testingServer:test",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:apiStudio:websocket:jvmTest",
        ":ui:desktop:breakpointManager:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":products:desktop:test",
    )
}

/** Cross-module GraphQL-over-WebSocket qualification gate. It never launches the desktop application. */
tasks.register("graphQLWebSocketQualification") {
    group = "verification"
    description = "Runs GraphQL WebSocket semantics, capture, breakpoints, API Studio, Traffic, and protocol-lab tests."
    dependsOn(
        verifyArchitectureFoundation,
        ":application:desktop:test",
        ":core:traffic:jvmTest",
        ":engine:proxy:test",
        ":engine:websocket:test",
        ":engine:graphqlWebSocket:test",
        ":data:desktop:jvmTest",
        ":storage:jvmTest",
        ":testingServer:test",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:apiStudio:websocket:jvmTest",
        ":ui:desktop:apiStudio:graphqlWebSocket:jvmTest",
        ":ui:desktop:breakpointManager:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":products:desktop:test",
    )
}

/** Cross-module Server-Sent Events qualification gate. It never launches the desktop application. */
tasks.register("sseQualification") {
    group = "verification"
    description = "Runs SSE codecs, capture, breakpoints, API Studio, Traffic, persistence, and protocol-lab tests."
    dependsOn(
        verifyArchitectureFoundation,
        ":application:desktop:test",
        ":core:http:jvmTest",
        ":core:traffic:jvmTest",
        ":engine:proxy:test",
        ":engine:sse:test",
        ":data:desktop:jvmTest",
        ":storage:jvmTest",
        ":testingServer:test",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:breakpointManager:jvmTest",
        ":ui:desktop:httpPanel:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":products:desktop:test",
    )
}

/** Explicit release-only SSE soak; defaults to three hours and is not part of ordinary CI. */
tasks.register("sseReleaseSoak") {
    group = "verification"
    description = "Runs the configurable long-duration SSE codec churn qualification."
    dependsOn(":engine:sse:sseReleaseSoak")
}

/** Cross-module Network Conditions gate for proxy, presentation, persistence, and Android host policy. */
tasks.register("networkConditionsQualification") {
    group = "verification"
    description = "Runs Network Conditions domain, persistence, engine, UI, API Studio, Traffic, and Android-host tests."
    dependsOn(
        verifyNetworkProtectionLedger,
        verifyArchitectureFoundation,
        ":core:domain:jvmTest",
        ":storage:jvmTest",
        ":data:desktop:jvmTest",
        ":application:desktop:test",
        ":engine:proxy:test",
        ":engine:protocol:test",
        ":engine:grpc:test",
        ":engine:websocket:test",
        ":engine:simulator:test",
        ":engine:graphqlWebSocket:test",
        ":engine:sse:test",
        ":ui:desktop:networkConditions:jvmTest",
        ":ui:desktop:apiStudio:jvmTest",
        ":ui:desktop:apiStudio:websocket:jvmTest",
        ":ui:desktop:apiStudio:graphqlWebSocket:jvmTest",
        ":ui:desktop:apiStudio:grpc:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":ui:desktop:app:jvmTest",
        ":core:companion:jvmTest",
        ":application:companion:jvmTest",
        ":connectivity:companion:testAndroidHostTest",
        ":products:desktop:test",
    )
}

/** Cross-module saved-policy/TLS gate; physical platform qualification remains independently tracked. */
tasks.register("protectedTrafficQualification") {
    group = "verification"
    description = "Qualifies saved TLS policy, compatible storage, manual-rule reconnects, Traffic controls, and composition."
    dependsOn(
        verifyNetworkProtectionLedger,
        verifyArchitectureFoundation,
        ":core:domain:jvmTest",
        ":core:traffic:jvmTest",
        ":application:desktop:test",
        ":storage:jvmTest",
        ":data:desktop:jvmTest",
        ":engine:proxy:test",
        ":engine:simulator:test",
        ":ui:core:jvmTest",
        ":ui:desktop:protectedTraffic:jvmTest",
        ":ui:desktop:traffic:jvmTest",
        ":ui:desktop:app:jvmTest",
        ":core:companion:jvmTest",
        ":application:companion:jvmTest",
        ":connectivity:companion:testAndroidHostTest",
        ":products:desktop:test",
    )
}

/** Aggregate gate with independently checked XML evidence; it never launches the desktop application. */
tasks.register<VerifyNetworkProtectionReportsTask>("networkProtectionQualification") {
    group = "verification"
    description = "Runs both feature gates and rejects missing, failed or skipped mandatory test evidence."
    dependsOn("networkConditionsQualification", "protectedTrafficQualification", ":data:desktop:canonicalTrafficScaleQualification")
    repositoryDirectory.set(layout.projectDirectory)
    reportDirectories.set(listOf(
        "core/domain/build/test-results/jvmTest", "core/traffic/build/test-results/jvmTest",
        "core/companion/build/test-results/jvmTest", "application/desktop/build/test-results/test",
        "application/companion/build/test-results/jvmTest", "storage/build/test-results/jvmTest",
        "data/desktop/build/test-results/jvmTest", "data/desktop/build/test-results/canonicalTrafficScaleQualification",
        "engine/proxy/build/test-results/test",
        "engine/protocol/build/test-results/test", "engine/grpc/build/test-results/test",
        "engine/websocket/build/test-results/test", "engine/graphqlWebSocket/build/test-results/test",
        "engine/sse/build/test-results/test", "engine/simulator/build/test-results/test",
        "ui/core/build/test-results/jvmTest", "ui/desktop/networkConditions/build/test-results/jvmTest",
        "ui/desktop/protectedTraffic/build/test-results/jvmTest", "ui/desktop/traffic/build/test-results/jvmTest",
        "ui/desktop/apiStudio/build/test-results/jvmTest", "ui/desktop/apiStudio/websocket/build/test-results/jvmTest",
        "ui/desktop/apiStudio/graphqlWebSocket/build/test-results/jvmTest", "ui/desktop/apiStudio/grpc/build/test-results/jvmTest",
        "ui/desktop/app/build/test-results/jvmTest", "products/desktop/build/test-results/test",
        "connectivity/companion/build/test-results/testAndroidHostTest",
    ))
    reports.from(reportDirectories.map { directories -> directories.map { relative -> layout.projectDirectory.dir(relative).asFileTree.matching { include("TEST-*.xml") } } })
    requiredSuites.set(listOf(
        "com.devuloopers.knet.engine.proxy.tls.TlsServerFlightMetadataTest",
        "com.devuloopers.knet.engine.proxy.tls.TlsResumedCertificateFlightTest",
        "com.devuloopers.knet.traffic.model.TlsFallbackClientScopeTest",
        "com.devuloopers.knet.data.desktop.protectedtraffic.AutomaticTlsFallbackTest",
        "com.devuloopers.knet.data.desktop.protectedtraffic.AutomaticTlsFallbackSocketTest",
        "com.devuloopers.knet.application.usecase.protectedtraffic.AutomaticTlsFallbackHandlingTest",
        "com.devuloopers.knet.ui.desktop.protectedtraffic.model.ProtectedRuleSearchTest",
        "com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficManagerViewModelTest",
        "com.devuloopers.knet.ui.desktop.protectedtraffic.view.ProtectedTrafficSettingsTest",
        "com.devuloopers.knet.companion.connectivity.transport.UdpAssociationLifecycleTest",
        "com.devuloopers.knet.companion.connectivity.transport.LivePacketPolicyTest",
        "com.devuloopers.knet.companion.application.usecase.MaintainCompanionPacketPolicyUseCaseTest",
        "com.devuloopers.knet.engine.proxy.tls.TlsCertificateFlightObserverTest",
        "com.devuloopers.knet.data.desktop.protectedtraffic.ManualProtectedTrafficSocketIntegrationTest",
        "com.devuloopers.knet.data.desktop.capture.OpaqueKeepAliveCaptureTest",
        "com.devuloopers.knet.data.desktop.capture.OpaqueClosureCaptureTest",
        "com.devuloopers.knet.traffic.model.OpaqueFlowClosureTest",
        "com.devuloopers.knet.ui.desktop.traffic.model.OpaqueFlowStatusPresentationTest",
        "com.devuloopers.knet.data.desktop.protectedtraffic.ProtectedHttpTwoTunnelIntegrationTest",
        "com.devuloopers.knet.data.desktop.networkconditions.NetworkConditionAtomicSnapshotTest",
        "com.devuloopers.knet.engine.proxy.pipeline.ProxyTransportIdleHandlerTest",
        "com.devuloopers.knet.engine.proxy.pipeline.RawTunnelInactivityTest",
        "com.devuloopers.knet.engine.proxy.handler.RawTunnelFailureReasonTest",
        "com.devuloopers.knet.engine.proxy.handler.RawTunnelCloseAttributionTest",
        "com.devuloopers.knet.engine.proxy.handler.RawTunnelTerminalLifecycleTest",
        "com.devuloopers.knet.engine.proxy.handler.RawTunnelReadBackpressureTest",
        "com.devuloopers.knet.engine.proxy.handler.DuplexTransformLifecycleTest",
        "com.devuloopers.knet.engine.simulator.ShapedInputShutdownTest",
        "com.devuloopers.knet.engine.simulator.ProtectedTunnelUploadEofIntegrationTest",
        "com.devuloopers.knet.engine.simulator.SseNetworkConditionIntegrationTest",
        "com.devuloopers.knet.data.desktop.capture.CanonicalTrafficScaleBaselineTest",
        "com.devuloopers.knet.products.desktop.CoroutineScopeShutdownResourceTest",
    ))
    summaryFile.set(layout.buildDirectory.file("reports/network-protection/qualification-summary.json"))
    ledger.set(layout.projectDirectory.file("docs/qualification/network_protection_cases.tsv"))
}

// The measured storage stage retains its real threshold while avoiding simultaneous feature compilation/tests.
project(":data:desktop").tasks.matching { it.name == "canonicalTrafficScaleQualification" }.configureEach {
    mustRunAfter("${rootProject.path}networkConditionsQualification", "${rootProject.path}protectedTrafficQualification")
}

/** Standard Phase 18 release gate; extended soak is invoked separately with its configured duration. */
tasks.register("phase18ReleaseGate") {
    group = "verification"
    description = "Runs architecture checks, all tests, and the packaged desktop distributable gate."
    dependsOn(
        verifyArchitectureFoundation,
        subprojects.filter { module ->
            module.buildFile.isFile && module.buildFile.readText().contains("plugins")
        }.map { module -> "${module.path}:check" },
        ":products:desktop:createDistributable",
    )
}

subprojects {
    tasks.matching { task -> task.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitectureFoundation"))
    }
}

/** Checks executed multi-device regression reports independently of task success and native hardware claims. */
abstract class VerifyMultiDeviceReportsTask : DefaultTask() {
    /** Per-task XML output directories provided by each module's actual buildDirectory. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportDirectories: ConfigurableFileCollection
    /** New regression suites which must be present in those outputs. */
    @get:Input abstract val requiredSuites: ListProperty<String>
    /** Reproducible machine-readable execution totals for this gate. */
    @get:OutputFile abstract val summaryFile: RegularFileProperty

    /** Fails on missing/empty reports, failed cases or absent required regressions. */
    @TaskAction fun verify() {
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder()
        val suites = mutableSetOf<String>()
        var total = 0
        var skipped = 0
        val counts = reportDirectories.files.sortedBy { it.path }.map { directory ->
            val files = directory.listFiles { file -> file.name.startsWith("TEST-") && file.extension == "xml" }.orEmpty()
            check(files.isNotEmpty()) { "Multi-device test reports missing: $directory" }
            var count = 0
            files.forEach { file ->
                val suite = parser.parse(file).documentElement
                check(listOf("failures", "errors").all { suite.getAttribute(it).ifEmpty { "0" }.toInt() == 0 }) { "Failed multi-device regression: $file" }
                val cases = suite.getElementsByTagName("testcase")
                for (index in 0 until cases.length) suites += (cases.item(index) as org.w3c.dom.Element).getAttribute("classname")
                skipped += suite.getAttribute("skipped").ifEmpty { "0" }.toInt()
                count += suite.getAttribute("tests").toInt()
            }
            check(count > 0) { "Empty multi-device test reports: $directory" }
            total += count
            count
        }
        check(requiredSuites.get().all { required -> suites.any { it == required || it.endsWith(".$required") } }) {
            "Required multi-device regression suite missing: ${requiredSuites.get().filterNot { required -> suites.any { it == required || it.endsWith(".$required") } }}"
        }
        check(skipped == 0) { "Multi-device automated gate has $skipped skipped cases; inspect them before claiming qualification." }
        val summary = summaryFile.get().asFile
        summary.parentFile.mkdirs()
        summary.writeText("{\"tests\":$total,\"failures\":0,\"skipped\":$skipped,\"taskCounts\":[${counts.joinToString(",")}]}\n")
        logger.lifecycle("Multi-device qualification: $total tests across ${counts.size} task outputs. Hardware/native gestures remain separate.")
    }
}

/** Complete affected JVM/Android test outputs required by the multi-device gate. */
val multiDeviceTestTasks = mapOf(
    ":core:companion" to "jvmTest", ":application:desktop" to "test", ":application:companion" to "jvmTest",
    ":connectivity:desktop" to "multiDeviceControlQualification", ":connectivity:companion" to "testAndroidHostTest",
    ":data:desktop" to "jvmTest", ":storage" to "jvmTest", ":engine:proxy" to "test", ":engine:protocol" to "test",
    ":ui:desktop:app" to "jvmTest", ":ui:desktop:traffic" to "jvmTest", ":products:desktop" to "test",
    ":products:companion:androidApp" to "testDebugUnitTest", ":products:companion:di" to "testAndroidHostTest",
)

/** Desktop/Android qualification with complete affected test outputs and build artifacts, without app launch. */
tasks.register<VerifyMultiDeviceReportsTask>("multiDeviceQualification") {
    group = "verification"
    description = "Qualifies authenticated multi-device workspaces, control channels, source storage, native builds and regressions."
    dependsOn("verifyArchitectureFoundation", ":products:desktop:createDistributable", ":products:companion:androidApp:assembleDebug", ":data:desktop:canonicalTrafficScaleQualification")
    dependsOn(multiDeviceTestTasks.map { (module, task) -> "$module:$task" })
    reportDirectories.from(multiDeviceTestTasks.map { (module, task) -> project(module).layout.buildDirectory.dir("test-results/$task") })
    reportDirectories.from(project(":data:desktop").layout.buildDirectory.dir("test-results/canonicalTrafficScaleQualification"))
    requiredSuites.set(listOf(
        "com.devuloopers.knet.data.desktop.capture.CanonicalTrafficScaleBaselineTest",
        "com.devuloopers.knet.companion.model.CompanionSessionProtocolTest",
        "com.devuloopers.knet.ui.desktop.traffic.TrafficPagingViewModelTest",
        "com.devuloopers.knet.engine.proxy.handler.SourceTrafficMeterHandlerTest",
        "com.devuloopers.knet.connectivity.desktop.gateway.ControlSessionOutboundTest",
        "com.devuloopers.knet.connectivity.desktop.gateway.ControlConnectionAdmissionTest",
        "com.devuloopers.knet.application.device.DevicePresenceCoordinatorTest",
        "com.devuloopers.knet.application.device.ObserveDeviceWorkspacesUseCaseTest",
        "com.devuloopers.knet.data.desktop.capture.CanonicalTrafficHistoryQueryTest",
        "com.devuloopers.knet.companion.application.usecase.MaintainCompanionSessionUseCaseTest",
        "com.devuloopers.knet.companion.connectivity.control.AndroidCompanionSessionTransportTest",
        "com.devuloopers.knet.data.desktop.traffic.SourceTrafficMetersTest",
        "com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspaceViewModelTest",
        "com.devuloopers.knet.ui.desktop.app.workspace.DeviceWorkspacePresentationTest",
        "com.devuloopers.knet.products.desktop.window.NativeTitleBarPolicyTest",
    ))
    summaryFile.set(layout.buildDirectory.file("reports/multi-device/automated-summary.json"))
}

/** Simulator/device-target compile and Simulator tests; signed NetworkExtension hardware checks stay manual. */
tasks.register<VerifyMultiDeviceReportsTask>("multiDeviceAppleQualification") {
    group = "verification"
    description = "Qualifies the iOS extension's multi-device session lifecycle and native linkage without hardware."
    dependsOn(":products:companion:iosPacketTunnel:iosSimulatorArm64Test", ":products:companion:iosPacketTunnel:linkDebugFrameworkIosSimulatorArm64",
        ":products:companion:iosPacketTunnel:compileKotlinIosArm64")
    reportDirectories.from(project(":products:companion:iosPacketTunnel").layout.buildDirectory.dir("test-results/iosSimulatorArm64Test"))
    requiredSuites.set(listOf("com.devuloopers.knet.companion.packettunnel.network.NativeCompanionControlSessionTest"))
    summaryFile.set(layout.buildDirectory.file("reports/multi-device/apple-summary.json"))
}
