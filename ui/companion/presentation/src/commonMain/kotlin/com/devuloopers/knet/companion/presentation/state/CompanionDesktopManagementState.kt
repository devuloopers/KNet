package com.devuloopers.knet.companion.presentation.state

import com.devuloopers.knet.companion.application.usecase.CompanionDesktopSwitchPhase
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionRegistration

/** Presentation-only desktop-manager overlays; saved registrations remain the durable source of truth. */
public sealed interface CompanionDesktopManagementState {
    /** Normal setup or inspection content. */
    public data object Closed : CompanionDesktopManagementState
    /** Saved desktops sheet. */
    public data object Listing : CompanionDesktopManagementState
    /** QR pairing flow, cancellable without clearing an existing registration. */
    public data object Adding : CompanionDesktopManagementState
    /** Explicit removal confirmation for one pairing. */
    public data class ConfirmForget(public val registration: CompanionRegistration) : CompanionDesktopManagementState
    /**
     * A serialized handoff with a stable source and target label.
     * @property source selected desktop when the handoff started, or null when restoring an unselected pairing.
     * @property target saved desktop being authenticated and selected.
     * @property phase currently executing capture/verification stage.
     */
    public data class Switching(
        public val source: CompanionRegistration?,
        public val target: CompanionRegistration,
        public val phase: CompanionDesktopSwitchPhase,
    ) : CompanionDesktopManagementState
    /**
     * Retryable handoff error; actual active selection is still read from the aggregate state.
     * @property target saved desktop to use for an explicit retry.
     * @property failure sanitized reason for the failed handoff.
     */
    public data class SwitchFailed(
        public val target: CompanionRegistration,
        public val failure: CompanionFailure,
        /** Retains the original running-session intent for an explicit retry, subject to fresh trust/consent. */
        public val resumeInspection: Boolean = false,
    ) : CompanionDesktopManagementState
}
