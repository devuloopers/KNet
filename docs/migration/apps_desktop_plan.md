# Architecture Specification & Migration Plan — `:apps:desktop` (Frozen 10/10 Architecture)

**Target Module:** `apps/desktop/`  
**Gradle Module:** `:apps:desktop`  
**Package Namespace:** `com.devuloopers.knet.apps.desktop`  
**Platform:** Desktop JVM (Compose Multiplatform)  
**Status:** Frozen Final Stable Architecture Specification

---

# 📌 Composition Root Vision

`:apps:desktop` is the **Desktop Application Composition Root** of KNet.

It exists only to assemble the application.

It loads configuration via `DesktopConfiguration.load()`, sequences initializers by `priority`, registers Koin DI modules organized by layer (`core`, `storage`, `data`, `engine`, `ui`), installs JVM shutdown hooks, and launches Compose `application`.

It owns **zero business logic**, **no manual repository/database/proxy creations**, and **no singleton creation in Compose**.

---

# 🏗 Frozen Package Structure

```text
apps/
└── desktop/
    ├── build.gradle.kts
    │
    └── src/
        ├── jvmMain/
        │   └── kotlin/
        │       └── com/devuloopers/knet/apps/desktop/
        │           │
        │           ├── Main.kt
        │           │
        │           ├── config/
        │           │   ├── DesktopConfiguration.kt
        │           │   └── Environment.kt
        │           │
        │           ├── bootstrap/
        │           │   ├── DesktopBootstrap.kt
        │           │   ├── ApplicationInitializer.kt
        │           │   ├── LoggingInitializer.kt
        │           │   ├── ExceptionHandlerInitializer.kt
        │           │   └── KoinInitializer.kt
        │           │
        │           ├── lifecycle/
        │           │   ├── ShutdownAware.kt
        │           │   └── ApplicationLifecycle.kt
        │           │
        │           └── di/
        │               └── DesktopModules.kt
        │
        └── jvmTest/
            └── kotlin/
                └── com/devuloopers/knet/apps/desktop/
                    ├── DesktopBootstrapTest.kt
                    ├── DesktopConfigurationTest.kt
                    ├── DesktopModulesTest.kt
                    ├── ApplicationLifecycleTest.kt
                    └── BootstrapInitializationTest.kt
```

---

# 🎯 Component Specifications

### 1. Configuration (`config/`)
- **`Environment.kt`**:
  ```kotlin
  public enum class Environment {
      DEVELOPMENT,
      STAGING,
      PRODUCTION
  }
  ```
- **`DesktopConfiguration.kt`**: Owns centralized paths & factory entry point:
  ```kotlin
  public data class DesktopConfiguration(
      val environment: Environment = Environment.DEVELOPMENT,
      val appDirectory: Path = Path.of(System.getProperty("user.home"), ".knet"),
      val databaseDirectory: Path = appDirectory.resolve("database"),
      val logDirectory: Path = appDirectory.resolve("logs")
  ) {
      public companion object {
          public fun load(): DesktopConfiguration = DesktopConfiguration()
      }
  }
  ```

### 2. Priority-Based Initializers (`bootstrap/`)
- **`ApplicationInitializer.kt`**:
  ```kotlin
  public interface ApplicationInitializer {
      public val priority: Int
      public fun initialize(configuration: DesktopConfiguration)
  }
  ```
- **`LoggingInitializer`**: `priority = 100` — Configures Kermit logging & log formatting.
- **`ExceptionHandlerInitializer`**: `priority = 200` — Installs JVM default uncaught exception handler.
- **`KoinInitializer`**: `priority = 300` — Starts Koin DI container (`DesktopModules.all`) & binds configuration.

- **`DesktopBootstrap.kt`**:
  ```kotlin
  public object DesktopBootstrap {
      private val initializers: List<ApplicationInitializer> = listOf(
          LoggingInitializer,
          ExceptionHandlerInitializer,
          KoinInitializer
      )

      public fun start(configuration: DesktopConfiguration = DesktopConfiguration.load()) {
          initializers.sortedBy { it.priority }.forEach { it.initialize(configuration) }
          ApplicationLifecycle.installShutdownHook()

          application {
              MainWindow()
          }
      }
  }
  ```

### 3. Decoupled Lifecycle (`lifecycle/`)
- **`ShutdownAware.kt`**:
  ```kotlin
  public interface ShutdownAware {
      public fun close()
  }
  ```
- **`ApplicationLifecycle.kt`**:
  ```kotlin
  public object ApplicationLifecycle {
      private val resources = CopyOnWriteArrayList<ShutdownAware>()

      public fun registerResource(resource: ShutdownAware) {
          resources.add(resource)
      }

      public fun installShutdownHook() {
          Runtime.getRuntime().addShutdownHook(Thread {
              shutdown()
          })
      }

      public fun shutdown() {
          resources.reversed().forEach { resource ->
              try {
                  resource.close()
              } catch (e: Exception) {
                  // Log error safely
              }
          }
      }
  }
  ```

### 4. Layered Composition Root DI (`di/`)
- **`DesktopModules.kt`**:
  ```kotlin
  public object DesktopModules {
      public val core: List<Module> = emptyList()
      public val storage: List<Module> = emptyList()
      public val data: List<Module> = listOf(dataModule)
      public val engine: List<Module> = emptyList()
      public val ui: List<Module> = listOf(desktopAppUiModule)

      public val all: List<Module> = core + storage + data + engine + ui
  }
  ```

### 5. Entry Point (`Main.kt`)
```kotlin
fun main() {
    DesktopBootstrap.start()
}
```

---

# 🔄 System Sequence

### Startup Sequence
```text
Main()
  ↓
DesktopBootstrap.start()
  ↓
DesktopConfiguration.load()
  ↓
LoggingInitializer (priority=100)
  ↓
ExceptionHandlerInitializer (priority=200)
  ↓
KoinInitializer (priority=300)
  ↓
ApplicationLifecycle.installShutdownHook()
  ↓
Compose Application
  ↓
MainWindow
```

### Shutdown Sequence
```text
Window Close / JVM Shutdown
  ↓
ApplicationLifecycle.shutdown()
  ↓
ShutdownAware Resources (Reversed)
  ↓
resource.close()
  ↓
Exit JVM
```

---

# 🧪 Unit Test Suite

- `DesktopBootstrapTest`
- `DesktopConfigurationTest`
- `DesktopModulesTest`
- `ApplicationLifecycleTest`
- `BootstrapInitializationTest`
