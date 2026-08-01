package com.devuloopers.knet.domain.rules.model

/**
 * Action execution type when a rule condition matches.
 */
enum class RuleAction(val displayName: String) {
    BREAKPOINT("Breakpoint"),
    REWRITE_HEADER("Rewrite Header"),
    REWRITE_BODY("Rewrite Body"),
    DROP("Drop"),
    MAP_LOCAL("Map Local")
}
