package io.novafoundation.nova.feature_assets.di

import dagger.Module
import dagger.Provides
import io.novafoundation.nova.common.data.memory.ComputationalCache
import io.novafoundation.nova.common.data.storage.Preferences
import io.novafoundation.nova.common.di.scope.FeatureScope
import io.novafoundation.nova.common.mixin.actionAwaitable.ActionAwaitableMixin
import io.novafoundation.nova.common.resources.ResourceManager
import io.novafoundation.nova.core_db.dao.OperationDao
import io.novafoundation.nova.feature_account_api.domain.interfaces.AccountRepository
import io.novafoundation.nova.feature_account_api.domain.interfaces.SelectedAccountUseCase
import io.novafoundation.nova.feature_account_api.domain.updaters.AccountUpdateScope
import io.novafoundation.nova.feature_account_api.presenatation.account.watchOnly.WatchOnlyMissingKeysPresenter
import io.novafoundation.nova.feature_assets.BuildConfig
import io.novafoundation.nova.feature_assets.data.buyToken.BuyTokenRegistry
import io.novafoundation.nova.feature_assets.data.buyToken.providers.BanxaProvider
import io.novafoundation.nova.feature_assets.data.buyToken.providers.MercuryoProvider
import io.novafoundation.nova.feature_assets.data.buyToken.providers.TransakProvider
import io.novafoundation.nova.feature_assets.data.network.BalancesUpdateSystem
import io.novafoundation.nova.feature_assets.data.repository.RealTransactionHistoryRepository
import io.novafoundation.nova.feature_assets.data.repository.TransactionHistoryRepository
import io.novafoundation.nova.feature_assets.data.repository.assetFilters.AssetFiltersRepository
import io.novafoundation.nova.feature_assets.data.repository.assetFilters.PreferencesAssetFiltersRepository
import io.novafoundation.nova.feature_assets.di.modules.AddTokenModule
import io.novafoundation.nova.feature_assets.di.modules.ManageTokensCommonModule
import io.novafoundation.nova.feature_assets.di.modules.SendModule
import io.novafoundation.nova.feature_assets.domain.WalletInteractor
import io.novafoundation.nova.feature_assets.domain.WalletInteractorImpl
import io.novafoundation.nova.feature_assets.domain.assets.ExternalBalancesInteractor
import io.novafoundation.nova.feature_assets.domain.assets.RealExternalBalancesInteractor
import io.novafoundation.nova.feature_assets.domain.assets.search.AssetSearchInteractor
import io.novafoundation.nova.feature_assets.domain.tokens.add.CoinGeckoLinkParser
import io.novafoundation.nova.feature_assets.presentation.balance.assetActions.buy.BuyMixinFactory
import io.novafoundation.nova.feature_assets.presentation.balance.common.ControllableAssetCheckMixin
import io.novafoundation.nova.feature_assets.presentation.transaction.filter.HistoryFiltersProviderFactory
import io.novafoundation.nova.feature_currency_api.domain.interfaces.CurrencyRepository
import io.novafoundation.nova.feature_nft_api.data.repository.NftRepository
import io.novafoundation.nova.feature_staking_api.data.network.blockhain.updaters.PooledBalanceUpdaterFactory
import io.novafoundation.nova.feature_staking_api.data.nominationPools.pool.PoolAccountDerivation
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.assets.AssetSourceRegistry
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.updaters.BalanceLocksUpdaterFactory
import io.novafoundation.nova.feature_wallet_api.data.network.blockhain.updaters.PaymentUpdaterFactory
import io.novafoundation.nova.feature_wallet_api.data.repository.ExternalBalanceRepository
import io.novafoundation.nova.feature_wallet_api.domain.interfaces.CoinPriceRepository
import io.novafoundation.nova.feature_wallet_api.domain.interfaces.WalletRepository
import io.novafoundation.nova.runtime.ethereum.StorageSharedRequestsBuilderFactory
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry

@Module(includes = [SendModule::class, ManageTokensCommonModule::class, AddTokenModule::class])
class AssetsFeatureModule {

    @Provides
    @FeatureScope
    fun provideExternalBalancesInteractor(
        accountRepository: AccountRepository,
        externalBalanceRepository: ExternalBalanceRepository
    ): ExternalBalancesInteractor = RealExternalBalancesInteractor(accountRepository, externalBalanceRepository)

