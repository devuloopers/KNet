package com.devuloopers.knet.data.desktop.policy

import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.data.desktop.networkconditions.*
import com.devuloopers.knet.data.desktop.protectedtraffic.*
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.policy.entity.*
import com.devuloopers.knet.storage.rules.entity.BreakpointRuleEntity
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Committed shared definitions and independent workspace bindings. Room's writer transaction serializes
 * ownership/revision/conflict validation across repository instances. A publication sequence fences save
 * completion against asynchronous invalidation, without publishing any uncommitted in-memory state.
 */
class RoomDevicePolicyLibrary(private val database: KNetDatabase, scope: CoroutineScope) : DevicePolicyLibrary {
    private val dao = database.devicePolicyDao()
    private val network = database.networkConditionDao()
    private val protection = database.protectedTrafficDao()
    private val breakpoints = database.breakpointRuleDao()
    private val transactions = NetworkConditionTransactions(database)

    override val snapshot: StateFlow<DevicePolicyLibrarySnapshot> = database.invalidationTracker.createFlow(
        "policy_workspaces", "policy_library_revision", "registered_devices", "breakpoint_rules",
        "network_condition_rules", "network_condition_profiles", "protected_traffic_rules",
        "policy_breakpoint_activations", "policy_condition_activations", "policy_protection_activations",
    ).map {
        transactions.read {
            try { readSnapshot() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DevicePolicyLibrarySnapshot() }
        }
    }.stateIn(scope, SharingStarted.Eagerly, DevicePolicyLibrarySnapshot())

    private suspend fun readSnapshot(): DevicePolicyLibrarySnapshot {
        val savedSettings = dao.workspaces()
        val devices = dao.devices()
        val settings = buildMap {
            put(PolicyWorkspace.Other, DevicePolicySettings())
            devices.forEach { put(PolicyWorkspace.Device(RegisteredDeviceId(it.id)), DevicePolicySettings()) }
            savedSettings.forEach { put(PolicyWorkspace.decode(it.key), it.settings()) }
        }
        val breakpointBindings = dao.breakpointActivations().filter { it.enabled }.groupBy({ it.ruleId }, { PolicyWorkspace.decode(it.workspaceKey) })
        val conditionBindings = dao.conditionActivations().filter { it.enabled }.groupBy({ it.ruleId }, { PolicyWorkspace.decode(it.workspaceKey) })
        val protectionBindings = dao.protectionActivations().filter { it.enabled }.groupBy({ it.ruleId }, { PolicyWorkspace.decode(it.workspaceKey) })
        return DevicePolicyLibrarySnapshot(ready = true, revision = dao.revision() ?: 0L,
            workspaceNames = buildMap {
                put(PolicyWorkspace.Other, "Other traffic")
                devices.forEach { device ->
                    var length = minOf(6, device.id.length)
                    while (devices.any { it.id != device.id && it.id.takeLast(length) == device.id.takeLast(length) } && length < device.id.length) length++
                    val companionSuffix = device.id.filter(Char::isLetterOrDigit).takeLast(4).uppercase().ifBlank { "KNET" }
                    val name = device.displayName.removeSuffix(" · $companionSuffix").ifBlank { device.displayName }
                    put(PolicyWorkspace.Device(RegisteredDeviceId(device.id)), "$name · ${device.id.takeLast(length)}")
                }
            }, settings = settings,
            profiles = network.getProfiles().map { it.toDomain() },
            breakpoints = breakpoints.getAllRules().map { row -> PolicyDefinition(row.definition(),
                availability(row.libraryOwnerKey), row.libraryRevision, breakpointBindings[row.id].orEmpty().toSet()) },
            conditions = network.getRules().map { row -> PolicyDefinition(row.toDomain().copy(enabled = true),
                availability(row.libraryOwnerKey), row.libraryRevision, conditionBindings[row.id].orEmpty().toSet()) },
            protectedRules = protection.getRules().map { row -> PolicyDefinition(row.toDomain().copy(enabled = true),
                availability(row.libraryOwnerKey), row.libraryRevision, protectionBindings[row.id].orEmpty().toSet()) })
    }

