package io.novafoundation.nova.feature_staking_impl.data.parachainStaking.network.bindings

import io.novafoundation.nova.common.data.network.runtime.binding.bindAccountId
import io.novafoundation.nova.common.data.network.runtime.binding.bindList
import io.novafoundation.nova.common.data.network.runtime.binding.bindNumber
import io.novafoundation.nova.common.data.network.runtime.binding.castToStruct
import io.novafoundation.nova.common.data.network.runtime.binding.getTyped
import io.novafoundation.nova.common.data.network.runtime.binding.incompatible
import io.novafoundation.nova.feature_staking_api.domain.model.parachain.DelegatorBond
import io.novafoundation.nova.feature_staking_api.domain.model.parachain.DelegatorState
import io.novafoundation.nova.feature_staking_api.domain.model.parachain.DelegatorStatus
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct

fun bindDelegatorState(
    dynamicInstance: Any?,
    accountId: AccountId,
    chain: Chain
): DelegatorState {
    return when (dynamicInstance) {
        null -> DelegatorState.None(chain)
        is Struct.Instance -> bindDelegator(dynamicInstance, accountId, chain)
        else -> incompatible()
    }
}

private fun bindDelegator(
    struct: Struct.Instance,
    accountId: AccountId,
    chain: Chain
): DelegatorState.Delegator {
    val requests: Any? = struct["requests"]

    return DelegatorState.Delegator(
        accountId = accountId,
        chain = chain,
        delegations = bindList(struct["delegations"], ::bindBond),
        total = bindNumber(struct["total"]),
        status = bindDelegatorStatus(struct.getTyped("status"))
    )
}

fun bindBond(
    instance: Any?,
): DelegatorBond {
    val struct = instance.castToStruct()

    return DelegatorBond(
        owner = bindAccountId(struct["owner"]),
        balance = bindNumber(struct["amount"])
    )
}

private fun bindDelegatorStatus(
    instance: DictEnum.Entry<Any?>
): DelegatorStatus {
    return when (instance.name) {
        "Active" -> DelegatorStatus.Active
        "Leaving" -> DelegatorStatus.Leaving(
            roundIndex = bindRoundIndex(instance.value)
        )
        else -> incompatible()
    }
}