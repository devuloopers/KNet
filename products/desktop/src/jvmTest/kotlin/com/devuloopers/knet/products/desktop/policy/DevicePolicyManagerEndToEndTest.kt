package com.devuloopers.knet.products.desktop.policy

import com.devuloopers.knet.application.contract.device.DevicePolicyLibrary
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.products.desktop.config.DesktopConfiguration
import com.devuloopers.knet.products.desktop.di.DesktopModules
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import com.devuloopers.knet.ui.desktop.protectedtraffic.viewmodel.ProtectedTrafficViewModel
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import kotlin.test.*

/** Real product composition and Room, driven through the same ViewModel commands as the desktop editors. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicePolicyManagerEndToEndTest {
    @Test fun createSharedFromAThenSwitchToBBeforeSaveStillEnablesOnlyAForAllThreeManagers() = runBlocking {
        fixture { library, network, protection, breakpoint ->
            network.selectWorkspace(a); protection.selectWorkspace(a); breakpoint.selectWorkspace(a)
            awaitManagers(a,network,protection,breakpoint)
            network.beginAddRule(); network.updateDraftHostPattern("network.test"); network.setDraftShared(true)
            protection.beginAddRule(); protection.updateRuleDraft(protection.state.value.ruleDraft!!.copy(destination="tls.test")); protection.setDraftShared(true)
            breakpoint.openAddDrawer(); breakpoint.setDraftShared(true)
            network.selectWorkspace(b); protection.selectWorkspace(b); breakpoint.selectWorkspace(b)
            awaitManagers(b,network,protection,breakpoint)
            assertEquals(a,network.state.value.ruleDraft!!.context.workspace)
            assertEquals(a,protection.state.value.ruleDraft!!.context.workspace)
            assertEquals(a,breakpoint.uiState.value.editingContext.workspace)
            network.confirmRuleDraft(); network.confirmRuleDraft()
            protection.saveRuleDraft(); protection.saveRuleDraft()
            breakpoint.saveRule("*/breakpoint",null,BreakpointPhase.REQUEST,true)
            breakpoint.saveRule("*/breakpoint",null,BreakpointPhase.REQUEST,true)
            val saved=withTimeout(10_000) { library.snapshot.first { it.conditions.size==1 && it.protectedRules.size==1 && it.breakpoints.size==1 } }
            for (definition in listOf(saved.conditions.single(),saved.protectedRules.single(),saved.breakpoints.single())) {
                assertEquals(RuleAvailability.Shared,definition.availability)
                assertEquals(setOf(a),definition.enabledOn)
            }
            withTimeout(5_000) { network.state.first { it.ruleDraft==null && it.configuration.rules.size==1 } }
            withTimeout(5_000) { protection.state.first { it.ruleDraft==null && it.configuration.rules.size==1 } }
            withTimeout(5_000) { breakpoint.uiState.first { !it.isAddEditDrawerVisible && it.rules.size==1 } }
            assertFalse(network.state.value.configuration.rules.single().enabled)
            assertFalse(protection.state.value.configuration.rules.single().enabled)
            assertFalse(breakpoint.uiState.value.rules.single().enabled)
        }
    }

    @Test fun sharedEditingAndPrivateDuplicationRespectCapturedWorkspaceAndDeletionImpact() = runBlocking {
        fixture { library, network, protection, breakpoint ->
            library.saveProtection(a,ProtectedTrafficRule(ProtectedTrafficRuleId("shared"),action=ProtectedTrafficAction.TUNNEL,
                destination=ProtectedDestinationSelector.parse("shared.test")),RuleAvailability.Shared)
            library.activate(b,PolicyRuleKind.PROTECTED_TRAFFIC,"shared",true)
            protection.selectWorkspace(b); awaitManagers(b,network=null,protection=protection,breakpoint=null)
            withTimeout(5_000) { protection.state.first { it.configuration.rules.any { it.id.value=="shared" } } }
            protection.beginEditRule(protection.state.value.configuration.rules.single())
            assertEquals(2,protection.state.value.ruleDraft!!.usageNames.size)
            protection.duplicateDraft()
            protection.selectWorkspace(a)
            protection.saveRuleDraft()
            val saved=withTimeout(5_000) { library.snapshot.first { it.protectedRules.size==2 } }
            val duplicate=saved.protectedRules.single { it.availability is RuleAvailability.Private }
            assertEquals(RuleAvailability.Private(b),duplicate.availability)
            assertEquals(setOf(b),duplicate.enabledOn)
            assertEquals(setOf(a),saved.protectedRules.single { it.rule.id.value=="shared" }.enabledOn)
            awaitManagers(a,null,protection,null)
            // The library commits before manager collectors receive that revision. A deletion must
            // acknowledge the rendered revision, rather than racing the duplicate save's emission.
            val sharedRevision = saved.protectedRules.single { it.rule.id.value=="shared" }.revision
            withTimeout(5_000) { protection.state.first { state ->
                !state.isSaving && state.definitions.any { it.rule.id.value=="shared" && it.revision==sharedRevision }
            } }
            protection.requestRuleDeletion(ProtectedTrafficRuleId("shared"))
            assertEquals(a,protection.state.value.pendingDeletion!!.context.workspace)
            protection.selectWorkspace(b)
            protection.confirmRuleDeletion()
            withTimeout(5_000) { library.snapshot.first { it.protectedRules.size==1 } }
            assertEquals(duplicate,library.snapshot.value.protectedRules.single())
        }
    }

    @Test fun defaultSettingsAndUnsavedPromotionStayIndependentWhenSwitchingManagers() = runBlocking {
        fixture { library, network, protection, breakpoint ->
            network.selectWorkspace(a); protection.selectWorkspace(a); breakpoint.selectWorkspace(a)
            awaitManagers(a,network,protection,breakpoint)
            network.activateGlobalProfile(NetworkConditionBuiltIns.STREAMING_100_KBPS.id)
            protection.selectDefaultHandling(ProtectedTrafficDefaultHandling.TUNNEL)
            breakpoint.toggleGlobalInterception(false)
            withTimeout(5_000) { library.snapshot.first { it.settingsFor(a).networkEnabled && it.settingsFor(a).protectedAction==ProtectedTrafficAction.TUNNEL && !it.settingsFor(a).interceptionEnabled } }
            assertFalse(library.snapshot.value.settingsFor(b).networkEnabled)
            assertEquals(ProtectedTrafficAction.INSPECT,library.snapshot.value.settingsFor(b).protectedAction)
            assertTrue(library.snapshot.value.settingsFor(b).interceptionEnabled)
            network.beginAddRule(); network.setDraftShared(true); network.setDraftShared(false)
            assertEquals(RuleAvailability.Private(a),network.state.value.ruleDraft!!.context.availability)
            network.dismissRuleDraft()
        }
    }

    @Test fun savingEquivalentRulesAutomaticallyReplacesOnlyTheCapturedDeviceAcrossAllManagers() = runBlocking {
        fixture { library,network,protection,breakpoint ->
            library.saveCondition(a,NetworkConditionRule(NetworkConditionRuleId("old-network"),NetworkConditionTarget.parse("same.test"),NetworkConditionBuiltIns.STREAMING_100_KBPS.id),RuleAvailability.Shared)
            library.saveProtection(a,ProtectedTrafficRule(ProtectedTrafficRuleId("old-protection"),destination=ProtectedDestinationSelector.parse("same.test"),port=443,
                transport=ProtectedTransportProtocol.TCP,action=ProtectedTrafficAction.TUNNEL),RuleAvailability.Shared)
            library.saveBreakpoint(a,com.devuloopers.knet.domain.rules.model.BreakpointRule("old-breakpoint",urlPattern="https://same.test/*",phase=BreakpointPhase.REQUEST),RuleAvailability.Shared)
            for ((kind,id) in listOf(PolicyRuleKind.NETWORK_CONDITION to "old-network",PolicyRuleKind.PROTECTED_TRAFFIC to "old-protection",PolicyRuleKind.BREAKPOINT to "old-breakpoint"))
                library.activate(b,kind,id,true)
            network.selectWorkspace(a); protection.selectWorkspace(a); breakpoint.selectWorkspace(a)
            awaitManagers(a,network,protection,breakpoint)
            val name=network.state.value.workspaceName
            network.beginAddRule(); network.updateDraftHostPattern("same.test"); network.selectDraftProfile(NetworkConditionBuiltIns.OFFLINE.id); network.setDraftShared(true)
            protection.beginAddRule(); protection.updateRuleDraft(protection.state.value.ruleDraft!!.copy(destination="same.test",action=ProtectedTrafficAction.BLOCK)); protection.setDraftShared(true)
            breakpoint.openAddDrawer(); breakpoint.setDraftShared(true)
            network.selectWorkspace(b); protection.selectWorkspace(b); breakpoint.selectWorkspace(b)
            awaitManagers(b,network,protection,breakpoint)
            network.confirmRuleDraft(); protection.saveRuleDraft(); breakpoint.saveRule("https://same.test/*",null,BreakpointPhase.REQUEST,true)
            val saved=withTimeout(10_000) { library.snapshot.first { it.conditions.size==2 && it.protectedRules.size==2 && it.breakpoints.size==2 } }
            for (definitions in listOf(saved.conditions,saved.protectedRules,saved.breakpoints)) {
                assertEquals(1,definitions.count { it.activeOn(a) })
                assertEquals(1,definitions.count { it.activeOn(b) })
                assertEquals(1,definitions.count { it.enabledOn==setOf(a) })
                assertEquals(1,definitions.count { it.enabledOn==setOf(b) })
                assertTrue(definitions.all { it.availability==RuleAvailability.Shared })
            }
            val networkNotice=withTimeout(5_000) { network.state.first { it.replacementNotice!=null } }.replacementNotice!!
            val protectionNotice=withTimeout(5_000) { protection.state.first { it.replacementNotice!=null } }.replacementNotice!!
            val breakpointNotice=withTimeout(5_000) { breakpoint.uiState.first { it.replacementNotice!=null } }.replacementNotice!!
            for (notice in listOf(networkNotice,protectionNotice,breakpointNotice)) {
                assertEquals(name,notice.deviceName)
                assertEquals("Existing rule replaced for $name.",notice.message)
            }
            assertNull(network.state.value.errorMessage); assertNull(protection.state.value.draftError); assertNull(breakpoint.uiState.value.errorMessage)
            withTimeout(6_000) {
                network.state.first { it.replacementNotice==null }
                protection.state.first { it.replacementNotice==null }
                breakpoint.uiState.first { it.replacementNotice==null }
            }
        }
    }

    private suspend fun awaitManagers(workspace:PolicyWorkspace,network:NetworkConditionsViewModel?,protection:ProtectedTrafficViewModel?,breakpoint:BreakpointManagerViewModel?) {
        withTimeout(5_000) {
            network?.state?.first { it.workspace==workspace && it.workspaceName.contains("Device") }
            protection?.state?.first { it.workspace==workspace && it.configuration.configurationReady && it.workspaceName.contains("Device") }
            breakpoint?.uiState?.first { it.workspace==workspace && it.workspaceName.contains("Device") }
        }
    }
    private suspend fun fixture(block:suspend(DevicePolicyLibrary,NetworkConditionsViewModel,ProtectedTrafficViewModel,BreakpointManagerViewModel)->Unit) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root=Files.createTempDirectory("knet-policy-manager-")
        val application=koinApplication { modules(listOf(module { single { DesktopConfiguration(appDirectory=root) } })+DesktopModules.all) }
        val database=application.koin.get<KNetDatabase>()
        val store=androidx.lifecycle.ViewModelStore()
        val owner=application.koin.get<CoroutineScope>()
        try {
            for(id in listOf("a","b")) database.registeredDeviceDao().upsertRegisteredDevice(RegisteredDeviceEntity(id,"Device $id","PAIRED_COMPANION",1,1,null))
            val library=application.koin.get<DevicePolicyLibrary>()
            withTimeout(5_000) { library.snapshot.first { it.ready } }
            val network=application.koin.get<NetworkConditionsViewModel>()
            val protection=application.koin.get<ProtectedTrafficViewModel>()
            val breakpoint=application.koin.get<BreakpointManagerViewModel>()
            store.put("network",network); store.put("protection",protection); store.put("breakpoint",breakpoint)
            block(library,network,protection,breakpoint)
        } finally {
            store.clear(); application.close(); owner.coroutineContext.job.join()
            database.close(); root.toFile().deleteRecursively(); Dispatchers.resetMain()
        }
    }
    private companion object {
        val a=PolicyWorkspace.Device(RegisteredDeviceId("a")); val b=PolicyWorkspace.Device(RegisteredDeviceId("b"))
    }
}
