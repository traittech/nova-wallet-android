package io.novafoundation.nova.feature_staking_impl.domain.nominationPools.claimRewards.validations

import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.types.Balance
import io.novafoundation.nova.feature_wallet_api.domain.model.Asset
import io.novafoundation.nova.feature_wallet_api.domain.model.amountFromPlanks
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

class NominationPoolsClaimRewardsValidationPayload(
    val fee: BigDecimal,
    val pendingRewardsPlanks: Balance,
    val asset: Asset,
    val chain: Chain
)

val NominationPoolsClaimRewardsValidationPayload.pendingRewards: BigDecimal
    get() = asset.token.amountFromPlanks(pendingRewardsPlanks)
