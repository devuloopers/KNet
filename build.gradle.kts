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

    /** JVM source files allowed to adapt Compose clipboard entries to the desktop runtime. */
    @get:Input
    abstract val allowedAwtClipboardPaths: ListProperty<String>

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
        const val COMPANION_COMMON_SOURCE_PREFIX = "connectivity/companion/src/commonMain/"
        const val COMPANION_EXPECT_FACTORY_PATH =
            "connectivity/companion/src/commonMain/kotlin/com/devuloopers/knet/companion/connectivity/platform/PlatformCompanionAdapterFactory.kt"
        val COMPANION_NATIVE_CONTEXT_REFERENCE = Regex("""\b[A-Za-z0-9_.]*Context\b""")
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
    allowedAwtClipboardPaths.set(
        listOf("ui/core/src/jvmMain/kotlin/com/devuloopers/knet/ui/core/foundation/clipboard/ClipboardText.jvm.kt"),
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

/** Shared/Android companion foundation gate. It compiles portable iOS consumers and never launches an app. */
tasks.register("companionFoundationQualification") {
    group = "verification"
    description = "Runs companion models, workflows, persistence, presentation, Android adapters, pairing crypto, and iOS compile gates."
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
        ":ui:core:compileKotlinIosSimulatorArm64",
        ":connectivity:companion:testAndroidHostTest",
        ":data:companion:testAndroidHostTest",
        ":products:companion:di:testAndroidHostTest",
        ":products:companion:di:iosSimulatorArm64Test",
        ":connectivity:companion:compileKotlinIosArm64",
        ":connectivity:companion:compileKotlinIosSimulatorArm64",
        ":connectivity:companion:iosSimulatorArm64Test",
        ":data:companion:compileAndroidMain",
        ":ui:companion:presentation:compileAndroidMain",
        ":ui:companion:sharedUi:compileAndroidMain",
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

/** Installable Android companion product gate. It assembles the APK but never installs or launches it. */
tasks.register("companionAndroidProductQualification") {
    group = "verification"
    description = "Runs companion foundations, Android product composition tests/lint, and debug APK assembly."
    dependsOn(
        "companionFoundationQualification",
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
