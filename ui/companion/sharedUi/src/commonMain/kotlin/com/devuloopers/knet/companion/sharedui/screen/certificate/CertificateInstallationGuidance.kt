package com.devuloopers.knet.companion.sharedui.screen.certificate

/** Platform-supplied certificate installation copy rendered by the shared certificate screen. */
public data class CertificateInstallationGuidance(
    public val title: String,
    public val steps: List<String>,
) {
    init {
        require(title.isSafeGuidanceText()) { "Certificate guidance title is invalid." }
        require(steps.size in 1..8 && steps.all(String::isSafeGuidanceText)) {
            "Certificate guidance must contain 1 to 8 safe steps."
        }
    }
}

private fun String.isSafeGuidanceText(): Boolean =
    length in 1..512 && isNotBlank() && this == trim() && none { character ->
        character.code in 0..31 || character.code == 127
    }
