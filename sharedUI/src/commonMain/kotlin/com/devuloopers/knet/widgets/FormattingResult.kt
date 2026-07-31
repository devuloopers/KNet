package com.devuloopers.knet.widgets

import com.devuloopers.knet.bodyformatter.model.BodyFormat

/**
 * Represents the asynchronous state of body payload formatting.
 *
 * Used by [ResponseBodyWidget] and [RequestBodyWidget] to decouple expensive
 * pretty-printing and format resolution from the Compose main thread.
 * The formatting computation runs on [kotlinx.coroutines.Dispatchers.Default]
 * via `produceState`, and the UI observes state transitions reactively.
 */
sealed interface FormattingResult {

    /**
     * Initial state while formatting is computed off-thread.
     * The UI should display a lightweight loading shimmer or placeholder.
     */
    data object Loading : FormattingResult

    /**
     * Terminal state once formatting completes successfully.
     *
     * @property prettyBody The human-readable pretty-printed body text.
     * @property format The resolved [BodyFormat] used for syntax highlighting strategy selection.
     */
    data class Ready(
        val prettyBody: String,
        val format: BodyFormat,
        val charset: String = "UTF-8",
        val lineEnding: String = "LF",
        val bodySize: String = ""
    ) : FormattingResult
}