    @Provides
    @FeatureScope
    fun provideSearchInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        assetSourceRegistry: AssetSourceRegistry
    ) = AssetSearchInteractor(walletRepository, accountRepository, chainRegistry, assetSourceRegistry)

    @Provides
    @FeatureScope
    fun provideAssetFiltersRepository(preferences: Preferences): AssetFiltersRepository {
        return PreferencesAssetFiltersRepository(preferences)
    }

    @Provides
    @FeatureScope
    fun provideWalletInteractor(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        assetFiltersRepository: AssetFiltersRepository,
        chainRegistry: ChainRegistry,
        nftRepository: NftRepository,
        transactionHistoryRepository: TransactionHistoryRepository,
        currencyRepository: CurrencyRepository
    ): WalletInteractor = WalletInteractorImpl(
        walletRepository = walletRepository,
        accountRepository = accountRepository,
        assetFiltersRepository = assetFiltersRepository,
        chainRegistry = chainRegistry,
        nftRepository = nftRepository,
        transactionHistoryRepository = transactionHistoryRepository,
        currencyRepository = currencyRepository
    )

    @Provides
    @FeatureScope
    fun provideBanxaProvider(): BanxaProvider {
        return BanxaProvider(BuildConfig.BANXA_HOST)
    }

    @Provides
    @FeatureScope
    fun provideMercuryoProvider(): MercuryoProvider {
        return MercuryoProvider(
            host = BuildConfig.MERCURYO_HOST,
            widgetId = BuildConfig.MERCURYO_WIDGET_ID,
            secret = BuildConfig.MERCURYO_SECRET
        )
    }

    @Provides
    @FeatureScope
    fun provideTransakProvider(): TransakProvider {
        val environment = if (BuildConfig.DEBUG) "STAGING" else "PRODUCTION"

        return TransakProvider(
            host = BuildConfig.TRANSAK_HOST,
            apiKey = BuildConfig.TRANSAK_TOKEN,
            environment = environment
        )
    }

    @Provides
    @FeatureScope
    fun provideBuyTokenIntegration(
        transakProvider: TransakProvider,
        mercuryoProvider: MercuryoProvider,
        banxaProvider: BanxaProvider
    ): BuyTokenRegistry {
        return BuyTokenRegistry(
            providers = listOf(
                transakProvider,
                banxaProvider,
                mercuryoProvider
            )
        )
    }

    @Provides
    fun provideBuyMixinFactory(
        buyTokenRegistry: BuyTokenRegistry,
        chainRegistry: ChainRegistry,
        accountUseCase: SelectedAccountUseCase,
        actionAwaitableMixinFactory: ActionAwaitableMixin.Factory,
    ): BuyMixinFactory = BuyMixinFactory(
        buyTokenRegistry = buyTokenRegistry,
        chainRegistry = chainRegistry,
        accountUseCase = accountUseCase,
        awaitableMixinFactory = actionAwaitableMixinFactory
    )

    @Provides
    @FeatureScope
    fun provideHistoryFiltersProviderFactory(
        computationalCache: ComputationalCache,
        assetSourceRegistry: AssetSourceRegistry,
        chainRegistry: ChainRegistry,
    ) = HistoryFiltersProviderFactory(computationalCache, assetSourceRegistry, chainRegistry)

    @Provides
    @FeatureScope
    fun provideCoinGeckoLinkParser(): CoinGeckoLinkParser {
        return CoinGeckoLinkParser()
    }

    @Provides
    @FeatureScope
    fun provideControllableAssetCheckMixin(
        missingKeysPresenter: WatchOnlyMissingKeysPresenter,
        actionAwaitableMixinFactory: ActionAwaitableMixin.Factory,
        resourceManager: ResourceManager
    ): ControllableAssetCheckMixin {
        return ControllableAssetCheckMixin(
            missingKeysPresenter,
            actionAwaitableMixinFactory,
            resourceManager
        )
    }

    @Provides
    @FeatureScope
    fun provideBalancesUpdateSystem(
        chainRegistry: ChainRegistry,
        paymentUpdaterFactory: PaymentUpdaterFactory,
        balanceLocksUpdater: BalanceLocksUpdaterFactory,
        pooledBalanceUpdaterFactory: PooledBalanceUpdaterFactory,
        accountUpdateScope: AccountUpdateScope,
        storageSharedRequestsBuilderFactory: StorageSharedRequestsBuilderFactory,
    ): BalancesUpdateSystem {
        return BalancesUpdateSystem(
            chainRegistry = chainRegistry,
            paymentUpdaterFactory = paymentUpdaterFactory,
            balanceLocksUpdater = balanceLocksUpdater,
            pooledBalanceUpdaterFactory = pooledBalanceUpdaterFactory,
            accountUpdateScope = accountUpdateScope,
            storageSharedRequestsBuilderFactory = storageSharedRequestsBuilderFactory
        )
    }

    @Provides
    @FeatureScope
    fun provideTransactionHistoryRepository(
        assetSourceRegistry: AssetSourceRegistry,
        operationsDao: OperationDao,
        coinPriceRepository: CoinPriceRepository,
        poolAccountDerivation: PoolAccountDerivation
    ): TransactionHistoryRepository = RealTransactionHistoryRepository(
        assetSourceRegistry = assetSourceRegistry,
        operationDao = operationsDao,
        coinPriceRepository = coinPriceRepository,
        poolAccountDerivation = poolAccountDerivation
    )
}
