package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_STASH
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceElectionPeriodValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_BOND_MORE
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_REDEEM
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_UNBOND
import javax.inject.Named

@Module
class StakingBalanceValidationsModule {

    @FeatureScope
    @Named(BALANCE_REQUIRED_CONTROLLER)
    @Provides
    fun provideControllerValidation(
        accountRepository: AccountRepository
    ) = BalanceAccountRequiredValidation(
        accountRepository,
        accountAddressExtractor = { it.stashState.controllerAddress },
        errorProducer = ManageStakingValidationFailure::ControllerRequired
    )

    @FeatureScope
    @Named(BALANCE_REQUIRED_STASH)
    @Provides
    fun provideStashValidation(
        accountRepository: AccountRepository
    ) = BalanceAccountRequiredValidation(
        accountRepository,
        accountAddressExtractor = { it.stashState.stashAddress },
        errorProducer = ManageStakingValidationFailure::StashRequired
    )

    @FeatureScope
    @Provides
    fun provideElectionValidation(
        stakingRepository: StakingRepository,
    ) = BalanceElectionPeriodValidation(
        stakingRepository,
        networkTypeProvider = { it.stashState.controllerAddress.networkType() },
        errorProducer = { ManageStakingValidationFailure.ElectionPeriodOpen }
    )

    @FeatureScope
    @Provides
    fun provideUnbondingLimitValidation(
        stakingRepository: StakingRepository,
    ) = BalanceUnlockingLimitValidation(
        stakingRepository,
        stashStateProducer = { it.stashState },
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_REDEEM)
    @Provides
    fun provideRedeemValidationSystem(
        balanceElectionPeriodValidation: BalanceElectionPeriodValidation,
        @Named(BALANCE_REQUIRED_CONTROLLER)
        controllerRequiredValidation: BalanceAccountRequiredValidation,
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                controllerRequiredValidation,
                balanceElectionPeriodValidation
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_BOND_MORE)
    @Provides
    fun provideBondMoreValidationSystem(
        balanceElectionPeriodValidation: BalanceElectionPeriodValidation,
        @Named(BALANCE_REQUIRED_STASH)
        stashRequiredValidation: BalanceAccountRequiredValidation,
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                stashRequiredValidation,
                balanceElectionPeriodValidation
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_UNBOND)
    @Provides
    fun provideUnbondValidationSystem(
        balanceElectionPeriodValidation: BalanceElectionPeriodValidation,
        @Named(BALANCE_REQUIRED_CONTROLLER)
        controllerRequiredValidation: BalanceAccountRequiredValidation,
        balanceUnlockingLimitValidation: BalanceUnlockingLimitValidation
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                controllerRequiredValidation,
                balanceElectionPeriodValidation,
                balanceUnlockingLimitValidation
            )
        )
    )
}