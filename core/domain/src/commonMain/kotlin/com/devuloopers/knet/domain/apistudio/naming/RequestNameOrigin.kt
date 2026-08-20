package com.devuloopers.knet.domain.apistudio.naming

/**
 * Identifies who owns an API Studio session/request title.
 *
 * Generated titles may follow request edits. User-defined titles are stable and must never be replaced by
 * protocol naming strategies.
 */
enum class RequestNameOrigin {
    GENERATED,
    USER_DEFINED;

    companion object {
        /** Restores a persistence token while treating unknown legacy values as user-owned names. */
        fun fromToken(token: String): RequestNameOrigin = entries.firstOrNull {
            it.name.equals(token, ignoreCase = true)
        } ?: USER_DEFINED
    }
}
