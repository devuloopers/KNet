package com.devuloopers.knet.core.serialization

import com.devuloopers.knet.core.serialization.serializer.UuidSerializer
import kotlinx.serialization.Serializable

/**
 * Shared test models for `:core:serialization` unit tests.
 *
 * All models are declared at the top level to avoid JVM serialization plugin companion
 * generation issues that occur with private/local `@Serializable` inner classes inside
 * test functions or test classes.
 */

/** Sample model with optional fields and default values. Used in [KNetJsonTest]. */
@Serializable
internal data class SampleModel(
    val name: String,
    val count: Int = 42,
    val tag: String? = null,
)

/** Enum model for coercion tests. Used in [KNetJsonTest]. */
@Serializable
internal enum class Status { ACTIVE, INACTIVE }

/** Wrapper model using [Status]. Used in [KNetJsonTest]. */
@Serializable
internal data class WithStatus(val status: Status = Status.ACTIVE)

/** Simple string-only model. Used in [MigrationRegressionTest]. */
@Serializable
internal data class SimpleNameModel(val name: String)

/** Simple name + count model. Used in [MigrationRegressionTest]. */
@Serializable
internal data class SimpleNameCountModel(val name: String, val count: Int = 0)

/** UUID holder model. Used in [UuidSerializerTest] and [MigrationRegressionTest]. */
@Serializable
internal data class UuidHolder(
    @Serializable(with = UuidSerializer::class)
    val id: String,
)
