# KNet Feature Architecture Strategy

This document specifies the mandatory architectural standard for all feature developments and widget implementations in KNet.

---

## 🏛️ Architectural Overview & Strict Flow Contract

KNet enforces a **Feature-Centric MVVM Architecture with Unidirectional Data Flow (UDF)** and a strict **Single-Direction Flow Contract**:

$$\text{UI (View)} \xrightarrow{\text{Intents / State}} \text{ViewModel} \xrightarrow{\text{UseCases}} \text{UseCase(s)} \xrightarrow{\text{Data Access}} \text{Repository}$$

```
                             UI Layer (`sharedUI`)
                     ┌───────────────────────────────────┐
                     │           Composable View         │
                     └─────────────────┬─────────────────┘
                                       │ Emits Sealed Intents
                                       ▼
                     ┌───────────────────────────────────┐
                     │             ViewModel             │
                     └─────────────────┬─────────────────┘
                                       │ Calls ONLY UseCases (No Direct Repository Access)
                                       ▼
                           Domain Layer (`domain`)
                     ┌───────────────────────────────────┐
                     │             UseCases              │
                     └─────────────────┬─────────────────┘
                                       │ Accesses Repositories & Operates on Entities
                                       ▼
                            Data Layer (`data` / `storage`)
                     ┌───────────────────────────────────┐
                     │         Offline Repository        │
                     └───────────────────────────────────┘
```

---

## 📦 Package Conventions

Every feature must mirror its package name across all three layers (`domain`, `data`, and `sharedUI`):

- **Domain Layer**: `com.devuloopers.knet.domain.<feature_name>`
  - `di/`: Feature-specific domain Koin module (e.g. `LiveTrafficDomainModule.kt`)
  - `model/`: Sealed UI States, Enums, Value Objects
  - `usecase/`: Pure Kotlin use cases executing off-thread business logic
  - *Global Module DI Aggregator*: `com.devuloopers.knet.domain.di.DomainModule`
- **Data Layer**: `com.devuloopers.knet.data.<feature_name>`
  - `di/`: Feature-specific data Koin module (e.g. `LiveTrafficDataModule.kt`)
  - `repository/`: Repository contracts & offline persistent implementations
  - `datasource/`: Database DAOs (Room/SQLite) & File payload stores
  - *Global Module DI Aggregator*: `com.devuloopers.knet.data.di.DataModule`
- **UI Layer**: `com.devuloopers.knet.ui.<feature_name>`
  - `di/`: Feature-specific UI Koin module (e.g. `LiveTrafficUiModule.kt`)
  - `viewmodel/`: ViewModels managing StateFlow and Intent handling
  - `view/`: Pure layout Composables
  - `components/`: Modular sub-composables (toolbars, search bars, list items)
  - *Global Module DI Aggregator*: `com.devuloopers.knet.ui.di.UiModule`

---

## 🛡️ Core Architecture Rules

### 1. Strongly Typed Enums & Value Objects
- Avoid raw magic strings for filters, protocols, or status categories.
- Always use `enum class` or `sealed class` (e.g. `ProtocolFilter.HTTPS`, `WidgetType.TRAFFIC_FEED`).

### 2. Sealed UI States
- Every feature UI must expose an immutable `Sealed Interface` / `Sealed Class` representing all possible rendering states:
  ```kotlin
  sealed interface LiveTrafficUiState {
      object Loading : LiveTrafficUiState
      data class Success(
          val transactions: List<TrafficItemUiState>,
          val totalCount: Int,
          val activeFilter: ProtocolFilter,
          val searchQuery: String
      ) : LiveTrafficUiState
      data class Error(val message: String) : LiveTrafficUiState
  }
  ```

### 3. Unidirectional Data Flow (UDF) via Sealed Intents
- Views must not call ad-hoc ViewModel mutation methods.
- Views emit strongly typed `Intent` objects to the ViewModel:
  ```kotlin
  sealed interface LiveTrafficIntent {
      data class SelectProtocol(val filter: ProtocolFilter) : LiveTrafficIntent
      data class SearchQueryChanged(val query: String) : LiveTrafficIntent
      object ClearTraffic : LiveTrafficIntent
      data class SelectTransaction(val transactionId: String) : LiveTrafficIntent
  }
  ```

### 4. Pre-Calculated UI Display Models
- Do not perform formatting, color calculations, or string concatenations inside `@Composable` functions during recomposition.
- Perform all display mapping inside the ViewModel or UseCase when converting domain DTOs to UI models (`TrafficItemUiState`).

### 5. Database Push-Down Filtering
- Filtering, sorting, and search queries must be pushed down to the `Repository` and executed via indexed database queries or background `Dispatchers.Default` Coroutines.

### 6. Strict Single-Direction Layer Flow (`UI -> ViewModel -> UseCase -> Repository`)
- **No Direct Repository Access in ViewModels**: ViewModels MUST NOT inject or reference `Repository` interfaces directly.
- **UseCase Isolation**: Every operation (reading streams, executing mutations, clearing data) MUST be wrapped in a dedicated UseCase (e.g., `GetLiveTrafficUseCase`, `ClearLiveTrafficUseCase`).
- **Clean Dependency Injection**: ViewModels inject UseCases via Koin; UseCases inject Repositories via Koin.
