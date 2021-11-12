package io.novafoundation.nova.feature_crowdloan_impl.domain.contribute.validations.custom.moonbeam

import io.novafoundation.nova.common.validation.Validation
import io.novafoundation.nova.common.validation.ValidationSystem
import io.novafoundation.nova.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

typealias MoonbeamTermsValidationSystem = ValidationSystem<MoonbeamTermsPayload, MoonbeamTermsValidationFailure>
typealias MoonbeamTermsValidation = Validation<MoonbeamTermsPayload, MoonbeamTermsValidationFailure>

typealias MoonbeamTermsFeeValidation = EnoughToPayFeesValidation<MoonbeamTermsPayload, MoonbeamTermsValidationFailure>