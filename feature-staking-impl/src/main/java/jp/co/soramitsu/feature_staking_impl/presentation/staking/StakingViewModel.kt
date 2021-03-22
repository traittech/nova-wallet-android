package jp.co.soramitsu.feature_staking_impl.presentation.staking

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.di.StakingViewStateFactory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatWithDefaultPrecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val CURRENT_ICON_SIZE = 40

class StakingViewModel(
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val stakingViewStateFactory: StakingViewStateFactory,
    private val router: StakingRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val currentAssetFlow = interactor.currentAssetFlow()
        .share()

    val currentStakingState = interactor.selectedAccountStakingState()
        .map { transformStakingState(it) }
        .flowOn(Dispatchers.Default)
        .share()

    val networkInfoStateLiveData = currentAssetFlow
        .distinctUntilChanged()
        .withLoading { asset ->
            interactor.observeNetworkInfoState(asset.token.type.networkType)
                .map { transformNetworkInfo(asset, it) }
        }
        .asLiveData()

    val stories = interactor.stakingStoriesFlow()
        .map { it.map(::transformStories) }
        .asLiveData()

    val currentAddressModelLiveData = currentAddressModelFlow().asLiveData()

    fun avatarClicked() {
        router.openChangeAccountFromStaking()
    }

    val networkInfoTitle = currentAssetFlow
        .map { mapAssetToNetworkInfoTitle(it) }
        .asLiveData()

    fun storyClicked(story: StakingStoryModel) {
        // TODO
    }

    private fun transformStakingState(accountStakingState: StakingState) = when (accountStakingState) {
        is StakingState.Stash.Nominator -> stakingViewStateFactory.createNominatorViewState(
            accountStakingState,
            currentAssetFlow,
            viewModelScope,
            ::showError
        )

        is StakingState.Stash.None -> stakingViewStateFactory.createWelcomeViewState(currentAssetFlow, accountStakingState, viewModelScope)

        is StakingState.NonStash -> stakingViewStateFactory.createWelcomeViewState(currentAssetFlow, accountStakingState, viewModelScope)

        is StakingState.Stash.Validator -> stakingViewStateFactory.createValidatorViewState()
    }

    private fun transformStories(story: StakingStory): StakingStoryModel = with(story) {
        StakingStoryModel(title, iconSymbol)
    }

    private fun transformNetworkInfo(asset: Asset, networkInfo: NetworkInfo): StakingNetworkInfoModel {
        val totalStake = asset.token.amountFromPlanks(networkInfo.totalStake)
        val totalStakeFormatted = totalStake.formatWithDefaultPrecision(asset.token.type)

        val totalStakeFiat = asset.token.fiatAmount(totalStake)?.formatAsCurrency()

        val minimumStake = asset.token.amountFromPlanks(networkInfo.minimumStake)
        val minimumStakeFormatted = minimumStake.formatWithDefaultPrecision(asset.token.type)

        val minimumStakeFiat = asset.token.fiatAmount(minimumStake)?.formatAsCurrency()

        val lockupPeriod = resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, networkInfo.lockupPeriodInDays)
            .format(networkInfo.lockupPeriodInDays)

        return with(networkInfo) {
            StakingNetworkInfoModel(
                lockupPeriod,
                minimumStakeFormatted,
                minimumStakeFiat,
                totalStakeFormatted,
                totalStakeFiat,
                nominatorsCount.toString()
            )
        }
    }

    private fun mapAssetToNetworkInfoTitle(asset: Asset): String {
        return resourceManager.getString(R.string.staking_main_network_title, asset.token.type.networkType.readableName)
    }

    private fun currentAddressModelFlow(): Flow<AddressModel> {
        return interactor.selectedAccountFlow()
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }

    private suspend fun generateAddressModel(account: StakingAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }
}