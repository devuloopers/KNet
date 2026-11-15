package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.export.*

/** Freezes a captured source through the export adapter without presentation-owned file or database access. */
public class PrepareCaptureExportUseCase(private val repository: CaptureExportRepository) {
    /** Returns an isolated spool; caller must eventually release it. */
    public suspend fun execute(source: CaptureExportSource): PreparedCaptureExport = repository.prepare(source)
}

/** Saves a frozen export using the platform picker and the shared background writer. */
public class SaveCaptureExportUseCase(private val repository: CaptureExportRepository,
    private val picker: CaptureExportFilePicker) {
    /** Returns false when the user cancels destination selection; the spool remains available for another attempt. */
    public suspend fun execute(prepared: PreparedCaptureExport, options: CaptureExportOptions,
        progress: (CaptureExportProgress) -> Unit): Boolean {
        require(options.format != CaptureExportFormat.HAR || prepared.httpExchanges > 0) { "This capture has no HAR-compatible HTTP exchanges." }
        val destination = picker.choose(options.format) ?: return false
        repository.write(prepared, options, destination, progress)
        return true
    }
}

/** Releases private export artifacts after completion, dismissal or cancellation. */
public class ReleaseCaptureExportUseCase(private val repository: CaptureExportRepository) {
    /** Idempotently discards one prepared export. */
    public suspend fun execute(prepared: PreparedCaptureExport): Unit = repository.release(prepared)
}
