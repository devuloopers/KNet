package com.devuloopers.knet.data.desktop.policy

import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.*
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import java.nio.file.Files
import kotlin.test.*

/** Real SQLite qualification of definition visibility, independent activations and committed ownership. */
class RoomDevicePolicyLibraryTest {
    @Test fun sharedCreationDoesNotActivateSiblingOrFutureDevicesForAnyRuleFamily() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                fixture.save(kind, "shared-$kind", a, RuleAvailability.Shared)
                assertTrue(fixture.entry(kind, "shared-$kind").activeOn(a))
                assertFalse(fixture.entry(kind, "shared-$kind").activeOn(b))
                assertTrue(fixture.entry(kind, "shared-$kind").visibleTo(b))
                fixture.library.activate(b, kind, "shared-$kind", true)
                fixture.library.activate(a, kind, "shared-$kind", false)
                assertFalse(fixture.entry(kind, "shared-$kind").activeOn(a))
                assertTrue(fixture.entry(kind, "shared-$kind").activeOn(b))
                fixture.library.activate(b, kind, "shared-$kind", true)
            }
            fixture.database.registeredDeviceDao().upsertRegisteredDevice(device("future"))
            val future = PolicyWorkspace.Device(RegisteredDeviceId("future"))
            fixture.library.snapshot.first { future in it.settings }
            for (kind in PolicyRuleKind.entries) assertFalse(fixture.entry(kind, "shared-$kind").activeOn(future))
            assertFalse(fixture.library.snapshot.value.settingsFor(future).networkEnabled)
        }
    }

    @Test fun privateRulesCannotBeDiscoveredActivatedEditedOrDeletedFromAnotherDevice() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                val id = "private-$kind"
                fixture.save(kind, id, a, RuleAvailability.Private(a))
                assertFalse(fixture.entry(kind, id).visibleTo(b))
                assertFailure(DevicePolicyFailure.PRIVATE_DEFINITION) { fixture.library.activate(b, kind, id, true) }
                assertFailure(DevicePolicyFailure.PRIVATE_DEFINITION) { fixture.library.delete(b, kind, id, 1) }
                assertFailure(DevicePolicyFailure.PRIVATE_DEFINITION) { fixture.save(kind, id, b, RuleAvailability.Private(b), 1) }
                assertTrue(fixture.entry(kind, id).activeOn(a))
            }
        }
    }

    @Test fun sharedEditsRetainIndependentActivationsAndRejectStaleWriters() = runBlocking {
        fixture { fixture ->
            fixture.library.saveProtection(a, protected("shared"), RuleAvailability.Shared)
            fixture.library.activate(b, PolicyRuleKind.PROTECTED_TRAFFIC, "shared", true)
            fixture.library.activate(a, PolicyRuleKind.PROTECTED_TRAFFIC, "shared", false)
            fixture.library.saveProtection(b, protected("shared").copy(action = ProtectedTrafficAction.BLOCK), RuleAvailability.Shared, 1)
            val changed = fixture.library.snapshot.value.protectedRules.single()
            assertEquals(2, changed.revision)
            assertFalse(changed.activeOn(a)); assertTrue(changed.activeOn(b))
            assertEquals(ProtectedTrafficAction.BLOCK, changed.rule.action)
            assertFailure(DevicePolicyFailure.STALE_REVISION) {
                fixture.library.saveProtection(a, protected("shared"), RuleAvailability.Shared, 1)
            }
            assertEquals(changed, fixture.library.snapshot.value.protectedRules.single())
        }
    }

    @Test fun equivalentReplacementChangesOnlyTargetBindingAndRollsBackRejectedCreation() = runBlocking {
        fixture { fixture ->
            val shared = NetworkConditionRule(NetworkConditionRuleId("shared"), NetworkConditionTarget.parse("same.test"), NetworkConditionBuiltIns.all.first { it.id != NetworkConditionBuiltIns.NO_THROTTLING.id }.id)
            fixture.library.saveCondition(a, shared, RuleAvailability.Shared)
            fixture.library.activate(b, PolicyRuleKind.NETWORK_CONDITION, "shared", true)
            val private = shared.copy(id = NetworkConditionRuleId("private"), profileId = NetworkConditionBuiltIns.NO_THROTTLING.id)
            assertFailure(DevicePolicyFailure.ACTIVE_EQUIVALENT) {
                fixture.library.saveCondition(a, private, RuleAvailability.Private(a), replaceEquivalent = false)
            }
            assertEquals(1, fixture.library.snapshot.value.conditions.size)
            fixture.library.saveCondition(a, private, RuleAvailability.Private(a), replaceEquivalent = true)
            val state = fixture.library.snapshot.value
            assertFalse(state.conditions.first { it.rule.id == shared.id }.activeOn(a))
            assertTrue(state.conditions.first { it.rule.id == shared.id }.activeOn(b))
            assertEquals(listOf(private.id), state.network(a).rules.filter { it.enabled }.map { it.id })
            assertEquals(listOf(shared.id), state.network(b).rules.filter { it.enabled }.map { it.id })
        }
    }

    @Test fun sharedDeletionRequiresImpactAcknowledgementAndCascadesAllBindings() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                val id = "shared-$kind"
                fixture.save(kind, id, a, RuleAvailability.Shared)
                fixture.library.activate(b, kind, id, true)
                assertFailure(DevicePolicyFailure.SHARED_IN_USE) { fixture.library.delete(a, kind, id, 1) }
                fixture.library.delete(a, kind, id, 1, acknowledgeSharedUsage = true)
                assertFailure(DevicePolicyFailure.MISSING_DEFINITION) { fixture.library.activate(b, kind, id, true) }
            }
            assertTrue(fixture.database.devicePolicyDao().breakpointActivations().isEmpty())
            assertTrue(fixture.database.devicePolicyDao().conditionActivations().isEmpty())
            assertTrue(fixture.database.devicePolicyDao().protectionActivations().isEmpty())
        }
    }

    @Test fun settingsAndDefaultTlsHandlingRemainIndependentAcrossDeviceAndOtherTraffic() = runBlocking {
        fixture { fixture ->
            fixture.library.updateSettings(a) { it.copy(networkEnabled = true, profileId = NetworkConditionBuiltIns.all.first { it.id != NetworkConditionBuiltIns.NO_THROTTLING.id }.id, protectedAction = ProtectedTrafficAction.TUNNEL) }
            fixture.library.updateSettings(b) { it.copy(protectedAction = ProtectedTrafficAction.BLOCK, interceptionEnabled = false) }
            val state = fixture.library.snapshot.value
            assertTrue(state.network(a).enabled)
            assertFalse(state.network(b).enabled)
            assertFalse(state.network(PolicyWorkspace.Other).enabled)
            assertEquals(ProtectedTrafficAction.TUNNEL, state.protection(a).defaultAction)
            assertEquals(ProtectedTrafficAction.BLOCK, state.protection(b).defaultAction)
            assertEquals(ProtectedTrafficAction.INSPECT, state.protection(PolicyWorkspace.Other).defaultAction)
            assertTrue(state.settingsFor(a).interceptionEnabled)
            assertFalse(state.settingsFor(b).interceptionEnabled)
        }
    }

    @Test fun independentRepositoriesSerializeReadModifyWriteAndRejectOneConcurrentStaleEditor() = runBlocking {
        fixture { fixture ->
            val sibling = RoomDevicePolicyLibrary(fixture.database, fixture.scope)
            sibling.snapshot.first { it.ready }
            fixture.library.saveBreakpoint(a, breakpoint("shared"), RuleAvailability.Shared)
            val results = coroutineScope {
                listOf(fixture.library, sibling).mapIndexed { index, repository -> async(Dispatchers.Default) {
                    runCatching { repository.saveBreakpoint(a, breakpoint("shared").copy(priority = index + 1), RuleAvailability.Shared, 1) }
                } }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(DevicePolicyFailure.STALE_REVISION, (results.single { it.isFailure }.exceptionOrNull() as DevicePolicyException).reason)
            coroutineScope {
                listOf(fixture.library, sibling).flatMap { repository ->
                    listOf(a, b).map { workspace -> async(Dispatchers.Default) {
                        repeat(10) { repository.updateSettings(workspace) { it.copy(networkEnabled = !it.networkEnabled) } }
                    } }
                }.awaitAll()
            }
            val final = fixture.library.snapshot.first { it.revision >= sibling.snapshot.value.revision }
            assertFalse(final.settingsFor(a).networkEnabled); assertFalse(final.settingsFor(b).networkEnabled)
        }
    }

    @Test fun forgetCascadesPrivateDefinitionsAndBindingsButRetainsSharedDefinitionsAndSibling() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                fixture.save(kind, "shared-$kind", a, RuleAvailability.Shared)
                fixture.library.activate(b, kind, "shared-$kind", true)
                fixture.save(kind, "private-$kind", a, RuleAvailability.Private(a))
            }
            fixture.library.forget(a)
            for (kind in PolicyRuleKind.entries) {
                assertTrue(fixture.entry(kind, "shared-$kind").activeOn(b))
                assertFalse(fixture.entry(kind, "shared-$kind").activeOn(a))
                assertFailure(DevicePolicyFailure.WORKSPACE_REMOVED) { fixture.library.activate(a, kind, "private-$kind", true) }
            }
        }
    }

    @Test fun unregisteredWorkspaceCannotCreateAndCreationHasNoPartialDefinition() = runBlocking {
        fixture { fixture ->
            val unknown = PolicyWorkspace.Device(RegisteredDeviceId("missing"))
            assertFailure(DevicePolicyFailure.WORKSPACE_REMOVED) { fixture.save(PolicyRuleKind.BREAKPOINT, "orphan", unknown, RuleAvailability.Shared) }
            assertTrue(fixture.library.snapshot.value.breakpoints.isEmpty())
        }
    }

    @Test fun customProfileCannotBeDeletedWhenAnotherDeviceOrInactiveDefinitionReferencesIt() = runBlocking {
        fixture { fixture ->
            val profile = NetworkConditionProfile(NetworkConditionProfileId("custom"), "Custom")
            fixture.library.saveProfile(profile)
            fixture.library.updateSettings(b) { it.copy(profileId = profile.id) }
            assertFalse(fixture.library.deleteProfile(profile.id))
            fixture.library.updateSettings(b) { it.copy(profileId = null) }
            val rule = NetworkConditionRule(NetworkConditionRuleId("custom-rule"), NetworkConditionTarget.parse("custom.test"), profile.id)
            fixture.library.saveCondition(a, rule, RuleAvailability.Shared)
            fixture.library.activate(a, PolicyRuleKind.NETWORK_CONDITION, rule.id.value, false)
            assertFalse(fixture.library.deleteProfile(profile.id))
            fixture.library.delete(a, PolicyRuleKind.NETWORK_CONDITION, rule.id.value, 1)
            assertTrue(fixture.library.deleteProfile(profile.id))
        }
    }

    @Test fun editingInactiveSharedDefinitionsNeverEnablesAnyWorkspace() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                val id = "inactive-$kind"
                fixture.save(kind, id, a, RuleAvailability.Shared)
                fixture.library.activate(a, kind, id, false)
                fixture.save(kind, id, b, RuleAvailability.Shared, 1)
                assertTrue(fixture.entry(kind, id).enabledOn.isEmpty())
                assertEquals(2L, fixture.entry(kind, id).revision)
            }
        }
    }

    @Test fun createAndEnableIgnoresLegacyDisabledFieldForAllThreeFamilies() = runBlocking {
        fixture { fixture ->
            fixture.library.saveBreakpoint(a, breakpoint("new").copy(enabled = false), RuleAvailability.Shared)
            fixture.library.saveCondition(a, NetworkConditionRule(NetworkConditionRuleId("new"), NetworkConditionTarget.parse("new.test"), NetworkConditionBuiltIns.NO_THROTTLING.id, enabled = false), RuleAvailability.Shared)
            fixture.library.saveProtection(a, protected("new").copy(enabled = false), RuleAvailability.Shared)
            PolicyRuleKind.entries.forEach { assertEquals(setOf(a), fixture.entry(it, "new").enabledOn) }
        }
    }

    @Test fun promotionPreservesDisabledOwnerAndSharedToPrivateRequiresDuplication() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                val id = "promote-$kind"
                fixture.save(kind, id, a, RuleAvailability.Private(a))
                fixture.library.activate(a, kind, id, false)
                fixture.save(kind, id, a, RuleAvailability.Shared, 1)
                assertTrue(fixture.entry(kind, id).enabledOn.isEmpty())
                assertTrue(fixture.entry(kind, id).visibleTo(b))
                assertFailure(DevicePolicyFailure.PRIVATE_DEFINITION) { fixture.save(kind, id, a, RuleAvailability.Private(a), 2) }
                assertEquals(RuleAvailability.Shared, fixture.entry(kind, id).availability)
            }
        }
    }

    @Test fun sharedSelectorEditCannotCreateAmbiguityOnAnotherDevice() = runBlocking {
        fixture { fixture ->
            fixture.library.saveProtection(a, protected("shared"), RuleAvailability.Shared)
            fixture.library.activate(b, PolicyRuleKind.PROTECTED_TRAFFIC, "shared", true)
            val private = protected("private").copy(destination = ProtectedDestinationSelector.parse("other.test"))
            fixture.library.saveProtection(b, private, RuleAvailability.Private(b))
            assertFailure(DevicePolicyFailure.ACTIVE_EQUIVALENT) {
                fixture.library.saveProtection(a, protected("shared").copy(destination = private.destination), RuleAvailability.Shared, 1, replaceEquivalent = true)
            }
            assertTrue(fixture.library.snapshot.value.ready)
            assertEquals(1L, fixture.entry(PolicyRuleKind.PROTECTED_TRAFFIC, "shared").revision)
            assertEquals(2, fixture.library.snapshot.value.runtimeProtection(b).rules.size)
        }
    }

    @Test fun explicitQuickApplyReenablesAtomicallyWhileOrdinaryEditsPreserveActivation() = runBlocking {
        fixture { fixture ->
            fixture.library.saveProtection(a, protected("quick"), RuleAvailability.Private(a))
            fixture.library.activate(a, PolicyRuleKind.PROTECTED_TRAFFIC, "quick", false)
            fixture.library.saveProtection(a, protected("quick"), RuleAvailability.Private(a), 1, activateAfterSave = true)
            assertEquals(setOf(a), fixture.entry(PolicyRuleKind.PROTECTED_TRAFFIC, "quick").enabledOn)
        }
    }

    @Test fun cancelledSettingTransformRollsBackWorkspaceAndCannotPublishPartialState() = runBlocking {
        fixture { fixture ->
            val before = fixture.library.snapshot.value
            assertFailsWith<CancellationException> { fixture.library.updateSettings(a) { throw CancellationException("cancel fixture") } }
            assertEquals(before, fixture.library.snapshot.value)
            assertTrue(fixture.database.devicePolicyDao().workspaces().isEmpty())
        }
    }

    @Test fun reopeningDatabaseRetainsDefinitionsBindingsAndIndependentDefaults() = runBlocking {
        val directory = Files.createTempDirectory("knet-policy-reopen-").toFile()
        val file = directory.resolve("test.db")
        var database = DatabaseFactory.create(file)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            database.registeredDeviceDao().upsertRegisteredDevice(device("a"))
            var library = RoomDevicePolicyLibrary(database, scope)
            library.snapshot.first { it.ready }
            library.saveBreakpoint(a, breakpoint("retained"), RuleAvailability.Shared)
            library.updateSettings(a) { it.copy(networkEnabled = true, protectedAction = ProtectedTrafficAction.TUNNEL) }
            val expected = library.snapshot.value
            scope.coroutineContext.job.cancelAndJoin(); database.close()
            database = DatabaseFactory.create(file); scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            library = RoomDevicePolicyLibrary(database, scope)
            assertEquals(expected, withTimeout(5_000) { library.snapshot.first { it.ready } })
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); directory.deleteRecursively() }
    }

    @Test fun newlyEnabledSiblingInvalidatesPreviouslyDisclosedSharedEditAndDeleteImpact() = runBlocking {
        fixture { fixture ->
            fixture.library.saveProtection(a, protected("shared"), RuleAvailability.Shared)
            fixture.library.activate(b, PolicyRuleKind.PROTECTED_TRAFFIC, "shared", true)
            assertFailure(DevicePolicyFailure.STALE_REVISION) {
                fixture.library.saveProtection(a, protected("shared").copy(action=ProtectedTrafficAction.BLOCK), RuleAvailability.Shared,1,expectedUsage=setOf(a))
            }
            assertFailure(DevicePolicyFailure.STALE_REVISION) {
                fixture.library.delete(a,PolicyRuleKind.PROTECTED_TRAFFIC,"shared",1,true,expectedUsage=setOf(a))
            }
            assertEquals(setOf(a,b),fixture.entry(PolicyRuleKind.PROTECTED_TRAFFIC,"shared").enabledOn)
        }
    }

    @Test fun forgottenOwnerCannotCommitAnEditorOrCreateFromStaleHistory() = runBlocking {
        fixture { fixture ->
            fixture.library.saveProtection(a,protected("shared"),RuleAvailability.Shared)
            val opened = fixture.library.snapshot.value.protectedRules.single().editContext(a)
            fixture.library.forget(a)
            assertFailure(DevicePolicyFailure.WORKSPACE_REMOVED) { fixture.library.saveProtection(a,protected("shared"),RuleAvailability.Shared,opened.expectedRevision) }
            assertFailure(DevicePolicyFailure.WORKSPACE_REMOVED) { fixture.library.saveBreakpoint(a,breakpoint("stale"),RuleAvailability.Private(a)) }
            assertEquals(null,fixture.database.registeredDeviceDao().getRegisteredDevice("a"))
            assertTrue(fixture.library.snapshot.value.breakpoints.isEmpty())
        }
    }

    @Test fun malformedPrivateNetworkReferenceBypassesOnlyItsOwnerWithoutLosingTlsDefaults() = runBlocking {
        fixture { fixture ->
            fixture.library.updateSettings(a) { it.copy(networkEnabled=true,protectedAction=ProtectedTrafficAction.BLOCK) }
            fixture.library.updateSettings(b) { it.copy(networkEnabled=true,profileId=NetworkConditionBuiltIns.STREAMING_100_KBPS.id) }
            fixture.database.networkConditionDao().upsertRule(com.devuloopers.knet.storage.networkconditions.entity.NetworkConditionRuleEntity(
                id="orphan-profile",normalizedHost="orphan.test",wildcard=false,port=443,profileId="missing-profile",enabled=true,libraryOwnerKey=a.key))
            fixture.database.devicePolicyDao().saveConditionActivation(com.devuloopers.knet.storage.policy.entity.ConditionActivationEntity("orphan-profile",a.key,true))
            val state=withTimeout(5_000) { fixture.library.snapshot.first { it.conditions.any { it.rule.id.value=="orphan-profile" } } }
            assertFalse(state.network(a).enabled)
            assertTrue(state.network(b).enabled)
            assertEquals(ProtectedTrafficAction.BLOCK,state.protection(a).defaultAction)
            assertEquals(1,fixture.database.networkConditionDao().getRules().size)
        }
    }

    @Test fun concurrentProfileDeleteAndRuleCreationCannotLeaveAnOrphanReference() = runBlocking {
        fixture { fixture ->
            repeat(10) { index ->
                val profile=NetworkConditionProfile(NetworkConditionProfileId("race-$index"),"Race $index")
                fixture.library.saveProfile(profile)
                val rule=NetworkConditionRule(NetworkConditionRuleId("race-$index"),NetworkConditionTarget.parse("race-$index.test"),profile.id)
                coroutineScope {
                    val deletion=async(Dispatchers.Default) { fixture.library.deleteProfile(profile.id) }
                    val creation=async(Dispatchers.Default) { runCatching { fixture.library.saveCondition(a,rule,RuleAvailability.Private(a)) } }
                    val deleted=deletion.await(); val created=creation.await().isSuccess
                    assertEquals(!deleted,created)
                }
                val state=fixture.library.snapshot.value
                assertTrue(state.conditions.none { it.rule.id==rule.id } || state.profiles.any { it.id==profile.id })
            }
        }
    }

    @Test fun automaticExactReplacementKeepsPreviousDefinitionsAndSiblingSettingsForEveryRuleFamily() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) for (availability in listOf(RuleAvailability.Shared, RuleAvailability.Private(a))) {
                val oldId="old-$kind-${availability is RuleAvailability.Shared}"
                val newId="new-$kind-${availability is RuleAvailability.Shared}"
                fixture.saveEquivalent(kind,oldId,a,availability)
                if (availability == RuleAvailability.Shared) fixture.library.activate(b,kind,oldId,true)
                val previous=fixture.entry(kind,oldId)
                val result=fixture.saveEquivalent(kind,newId,a,availability,changed=true)
                assertEquals(setOf(oldId),result.replacedRuleIds)
                assertFalse(fixture.entry(kind,oldId).activeOn(a))
                assertEquals(previous.rule,fixture.entry(kind,oldId).rule)
                assertEquals(previous.revision,fixture.entry(kind,oldId).revision)
                assertEquals(previous.enabledOn - a,fixture.entry(kind,oldId).enabledOn)
                assertEquals(setOf(a),fixture.entry(kind,newId).enabledOn)
                assertFalse(fixture.entry(kind,newId).activeOn(PolicyWorkspace.Other))
            }
        }
    }

    @Test fun editingInactiveAlternativesAndSavingTheSameRuleDoNotReplaceOrEnableAnything() = runBlocking {
        fixture { fixture ->
            for (kind in PolicyRuleKind.entries) {
                fixture.saveEquivalent(kind,"inactive-$kind",a,RuleAvailability.Shared)
                fixture.library.activate(a,kind,"inactive-$kind",false)
                fixture.saveEquivalent(kind,"active-$kind",a,RuleAvailability.Shared)
                val result=fixture.saveEquivalent(kind,"inactive-$kind",a,RuleAvailability.Shared,changed=true,revision=1)
                assertFalse(result.replacedExistingRule)
                assertFalse(fixture.entry(kind,"inactive-$kind").activeOn(a))
                assertTrue(fixture.entry(kind,"active-$kind").activeOn(a))
                val self=fixture.saveEquivalent(kind,"active-$kind",a,RuleAvailability.Shared,changed=true,revision=1)
                assertFalse(self.replacedExistingRule)
                assertTrue(fixture.entry(kind,"active-$kind").activeOn(a))
            }
        }
    }

    @Test fun overlappingTargetsAndDifferentMethodPortPhaseOrOperationsRemainIndependentlyActive() = runBlocking {
        fixture { fixture ->
            val baseline=breakpoint("get").copy(urlPattern="https://same.test/*",method=com.devuloopers.knet.traffic.model.http.HttpMethod.GET,
                portCriteria=BreakpointPortCriteria.Exact(443),phase=BreakpointPhase.REQUEST)
            val rules=listOf(baseline,baseline.copy(id="post",method=com.devuloopers.knet.traffic.model.http.HttpMethod.POST),
                baseline.copy(id="response",phase=BreakpointPhase.RESPONSE),baseline.copy(id="port",portCriteria=BreakpointPortCriteria.Exact(8443)),
                baseline.copy(id="operation-one",protocolCriteria=ProtocolMatchCriteria(BreakpointProtocolId("graphql"),"""{"version":1,"operationName":"One"}""")),
                baseline.copy(id="operation-two",protocolCriteria=ProtocolMatchCriteria(BreakpointProtocolId("graphql"),"""{"version":1,"operationName":"Two"}""")))
            rules.forEach { assertFalse(fixture.library.saveBreakpoint(a,it,RuleAvailability.Shared).replacedExistingRule) }
            assertEquals(rules.map { it.id }.toSet(),fixture.library.snapshot.value.breakpoints.filter { it.activeOn(a) }.map { it.rule.id }.toSet())
            val wildcard=NetworkConditionRule(NetworkConditionRuleId("wildcard"),NetworkConditionTarget.parse("*.overlap.test"),NetworkConditionBuiltIns.OFFLINE.id,priority=100)
            val exact=wildcard.copy(id=NetworkConditionRuleId("exact"),target=NetworkConditionTarget.parse("api.overlap.test"),profileId=NetworkConditionBuiltIns.NO_THROTTLING.id,priority=0)
            fixture.library.saveCondition(a,wildcard,RuleAvailability.Shared)
            assertFalse(fixture.library.saveCondition(a,exact,RuleAvailability.Shared).replacedExistingRule)
            fixture.library.updateSettings(a) { it.copy(networkEnabled=true) }
            assertEquals(wildcard.id,NetworkConditionMatcher.resolve(fixture.library.snapshot.value.runtimeNetwork(a),"api.overlap.test",443)?.ruleId)
            val broad=protected("broad").copy(destination=ProtectedDestinationSelector.parse("*.overlap.test"))
            val narrow=broad.copy(id=ProtectedTrafficRuleId("narrow"),destination=ProtectedDestinationSelector.parse("api.overlap.test"))
            fixture.library.saveProtection(a,broad,RuleAvailability.Shared)
            assertFalse(fixture.library.saveProtection(a,narrow,RuleAvailability.Shared).replacedExistingRule)
            val application=narrow.copy(id=ProtectedTrafficRuleId("application"),sourceApplication=ProtectedSourceApplicationId("test.application"))
            assertFalse(fixture.library.saveProtection(a,application,RuleAvailability.Shared).replacedExistingRule)
            assertEquals(setOf(broad.id,narrow.id,application.id),fixture.library.snapshot.value.protectedRules.filter { it.activeOn(a) }.map { it.rule.id }.toSet())
        }
    }

    @Test fun failureAfterDisablingAnEquivalentRuleRollsBackTheWholeReplacement() = runBlocking {
        fixture { fixture ->
            val old=breakpoint("old")
            val editing=breakpoint("editing")
            fixture.library.saveBreakpoint(a,old,RuleAvailability.Shared)
            fixture.library.activate(b,PolicyRuleKind.BREAKPOINT,old.id,true)
            fixture.library.saveBreakpoint(a,editing,RuleAvailability.Shared)
            val originalEditing=fixture.entry(PolicyRuleKind.BREAKPOINT,editing.id).rule
            val row=fixture.database.breakpointRuleDao().getAllRules().single { it.id==editing.id }
            fixture.database.breakpointRuleDao().upsertRule(row.copy(libraryRevision=Long.MAX_VALUE))
            withTimeout(5_000) { fixture.library.snapshot.first { snapshot -> snapshot.breakpoints.any { it.rule.id==editing.id && it.revision==Long.MAX_VALUE } } }
            // Revision overflow occurs after conflict binding changes but before the definition upsert.
            assertFailsWith<ArithmeticException> { fixture.library.saveBreakpoint(a,editing.copy(urlPattern=old.urlPattern),RuleAvailability.Shared,Long.MAX_VALUE) }
            val persisted=fixture.database.devicePolicyDao().breakpointActivations().filter { it.ruleId==old.id }
            assertEquals(2,persisted.count { it.enabled })
            assertEquals(editing.urlPattern,fixture.database.breakpointRuleDao().getAllRules().single { it.id==editing.id }.urlPattern)
            assertEquals(setOf(a,b),fixture.entry(PolicyRuleKind.BREAKPOINT,old.id).enabledOn)
            assertEquals(originalEditing,fixture.entry(PolicyRuleKind.BREAKPOINT,editing.id).rule)
            assertTrue(fixture.entry(PolicyRuleKind.BREAKPOINT,editing.id).activeOn(a))
        }
    }

    @Test fun concurrentAutomaticReplacementsPublishOneActiveRuleAndPreserveEverySavedDefinition() = runBlocking {
        fixture { fixture ->
            fixture.saveEquivalent(PolicyRuleKind.NETWORK_CONDITION,"original",a,RuleAvailability.Shared)
            fixture.library.activate(b,PolicyRuleKind.NETWORK_CONDITION,"original",true)
            val second=RoomDevicePolicyLibrary(fixture.database,fixture.scope)
            withTimeout(5_000) { second.snapshot.first { it.ready } }
            val observed=java.util.concurrent.CopyOnWriteArrayList<Int>()
            val observer=fixture.scope.launch { fixture.library.snapshot.collect { snapshot -> observed += snapshot.conditions.count { it.activeOn(a) } } }
            withTimeout(5_000) { while (observed.isEmpty()) delay(1) }
            val source=fixture.library.snapshot.value.conditions.single().rule
            val results=listOf(fixture.library,second).mapIndexed { index,repository -> async(Dispatchers.Default) {
                repository.saveCondition(a,source.copy(id=NetworkConditionRuleId("concurrent-$index")),RuleAvailability.Shared)
            } }.awaitAll()
            val committed=withTimeout(5_000) { fixture.library.snapshot.first { it.conditions.size==3 } }
            observer.cancelAndJoin()
            assertEquals(3,committed.conditions.size)
            assertEquals(1,committed.conditions.count { it.activeOn(a) })
            assertEquals(setOf(b),committed.conditions.single { it.rule.id.value=="original" }.enabledOn)
            assertTrue(results.all { it.replacedExistingRule })
            assertTrue(results.flatMap { it.replacedRuleIds }.contains("original"))
            assertTrue(observed.all { it<=1 })
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("knet-device-policy-").toFile()
        val database = DatabaseFactory.create(directory.resolve("test.db"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            for (id in listOf("a", "b")) database.registeredDeviceDao().upsertRegisteredDevice(device(id))
            val library = RoomDevicePolicyLibrary(database, scope)
            withTimeout(10_000) { library.snapshot.first { it.ready } }
            block(Fixture(database, scope, library))
        } finally { scope.coroutineContext.job.cancelAndJoin(); database.close(); directory.deleteRecursively() }
    }

    private class Fixture(val database: KNetDatabase, val scope: CoroutineScope, val library: RoomDevicePolicyLibrary) {
        suspend fun save(kind: PolicyRuleKind, id: String, workspace: PolicyWorkspace, availability: RuleAvailability, revision: Long? = null) = when (kind) {
            PolicyRuleKind.BREAKPOINT -> library.saveBreakpoint(workspace, breakpoint(id), availability, revision)
            PolicyRuleKind.NETWORK_CONDITION -> library.saveCondition(workspace, NetworkConditionRule(NetworkConditionRuleId(id), NetworkConditionTarget.parse("${id.lowercase().replace('_', '-')}.test"), NetworkConditionBuiltIns.NO_THROTTLING.id), availability, revision)
            PolicyRuleKind.PROTECTED_TRAFFIC -> library.saveProtection(workspace, protected(id), availability, revision)
        }
        suspend fun saveEquivalent(kind: PolicyRuleKind, id: String, workspace: PolicyWorkspace, availability: RuleAvailability,
            changed: Boolean = false, revision: Long? = null): RuleSaveResult = when(kind) {
            PolicyRuleKind.BREAKPOINT -> library.saveBreakpoint(workspace,breakpoint(id).copy(urlPattern="https://equivalent.test/*",priority=if(changed) 10 else 0),availability,revision)
            PolicyRuleKind.NETWORK_CONDITION -> library.saveCondition(workspace,NetworkConditionRule(NetworkConditionRuleId(id),NetworkConditionTarget.parse("equivalent.test"),
                if(changed) NetworkConditionBuiltIns.OFFLINE.id else NetworkConditionBuiltIns.STREAMING_100_KBPS.id),availability,revision)
            PolicyRuleKind.PROTECTED_TRAFFIC -> library.saveProtection(workspace,protected(id).copy(destination=ProtectedDestinationSelector.parse("equivalent.test"),
                action=if(changed) ProtectedTrafficAction.BLOCK else ProtectedTrafficAction.TUNNEL),availability,revision)
        }
        fun entry(kind: PolicyRuleKind, id: String): PolicyDefinition<*> = when (kind) {
            PolicyRuleKind.BREAKPOINT -> library.snapshot.value.breakpoints.first { it.rule.id == id }
            PolicyRuleKind.NETWORK_CONDITION -> library.snapshot.value.conditions.first { it.rule.id.value == id }
            PolicyRuleKind.PROTECTED_TRAFFIC -> library.snapshot.value.protectedRules.first { it.rule.id.value == id }
        }
    }

    private companion object {
        val a = PolicyWorkspace.Device(RegisteredDeviceId("a"))
        val b = PolicyWorkspace.Device(RegisteredDeviceId("b"))
        fun device(id: String) = RegisteredDeviceEntity(id, "Test $id", "PAIRED_COMPANION", 1L, 1L, null)
        fun breakpoint(id: String) = BreakpointRule(id = id, urlPattern = "*/$id")
        fun protected(id: String) = ProtectedTrafficRule(ProtectedTrafficRuleId(id), action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse("${id.lowercase().replace('_', '-')}.test"), port = 443)
        suspend fun assertFailure(reason: DevicePolicyFailure, operation: suspend () -> Unit) {
            val failure = assertFailsWith<DevicePolicyException> { operation() }
            assertEquals(reason, failure.reason)
        }
    }
}
