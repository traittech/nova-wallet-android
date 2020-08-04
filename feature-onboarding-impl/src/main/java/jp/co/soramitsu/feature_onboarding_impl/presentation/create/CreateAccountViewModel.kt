package jp.co.soramitsu.feature_onboarding_impl.presentation.create

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class CreateAccountViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter
) : BaseViewModel() {

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    fun homeButtonClicked() {
        router.backToWelcomeScreen()
    }

    fun accountNameChanged(accountName: CharSequence?) {
        accountName?.let {
            _nextButtonEnabledLiveData.value = it.isNotEmpty()
        }
    }

    fun nextClicked(accountName: String) {
        router.openMnemonicScreen(accountName)
    }
}