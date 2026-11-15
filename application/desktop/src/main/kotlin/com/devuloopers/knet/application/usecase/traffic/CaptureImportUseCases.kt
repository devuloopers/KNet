package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.importcapture.*

/** Native selection and parsing remain one cancellable application operation. */
public class OpenCaptureImportUseCase(private val repository: CaptureImportRepository, private val picker: CaptureImportFilePicker) {
    /** Returns null when the native dialog was cancelled. */
    public suspend fun execute(progress: (CaptureImportProgress) -> Unit): ImportedCapture? = picker.choose()?.let { repository.open(it, progress) }
    /** Opens one locally dropped path through the identical content validation and bounded spool. */
    public suspend fun execute(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture = repository.open(path, progress)
}
/** Retrieves one bounded file-local summary page without accessing live capture storage. */
public class ReadCaptureImportPageUseCase(private val repository: CaptureImportRepository) {
    /** Offset is file-local and unaffected by arriving live traffic. */
    public suspend fun execute(id: String, offset: Long): List<ImportedRecord> = repository.page(id, offset)
}
/** Retires one temporary import candidate; live data, pairing and policies remain independent. */
public class CloseCaptureImportUseCase(private val repository: CaptureImportRepository) {
    /** Releases this handle's private files. */
    public suspend fun execute(id: String): Unit = repository.close(id)
}

/** Retrieves full bounded details only for the selected historical item. */
public class ReadCaptureImportRecordUseCase(private val repository: CaptureImportRepository) {
    /** Selection index belongs only to this imported file. */
    public suspend fun execute(id: String, index: Long): ImportedRecord = repository.record(id, index)
}
