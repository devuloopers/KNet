package com.devuloopers.knet.ui.desktop.httppanel.editor

import com.devuloopers.knet.ui.desktop.httppanel.mapper.GraphQlPayloadMapper
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQlSubTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQlPayloadMapperTest {

    private val mapper = GraphQlPayloadMapper()

    @Test
    fun parsePayload_givenComplexNestedJsonPayload_parsesAndAutoFormatsQueryVariablesOperationNameAndExtensions() {
        val samplePayload = """
            {
              "query": "query SectionsData($${"id"}s: [ID!]!, $${"partner"}: String!) { sections(ids: $${"id"}s, partner: $${"partner"}) { id title } }",
              "operationName": "SectionsData",
              "variables": {
                "ids": [
                  "108333068"
                ],
                "partner": "and01",
                "pageSize": 10,
                "includeNestedAssets": true
              },
              "extensions": {
                "clientLibrary": {
                  "name": "apollo-kotlin",
                  "version": "5.0.0"
                }
              }
            }
        """.trimIndent()

        val state = mapper.parsePayload(samplePayload)

        assertTrue(state.queryText.contains("query SectionsData"))
        assertTrue(state.queryText.contains("sections(ids: \$ids, partner: \$partner)"))
        assertTrue(state.queryText.contains("    id"))
        assertEquals("SectionsData", state.operationName)
        assertTrue(state.variablesText.contains("\"108333068\""))
        assertTrue(state.variablesText.contains("\"and01\""))
        assertTrue(state.extensionsText.contains("apollo-kotlin"))
        assertEquals(GraphQlSubTab.QUERY, state.activeSubTab)
    }

    @Test
    fun serializePayload_givenGraphQlState_serializesToValidJson() {
        val state = mapper.parsePayload(
            """
            {
              "query": "query GetUser { user { id } }",
              "operationName": "GetUser",
              "variables": { "id": "1" }
            }
            """.trimIndent()
        )

        val json = mapper.serializePayload(state)

        assertTrue(json.contains("\"query\""))
        assertTrue(json.contains("GetUser"))
        assertTrue(json.contains("\"id\": \"1\""))
    }
}
