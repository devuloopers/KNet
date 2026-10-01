package com.devuloopers.knet.domain.protectedtraffic

/** Versioned protected-service compatibility group definitions shipped with KNet. */
object ProtectedTrafficBuiltIns {
    /** Google Play Billing traffic attributed to the Play Store package. */
    val GooglePlayBillingGroupId: ProtectedServiceGroupId = ProtectedServiceGroupId("google-play-billing")

    /** Optional broader Google system-services attribution group. */
    val GoogleSystemServicesGroupId: ProtectedServiceGroupId = ProtectedServiceGroupId("google-system-services")

    /** Apple App Store and StoreKit traffic with verified source identity. */
    val AppleAppStoreGroupId: ProtectedServiceGroupId = ProtectedServiceGroupId("apple-app-store-storekit")

    /** Current immutable built-in rules; group toggles determine whether each rule participates. */
    val rules: List<ProtectedTrafficRule> = listOf(
        builtInApplicationRule(
            ruleId = "builtin-google-play-store-v1",
            applicationId = "com.android.vending",
            groupId = GooglePlayBillingGroupId,
        ),
        builtInApplicationRule(
            ruleId = "builtin-google-play-services-v1",
            applicationId = "com.google.android.gms",
            groupId = GoogleSystemServicesGroupId,
        ),
        builtInApplicationRule(
            ruleId = "builtin-google-services-framework-v1",
            applicationId = "com.google.android.gsf",
            groupId = GoogleSystemServicesGroupId,
        ),
        builtInApplicationRule(
            ruleId = "builtin-apple-storekit-agent-v1",
            applicationId = "com.apple.storekitagent",
            groupId = AppleAppStoreGroupId,
        ),
        builtInApplicationRule(
            ruleId = "builtin-apple-app-store-v1",
            applicationId = "com.apple.AppStore",
            groupId = AppleAppStoreGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-google-play-api-v1",
            destination = "play.googleapis.com",
            groupId = GooglePlayBillingGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-google-play-frontend-v1",
            destination = "play-fe.googleapis.com",
            groupId = GooglePlayBillingGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-google-android-clients-v1",
            destination = "android.clients.google.com",
            groupId = GooglePlayBillingGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-apple-buy-itunes-v1",
            destination = "buy.itunes.apple.com",
            groupId = AppleAppStoreGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-apple-sandbox-itunes-v1",
            destination = "sandbox.itunes.apple.com",
            groupId = AppleAppStoreGroupId,
        ),
        builtInDestinationRule(
            ruleId = "builtin-apple-storekit-itunes-v1",
            destination = "storekit.itunes.apple.com",
            groupId = AppleAppStoreGroupId,
        ),
    )

    private fun builtInApplicationRule(
        ruleId: String,
        applicationId: String,
        groupId: ProtectedServiceGroupId,
    ): ProtectedTrafficRule = ProtectedTrafficRule(
        id = ProtectedTrafficRuleId(ruleId),
        action = ProtectedTrafficAction.TUNNEL,
        sourceApplication = ProtectedSourceApplicationId(applicationId),
        origin = ProtectedTrafficRuleOrigin.BUILT_IN,
        groupId = groupId,
        groupVersion = 1,
    )

    private fun builtInDestinationRule(
        ruleId: String,
        destination: String,
        groupId: ProtectedServiceGroupId,
    ): ProtectedTrafficRule = ProtectedTrafficRule(
        id = ProtectedTrafficRuleId(ruleId),
        action = ProtectedTrafficAction.TUNNEL,
        destination = ProtectedDestinationSelector.parse(destination),
        port = 443,
        transport = ProtectedTransportProtocol.TCP,
        origin = ProtectedTrafficRuleOrigin.BUILT_IN,
        groupId = groupId,
        groupVersion = 1,
    )
}