    override suspend fun saveBreakpoint(workspace: PolicyWorkspace, rule: BreakpointRule, availability: RuleAvailability,
        expectedRevision: Long?, replaceEquivalent: Boolean, activateAfterSave: Boolean, expectedUsage: Set<PolicyWorkspace>?) = write {
        ensureWorkspace(workspace)
        val state = readSnapshot()
        val existing = state.breakpoints.firstOrNull { it.rule.id == rule.id }
        validateSave(workspace, availability, existing, expectedRevision, expectedUsage)
        val key = rule.matchKey()
        validateSiblingConflicts(workspace, existing, state.breakpoints.filter { it.rule.id != rule.id && it.rule.matchKey() == key })
        if (!replaceEquivalent) rejectLibraryDuplicate(state.breakpoints.filter { it.rule.id != rule.id }, availability) { it.matchKey() == key }
        val replaced = if (existing == null || activateAfterSave || existing.activeOn(workspace)) replaceConflicts(workspace, PolicyRuleKind.BREAKPOINT, rule.id,
            state.breakpoints.filter { it.activeOn(workspace) && it.rule.matchKey() == key }.map { it.rule.id }, replaceEquivalent) else emptySet()
        breakpoints.upsertRule(rule.entity(owner(availability), nextRevision(existing)))
        if (existing == null || activateAfterSave) bind(workspace, PolicyRuleKind.BREAKPOINT, rule.id, true)
        RuleSaveResult(replaced)
    }

    override suspend fun saveCondition(workspace: PolicyWorkspace, rule: NetworkConditionRule, availability: RuleAvailability,
        expectedRevision: Long?, replaceEquivalent: Boolean, activateAfterSave: Boolean, expectedUsage: Set<PolicyWorkspace>?) = write {
        ensureWorkspace(workspace)
        val state = readSnapshot()
        if (state.network(workspace).profile(rule.profileId) == null) fail(DevicePolicyFailure.INVALID_CONFIGURATION)
        val existing = state.conditions.firstOrNull { it.rule.id == rule.id }
        validateSave(workspace, availability, existing, expectedRevision, expectedUsage)
        validateSiblingConflicts(workspace, existing, state.conditions.filter { it.rule.id != rule.id && it.rule.target == rule.target && it.rule.protocolCriteria == rule.protocolCriteria })
        if (!replaceEquivalent) rejectLibraryDuplicate(state.conditions.filter { it.rule.id != rule.id }, availability) { it.target == rule.target && it.protocolCriteria == rule.protocolCriteria }
        val replaced = if (existing == null || activateAfterSave || existing.activeOn(workspace)) replaceConflicts(workspace, PolicyRuleKind.NETWORK_CONDITION, rule.id.value,
            state.conditions.filter { it.activeOn(workspace) && it.rule.target == rule.target && it.rule.protocolCriteria == rule.protocolCriteria }
                .map { it.rule.id.value }, replaceEquivalent) else emptySet()
        network.upsertRule(rule.copy(enabled = true).toEntity().copy(libraryOwnerKey = owner(availability), libraryRevision = nextRevision(existing)))
        if (existing == null || activateAfterSave) bind(workspace, PolicyRuleKind.NETWORK_CONDITION, rule.id.value, true)
        RuleSaveResult(replaced)
    }

