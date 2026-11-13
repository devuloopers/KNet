package com.devuloopers.knet.companion.sharedui.screen.certificate

/** Platform-supplied certificate installation copy rendered by the shared certificate screen. */
public data class CertificateInstallationGuidance(
    /** Native Settings instructions, preserved in platform order. */
    public val steps: List<String>,
    /** Native installation workflow whose label and Settings action are shown. */
    public val platform: CertificateInstallationPlatform = CertificateInstallationPlatform.ANDROID,
) {
    init {
        require(steps.size in 1..8 && steps.all(String::isSafeGuidanceText)) {
            "Certificate guidance must contain 1 to 8 safe steps."
        }
    }
}

/** Supported installation guidance variants; native Settings remain owned by each product. */
public enum class CertificateInstallationPlatform {
    /** Android CA-certificate installer. */
    ANDROID,
    /** iOS profile installation followed by explicit full trust. */
    IOS,
}

private fun String.isSafeGuidanceText(): Boolean =
    length in 1..512 && isNotBlank() && this == trim() && none { character ->
        character.code in 0..31 || character.code == 127
    }
