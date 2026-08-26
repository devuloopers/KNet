package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionFailure

/** Platform-protected credential storage. Credential values must never enter observable state. */
public interface CompanionCredentialStore {
    public suspend fun write(reference: CompanionCredentialReference, credential: String)
    public suspend fun read(reference: CompanionCredentialReference): String?
    public suspend fun remove(reference: CompanionCredentialReference)
}

/** Secret-bearing refresh reply consumed immediately by the refresh use case. */
public sealed interface CompanionCredentialRefreshResult {
    public data class Refreshed(
        public val credential: String,
        public val credentialExpiresAtEpochMillis: Long,
    ) : CompanionCredentialRefreshResult

    public data class Rejected(public val failure: CompanionFailure) : CompanionCredentialRefreshResult
}
