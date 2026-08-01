package com.devuloopers.knet.engine.traffic

import kotlin.test.Test
import kotlin.test.assertNotNull

class MigrationRegressionTest {

    @Test
    fun testPublicApiContractsIntact() {
        val manager = TrafficModifierManager()
        val tempFile = TestFixtures.createTempFile()

        manager.addModifierRule(ModifierRule("r1", "Mod", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD))
        manager.addMapLocalRule(MapLocalRule("ml1", "Local", ".*", tempFile.absolutePath))
        manager.addMapRemoteRule(MapRemoteRule("mr1", "Remote", ".*", "target.com", 443))

        assertNotNull(manager.getModifierRules())
        assertNotNull(manager.getMapLocalRules())
        assertNotNull(manager.getMapRemoteRules())
    }
}
