package com.devuloopers.knet.interceptor

/**
 * Defines a pattern and criteria to intercept HTTP requests and responses.
 *
 * @property id Unique identifier of the rule.
 * @property urlRegex A regular expression to match against the full URL. If null, matches any URL.
 * @property method An HTTP method matching criteria (e.g., GET, POST). If null, matches any method.
 * @property isRequestEnabled True if matching requests should be intercepted and paused.
 * @property isResponseEnabled True if matching responses should be intercepted and paused.
 */
class BreakpointRule(
    val id: String,
    val urlRegex: String?,
    val method: String?,
    val isRequestEnabled: Boolean,
    val isResponseEnabled: Boolean
) {
    private val regexPattern = urlRegex?.toRegex()

    /**
     * Checks if a request URL and method match this breakpoint rule.
     *
     * @param url The full request URL.
     * @param method The HTTP request method.
     * @return True if the request details match the rule.
     */
    fun matches(url: String, method: String): Boolean {
        if (this.method != null && !this.method.equals(method, ignoreCase = true)) {
            return false
        }
        if (regexPattern != null && !regexPattern.containsMatchIn(url)) {
            return false
        }
        return true
    }
}
