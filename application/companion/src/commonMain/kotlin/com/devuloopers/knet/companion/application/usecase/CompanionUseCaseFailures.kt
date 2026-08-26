package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode

internal fun registrationMissing(): CompanionFailure = CompanionFailure(
    code = CompanionFailureCode.REGISTRATION_NOT_FOUND,
    message = "Pair or select a desktop first.",
    recoverable = true,
)

