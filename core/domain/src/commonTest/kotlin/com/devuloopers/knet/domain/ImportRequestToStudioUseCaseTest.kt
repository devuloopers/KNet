package com.devuloopers.knet.domain

import com.devuloopers.knet.domain.apistudio.usecase.ImportRequestToStudioUseCase
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportRequestToStudioUseCaseTest {

    private val useCase = ImportRequestToStudioUseCase()

    @Test
    fun execute_normalizesMissingUrlSchemeToHttps() {
        val spec = NetworkRequestSpec(
            method = HttpMethod.GET,
            url = "api.example.com/v1/users"
        )

        val result = useCase.execute(spec)

        assertEquals("https://api.example.com/v1/users", result.spec.url)
        assertEquals("/v1/users", result.displayTitle)
    }

    @Test
    fun execute_trimsHeaderAndQueryParamWhitespaces() {
        val spec = NetworkRequestSpec(
            method = HttpMethod.POST,
            url = "https://api.example.com/orders",
            headers = listOf(" Authorization " to " Bearer token123 "),
            queryParams = listOf(" limit " to " 50 ")
        )

        val result = useCase.execute(spec)

        assertEquals(listOf("Authorization" to "Bearer token123"), result.spec.headers)
        assertEquals(listOf("limit" to "50"), result.spec.queryParams)
    }

    @Test
    fun execute_usesCustomTitleIfProvided() {
        val spec = NetworkRequestSpec(
            method = HttpMethod.PUT,
            url = "https://api.example.com/settings"
        )

        val result = useCase.execute(spec, title = "Update Settings Request")

        assertEquals("Update Settings Request", result.displayTitle)
    }
}
