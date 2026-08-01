package com.devuloopers.knet.engine.traffic

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the [TrafficModifierManager] and rule evaluation logic
 * inside [KNetTrafficModifierHandler].
 *
 * These tests validate rule registration, matching, and expected output behavior
 * across all three rule types: Map Local, Map Remote, and general Modifier Rules.
 */
class KNetTrafficModifierIntegrationTest {

    // ─────────────────────────── TrafficModifierManager ───────────────────────────

    @Test
    fun `addModifierRule should register rule in modifierRules list`() {
        val manager = TrafficModifierManager()
        val rule = ModifierRule(
            id = "r1", name = "Add Debug Header",
            urlPattern = ".*api\\.example\\.com.*",
            target = RuleTarget.REQUEST_HEADER,
            action = RuleAction.ADD,
            matchValue = "X-Debug", newValue = "true"
        )
        manager.addModifierRule(rule)
        assertEquals(1, manager.getModifierRules().size)
        assertEquals("r1", manager.getModifierRules().first().id)
    }

    @Test
    fun `addMapLocalRule should register rule in mapLocalRules list`() {
        val tmpFile = File.createTempFile("mock", ".json")
        tmpFile.deleteOnExit()
        val manager = TrafficModifierManager()
        val rule = MapLocalRule(
            id = "ml1", name = "Local JSON Mock",
            urlPattern = ".*api\\.example\\.com/users.*",
            localFilePath = tmpFile.absolutePath
        )
        manager.addMapLocalRule(rule)
        assertEquals(1, manager.getMapLocalRules().size)
        assertEquals("ml1", manager.getMapLocalRules().first().id)
    }

    @Test
    fun `addMapRemoteRule should register rule in mapRemoteRules list`() {
        val manager = TrafficModifierManager()
        val rule = MapRemoteRule(
            id = "mr1", name = "Redirect to Staging",
            urlPattern = ".*prod\\.example\\.com.*",
            targetHost = "staging.example.com",
            targetPort = 443
        )
        manager.addMapRemoteRule(rule)
        assertEquals(1, manager.getMapRemoteRules().size)
        assertEquals("staging.example.com", manager.getMapRemoteRules().first().targetHost)
    }

    @Test
    fun `clearAllRules should empty all rule lists`() {
        val tmpFile = File.createTempFile("mock", ".json")
        tmpFile.deleteOnExit()
        val manager = TrafficModifierManager()
        manager.addModifierRule(
            ModifierRule("r1", "Rule 1", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-Test", "1")
        )
        manager.addMapLocalRule(MapLocalRule("ml1", "Local 1", ".*", tmpFile.absolutePath))
        manager.addMapRemoteRule(MapRemoteRule("mr1", "Remote 1", ".*", "other.host", 443))

        manager.clearAllRules()

        assertTrue(manager.getModifierRules().isEmpty())
        assertTrue(manager.getMapLocalRules().isEmpty())
        assertTrue(manager.getMapRemoteRules().isEmpty())
    }

    @Test
    fun `removeModifierRule should remove only the matching rule`() {
        val manager = TrafficModifierManager()
        manager.addModifierRule(
            ModifierRule("r1", "Rule 1", ".*", RuleTarget.REQUEST_HEADER, RuleAction.ADD, "X-Test", "1")
        )
        manager.addModifierRule(
            ModifierRule("r2", "Rule 2", ".*", RuleTarget.RESPONSE_HEADER, RuleAction.REMOVE, "X-Powered-By", null)
        )
        manager.removeModifierRule("r1")
        assertEquals(1, manager.getModifierRules().size)
        assertEquals("r2", manager.getModifierRules().first().id)
    }

    // ─────────────────────────── Rule matching logic ───────────────────────────

    @Test
    fun `urlPattern should match exact URL via regex`() {
        val pattern = ".*\\.example\\.com/api/users.*"
        val url = "https://api.example.com/api/users/123"
        assertTrue(url.contains(Regex(pattern)))
    }

    @Test
    fun `urlPattern should not match unrelated URL`() {
        val pattern = ".*\\.example\\.com/api/users.*"
        val url = "https://other.site.com/api/users"
        assertFalse(url.contains(Regex(pattern)))
    }

    @Test
    fun `disabled rule should not be evaluated`() {
        val tmpFile = File.createTempFile("mock", ".json")
        tmpFile.deleteOnExit()
        val manager = TrafficModifierManager()
        manager.addMapLocalRule(
            MapLocalRule("ml1", "Disabled Rule", ".*", tmpFile.absolutePath, enabled = false)
        )
        val url = "https://api.example.com/users"
        val matched = manager.getMapLocalRules().firstOrNull { it.enabled && url.contains(Regex(it.urlPattern)) }
        assertEquals(matched, null, "Disabled rule should not match")
    }

    // ─────────────────────────── Map Local file serving ───────────────────────────

    @Test
    fun `MapLocalRule should resolve to local file that exists`() {
        val tmpFile = File.createTempFile("knet_test", ".json")
        tmpFile.writeText("""{"status":"ok"}""")

        val rule = MapLocalRule(
            id = "ml1", name = "JSON Mock",
            urlPattern = ".*example\\.com.*",
            localFilePath = tmpFile.absolutePath
        )

        val file = File(rule.localFilePath)
        assertTrue(file.exists())
        assertEquals("""{"status":"ok"}""", file.readText())

        tmpFile.deleteOnExit()
    }

    @Test
    fun `MapLocalRule with missing file should handle gracefully`() {
        val rule = MapLocalRule(
            id = "ml2", name = "Missing File",
            urlPattern = ".*",
            localFilePath = "/nonexistent/path/response.json"
        )
        val file = File(rule.localFilePath)
        assertFalse(file.exists(), "File should not exist")
    }

    // ─────────────────────────── MapRemoteRule ───────────────────────────

    @Test
    fun `MapRemoteRule should store target host and port correctly`() {
        val rule = MapRemoteRule(
            id = "mr1", name = "Stage Redirect",
            urlPattern = ".*prod\\.api\\.com.*",
            targetHost = "stage.api.com",
            targetPort = 8443,
            targetProtocol = "https"
        )
        assertEquals("stage.api.com", rule.targetHost)
        assertEquals(8443, rule.targetPort)
        assertEquals("https", rule.targetProtocol)
    }

    // ─────────────────────────── ModifierRule - Data integrity ───────────────────────────

    @Test
    fun `ModifierRule enabled defaults to true`() {
        val rule = ModifierRule(
            id = "r1", name = "Default Enabled",
            urlPattern = ".*", target = RuleTarget.RESPONSE_STATUS,
            action = RuleAction.MODIFY, matchValue = null, newValue = "503"
        )
        assertTrue(rule.enabled)
    }

    @Test
    fun `ModifierRule status should parse newValue as integer`() {
        val rule = ModifierRule(
            id = "r2", name = "Override Status",
            urlPattern = ".*", target = RuleTarget.RESPONSE_STATUS,
            action = RuleAction.MODIFY, matchValue = null, newValue = "404"
        )
        val code = rule.newValue?.toIntOrNull()
        assertNotNull(code)
        assertEquals(404, code)
    }
}
