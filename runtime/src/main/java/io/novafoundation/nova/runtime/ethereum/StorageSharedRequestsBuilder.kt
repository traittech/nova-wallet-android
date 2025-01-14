package io.novafoundation.nova.runtime.ethereum

import io.novafoundation.nova.common.utils.inBackground
import io.novafoundation.nova.common.utils.invokeOnCompletion
import io.novafoundation.nova.core.ethereum.Web3Api
import io.novafoundation.nova.core.ethereum.log.Topic
import io.novafoundation.nova.core.model.StorageChange
import io.novafoundation.nova.core.updater.SharedRequestsBuilder
import io.novafoundation.nova.runtime.ethereum.subscribtion.EthereumRequestsAggregator
import io.novafoundation.nova.runtime.multiNetwork.ChainRegistry
import io.novafoundation.nova.runtime.multiNetwork.awaitCallEthereumApi
import io.novafoundation.nova.runtime.multiNetwork.awaitSocketOrNull
import io.novafoundation.nova.runtime.multiNetwork.awaitSubscriptionEthereumApi
import io.novafoundation.nova.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.StorageSubscriptionMultiplexer
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.fearless_utils.wsrpc.subscribe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.websocket.events.LogNotification
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

class StorageSharedRequestsBuilderFactory(
    private val chainRegistry: ChainRegistry,
) {

    suspend fun create(chainId: ChainId): StorageSharedRequestsBuilder {
        val substrateProxy = StorageSubscriptionMultiplexer.Builder()
        val ethereumProxy = EthereumRequestsAggregator.Builder()

        val rpcSocket = chainRegistry.awaitSocketOrNull(chainId)

        val subscriptionApi = chainRegistry.awaitSubscriptionEthereumApi(chainId)
        val callApi = chainRegistry.awaitCallEthereumApi(chainId)

        return StorageSharedRequestsBuilder(
            socketService = rpcSocket,
            substrateProxy = substrateProxy,
            ethereumProxy = ethereumProxy,
            subscriptionApi = subscriptionApi,
            callApi = callApi
        )
    }
}

class StorageSharedRequestsBuilder(
    override val socketService: SocketService?,
    private val substrateProxy: StorageSubscriptionMultiplexer.Builder,
    private val ethereumProxy: EthereumRequestsAggregator.Builder,
    override val subscriptionApi: Web3Api?,
    override val callApi: Web3Api?,
) : SharedRequestsBuilder {

    override fun subscribe(key: String): Flow<StorageChange> {
        return substrateProxy.subscribe(key)
            .map { StorageChange(it.block, it.key, it.value) }
    }

    override fun <S, T : Response<*>> ethBatchRequestAsync(batchId: String, request: Request<S, T>): CompletableFuture<T> {
        return ethereumProxy.batchRequest(batchId, request)
    }

    override fun subscribeEthLogs(address: String, topics: List<Topic>): Flow<LogNotification> {
        return ethereumProxy.subscribeLogs(address, topics)
    }

    fun subscribe(coroutineScope: CoroutineScope) {
        val ethereumRequestsAggregator = ethereumProxy.build()

        subscriptionApi?.let { web3Api ->
            ethereumRequestsAggregator.subscribeUsing(web3Api)
                .inBackground()
                .launchIn(coroutineScope)
        }

        callApi?.let { web3Api ->
            ethereumRequestsAggregator.executeBatches(coroutineScope, web3Api)
        }

        val cancellable = socketService?.subscribeUsing(substrateProxy.build())

        if (cancellable != null) {
            coroutineScope.invokeOnCompletion { cancellable.cancel() }
        }
    }
}

fun StorageSharedRequestsBuilder.subscribe(coroutineContext: CoroutineContext) {
    subscribe(CoroutineScope(coroutineContext))
}