    override suspend fun saveProtection(workspace: PolicyWorkspace, rule: ProtectedTrafficRule, availability: RuleAvailability,
        expectedRevision: Long?, replaceEquivalent: Boolean, activateAfterSave: Boolean, expectedUsage: Set<PolicyWorkspace>?) = write {
        ensureWorkspace(workspace)
        val state = readSnapshot()
        val existing = state.protectedRules.firstOrNull { it.rule.id == rule.id }
        validateSave(workspace, availability, existing, expectedRevision, expectedUsage)
        require(rule.origin == ProtectedTrafficRuleOrigin.USER || existing?.rule?.let {
            it.origin == rule.origin && it.selectorKey == rule.selectorKey && it.action == rule.action
        } == true) { "Only user rules or unchanged migrated exceptions may be saved." }
        validateSiblingConflicts(workspace, existing, state.protectedRules.filter { it.rule.id != rule.id && it.rule.selectorKey == rule.selectorKey })
        if (!replaceEquivalent) rejectLibraryDuplicate(state.protectedRules.filter { it.rule.id != rule.id }, availability) { it.selectorKey == rule.selectorKey }
        val replaced = if (existing == null || activateAfterSave || existing.activeOn(workspace)) replaceConflicts(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, rule.id.value,
            state.protectedRules.filter { it.activeOn(workspace) && it.rule.selectorKey == rule.selectorKey }.map { it.rule.id.value }, replaceEquivalent) else emptySet()
        protection.upsertRule(rule.copy(enabled = true).toEntity().copy(libraryOwnerKey = owner(availability), libraryRevision = nextRevision(existing)))
        if (existing == null || activateAfterSave) bind(workspace, PolicyRuleKind.PROTECTED_TRAFFIC, rule.id.value, true)
        RuleSaveResult(replaced)
    }

    override suspend fun activate(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String, enabled: Boolean,
        replaceEquivalent: Boolean) = write<Unit> {
        ensureWorkspace(workspace)
        val state = readSnapshot()
        val definition = definition(state, kind, id)
        if (!definition.visibleTo(workspace)) fail(DevicePolicyFailure.PRIVATE_DEFINITION)
        if (enabled) {
            val conflicts = when (kind) {
                PolicyRuleKind.BREAKPOINT -> {
                    val rule = state.breakpoints.first { it.rule.id == id }.rule
                    state.breakpoints.filter { it.activeOn(workspace) && it.rule.matchKey() == rule.matchKey() }.map { it.rule.id }
                }
                PolicyRuleKind.NETWORK_CONDITION -> {
                    val rule = state.conditions.first { it.rule.id.value == id }.rule
                    state.conditions.filter { it.activeOn(workspace) && it.rule.target == rule.target && it.rule.protocolCriteria == rule.protocolCriteria }.map { it.rule.id.value }
                }
                PolicyRuleKind.PROTECTED_TRAFFIC -> {
                    val rule = state.protectedRules.first { it.rule.id.value == id }.rule
                    state.protectedRules.filter { it.activeOn(workspace) && it.rule.selectorKey == rule.selectorKey }.map { it.rule.id.value }
                }
            }
            replaceConflicts(workspace, kind, id, conflicts, replaceEquivalent)
        }
        bind(workspace, kind, id, enabled)
    }

    override suspend fun delete(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String,
        expectedRevision: Long, acknowledgeSharedUsage: Boolean, expectedUsage: Set<PolicyWorkspace>?) = write<Unit> {
        ensureWorkspace(workspace)
        val definition = definition(readSnapshot(), kind, id)
        if (!definition.visibleTo(workspace)) fail(DevicePolicyFailure.PRIVATE_DEFINITION)
        if (definition.revision != expectedRevision || definition.availability == RuleAvailability.Shared && expectedUsage != null && definition.enabledOn != expectedUsage)
            fail(DevicePolicyFailure.STALE_REVISION)
        if (!acknowledgeSharedUsage && definition.enabledOn.any { it != workspace }) fail(DevicePolicyFailure.SHARED_IN_USE)
        when (kind) {
            PolicyRuleKind.BREAKPOINT -> breakpoints.deleteRule(id)
            PolicyRuleKind.NETWORK_CONDITION -> network.deleteRule(id)
            PolicyRuleKind.PROTECTED_TRAFFIC -> protection.deleteRule(id)
        }
    }

    override suspend fun updateSettings(workspace: PolicyWorkspace, transform: (DevicePolicySettings) -> DevicePolicySettings) = write<Unit> {
        ensureWorkspace(workspace)
        val state = readSnapshot()
        val updated = transform(state.settingsFor(workspace))
        val profiles = NetworkConditionBuiltIns.all.map { it.id }.toSet() + state.profiles.map { it.id }
        require(updated.profileId == null || updated.profileId in profiles)
        require(updated.quickAddProfileId == null || updated.quickAddProfileId in profiles)
        dao.saveWorkspace(updated.entity(workspace))
    }

