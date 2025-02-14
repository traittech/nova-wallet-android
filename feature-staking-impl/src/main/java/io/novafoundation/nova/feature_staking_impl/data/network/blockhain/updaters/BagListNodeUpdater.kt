package io.novafoundation.nova.feature_staking_impl.data.network.blockhain.updaters

import io.novafoundation.nova.common.utils.voterListOrNull
import io.novafoundation.nova.core.storage.StorageCache
import io.novafoundation.nova.core_db.model.AccountStakingLocal
import io.novafoundation.nova.feature_staking_impl.data.StakingSharedState
import io.novafoundation.nova.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import io.novafoundation.nova.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.network.updaters.SingleStorageKeyUpdater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey

class BagListNodeUpdater(
    scope: AccountStakingScope,
    storageCache: StorageCache,
    stakingSharedState: StakingSharedState,
    chainRegistry: ChainRegistry,
) : SingleStorageKeyUpdater<AccountStakingLocal>(scope, stakingSharedState, chainRegistry, storageCache), StakingUpdater<AccountStakingLocal> {

    override suspend fun storageKey(runtime: RuntimeSnapshot, scopeValue: AccountStakingLocal): String? {
        val stakingAccessInfo = scopeValue.stakingAccessInfo ?: return null
        val stashId = stakingAccessInfo.stashId

        return runtime.metadata.voterListOrNull()?.storage("ListNodes")?.storageKey(runtime, stashId)
    }
}
