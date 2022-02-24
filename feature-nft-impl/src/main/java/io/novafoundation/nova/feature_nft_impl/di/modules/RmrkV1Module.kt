package io.novafoundation.nova.feature_nft_impl.di.modules

import dagger.Module
import dagger.Provides
import io.novafoundation.nova.common.data.network.NetworkApiCreator
import io.novafoundation.nova.common.di.scope.FeatureScope
import io.novafoundation.nova.core_db.dao.NftDao
import io.novafoundation.nova.feature_nft_impl.data.source.providers.rmrkV1.RmrkV1NftProvider
import io.novafoundation.nova.feature_nft_impl.data.source.providers.rmrkV1.network.RmrkV1Api

@Module
class RmrkV1Module {

    @Provides
    @FeatureScope
    fun provideApi(networkApiCreator: NetworkApiCreator): RmrkV1Api {
        return networkApiCreator.create(RmrkV1Api::class.java)
    }

    @Provides
    @FeatureScope
    fun provideNftProvider(
        api: RmrkV1Api,
        nftDao: NftDao
    ) = RmrkV1NftProvider(api, nftDao)
}
