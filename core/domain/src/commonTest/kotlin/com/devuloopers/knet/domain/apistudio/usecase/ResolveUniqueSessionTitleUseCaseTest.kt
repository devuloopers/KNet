package com.devuloopers.knet.domain.apistudio.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveUniqueSessionTitleUseCaseTest {

    private val useCase = ResolveUniqueSessionTitleUseCase()

    @Test
    fun `execute returns base title when no conflict exists`() {
        val result = useCase.execute("/v1/users", listOf("/v1/orders", "/v1/auth"))
        assertEquals("/v1/users", result)
    }

    @Test
    fun `execute appends suffix 2 on initial collision`() {
        val result = useCase.execute("/v1/users", listOf("/v1/users"))
        assertEquals("/v1/users (2)", result)
    }

    @Test
    fun `execute increments monotonically when multiple duplicates exist`() {
        val existing = listOf("/v1/users", "/v1/users (2)", "/v1/users (5)")
        val result = useCase.execute("/v1/users", existing)
        assertEquals("/v1/users (6)", result)
    }

    @Test
    fun `execute falls back to Untitled Request for blank base title`() {
        val result = useCase.execute("   ", listOf("Untitled Request"))
        assertEquals("Untitled Request (2)", result)
    }
}
