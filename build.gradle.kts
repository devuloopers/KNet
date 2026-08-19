import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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

    /** Pure engine packages deliberately approved for presentation-only formatting. */
    @get:Input
    abstract val allowedEngineImportPrefixes: ListProperty<String>

    /** Pure engine project dependencies deliberately approved for presentation. */
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

    /** JVM source files allowed to adapt Compose clipboard entries to the desktop runtime. */
    @get:Input
    abstract val allowedAwtClipboardPaths: ListProperty<String>

    /** Verifies common-source portability and prohibited production Java/coroutine shortcuts. */
    @TaskAction
    fun verify() {
        val repositoryRoot = repositoryDirectory.get().asFile
        val commonViolations = commonSources.files.sortedBy { it.path }.flatMap { source ->
            source.codeLines().mapNotNull { (lineNumber, line) ->
                when {
                    line.startsWith("import java.") || line.startsWith("import javax.") ->
                        "${source.relativeTo(repositoryRoot)}:$lineNumber imports a JVM API from commonMain"
                    JVM_QUALIFIED_REFERENCE.containsMatchIn(line) ->
                        "${source.relativeTo(repositoryRoot)}:$lineNumber references a JVM API from commonMain"
                    SYSTEM_REFERENCE.containsMatchIn(line) ->
                        "${source.relativeTo(repositoryRoot)}:$lineNumber references System from commonMain"
                    else -> null
                }
            }
        }

        val approvedClipboardPaths = allowedAwtClipboardPaths.get().toSet()
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
                        add("$relativePath:$lineNumber uses AWT clipboard outside the platform adapter")
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

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
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
    ":application" to setOf(
        ":core:traffic",
        ":core:scripting",
        ":core:domain",
        ":core:connectivity",
        ":core:identity",
        ":core:pairing",
    ),
    ":core:domain" to setOf(":core:logger", ":core:traffic", ":core:scripting"),
    ":core:scripting" to emptySet(),
    ":core:traffic" to emptySet(),
    ":core:identity" to emptySet(),
    ":core:pairing" to setOf(":core:identity"),
    ":core:connectivity" to emptySet(),
    ":connectivity:desktop" to setOf(
        ":application",
        ":core:traffic",
        ":core:connectivity",
        ":core:identity",
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
    allowedEngineImportPrefixes.set(listOf("com.devuloopers.knet.engine.formatter."))
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
            "products/src/**/*.kt",
            "products/desktop/src/**/*.kt",
            "storage/src/**/*.kt",
            "testingServer/src/**/*.kt",
            "ui/src/**/*.kt",
            "ui/core/src/**/*.kt",
            "ui/desktop/*/src/**/*.kt",
        )
        exclude("**/build/**")
    })
    allowedAwtClipboardPaths.set(
        listOf("ui/core/src/jvmMain/kotlin/com/devuloopers/knet/ui/core/foundation/clipboard/ClipboardText.jvm.kt"),
    )
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
    )
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
