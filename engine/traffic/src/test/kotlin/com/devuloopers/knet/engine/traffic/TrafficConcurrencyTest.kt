package com.devuloopers.knet.engine.traffic

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficConcurrencyTest {

    @Test
    fun testParallelRuleRegistrationAndLookups() {
        val manager = TrafficModifierManager()
        val executor = Executors.newFixedThreadPool(10)

        for (i in 0 until 100) {
            executor.submit {
                manager.addModifierRule(
                    ModifierRule("r-$i", "Rule $i", ".*api-$i.*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-Idx", "$i")
                )
                manager.getModifierRules()
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(true, finished, "Concurrent rule registration must complete within timeout")
        assertEquals(100, manager.getModifierRules().size)
    }
}
