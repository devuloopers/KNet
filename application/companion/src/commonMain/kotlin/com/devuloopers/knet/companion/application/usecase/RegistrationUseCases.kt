package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionInspectionController
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.CompanionTransport
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.StateFlow

/** Exposes durable registrations without presentation depending on a concrete repository. */
public class ObserveCompanionRegistrationsUseCase(
    repository: CompanionRegistrationRepository,
) {
    public val registrations: StateFlow<List<CompanionRegistration>> = repository.registrations
    public val activeRegistration: StateFlow<CompanionRegistration?> = repository.activeRegistration
}

/** Selects the desktop used by connection, certificate, and inspection workflows. */
public class SelectCompanionRegistrationUseCase(
    private val repository: CompanionRegistrationRepository,
) {
    public suspend fun execute(desktopId: CompanionDesktopId?): Boolean = repository.setActive(desktopId)
}

/** Removes all local trust for one desktop after stopping active network resources. */
public class ForgetCompanionDesktopUseCase(
    private val registrations: CompanionRegistrationRepository,
    private val credentials: CompanionCredentialStore,
    private val inspection: CompanionInspectionController,
    private val transport: CompanionTransport,
) {
    public suspend fun execute(desktopId: CompanionDesktopId): Boolean {
        val registration = registrations.registrations.value.firstOrNull { it.desktopId == desktopId } ?: return false
        if (registrations.activeRegistration.value?.desktopId == desktopId) {
            try {
                inspection.stop()
            } finally {
                transport.disconnect()
            }
        }
        val removed = registrations.remove(desktopId) ?: return false
        credentials.remove(removed.credentialReference)
        return true
    }
}

