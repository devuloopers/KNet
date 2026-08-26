package com.devuloopers.knet.companion.application.contract

import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.StateFlow

/** Durable, non-secret companion registration source of truth. */
public interface CompanionRegistrationRepository {
    public val registrations: StateFlow<List<CompanionRegistration>>
    public val activeRegistration: StateFlow<CompanionRegistration?>

    public suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean)
    public suspend fun setActive(desktopId: CompanionDesktopId?): Boolean
    public suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration?
}
