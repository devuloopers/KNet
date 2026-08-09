# Implementation Plan: Unsaved Session Sidebar Ordering [COMPLETED]

## Phase 1: Room Database DAO Ordering Queries [COMPLETED]
- [x] Update `getRequestsForCollectionFlow` in `CollectionDao.kt` with `ORDER BY id ASC`
- [x] Update `getRequestsForCollection` in `CollectionDao.kt` with `ORDER BY id ASC`
- [x] Update `getRequestsForFolder` in `CollectionDao.kt` with `ORDER BY id ASC`
- [x] Ensure auto-save `OnConflictStrategy.REPLACE` operations preserve creation order (`unsaved_<timestamp>`)

## Phase 2: Verification & Testing [COMPLETED]
- [x] Run `./gradlew :storage:jvmTest :ui:desktop:apistudio:jvmTest :apps:desktop:compileKotlin`
- [x] Verify stable sidebar list positioning across draft auto-saves
