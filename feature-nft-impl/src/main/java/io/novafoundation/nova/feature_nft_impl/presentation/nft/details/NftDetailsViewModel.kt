package io.novafoundation.nova.feature_nft_impl.presentation.nft.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.novafoundation.nova.common.address.AddressIconGenerator
import io.novafoundation.nova.common.base.BaseViewModel
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.common.utils.Event
import io.novafoundation.nova.common.utils.capitalize
import io.novafoundation.nova.common.utils.event
import io.novafoundation.nova.common.utils.inBackground
import io.novafoundation.nova.feature_account_api.data.mappers.mapChainToUi
import io.novafoundation.nova.feature_account_api.presenatation.account.AddressDisplayUseCase
import io.novafoundation.nova.feature_account_api.presenatation.account.icon.createAddressModel
import io.novafoundation.nova.feature_account_api.presenatation.actions.ExternalActions
import io.novafoundation.nova.feature_account_api.presenatation.actions.showAddressActions
import io.novafoundation.nova.feature_nft_api.data.model.NftDetails
import io.novafoundation.nova.feature_nft_impl.NftRouter
import io.novafoundation.nova.feature_nft_impl.domain.nft.details.NftDetailsInteractor
import io.novafoundation.nova.feature_nft_impl.domain.nft.details.PricedNftDetails
import io.novafoundation.nova.feature_nft_impl.presentation.NftPayload
import io.novafoundation.nova.feature_nft_impl.presentation.nft.common.formatIssuance
import io.novafoundation.nova.feature_wallet_api.presentation.model.mapAmountToAmountModel
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NftDetailsViewModel(
    private val router: NftRouter,
    private val resourceManager: ResourceManager,
    private val interactor: NftDetailsInteractor,
    private val nftIdentifier: String,
    private val externalActionsDelegate: ExternalActions.Presentation,
    private val addressIconGenerator: AddressIconGenerator,
    private val addressDisplayUseCase: AddressDisplayUseCase
) : BaseViewModel(), ExternalActions by externalActionsDelegate {

    private val _exitingErrorLiveData = MutableLiveData<Event<String>>()
    val exitingErrorLiveData: LiveData<Event<String>> = _exitingErrorLiveData

    private val nftDetailsFlow = interactor.nftDetailsFlow(nftIdentifier)
        .inBackground()
        .catch { showExitingError(it) }
        .share()

    private val nftSupportedForSendFlow = nftDetailsFlow.map {
        val nftType = it.nftDetails.type
        interactor.isNftTypeSupportedForSend(nftType, it.nftDetails.chain)
    }
        .state(initialValue = false)

    val nftDetailsUi = combine(nftDetailsFlow, nftSupportedForSendFlow) { nftDetails, nftSupportedForSend ->
        mapNftDetailsToUi(nftDetails, nftSupportedForSend)
    }
        .inBackground()
        .share()

    fun ownerClicked() = launch {
        val pricedNftDetails = nftDetailsFlow.first()

        with(pricedNftDetails.nftDetails) {
            externalActionsDelegate.showAddressActions(owner, chain)
        }
    }

    fun creatorClicked() = launch {
        val pricedNftDetails = nftDetailsFlow.first()

        with(pricedNftDetails.nftDetails) {
            externalActionsDelegate.showAddressActions(creator!!, chain)
        }
    }

    private fun showExitingError(exception: Throwable) {
        _exitingErrorLiveData.value = exception.message.orEmpty().event()
    }

    private suspend fun mapNftDetailsToUi(
        pricedNftDetails: PricedNftDetails,
        nftSupportedForSend: Boolean
    ): NftDetailsModel {
        val nftDetails = pricedNftDetails.nftDetails

        return NftDetailsModel(
            media = nftDetails.media,
            name = nftDetails.name,
            issuance = resourceManager.formatIssuance(nftDetails.issuance),
            description = nftDetails.description,
            price = pricedNftDetails.price?.let {
                mapAmountToAmountModel(it.amount, it.token)
            },
            collection = nftDetails.collection?.let {
                NftDetailsModel.Collection(
                    name = it.name ?: it.id,
                    media = it.media
                )
            },
            owner = createAddressModel(nftDetails.owner, nftDetails.chain),
            creator = nftDetails.creator?.let {
                createAddressModel(it, nftDetails.chain)
            },
            network = mapChainToUi(nftDetails.chain),
            isSupportedForSend = nftSupportedForSend,
            tags = mapTags(nftDetails.tags),
            attributes = mapAttributes(nftDetails.attributes)
        )
    }

    private fun mapTags(tags: List<String>): List<String> {
        return tags.map { it.uppercase() }
    }

    private fun mapAttributes(attributes: List<NftDetails.Attribute>): List<NftDetailsModel.Attribute> {
        return attributes.map {
            NftDetailsModel.Attribute(
                label = it.label.lowercase().capitalize(),
                value = it.value
            )
        }
    }

    private suspend fun createAddressModel(accountId: AccountId, chain: Chain) = addressIconGenerator.createAddressModel(
        chain = chain,
        accountId = accountId,
        sizeInDp = AddressIconGenerator.SIZE_MEDIUM,
        addressDisplayUseCase = addressDisplayUseCase,
        background = AddressIconGenerator.BACKGROUND_TRANSPARENT
    )

    fun backClicked() {
        router.back()
    }

    fun assetActionSend() {
        viewModelScope.launch {
            val nftDetails = nftDetailsFlow.first().nftDetails

            router.openInputAddressNftFromNftList(
                nftPayload = NftPayload(
                    chainId = nftDetails.chain.id,
                    identifier = nftIdentifier
                )
            )
        }
    }
}
