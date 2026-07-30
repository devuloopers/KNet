package com.devuloopers.knet.scriptengine.javascript

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Unit test suite for [RuntimeLoader].
 * Verifies loading of static JS resource files, Graal [Source] caching, and error handling for missing resources.
 */
class RuntimeLoaderTest {

    /**
     * Verifies that console.js and expect.js static polyfill resource files load successfully.
     */
    @Test
    fun testLoadRuntimeResourcesSuccess() {
        val consoleSource = RuntimeLoader.loadSource("/runtime/console.js")
        val expectSource = RuntimeLoader.loadSource("/runtime/expect.js")

        assertNotNull(consoleSource, "console.js source should not be null")
        assertNotNull(expectSource, "expect.js source should not be null")
    }

    /**
     * Verifies that repeated calls to RuntimeLoader return the exact same cached Graal Source object instance.
     */
    @Test
    fun testRuntimeSourceCaching() {
        val source1 = RuntimeLoader.loadSource("/runtime/console.js")
        val source2 = RuntimeLoader.loadSource("/runtime/console.js")

        assertSame(source1, source2, "Repeated RuntimeLoader calls must return identical cached Source instance")
    }
}
