package io.novafoundation.nova.app.root.presentation.di

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import io.novafoundation.nova.app.root.domain.RootInteractor
import io.novafoundation.nova.app.root.presentation.RootRouter
import io.novafoundation.nova.common.utils.coroutines.RootScope
import io.novafoundation.nova.app.root.presentation.RootViewModel
import io.novafoundation.nova.common.di.viewmodel.ViewModelKey
import io.novafoundation.nova.common.di.viewmodel.ViewModelModule
import io.novafoundation.nova.common.mixin.api.NetworkStateMixin
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.common.sequrity.SafeModeService
import io.novafoundation.nova.common.utils.sequrity.BackgroundAccessObserver
import io.novafoundation.nova.feature_crowdloan_api.domain.contributions.ContributionsInteractor
import io.novafoundation.nova.feature_currency_api.domain.CurrencyInteractor
import io.novafoundation.nova.feature_versions_api.domain.UpdateNotificationsInteractor
import io.novafoundation.nova.feature_wallet_connect_api.presentation.WalletConnectService
import io.novafoundation.nova.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow

@Module(
    includes = [
        ViewModelModule::class
    ]
)
class RootActivityModule {

    @Provides
    @IntoMap
    @ViewModelKey(RootViewModel::class)
    fun provideViewModel(
        interactor: RootInteractor,
        currencyInteractor: CurrencyInteractor,
        rootRouter: RootRouter,
        resourceManager: ResourceManager,
        networkStateMixin: NetworkStateMixin,
        externalRequirementsFlow: MutableStateFlow<ChainConnection.ExternalRequirement>,
        contributionsInteractor: ContributionsInteractor,
        backgroundAccessObserver: BackgroundAccessObserver,
        safeModeService: SafeModeService,
        updateNotificationsInteractor: UpdateNotificationsInteractor,
        walletConnectService: WalletConnectService,
        rootScope: RootScope
    ): ViewModel {
        return RootViewModel(
            interactor,
            currencyInteractor,
            rootRouter,
            externalRequirementsFlow,
            resourceManager,
            networkStateMixin,
            contributionsInteractor,
            backgroundAccessObserver,
            safeModeService,
            updateNotificationsInteractor,
            walletConnectService,
            rootScope
        )
    }

    @Provides
    fun provideViewModelCreator(
        activity: AppCompatActivity,
        viewModelFactory: ViewModelProvider.Factory
    ): RootViewModel {
        return ViewModelProvider(activity, viewModelFactory).get(RootViewModel::class.java)
    }
}
