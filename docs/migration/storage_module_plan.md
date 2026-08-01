# Architecture Specification — `:storage` (JVM Desktop Persistence)

**Module:** `storage/` (`:storage`)  
**Package Namespace:** `com.devuloopers.knet.storage`  
**Platform:** Kotlin JVM / Room Multiplatform + SQLite + DataStore  
**Status:** Complete Consolidated Specification

---

# 📌 Purpose & Role of `:storage`

The **`:storage`** module is KNet's **Local Persistence Engine**.

It owns all low-level SQLite database schemas, Room DAOs, DataStore preference adapters, database migration routines, and the concrete `DatabaseFactory`.

### Key Principles:
- **Pragmatic `jvmMain` Consolidation**: All persistence classes (`KNetDatabase`, `DatabaseFactory`, entities, DAOs, converters, migrations, and datasources) reside under `storage/src/jvmMain/kotlin/com/devuloopers/knet/storage/`.
- **Concrete Factory**: `DatabaseFactory.create(dbFile)` is a clean, concrete JVM object with zero `expect/actual` boilerplate.
- **Identical Package Namespace**: Uses package namespace `com.devuloopers.knet.storage.*`. When mobile (Android/iOS) development begins in the future, moving `KNetDatabase`, entities, DAOs, and converters to `commonMain` will require zero refactoring in consumer code.

---

# 🏗 Module Structure

```text
storage/
├── build.gradle.kts
│
└── src/
    ├── jvmMain/
    │   └── kotlin/
    │       └── com/devuloopers/knet/storage/
    │
    │           ├── database/
    │           │     ├── KNetDatabase.kt
    │           │     ├── DatabaseFactory.kt           (Concrete JVM object)
    │           │     └── DatabaseMigrations.kt
    │           │
    │           ├── converter/
    │           │     └── RoomConverters.kt
    │           │
    │           ├── datasource/
    │           │     └── WorkspacePreferencesDataSource.kt
    │           │
    │           ├── apistudio/
    │           │     ├── entity/
    │           │     │    ├── CollectionEntity.kt
    │           │     │    ├── CollectionFolderEntity.kt
    │           │     │    └── SavedRequestEntity.kt
    │           │     └── dao/
    │           │          └── CollectionDao.kt
    │           │
    │           └── traffic/
    │                 ├── entity/
    │                 │    └── HttpTransactionEntity.kt
    │                 └── dao/
    │                      └── HttpTransactionDao.kt
    │
    └── jvmTest/
        └── kotlin/
            └── com/devuloopers/knet/storage/
                ├── converter/RoomConvertersTest.kt
                └── MigrationRegressionTest.kt
```

---

# 🎯 Component Specifications

## 1. Database Factory (`database/DatabaseFactory.kt`)
```kotlin
package com.devuloopers.knet.storage.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

public object DatabaseFactory {
    public fun create(dbFile: File): KNetDatabase {
        dbFile.parentFile?.mkdirs()
        val builder = Room.databaseBuilder<KNetDatabase>(
            name = dbFile.absolutePath,
            factory = { KNetDatabase_Impl() }
        )
        builder.setDriver(BundledSQLiteDriver())
        builder.addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_3_4)
        builder.fallbackToDestructiveMigration(dropAllTables = true)
        return builder.build()
    }
}
```

## 2. Database Definition (`database/KNetDatabase.kt`)
```kotlin
package com.devuloopers.knet.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.devuloopers.knet.storage.apistudio.dao.CollectionDao
import com.devuloopers.knet.storage.apistudio.entity.CollectionEntity
import com.devuloopers.knet.storage.apistudio.entity.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.storage.traffic.dao.HttpTransactionDao
import com.devuloopers.knet.storage.traffic.entity.HttpTransactionEntity

@Database(
    entities = [
        HttpTransactionEntity::class,
        CollectionEntity::class,
        CollectionFolderEntity::class,
        SavedRequestEntity::class
    ],
    version = 4
)
public abstract class KNetDatabase : RoomDatabase() {
    public abstract fun httpTransactionDao(): HttpTransactionDao
    public abstract fun collectionDao(): CollectionDao
}
```

---

# 🧪 Verification & Build Commands

```bash
./gradlew :storage:compileKotlinJvm
./gradlew :storage:jvmTest
./gradlew compileKotlinJvm
```
