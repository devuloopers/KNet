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

/** Platform bootstrap result that keeps DataStore implementation types inside the data module. */
public data class CompanionPersistenceStores(
    public val records: CompanionRecordStore,
    public val secrets: CompanionSecretStore,
)

/** Platform security boundary that seals credentials before common persistence writes them. */
internal interface CompanionSecretProtector {
    public suspend fun protect(key: String, value: String): ProtectedCompanionSecret
    public suspend fun reveal(key: String, secret: ProtectedCompanionSecret): String?

    /** Removes any platform-owned secret material associated with [key]. */
    public suspend fun remove(key: String) {}
}

/** Opaque serialized ciphertext safe to persist outside a platform keystore. */
internal data class ProtectedCompanionSecret(val serializedValue: String) {
    init {
        require(serializedValue.isNotBlank()) { "Protected companion secret must not be blank." }
    }
}
