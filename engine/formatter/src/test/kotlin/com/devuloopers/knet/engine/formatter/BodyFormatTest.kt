package com.devuloopers.knet.engine.formatter

import com.devuloopers.knet.engine.formatter.model.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class BodyFormatTest {

    @Test
    fun testBadgeLabels() {
        assertEquals("JSON", BodyFormat.Json("{}").badgeLabel)
        assertEquals("JSON Stream", BodyFormat.JsonStream(listOf("{}")).badgeLabel)
        assertEquals("Form Data", BodyFormat.FormData(listOf("key" to "val")).badgeLabel)
        assertEquals("SSE Stream", BodyFormat.SseStream(listOf("data: hi")).badgeLabel)
        assertEquals("Protobuf", BodyFormat.Protobuf("field_1: 1").badgeLabel)
        assertEquals("PNG Image", BodyFormat.Image("PNG Image").badgeLabel)
        assertEquals("HTML", BodyFormat.Html("<h1></h1>").badgeLabel)
        assertEquals("XML", BodyFormat.Xml("<root/>").badgeLabel)
        assertEquals("CBOR", BodyFormat.Cbor("{}").badgeLabel)
        assertEquals("JS", BodyFormat.Js("var x = 1;").badgeLabel)
        assertEquals("CSS", BodyFormat.Css("body {}").badgeLabel)
        assertEquals("gRPC-Web", BodyFormat.GrpcWeb(emptyList()).badgeLabel)
        assertEquals("GQL: GetUser", BodyFormat.GraphQL("Query", "GetUser", "query {}", "").badgeLabel)
        assertEquals("GQL: Query", BodyFormat.GraphQL("Query", null, "query {}", "").badgeLabel)
        assertEquals("PLAIN", BodyFormat.RawText("plain text").badgeLabel)
    }
}
