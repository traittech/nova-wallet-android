package io.novafoundation.nova.feature_staking_impl.domain.nominationPools.common

import io.novafoundation.nova.common.address.AccountIdKey
import io.novafoundation.nova.common.data.memory.ComputationalCache
import io.novafoundation.nova.feature_staking_api.domain.model.Nominations
import io.novafoundation.nova.feature_staking_api.domain.model.StakingLedger
import io.novafoundation.nova.feature_staking_api.domain.model.activeBalance
import io.novafoundation.nova.feature_staking_impl.data.StakingOption
import io.novafoundation.nova.feature_staking_impl.data.chain
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.network.blockhain.models.BondedPool
import io.novafoundation.nova.feature_staking_api.domain.nominationPool.model.PoolId
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.network.blockhain.models.PoolMember
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.network.blockhain.models.UnbondingPools
import io.novafoundation.nova.feature_staking_api.data.nominationPools.pool.PoolAccountDerivation
import io.novafoundation.nova.feature_staking_api.data.nominationPools.pool.bondedAccountOf
import io.novafoundation.nova.feature_staking_api.data.nominationPools.pool.deriveAllBondedPools
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.repository.NominationPoolGlobalsRepository
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.repository.NominationPoolStateRepository
import io.novafoundation.nova.feature_staking_impl.data.nominationPools.repository.NominationPoolUnbondRepository
import io.novafoundation.nova.feature_staking_impl.domain.nominationPools.common.rewards.NominationPoolRewardCalculator
import io.novafoundation.nova.feature_staking_impl.domain.nominationPools.common.rewards.NominationPoolRewardCalculatorFactory
import io.novafoundation.nova.feature_staking_impl.domain.nominationPools.model.BondedPoolState
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.types.Balance
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.coroutines.coroutineContext

class NominationPoolSharedComputation(
    private val computationalCache: ComputationalCache,
    private val nominationPoolMemberUseCase: NominationPoolMemberUseCase,
    private val nominationPoolStateRepository: NominationPoolStateRepository,
    private val nominationPoolUnbondRepository: NominationPoolUnbondRepository,
    private val nominationPoolGlobalsRepository: NominationPoolGlobalsRepository,
    private val poolAccountDerivation: PoolAccountDerivation,
    private val nominationPoolRewardCalculatorFactory: dagger.Lazy<NominationPoolRewardCalculatorFactory>,
) {

    fun currentPoolMemberFlow(chain: Chain, scope: CoroutineScope): Flow<PoolMember?> {
        val key = "POOL_MEMBER:${chain.id}"

        return computationalCache.useSharedFlow(key, scope) {
            nominationPoolMemberUseCase.currentPoolMemberFlow(chain)
        }
    }

    fun participatingBondedPoolFlow(poolId: PoolId, chainId: ChainId, scope: CoroutineScope): Flow<BondedPool> {
        val key = "BONDED_POOL:$chainId:${poolId.value}"

        return computationalCache.useSharedFlow(key, scope) {
            nominationPoolStateRepository.observeParticipatingBondedPool(poolId, chainId)
        }
    }

    fun unbondingPoolsFlow(poolId: PoolId, chainId: ChainId, scope: CoroutineScope): Flow<UnbondingPools?> {
        val key = "UNBONDING_POOLS:$chainId:${poolId.value}"

        return computationalCache.useSharedFlow(key, scope) {
            nominationPoolUnbondRepository.unbondingPoolsFlow(poolId, chainId)
        }
    }

    fun participatingPoolNominationsFlow(
        poolStash: AccountId,
        poolId: PoolId,
        chainId: ChainId,
        scope: CoroutineScope
    ): Flow<Nominations?> {
        val key = "POOL_NOMINATION:$chainId:${poolId.value}"

        return computationalCache.useSharedFlow(key, scope) {
            nominationPoolStateRepository.observeParticipatingPoolNominations(poolStash, chainId)
        }
    }

    fun participatingBondedPoolLedgerFlow(
        poolStash: AccountId,
        poolId: PoolId,
        chainId: ChainId,
        scope: CoroutineScope
    ): Flow<StakingLedger?> {
        val key = "POOL_BONDED_LEDGER:$chainId:${poolId.value}"

        return computationalCache.useSharedFlow(key, scope) {
            nominationPoolStateRepository.observeParticipatingPoolLedger(poolStash, chainId)
        }
    }

    suspend fun participatingBondedPoolLedger(
        poolId: PoolId,
        chainId: ChainId,
        scope: CoroutineScope
    ): StakingLedger? {
        val poolStash = poolAccountDerivation.bondedAccountOf(poolId, chainId)

        return participatingBondedPoolLedgerFlow(poolStash, poolId, chainId, scope).first()
    }

    suspend fun poolRewardCalculator(
        stakingOption: StakingOption,
        scope: CoroutineScope
    ): NominationPoolRewardCalculator {
        val key = "NOMINATION_POOLS_REWARD_CALCULATOR:${stakingOption.chain.id}"

        return computationalCache.useCache(key, scope) {
            nominationPoolRewardCalculatorFactory.get().create(stakingOption, scope)
        }
    }

    suspend fun minJoinBond(
        chainId: ChainId,
        scope: CoroutineScope
    ): Balance {
        val key = "NOMINATION_POOLS_MIN_JOIN_BOND"

        return computationalCache.useCache(key, scope) {
            nominationPoolGlobalsRepository.minJoinBond(chainId)
        }
    }

    suspend fun allBondedPoolAccounts(
        chainId: ChainId,
        scope: CoroutineScope
    ): Map<PoolId, AccountIdKey> {
        val key = "NOMINATION_POOLS_STASH_IDS"

        return computationalCache.useCache(key, scope) {
            val lastPoolId = nominationPoolGlobalsRepository.lastPoolId(chainId)

            poolAccountDerivation.deriveAllBondedPools(lastPoolId, chainId)
        }
    }

    suspend fun allBondedPools(
        chainId: ChainId,
        scope: CoroutineScope
    ): Map<PoolId, BondedPool> {
        val key = "NOMINATION_POOLS_ALL_BONDED_POOLS"

        return computationalCache.useCache(key, scope) {
            val allBondedPoolAccounts = allBondedPoolAccounts(chainId, scope)

            nominationPoolStateRepository.getBondedPools(allBondedPoolAccounts.keys, chainId)
        }
    }
}

fun NominationPoolSharedComputation.participatingBondedPoolStateFlow(
    poolStash: AccountId,
    poolId: PoolId,
    chainId: ChainId,
    scope: CoroutineScope
): Flow<BondedPoolState> = combine(
    participatingBondedPoolFlow(poolId, chainId, scope),
    participatingBondedPoolLedgerFlow(poolStash, poolId, chainId, scope).map { it.activeBalance() },
    ::BondedPoolState
)

suspend fun NominationPoolSharedComputation.getParticipatingBondedPoolState(
    poolStash: AccountId,
    poolId: PoolId,
    chainId: ChainId
): BondedPoolState = participatingBondedPoolStateFlow(poolStash, poolId, chainId, CoroutineScope(coroutineContext)).first()
