package io.novafoundation.nova.balances

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import io.novafoundation.nova.common.data.network.runtime.binding.AccountInfo
import io.novafoundation.nova.common.data.network.runtime.binding.bindAccountInfo
import io.novafoundation.nova.common.di.FeatureUtils
import io.novafoundation.nova.common.utils.fromJson
import io.novafoundation.nova.common.utils.system
import io.novafoundation.nova.feature_account_api.di.AccountFeatureApi
import io.novafoundation.nova.feature_account_impl.di.AccountFeatureComponent
import io.novafoundation.nova.runtime.BuildConfig.TEST_CHAINS_URL
import io.novafoundation.nova.runtime.di.RuntimeApi
import io.novafoundation.nova.runtime.di.RuntimeComponent
import io.novafoundation.nova.runtime.extrinsic.systemRemark
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain
import io.novafoundation.nova.runtime.multiNetwork.connection.ChainConnection
import io.novafoundation.nova.runtime.multiNetwork.getSocket
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.networkStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.net.URL
import kotlin.time.Duration.Companion.seconds


@RunWith(Parameterized::class)
class BalancesIntegrationTest(
    private val testChainId: String,
    private val testChainName: String,
    private val testAccount: String
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")
        fun data(): ArrayList<Array<String?>> {
            val arrayOfNetworks: Array<TestData> = Gson().fromJson(URL(TEST_CHAINS_URL).readText())
            val listNetworks: ArrayList<Array<String?>> = ArrayList()
            arrayOfNetworks.forEach { listNetworks.add(arrayOf(it.chainId, it.name, it.account)) }
            return listNetworks
        }

        class TestData(
            val chainId: String,
            val name: String,
            val account: String?
        )
    }

    private val maxAmount = BigInteger.valueOf(10).pow(30)

    private val runtimeApi = FeatureUtils.getFeature<RuntimeComponent>(
        ApplicationProvider.getApplicationContext<Context>(),
        RuntimeApi::class.java
    )

    private val accountApi = FeatureUtils.getFeature<AccountFeatureComponent>(
        ApplicationProvider.getApplicationContext<Context>(),
        AccountFeatureApi::class.java
    )

    private val chainRegistry = runtimeApi.chainRegistry()
    private val externalRequirementFlow = runtimeApi.externalRequirementFlow()

    private val remoteStorage = runtimeApi.remoteStorageSource()

    private val extrinsicService = accountApi.extrinsicService()

    @Before
    fun before() = runBlocking {
        externalRequirementFlow.emit(ChainConnection.ExternalRequirement.ALLOWED)
    }

    @Test
    fun testBalancesLoading() = runBlocking(Dispatchers.Default) {
        val chains = chainRegistry.getChain(testChainId)

        val freeBalance = testBalancesInChainAsync(chains, testAccount)?.data?.free ?: error("Balance was null")

        assertTrue("Free balance: $freeBalance is less than $maxAmount", maxAmount > freeBalance)
        assertTrue("Free balance: $freeBalance is greater than 0", ZERO < freeBalance)
    }

    @Test
    fun testFeeLoading() = runBlocking(Dispatchers.Default) {
        val chains = chainRegistry.getChain(testChainId)

        testFeeLoadingAsync(chains)

        Unit
    }

    private suspend fun testBalancesInChainAsync(chain: Chain, currentAccount: String): AccountInfo? {
        return coroutineScope {
            try {
                withTimeout(80.seconds) {
                    remoteStorage.query(
                        chainId = chain.id,
                        keyBuilder = { it.metadata.system().storage("Account").storageKey(it, currentAccount.fromHex()) },
                        binding = { scale, runtime -> scale?.let { bindAccountInfo(scale, runtime) } }
                    )
                }
            } catch(e: Exception) {
                throw Exception("Socket state: ${chainRegistry.getSocket(chain.id).networkStateFlow().first()}, error: ${e.message}", e)
            }
        }
    }

    private suspend fun testFeeLoadingAsync(chain: Chain) {
        return coroutineScope {
            withTimeout(80.seconds) {
                extrinsicService.estimateFee(chain) {
                    systemRemark(byteArrayOf(0))
                }
            }
        }
    }
}
