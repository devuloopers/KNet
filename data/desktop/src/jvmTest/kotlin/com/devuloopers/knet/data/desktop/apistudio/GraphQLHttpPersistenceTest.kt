package com.devuloopers.knet.data.desktop.apistudio

import com.devuloopers.knet.data.desktop.apistudio.repository.CollectionsRepositoryImpl
import com.devuloopers.knet.domain.collection.model.*
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.util.UrlQueryStringParser
import com.devuloopers.knet.engine.formatter.graphql.GraphQLHttpRequestFormatter
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

/** Existing Room draft/collection schema retains both sources without a migration. */
class GraphQLHttpPersistenceTest {
    @Test fun `GET and POST survive draft promotion and database restart with original source`() = runTest {
        val root = Files.createTempDirectory("knet-graphql-http-persistence-").toFile()
        val file = root.resolve("knet.db")
        val requests = listOf(
            SavedApiRequest(id = "get", name = "Read", method = HttpMethod.GET,
                url = "https://fixture.test/startup?tracking=1&query=query%20Read%20%7Bx%7D&tracking=2",
                headers = listOf(RequestHeader("X-Fixture", "GET"))),
            SavedApiRequest(id = "post", name = "Read", method = HttpMethod.POST,
                url = "https://fixture.test/startup?tracking=1&tracking=2",
                headers = listOf(RequestHeader("Content-Type", "application/json")),
                body = ApiRequestBody(" \n{\"query\":\"query Read { x }\",\"variables\":{\"id\":7},\"client\":{\"id\":1}}\n ", RequestBodyType.GRAPHQL)),
        ).map { it.copy(queryParameters = UrlQueryStringParser.parseQueryParams(it.url).map { (name, value) -> RequestQueryParameter(name, value) }) }
        try {
            val first = DatabaseFactory.create(file)
            try {
                val repository = CollectionsRepositoryImpl(first.collectionDao())
                for (request in requests) {
                    repository.saveUnsavedRequest(request)
                    assertEquals(request, repository.getRequestById(request.id))
                    repository.saveUnsavedToNewCollectionTx(ApiCollection("collection-${request.id}", "GraphQL"),
                        CollectionFolder("folder-${request.id}", "Requests"), request.copy(id = "saved-${request.id}"), request.id)
                    assertNull(repository.getRequestById(request.id))
                }
            } finally { first.close() }
            val restarted = DatabaseFactory.create(file)
            try {
                val repository = CollectionsRepositoryImpl(restarted.collectionDao())
                for (request in requests) {
                    val restored = assertNotNull(repository.getRequestById("saved-${request.id}"))
                    assertEquals(request.copy(id = "saved-${request.id}"), restored)
                    val facts = GraphQLHttpRequestFormatter.inspect(restored.method.token, restored.url, body = restored.body.content.encodeToByteArray())!!
                    assertEquals("Read", facts.selectedOperation?.name)
                    if (restored.method == HttpMethod.GET) assertEquals("", restored.body.content)
                }
            } finally { restarted.close() }
        } finally { root.deleteRecursively() }
    }
}
