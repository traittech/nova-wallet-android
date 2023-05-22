package io.novafoundation.nova.feature_settings_impl.presentation.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.novafoundation.nova.common.base.BaseViewModel
import io.novafoundation.nova.common.data.network.AppLinksProvider
import io.novafoundation.nova.common.mixin.actionAwaitable.ActionAwaitableMixin
import io.novafoundation.nova.common.mixin.actionAwaitable.ConfirmationDialogInfo
import io.novafoundation.nova.common.mixin.actionAwaitable.confirmingAction
import io.novafoundation.nova.common.mixin.api.Browserable
import io.novafoundation.nova.common.resources.AppVersionProvider
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.common.sequrity.BiometricResponse
import io.novafoundation.nova.common.sequrity.BiometricService
import io.novafoundation.nova.common.sequrity.SafeModeService
import io.novafoundation.nova.common.sequrity.TwoFactorVerificationService
import io.novafoundation.nova.common.utils.Event
import io.novafoundation.nova.common.utils.event
import io.novafoundation.nova.common.utils.flowOf
import io.novafoundation.nova.common.utils.formatting.format
import io.novafoundation.nova.common.utils.inBackground
import io.novafoundation.nova.feature_account_api.domain.interfaces.SelectedAccountUseCase
import io.novafoundation.nova.feature_account_api.presenatation.language.LanguageUseCase
import io.novafoundation.nova.common.sequrity.biometry.mapBiometricErrors
import io.novafoundation.nova.feature_currency_api.domain.CurrencyInteractor
import io.novafoundation.nova.feature_currency_api.presentation.mapper.mapCurrencyToUI
import io.novafoundation.nova.feature_settings_impl.R
import io.novafoundation.nova.feature_settings_impl.SettingsRouter
import io.novafoundation.nova.feature_settings_impl.presentation.settings.model.WalletConnectSessionsModel
import io.novafoundation.nova.feature_wallet_connect_api.domain.sessions.WalletConnectSessionsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val languageUseCase: LanguageUseCase,
    private val router: SettingsRouter,
    private val appLinksProvider: AppLinksProvider,
    private val resourceManager: ResourceManager,
    private val appVersionProvider: AppVersionProvider,
    private val selectedAccountUseCase: SelectedAccountUseCase,
    private val currencyInteractor: CurrencyInteractor,
    private val safeModeService: SafeModeService,
    private val actionAwaitableMixinFactory: ActionAwaitableMixin.Factory,
    private val walletConnectSessionsUseCase: WalletConnectSessionsUseCase,
    private val twoFactorVerificationService: TwoFactorVerificationService,
    private val biometricService: BiometricService
) : BaseViewModel(), Browserable {

    val confirmationAwaitableAction = actionAwaitableMixinFactory.confirmingAction<ConfirmationDialogInfo>()

    val selectedWalletModel = selectedAccountUseCase.selectedWalletModelFlow()
        .shareInBackground()

    val selectedCurrencyFlow = currencyInteractor.observeSelectCurrency()
        .map { mapCurrencyToUI(it) }
        .inBackground()
        .share()

    val selectedLanguageFlow = flowOf {
        languageUseCase.selectedLanguageModel()
    }
        .shareInBackground()

    val appVersionFlow = flowOf {
        resourceManager.getString(R.string.about_version_template, appVersionProvider.versionName)
    }
        .inBackground()
        .share()

    val pinCodeVerificationStatus = twoFactorVerificationService.isEnabledFlow()

    private val walletConnectSessions = walletConnectSessionsUseCase.activeSessionsNumberFlow()
        .shareInBackground()

    val walletConnectSessionsUi = walletConnectSessions
        .map(::mapNumberOfActiveSessionsToUi)
        .shareInBackground()

    val safeModeStatus = safeModeService.safeModeStatusFlow()

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val _openEmailEvent = MutableLiveData<Event<String>>()
    val openEmailEvent: LiveData<Event<String>> = _openEmailEvent

    val biometricAuthStatus = biometricService.isEnabledFlow()

    val biometricEventMessages = biometricService.biometryServiceResponseFlow
        .mapNotNull { mapBiometricErrors(resourceManager, it) }
        .shareInBackground()
        .asLiveData()

    val showBiometricNotReadyDialogEvent = biometricService.biometryServiceResponseFlow
        .filterIsInstance<BiometricResponse.NotReady>()
        .map { Event(true) }
        .asLiveData()

    init {
        syncWalletConnectSessions()
        setupBiometric()
    }

    fun walletsClicked() {
        router.openWallets()
    }

    fun currenciesClicked() {
        router.openCurrencies()
    }

    fun languagesClicked() {
        router.openLanguages()
    }

    fun changeBiometricAuth() {
        launch {
            if (!biometricService.isEnabled()) {
                biometricService.requestBiometric()
            } else {
                biometricService.toggle()
            }
        }
    }

    fun changePincodeVerification() {
        launch {
            if (!twoFactorVerificationService.isEnabled()) {
                confirmationAwaitableAction.awaitAction(
                    ConfirmationDialogInfo(
                        R.string.settings_pin_code_verification_confirmation_title,
                        R.string.settings_pin_code_verification_confirmation_message
                    )
                )
            }

            twoFactorVerificationService.toggle()
        }
    }

    fun changeSafeMode() {
        launch {
            if (!safeModeService.isSafeModeEnabled()) {
                confirmationAwaitableAction.awaitAction(
                    ConfirmationDialogInfo(
                        R.string.settings_safe_mode_confirmation_title,
                        R.string.settings_safe_mode_confirmation_message
                    )
                )
            }

            safeModeService.toggleSafeMode()
        }
    }

    fun changePinCodeClicked() {
        router.openChangePinCode()
    }

    fun telegramClicked() {
        openLink(appLinksProvider.telegram)
    }

    fun twitterClicked() {
        openLink(appLinksProvider.twitter)
    }

    fun rateUsClicked() {
        openLink(appLinksProvider.rateApp)
    }

    fun websiteClicked() {
        openLink(appLinksProvider.website)
    }

    fun githubClicked() {
        openLink(appLinksProvider.github)
    }

    fun termsClicked() {
        openLink(appLinksProvider.termsUrl)
    }

    fun privacyClicked() {
        openLink(appLinksProvider.privacyUrl)
    }

    fun emailClicked() {
        _openEmailEvent.value = appLinksProvider.email.event()
    }

    fun openYoutube() {
        openLink(appLinksProvider.youtube)
    }

    fun accountActionsClicked() = launch {
        val selectedWalletId = selectedAccountUseCase.getSelectedMetaAccount().id

        router.openAccountDetails(selectedWalletId)
    }

    fun selectedWalletClicked() {
        router.openSwitchWallet()
    }

    fun walletConnectClicked() = launch {
        if (walletConnectSessions.first() > 0) {
            router.openWalletConnectSessions()
        } else {
            router.openWalletConnectScan()
        }
    }

    private fun openLink(link: String) {
        openBrowserEvent.value = link.event()
    }

    private fun mapNumberOfActiveSessionsToUi(activeSessions: Int): WalletConnectSessionsModel {
        return if (activeSessions > 0) {
            WalletConnectSessionsModel(activeSessions.format())
        } else {
            WalletConnectSessionsModel(null)
        }
    }

    private fun syncWalletConnectSessions() = launch {
        walletConnectSessionsUseCase.syncActiveSessions()
    }

    private fun setupBiometric() {
        biometricService.biometryServiceResponseFlow
            .filterIsInstance<BiometricResponse.Success>()
            .onEach { biometricService.toggle() }
            .launchIn(this)
    }

    fun onResume() {
        biometricService.refreshBiometryState()
    }

    fun onPause() {
        biometricService.cancel()
    }
}