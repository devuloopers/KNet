package com.devuloopers.knet.ui.desktop.apistudio.model

/**
 * Encapsulates serialization and deserialization between [SessionContext] and persistent DataStore setting strings.
 */
public object SessionContextSerializer {

    /**
     * Deserializes a raw stored session string back into a [SessionContext].
     *
     * Encoding format:
     * - `""` or unrecognized → [SessionContext.None]
     * - `"unsaved:<sessionId>"` → [SessionContext.UnsavedDraft]
     * - `"saved:<requestId>:<collectionId>:<folderId>"` → [SessionContext.SavedRequest]
     *
     * @param raw Raw encoded string from preferences DataStore.
     * @return Strongly-typed [SessionContext] instance.
     */
    public fun deserialize(raw: String): SessionContext {
        if (raw.isBlank()) return SessionContext.None
        return when {
            raw.startsWith("unsaved:") -> {
                val sessionId = raw.removePrefix("unsaved:")
                SessionContext.UnsavedDraft(sessionId)
            }

            raw.startsWith("saved:") -> {
                val parts = raw.removePrefix("saved:").split(":")
                if (parts.size >= 3) {
                    SessionContext.SavedRequest(
                        requestId = parts[0],
                        collectionId = parts[1],
                        folderId = parts[2]
                    )
                } else {
                    SessionContext.None
                }
            }

            else -> SessionContext.None
        }
    }

    /**
     * Serializes a [SessionContext] into a compact string for DataStore persistence.
     *
     * @param context Active session context DTO.
     * @return Formatted preference string.
     */
    public fun serialize(context: SessionContext): String {
        return when (context) {
            is SessionContext.None -> ""
            is SessionContext.UnsavedDraft -> "unsaved:${context.sessionId}"
            is SessionContext.SavedRequest -> "saved:${context.requestId}:${context.collectionId}:${context.folderId}"
        }
    }
}
