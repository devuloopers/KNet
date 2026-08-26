package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.model.CompanionDesktopId

/** Presentation lifecycle for exporting one paired desktop's public KNet root certificate. */
public sealed interface CompanionCertificateExportState {
    /** No certificate file has been exported during the current desktop session. */
    public data object Idle : CompanionCertificateExportState

    /** Certificate material is being retrieved or written for [desktopId]. */
    public data class Saving(public val desktopId: CompanionDesktopId) : CompanionCertificateExportState

    /** The public certificate was written to a user-visible platform location. */
    public data class Saved(
        public val desktopId: CompanionDesktopId,
        public val fileName: String,
        public val locationDescription: String,
    ) : CompanionCertificateExportState {
        init {
            require(fileName.isSafeDescription()) { "Certificate file name is invalid." }
            require(locationDescription.isSafeDescription()) { "Certificate location description is invalid." }
        }
    }

    /** The most recent export attempt failed and may be retried. */
    public data class Failed(public val desktopId: CompanionDesktopId) : CompanionCertificateExportState
}

private fun String.isSafeDescription(): Boolean =
    length in 1..256 && isNotBlank() && this == trim() && none { character ->
        character.code in 0..31 || character.code == 127
    }
