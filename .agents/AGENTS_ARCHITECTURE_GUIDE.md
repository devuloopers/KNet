# KNet Developer & Agent Architecture Guide

This document establishes the official architectural standards, design patterns, coding rules, and module interaction flow for KNet development.

---

## 🏛 1. Strict Clean Architecture Layering

Every feature added to KNet MUST strictly follow the Clean Architecture dependency flow:

```
[ UI Layer ] ────────> [ ViewModel Layer ] ────────> [ UseCase Layer ] ────────> [ Repository Layer ] ────────> [ Data Source Layer ]
(sharedUI)            (sharedUI/viewmodel)           (domain/usecase)             (domain/repository)           (storage / proxyEngine)
```

### Layer Rules & Boundaries:

1. **UI Layer (`sharedUI`)**:
   - Contains Compose Multiplatform composables (`TrafficFeedWidget`, `CodeViewerWidget`, `TimingsWidget`, `WidgetSearchBar`).
   - UI components observe immutable `StateFlow` from ViewModels.
   - UI components NEVER execute data processing or raw database/network calls directly.

2. **ViewModel Layer (`sharedUI/src/commonMain/kotlin/.../viewmodel`)**:
   - Exposes UI state via `StateFlow<UiState>`.
   - Delegates all business logic to specific, single-responsibility **UseCases**.
   - Handles coroutine scoping using `viewModelScope`.

3. **UseCase Layer (`domain/src/main/kotlin/.../usecase`)**:
   - Pure Kotlin classes (e.g. `GetLiveTrafficUseCase`, `GetTransactionDetailUseCase`).
   - Implements single business operations.
   - Encapsulates sorting, ordering (e.g., `sequentialId = totalCount - index`), search filtering, and state transformation.

4. **Repository Layer (`domain` & `data`)**:
   - Interface declared in `domain` module (e.g. `KNetCoreRepository.kt`).
   - Implementation provided in `data` module (e.g. `KNetCoreRepositoryImpl.kt`).
   - Mediates between live Netty proxy engine streams and persistent Room database storage.

5. **Data Source Layer (`storage` & `proxyEngine`)**:
   - `storage`: Room Database (v2) managing `HttpTransactionEntity` and SQL migrations (`MIGRATION_1_2`).
   - `proxyEngine`: Netty pipeline capturing raw network traffic and real socket timing metrics.

---

## 📏 2. Custom Development Rules & Naming Directives

### 2.1 Public API Documentation Rule
- Every public class, interface, method, and object must include descriptive KDoc comments explaining purpose, parameters, and return values.

### 2.2 Variable Naming Rules
- **NO shorthand context names**: Never use `ctx`. Always use full parameter names like `context: ChannelHandlerContext` or `context: Context`.
- **NO single-character lambda parameters**: Never use `f`, `e`, `it` (when ambiguous). Use full descriptive names like `handshakeFuture`, `exception`, `transaction`.

### 2.3 Application Execution Directive
- **Do NOT launch the desktop application** (`:desktopApp:run`) during automated agent sessions.
- Always verify changes via build and test commands:
  ```bash
  ./gradlew :domain:compileKotlin :sharedUI:compileKotlinJvm :bodyFormatter:test :sharedUI:jvmTest
  ```

### 2.4 Truthful Network Timing Metrics Directive
- Socket metrics (`dnsMs`, `tcpMs`, `tlsMs`, `ttfbMs`, `downloadMs`) MUST represent 100% real measured socket timings.
- Never use guessed multipliers or artificial offsets. Total duration MUST equal:
  $$\text{Total} = \text{DNS} + \text{TCP} + \text{TLS} + \text{TTFB} + \text{Download}$$

---

## 🧩 3. How to Extend KNet (Step-by-Step Guides)

### 3.1 Adding a New Body Formatter (`bodyFormatter` Module)
1. Create `YourFormatBodyFormatter.kt` implementing `BodyFormatter` interface in `bodyFormatter/src/main/kotlin/com/devuloopers/knet/bodyformatter/formatter/`.
2. Implement `priority`, `matches(headers, bodyText)`, and `format(headers, bodyText)`.
3. Add a corresponding `BodyFormat.YourFormat` sealed class case in `BodyFormat.kt`.
4. Register the new formatter in `BodyFormatterRegistry.kt`.
5. Add unit tests in `bodyFormatter/src/test/kotlin/`.

### 3.2 Adding a New Syntax Highlighter (`sharedUI` Module)
1. Create `YourLanguageHighlighter.kt` implementing `CodeLanguageHighlighter` in `sharedUI/src/commonMain/kotlin/com/devuloopers/knet/highlighter/`.
2. Reuse `TagMarkupHighlighter` (for markup languages) or `CollapsedBadge` (for code folding).
3. Register the highlighter in `CodeHighlighterRegistry.kt`.
4. Add unit tests in `sharedUI/src/commonTest/kotlin/com/devuloopers/knet/CodeHighlighterTest.kt`.

### 3.3 Upgrading Room Database Schema (`storage` Module)
1. Increment database version in `KNetDatabase.kt` (e.g. `@Database(version = 3)`).
2. Create migration script `val MIGRATION_2_3 = object : Migration(2, 3) { ... }`.
3. Register migration in `Room.databaseBuilder(...).addMigrations(MIGRATION_2_3)`.

---

## 🚀 4. Quick Context Checklist for Future Sessions

When starting a new session on KNet:
1. Read `README.md` for module overview and Content-Type status.
2. Read `.agents/AGENTS_ARCHITECTURE_GUIDE.md` for architectural rules.
3. Check `docs/implementation_plan.md` for ongoing phase status.
4. Run verification command:
   ```bash
   ./gradlew :domain:compileKotlin :sharedUI:compileKotlinJvm :bodyFormatter:test :sharedUI:jvmTest
   ```
