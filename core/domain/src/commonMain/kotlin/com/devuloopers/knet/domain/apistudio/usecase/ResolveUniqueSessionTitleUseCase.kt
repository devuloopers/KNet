package com.devuloopers.knet.domain.apistudio.usecase

/**
 * Domain UseCase to resolve unique display titles for API Studio unsaved sessions,
 * resolving duplicate title conflicts monotonically without decrementing indices.
 *
 * SRP: Encapsulates title uniqueness checks, suffix parsing (`(N)`), and monotonic index generation.
 */
public class ResolveUniqueSessionTitleUseCase {

    /**
     * Resolves a unique display title for a new or imported request session.
     *
     * @param baseTitle Candidate display title derived from URL or request specification.
     * @param existingTitles List of existing active session display titles.
     * @return A guaranteed unique display title string (e.g. `"/v1/users"` or `"/v1/users (2)"`).
     */
    public fun execute(
        baseTitle: String,
        existingTitles: List<String>
    ): String {
        val trimmedBase = baseTitle.trim().ifBlank { "Untitled Request" }
        
        if (!existingTitles.contains(trimmedBase)) {
            return trimmedBase
        }

        val suffixPattern = Regex("""^${Regex.escape(trimmedBase)}\s*\((\d+)\)$""")
        val maxSuffix = existingTitles.mapNotNull { title ->
            suffixPattern.matchEntire(title.trim())?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1

        val nextIndex = maxSuffix + 1
        return "$trimmedBase ($nextIndex)"
    }
}
