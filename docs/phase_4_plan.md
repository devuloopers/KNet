# KNet Phase 4 Plan [COMPLETED]: Storage, Workspaces & Sessions (storage & sessionManager)

This document specifies the exact design, files, dependencies, and implementation details for Phase 4 of KNet development, which introduces database persistence using Android Room, file-based payload body caching, session buffering, HAR export, and cURL generation.

---

## 1. Module Registration and Build Infrastructure

We will create two new subprojects: `:storage` and `:sessionManager`.

### 1.1 settings.gradle.kts
Register the modules in `settings.gradle.kts`:
```kotlin
include(":desktopApp")
include(":shared")
include(":logger")
include(":certificateManager")
include(":proxyEngine")
include(":interceptor")
include(":storage")
include(":sessionManager")
```

### 1.2 Version Catalog (libs.versions.toml)
Add Room and SQLite KMP dependencies:
```toml
[versions]
room = "2.8.4"
ksp = "2.3.10"
sqlite = "2.5.2"

[libraries]
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

### 1.3 storage/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)
    
    testImplementation(kotlin("test"))
}
```

### 1.4 sessionManager/build.gradle.kts
Configure dependencies:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(project(":storage"))
    
    testImplementation(kotlin("test"))
}
```

### 1.5 build.gradle.kts (Root)
Declare KSP and Room plugins with `apply false`:
```kotlin
plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
```


---

## 2. Storage Architecture

To prevent SQLite database bloat when capturing large amounts of traffic, we will split the request/response data:
* **Metadata**: persists HTTP methods, headers, status codes, URLs, timestamps, duration, and local file references inside Room SQLite.
* **Payload Bodies**: request/response body bytes are saved to distinct files in a temporary cache directory (e.g. `.knet/payloads/`). Only the file path string is saved in the database.

### 2.1 Database Schema (Entities)
All storage classes will reside in package `com.devuloopers.knet.storage`.

#### HttpTransactionEntity
Represents a single HTTP session transaction.
* `val id: String` (Primary Key)
* `val url: String`
* `val method: String`
* `val requestHeadersJson: String` (Serialized JSON)
* `val requestBodyPath: String?` (Path to request body file on disk)
* `val responseStatusCode: Int?`
* `val responseStatusText: String?`
* `val responseHeadersJson: String?`
* `val responseBodyPath: String?` (Path to response body file on disk)
* `val durationMs: Long`
* `val timestamp: Long`

### 2.2 Room Database and DAO

#### HttpTransactionDao
* `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(transaction: HttpTransactionEntity)`
* `@Query("SELECT * FROM HttpTransactionEntity ORDER BY timestamp DESC") fun getAllTransactions(): Flow<List<HttpTransactionEntity>>`
* `@Query("SELECT * FROM HttpTransactionEntity WHERE id = :id") suspend fun getTransactionById(id: String): HttpTransactionEntity?`
* `@Query("DELETE FROM HttpTransactionEntity") suspend fun clearAll()`

#### KNetDatabase
```kotlin
@Database(entities = [HttpTransactionEntity::class], version = 1)
abstract class KNetDatabase : RoomDatabase() {
    abstract fun httpTransactionDao(): HttpTransactionDao
}
```

---

## 3. Session Manager Architecture

The session manager acts as the in-memory orchestrator, accepting transactions from `proxyEngine`, saving payloads to disk, writing metadata to Room database, and managing the current active workspaces/session captures.

Package: `com.devuloopers.knet.session`.

### 3.1 KNetSession
Manages state of the active workspace.
* `val activeTransactions = MutableStateFlow<List<HttpTransaction>>(emptyList())`
* `suspend fun recordRequest(request: HttpRequest)`
* `suspend fun recordResponse(requestId: String, response: HttpResponse, durationMs: Long)`
* `suspend fun clearSession()`

### 3.2 FilePayloadStore
A helper utility to handle write/read operations of body payloads inside a user's workspace directory (e.g. `System.getProperty("user.home") + "/.knet/payloads/"`).

---

## 4. Session Utilities

### 4.1 HAR Export & Import
We will build a exporter utility in `com.devuloopers.knet.session.har.HarExporter` to serialize database transactions into a standard HTTP Archive (HAR 1.2) JSON structure, enabling interoperability with other debugging tools.

### 4.2 cURL Generator
A utility to generate executable cURL commands from stored `HttpTransaction` models:
```kotlin
object CurlGenerator {
    fun generate(transaction: HttpTransaction): String
}
```

---

## 5. Verification Plan

We will write integration tests in `sessionManager/src/test/kotlin/com/devuloopers/knet/session/KNetSessionTest.kt`:
1. **Payload File Caching**: Assert that recording request and response bodies correctly writes byte arrays to files, stores path strings in Room, and reads them back.
2. **HAR Exporter**: Assert that exporting a mock HTTP transaction correctly generates a valid HAR-compliant JSON structure.
3. **cURL Generator**: Assert that generating a cURL command preserves HTTP methods, headers, and payload inputs.
