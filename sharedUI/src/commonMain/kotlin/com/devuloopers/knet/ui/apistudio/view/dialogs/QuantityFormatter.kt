package com.devuloopers.knet.ui.apistudio.view.dialogs

/**
 * Reusable formatting utility for pluralizing quantities across KNet UI components.
 */
object QuantityFormatter {

    /**
     * Formats a numeric count with its singular or plural noun representation.
     *
     * @param count Integer quantity.
     * @param singular Singular noun form (e.g. "Request", "Collection", "Assertion").
     * @param plural Optional custom plural noun form. Defaults to appending "s".
     * @return Formatted string (e.g. "1 Request", "2 Requests").
     */
    fun format(count: Int, singular: String, plural: String = "${singular}s"): String {
        val noun = if (count == 1) singular else plural
        return "$count $noun"
    }
}