    override suspend fun saveProfile(profile: NetworkConditionProfile) = write<Unit> {
        require(!profile.builtIn && NetworkConditionBuiltIns.all.none { it.id == profile.id })
        network.upsertProfile(profile.toEntity())
    }

    override suspend fun deleteProfile(id: NetworkConditionProfileId): Boolean {
        var removed = false
        write {
            val state = readSnapshot()
            if (state.conditions.any { it.rule.profileId == id } || state.settings.values.any { it.profileId == id }) return@write
            state.settings.filterValues { it.quickAddProfileId == id }.forEach { (workspace, settings) ->
                dao.saveWorkspace(settings.copy(quickAddProfileId = null).entity(workspace))
            }
            if (NetworkConditionBuiltIns.all.any { it.id == id }) return@write
            network.deleteProfile(id.value)
            removed = true
        }
        return removed
    }

    override suspend fun forget(workspace: PolicyWorkspace) = write<Unit> {
        require(workspace is PolicyWorkspace.Device) { "Other traffic cannot be forgotten." }
        dao.forgetRegisteredDevice(workspace.id.value)
    }

    private suspend fun <Result> write(block: suspend () -> Result): Result {
        val (result, committed) = transactions.write {
            val result = block()
            dao.seedRevision(PolicyLibraryRevisionEntity())
            dao.advanceRevision()
            result to checkNotNull(dao.revision())
        }
        // Readers publish serially from committed transactions. Concurrent later writes also satisfy this
        // acknowledgement; waiting for exact content would deadlock a superseded concurrent toggle.
        withTimeout(5_000L) { snapshot.first { it.ready && it.revision >= committed } }
        return result
    }

    private suspend fun ensureWorkspace(workspace: PolicyWorkspace) {
        val current = dao.workspaces().firstOrNull { it.key == workspace.key }
        if (workspace is PolicyWorkspace.Device && dao.devices().none { it.id == workspace.id.value }) fail(DevicePolicyFailure.WORKSPACE_REMOVED)
        if (current == null) dao.saveWorkspace(DevicePolicySettings().entity(workspace))
    }

    private suspend fun bind(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String, enabled: Boolean) = when (kind) {
        PolicyRuleKind.BREAKPOINT -> dao.saveBreakpointActivation(BreakpointActivationEntity(id, workspace.key, enabled))
        PolicyRuleKind.NETWORK_CONDITION -> dao.saveConditionActivation(ConditionActivationEntity(id, workspace.key, enabled))
        PolicyRuleKind.PROTECTED_TRAFFIC -> dao.saveProtectionActivation(ProtectionActivationEntity(id, workspace.key, enabled))
    }

    private fun <Rule> validateSiblingConflicts(workspace: PolicyWorkspace, existing: PolicyDefinition<Rule>?, equivalent: List<PolicyDefinition<Rule>>) {
        val siblings = existing?.enabledOn.orEmpty() - workspace
        if (equivalent.any { definition -> siblings.any(definition::activeOn) }) fail(DevicePolicyFailure.ACTIVE_EQUIVALENT)
    }

    private suspend fun replaceConflicts(workspace: PolicyWorkspace, kind: PolicyRuleKind, id: String, conflicts: List<String>, replace: Boolean): Set<String> {
        val others = conflicts.filterNot { it == id }
        if (others.isNotEmpty() && !replace) fail(DevicePolicyFailure.ACTIVE_EQUIVALENT)
        others.forEach { bind(workspace, kind, it, false) }
        return others.toSet()
    }

