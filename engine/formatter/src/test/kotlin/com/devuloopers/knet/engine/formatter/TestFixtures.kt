package com.devuloopers.knet.engine.formatter

object TestFixtures {

    const val SAMPLE_JSON = """{"name":"KNet","version":1.0,"features":["interceptor","simulator","session","protocol","formatter"]}"""

    const val SAMPLE_XML = """<?xml version="1.0" encoding="UTF-8"?><root><item id="1">Value</item></root>"""

    const val SAMPLE_GRAPHQL = """{"operationName":"GetUser","query":"query GetUser(${'$'}id: ID!) { user(id: ${'$'}id) { name } }","variables":{"id":"123"}}"""

    const val SAMPLE_FORM_DATA = "name=KNet&version=1.0&status=active"

    const val SAMPLE_HTML = "<!DOCTYPE html><html><head><title>Test</title></head><body><h1>Hello KNet</h1></body></html>"

    const val SAMPLE_CSS = "body { margin: 0; padding: 0; background-color: #ffffff; }"

    const val SAMPLE_JS = "function greet(name) { console.log('Hello ' + name); return true; }"
}
