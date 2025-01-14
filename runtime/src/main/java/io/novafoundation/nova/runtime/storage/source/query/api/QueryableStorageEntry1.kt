package io.novafoundation.nova.runtime.storage.source.query.api

import io.novafoundation.nova.runtime.storage.source.query.StorageQueryContext
import io.novafoundation.nova.runtime.storage.source.query.WithRawValue
import io.novafoundation.nova.runtime.storage.source.query.wrapSingleArgumentKeys
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

typealias QueryableStorageBinder1<K, V> = (dynamicInstance: Any, key: K) -> V

interface QueryableStorageEntry1<I, T : Any> {

    context(StorageQueryContext)
    suspend fun query(argument: I): T?

    context(StorageQueryContext)
    suspend fun <K> multi(keys: List<I>, keyTransform: (I) -> K): Map<K, T?>

    context(StorageQueryContext)
    suspend fun multi(keys: List<I>): Map<I, T?>

    context(StorageQueryContext)
    suspend fun queryRaw(argument: I): String?

    context(StorageQueryContext)
    fun observe(argument: I): Flow<T?>

    context(StorageQueryContext)
    fun observeWithRaw(argument: I): Flow<WithRawValue<T?>>

    context(StorageQueryContext)
    fun storageKey(argument: I): String
}

context(StorageQueryContext)
fun <I, T : Any> QueryableStorageEntry1<I, T>.observeNonNull(argument: I): Flow<T> = observe(argument).filterNotNull()

context(StorageQueryContext)
suspend fun <I, T : Any> QueryableStorageEntry1<I, T>.queryNonNull(argument: I): T = requireNotNull(query(argument))

internal class RealQueryableStorageEntry1<I, T : Any>(
    private val storageEntry: StorageEntry,
    private val binding: QueryableStorageBinder1<I, T>
) : QueryableStorageEntry1<I, T> {

    context(StorageQueryContext)
    override suspend fun query(argument: I): T? {
        return storageEntry.query(argument, binding = { decoded -> decoded?.let { binding(it, argument) } })
    }

    context(StorageQueryContext)
    override fun observe(argument: I): Flow<T?> {
        return storageEntry.observe(argument, binding = { decoded -> decoded?.let { binding(it, argument) } })
    }

    context(StorageQueryContext)
    override suspend fun queryRaw(argument: I): String? {
        return storageEntry.queryRaw(argument)
    }

    context(StorageQueryContext)
    override fun storageKey(argument: I): String {
        return storageEntry.createStorageKey(argument)
    }

    context(StorageQueryContext)
    override fun observeWithRaw(argument: I): Flow<WithRawValue<T?>> {
        return storageEntry.observeWithRaw(argument, binding = { decoded -> decoded?.let { binding(it, argument) } })
    }

    context(StorageQueryContext)
    @Suppress("UNCHECKED_CAST")
    override suspend fun <K> multi(keys: List<I>, keyTransform: (I) -> K): Map<K, T?> {
        val reverseKeyLookup = keys.associateBy(keyTransform)

        return storageEntry.entries(
            keysArguments = keys.wrapSingleArgumentKeys(),
            keyExtractor = { (key: Any?) -> keyTransform(key as I) },
            binding = { decoded, key -> decoded?.let { binding(it, reverseKeyLookup.getValue(key)) } }
        )
    }

    context(StorageQueryContext)
    override suspend fun multi(keys: List<I>): Map<I, T?> {
        return storageEntry.singleArgumentEntries(
            keysArguments = keys,
            binding = { decoded, key -> decoded?.let { binding(it, key) } }
        )
    }
}
