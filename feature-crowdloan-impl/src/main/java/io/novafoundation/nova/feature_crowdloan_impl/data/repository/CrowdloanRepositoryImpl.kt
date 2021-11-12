package io.novafoundation.nova.feature_crowdloan_impl.data.repository

import io.novafoundation.nova.common.utils.Modules
import io.novafoundation.nova.common.utils.crowdloan
import io.novafoundation.nova.common.utils.hasModule
import io.novafoundation.nova.common.utils.numberConstant
import io.novafoundation.nova.common.utils.slots
import io.novafoundation.nova.common.utils.storageKeys
import io.novafoundation.nova.common.utils.u32ArgumentFromStorageKey
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.LeaseEntry
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.bindContribution
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.bindFundInfo
import io.novafoundation.nova.feature_crowdloan_api.data.network.blockhain.binding.bindLeases
import io.novafoundation.nova.feature_crowdloan_api.data.repository.CrowdloanRepository
import io.novafoundation.nova.feature_crowdloan_api.data.repository.ParachainMetadata
import io.novafoundation.nova.feature_crowdloan_impl.data.network.api.parachain.ParachainMetadataApi
import io.novafoundation.nova.feature_crowdloan_impl.data.network.api.parachain.mapParachainMetadataRemoteToParachainMetadata
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ChainId
import io.novafoundation.nova.runtime.multiNetwork.getRuntime
import io.novafoundation.nova.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigInteger

private const val CONTRIBUTIONS_CHILD_SUFFIX = "crowdloan"

class CrowdloanRepositoryImpl(
    private val remoteStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry,
    private val parachainMetadataApi: ParachainMetadataApi
) : CrowdloanRepository {

    override suspend fun isCrowdloansAvailable(chainId: ChainId): Boolean {
        return runtimeFor(chainId).metadata.hasModule(Modules.CROWDLOAN)
    }

    override suspend fun allFundInfos(chainId: ChainId): Map<ParaId, FundInfo> {
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            chainId = chainId
        ) { scale, runtime, paraId -> bindFundInfo(scale!!, runtime, paraId) }
    }

    override suspend fun getWinnerInfo(chainId: ChainId, funds: Map<ParaId, FundInfo>): Map<ParaId, Boolean> {
        return remoteStorage.queryKeys(
            keysBuilder = { it.metadata.slots().storage("Leases").storageKeys(it, funds.keys) },
            binding = { scale, runtimeSnapshot -> scale?.let { bindLeases(it, runtimeSnapshot) } },
            chainId = chainId
        ).mapValues { (paraId, leases) ->
            val fund = funds.getValue(paraId)

            leases?.let { isWinner(leases, fund.bidderAccountId) } ?: false
        }
    }

    private fun isWinner(leases: List<LeaseEntry?>, bidderAccount: AccountId): Boolean {
        return leases.any { it?.accountId.contentEquals(bidderAccount) }
    }

    override suspend fun getParachainMetadata(chain: Chain): Map<ParaId, ParachainMetadata> {
        return withContext(Dispatchers.Default) {
            chain.externalApi?.crowdloans?.let { section ->
                parachainMetadataApi.getParachainMetadata(section.url)
                    .associateBy { it.paraid }
                    .mapValues { (_, remoteMetadata) -> mapParachainMetadataRemoteToParachainMetadata(remoteMetadata) }
            } ?: emptyMap()
        }
    }

    override suspend fun blocksPerLeasePeriod(chainId: ChainId): BigInteger {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.slots().numberConstant("LeasePeriod", runtime)
    }

    override fun fundInfoFlow(chainId: ChainId, parachainId: ParaId): Flow<FundInfo> {
        return remoteStorage.observe(
            keyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey(it, parachainId) },
            binder = { scale, runtime -> bindFundInfo(scale!!, runtime, parachainId) },
            chainId = chainId
        )
    }

    override suspend fun getFundInfo(chainId: ChainId, parachainId: ParaId): FundInfo {
        return remoteStorage.query(
            keyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey(it, parachainId) },
            binding = { scale, runtime -> bindFundInfo(scale!!, runtime, parachainId) },
            chainId = chainId
        )
    }

    override suspend fun minContribution(chainId: ChainId): BigInteger {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.crowdloan().numberConstant("MinContribution", runtime)
    }

    override suspend fun getContribution(
        chainId: ChainId,
        accountId: AccountId,
        paraId: ParaId,
        trieIndex: BigInteger,
    ): Contribution? {
        return remoteStorage.queryChildState(
            storageKeyBuilder = { accountId.toHexString(withPrefix = true) },
            childKeyBuilder = {
                val suffix = (CONTRIBUTIONS_CHILD_SUFFIX.encodeToByteArray() + u32.toByteArray(it, trieIndex))
                    .blake2b256()

                write(suffix)
            },
            binder = { scale, runtime -> scale?.let { bindContribution(it, runtime) } },
            chainId = chainId
        )
    }

    private suspend fun runtimeFor(chainId: String) = chainRegistry.getRuntime(chainId)
}