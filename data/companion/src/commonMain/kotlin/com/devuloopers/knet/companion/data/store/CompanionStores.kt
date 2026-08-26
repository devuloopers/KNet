package com.devuloopers.knet.companion.data.store

import kotlinx.coroutines.flow.StateFlow

/** Small non-secret record store; the shared repository owns schema and migration. */
public interface CompanionRecordStore {
    public val content: StateFlow<String?>
    public suspend fun write(content: String?)
}

/** Platform-protected secret store used for credentials and, later, private-key references. */
public interface CompanionSecretStore {
    public suspend fun write(key: String, value: String)
    public suspend fun read(key: String): String?
    public suspend fun remove(key: String)
}