    private fun <Rule> validateSave(workspace: PolicyWorkspace, availability: RuleAvailability, existing: PolicyDefinition<Rule>?, expectedRevision: Long?, expectedUsage: Set<PolicyWorkspace>?) {
        if (availability is RuleAvailability.Private && availability.owner != workspace) fail(DevicePolicyFailure.PRIVATE_DEFINITION)
        if (existing == null && expectedRevision != null) fail(DevicePolicyFailure.MISSING_DEFINITION)
        if (existing != null) {
            if (!existing.visibleTo(workspace)) fail(DevicePolicyFailure.PRIVATE_DEFINITION)
            if (existing.revision != expectedRevision || existing.availability == RuleAvailability.Shared && expectedUsage != null && existing.enabledOn != expectedUsage)
                fail(DevicePolicyFailure.STALE_REVISION)
            if (existing.availability == RuleAvailability.Shared && availability != RuleAvailability.Shared) fail(DevicePolicyFailure.PRIVATE_DEFINITION)
        }
    }

    private fun <Rule> rejectLibraryDuplicate(entries: List<PolicyDefinition<Rule>>, availability: RuleAvailability, equivalent: (Rule) -> Boolean) {
        if (entries.any { it.availability == availability && equivalent(it.rule) }) fail(DevicePolicyFailure.ACTIVE_EQUIVALENT)
    }

    private fun definition(state: DevicePolicyLibrarySnapshot, kind: PolicyRuleKind, id: String): PolicyDefinition<*> = when (kind) {
        PolicyRuleKind.BREAKPOINT -> state.breakpoints.firstOrNull { it.rule.id == id }
        PolicyRuleKind.NETWORK_CONDITION -> state.conditions.firstOrNull { it.rule.id.value == id }
        PolicyRuleKind.PROTECTED_TRAFFIC -> state.protectedRules.firstOrNull { it.rule.id.value == id }
    } ?: fail(DevicePolicyFailure.MISSING_DEFINITION)

    private fun nextRevision(existing: PolicyDefinition<*>?): Long = if (existing == null) 1L else Math.addExact(existing.revision, 1L)
    private fun fail(reason: DevicePolicyFailure): Nothing = throw DevicePolicyException(reason)
    private fun availability(owner: String?): RuleAvailability = owner?.let { RuleAvailability.Private(PolicyWorkspace.decode(it)) } ?: RuleAvailability.Shared
    private fun owner(availability: RuleAvailability): String? = (availability as? RuleAvailability.Private)?.owner?.key
}

private fun PolicyWorkspaceEntity.settings() = DevicePolicySettings(networkEnabled, profileId?.let(::NetworkConditionProfileId),
    quickAddProfileId?.let(::NetworkConditionProfileId), interceptionEnabled, ProtectedTrafficAction.valueOf(protectedAction), automaticTlsFallback)
private fun DevicePolicySettings.entity(workspace: PolicyWorkspace) = PolicyWorkspaceEntity(workspace.key,
    (workspace as? PolicyWorkspace.Device)?.id?.value, networkEnabled, profileId?.value, quickAddProfileId?.value,
    interceptionEnabled, protectedAction.name, automaticTlsFallback)
private data class BreakpointSelector(val pattern: String, val port: BreakpointPortCriteria, val method: HttpMethod?, val phase: BreakpointPhase, val protocol: ProtocolMatchCriteria)
private fun BreakpointRule.matchKey(): BreakpointSelector = BreakpointSelector(urlPattern, portCriteria, method, phase, protocolCriteria)
private fun BreakpointRule.entity(owner: String?, revision: Long) = BreakpointRuleEntity(id = id, libraryOwnerKey = owner,
    libraryRevision = revision, urlPattern = urlPattern, port = (portCriteria as? BreakpointPortCriteria.Exact)?.value,
    method = method?.token, phase = phase.name, enabled = true, priority = priority,
    protocolCriteriaType = protocolCriteria.protocolId.value, protocolCriteriaData = protocolCriteria.encodedPayload)
private fun BreakpointRuleEntity.definition() = BreakpointRule(id = id, name = urlPattern, urlPattern = urlPattern,
    portCriteria = port?.let(BreakpointPortCriteria::Exact) ?: BreakpointPortCriteria.Any, method = method?.let(HttpMethod::fromToken),
    phase = BreakpointPhase.fromString(phase), priority = priority, protocolCriteria = ProtocolMatchCriteria(
        BreakpointProtocolId(protocolCriteriaType), protocolCriteriaData.orEmpty()))
