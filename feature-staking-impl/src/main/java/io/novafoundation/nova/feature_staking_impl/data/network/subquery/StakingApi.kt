package io.novafoundation.nova.feature_staking_impl.data.network.subquery

import io.novafoundation.nova.common.data.network.subquery.EraValidatorInfoQueryResponse
import io.novafoundation.nova.common.data.network.subquery.SubQueryResponse
import io.novafoundation.nova.feature_staking_impl.data.network.subquery.request.DirectStakingPeriodRewardsRequest
import io.novafoundation.nova.feature_staking_impl.data.network.subquery.request.PoolStakingPeriodRewardsRequest
import io.novafoundation.nova.feature_staking_impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import io.novafoundation.nova.feature_staking_impl.data.network.subquery.response.StakingPeriodRewardsResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface StakingApi {

    @POST
    suspend fun getRewardsByPeriod(
        @Url url: String,
        @Body body: DirectStakingPeriodRewardsRequest
    ): SubQueryResponse<StakingPeriodRewardsResponse>

    @POST
    suspend fun getPoolRewardsByPeriod(
        @Url url: String,
        @Body body: PoolStakingPeriodRewardsRequest
    ): SubQueryResponse<StakingPeriodRewardsResponse>

    @POST
    suspend fun getValidatorsInfo(
        @Url url: String,
        @Body body: StakingEraValidatorInfosRequest
    ): SubQueryResponse<EraValidatorInfoQueryResponse>
}
