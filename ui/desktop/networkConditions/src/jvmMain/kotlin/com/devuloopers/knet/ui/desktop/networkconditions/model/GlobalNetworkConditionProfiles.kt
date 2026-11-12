package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.domain.networkconditions.NetworkConditionBuiltIns
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile

/** Uses the profile catalog's display name for unlimited global shaping and domain-rule exceptions alike. */
internal val noThrottlingProfileName: String
    get() = NetworkConditionBuiltIns.NO_THROTTLING.name

/**
 * Presents both persisted no-global-limit representations as one choice without rewriting their stored identity.
 * Only the built-in pass-through profile is normalized; custom profiles keep their own identity and parameters.
 */
internal val NetworkConditionConfiguration.selectedGlobalProfile: NetworkConditionProfile?
    get() = profile(globalProfileId)?.takeUnless { it.id == NetworkConditionBuiltIns.NO_THROTTLING.id }

/** Global-only choices; rule editors retain the complete catalog, including the No throttling exception. */
internal val NetworkConditionConfiguration.globalProfileOptions: List<NetworkConditionProfile?>
    get() = listOf(null) + profiles.filterNot { it.id == NetworkConditionBuiltIns.NO_THROTTLING.id }
