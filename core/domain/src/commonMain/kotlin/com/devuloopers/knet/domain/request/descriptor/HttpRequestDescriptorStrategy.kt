package com.devuloopers.knet.domain.request.descriptor

/**
 * Terminal HTTP descriptor deriving a path/host title and retaining the actual method as its badge.
 *
 * Query parameters and fragments are excluded because they are mutable request data rather than document identity.
 */
class HttpRequestDescriptorStrategy : RequestDescriptorStrategy {
    override val priority: Int = Int.MIN_VALUE

    override fun describe(request: RequestDescriptorInput): RequestDescriptorContribution =
        RequestDescriptorContribution(
            kind = RequestKindId.HTTP,
            badgeLabel = request.transportMethod.token,
            suggestedName = request.absoluteUrl.meaningfulTargetName()
        )

    private fun String.meaningfulTargetName(): String? {
        val target = trim()
        if (target.isEmpty()) return null

        val withoutFragmentOrQuery = target.substringBefore('#').substringBefore('?')
        if (withoutFragmentOrQuery.startsWith('/')) {
            return withoutFragmentOrQuery.normalizedPath()
        }

        val schemeSeparator = withoutFragmentOrQuery.indexOf("://")
        val authorityAndPath = if (schemeSeparator >= 0) {
            withoutFragmentOrQuery.substring(schemeSeparator + 3)
        } else {
            withoutFragmentOrQuery
        }
        val authority = authorityAndPath.substringBefore('/').trim()
        val path = authorityAndPath.substringAfter('/', missingDelimiterValue = "")
            .normalizedPath()

        return path ?: authority.hostWithoutCredentialsOrPort()
    }

    private fun String.normalizedPath(): String? {
        val path = trim().trimEnd('/')
        if (path.isEmpty()) return null
        return "/${path.trimStart('/')}"
    }

    private fun String.hostWithoutCredentialsOrPort(): String? {
        val hostAndPort = substringAfterLast('@').trim()
        if (hostAndPort.isEmpty()) return null
        if (hostAndPort.startsWith('[')) {
            return hostAndPort.substringAfter('[').substringBefore(']').takeIf { it.isNotBlank() }
        }
        return hostAndPort.substringBefore(':').takeIf { it.isNotBlank() }
    }
}
