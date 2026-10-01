package com.devuloopers.knet.companion.model

/** Packet-layer conditions applied to opaque UDP flows in the companion VPN/TUN data plane. */
public data class PacketConditionConfiguration(
    public val enabled: Boolean = false,
    public val uploadBitsPerSecond: Long? = null,
    public val downloadBitsPerSecond: Long? = null,
    public val latencyMillis: Long = 0L,
    public val jitterMillis: Long = 0L,
    public val lossPercent: Int = 0,
    public val duplicationPercent: Int = 0,
    public val reorderingPercent: Int = 0,
    public val seed: Long = DEFAULT_SEED,
    public val maximumQueuedDatagrams: Int = 1_024,
) {
    init {
        require(uploadBitsPerSecond == null || uploadBitsPerSecond in MINIMUM_RATE..MAXIMUM_RATE)
        require(downloadBitsPerSecond == null || downloadBitsPerSecond in MINIMUM_RATE..MAXIMUM_RATE)
        require(latencyMillis in 0L..MAXIMUM_DELAY_MILLIS)
        require(jitterMillis in 0L..MAXIMUM_DELAY_MILLIS)
        require(lossPercent in 0..100)
        require(duplicationPercent in 0..100)
        require(reorderingPercent in 0..100)
        require(maximumQueuedDatagrams in 1..16_384)
    }

    public companion object {
        public val Disabled: PacketConditionConfiguration = PacketConditionConfiguration()
        public const val DEFAULT_SEED: Long = 0x4b4e4554L
        public const val MINIMUM_RATE: Long = 1_000L
        public const val MAXIMUM_RATE: Long = 100_000_000_000L
        public const val MAXIMUM_DELAY_MILLIS: Long = 120_000L
    }
}

/** Versioned bounded wire codec suitable for authenticated companion control transport. */
public object PacketConditionConfigurationCodec {
    public const val VERSION: Int = 1

    public fun encode(value: PacketConditionConfiguration): ByteArray = buildString {
        append("version=").append(VERSION).append('\n')
        append("enabled=").append(value.enabled).append('\n')
        append("uploadBitsPerSecond=").append(value.uploadBitsPerSecond ?: "").append('\n')
        append("downloadBitsPerSecond=").append(value.downloadBitsPerSecond ?: "").append('\n')
        append("latencyMillis=").append(value.latencyMillis).append('\n')
        append("jitterMillis=").append(value.jitterMillis).append('\n')
        append("lossPercent=").append(value.lossPercent).append('\n')
        append("duplicationPercent=").append(value.duplicationPercent).append('\n')
        append("reorderingPercent=").append(value.reorderingPercent).append('\n')
        append("seed=").append(value.seed).append('\n')
        append("maximumQueuedDatagrams=").append(value.maximumQueuedDatagrams).append('\n')
    }.encodeToByteArray().also { require(it.size <= MAXIMUM_BYTES) }

    public fun decode(bytes: ByteArray): PacketConditionConfiguration {
        require(bytes.size in 1..MAXIMUM_BYTES)
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).lineSequence()
            .filter(String::isNotBlank)
            .toList()
        val values = lines.associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0)
                line.substring(0, separator) to line.substring(separator + 1)
            }
        require(values.size == lines.size) { "Packet condition fields must not be duplicated." }
        require(values["version"]?.toInt() == VERSION)
        require(values.keys == REQUIRED_KEYS)
        return PacketConditionConfiguration(
            enabled = values.getValue("enabled").toBooleanStrict(),
            uploadBitsPerSecond = values.getValue("uploadBitsPerSecond").parseOptionalLong(),
            downloadBitsPerSecond = values.getValue("downloadBitsPerSecond").parseOptionalLong(),
            latencyMillis = values.getValue("latencyMillis").toLong(),
            jitterMillis = values.getValue("jitterMillis").toLong(),
            lossPercent = values.getValue("lossPercent").toInt(),
            duplicationPercent = values.getValue("duplicationPercent").toInt(),
            reorderingPercent = values.getValue("reorderingPercent").toInt(),
            seed = values.getValue("seed").toLong(),
            maximumQueuedDatagrams = values.getValue("maximumQueuedDatagrams").toInt(),
        )
    }

    private const val MAXIMUM_BYTES: Int = 4_096
    private val REQUIRED_KEYS: Set<String> = setOf(
        "version", "enabled", "uploadBitsPerSecond", "downloadBitsPerSecond", "latencyMillis",
        "jitterMillis", "lossPercent", "duplicationPercent", "reorderingPercent", "seed",
        "maximumQueuedDatagrams",
    )
}

private fun String.parseOptionalLong(): Long? = if (isEmpty()) null else toLong()
