package io.novafoundation.nova.feature_nft_impl.presentation.nft.list

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.clear
import coil.load
import coil.transform.RoundedCornersTransformation
import io.novafoundation.nova.common.presentation.LoadingState
import io.novafoundation.nova.common.utils.dpF
import io.novafoundation.nova.common.utils.inflateChild
import io.novafoundation.nova.common.utils.makeGone
import io.novafoundation.nova.common.utils.makeVisible
import io.novafoundation.nova.common.view.shape.addRipple
import io.novafoundation.nova.common.view.shape.getRippleMask
import io.novafoundation.nova.common.view.shape.getRoundedCornerDrawable
import io.novafoundation.nova.feature_nft_impl.R
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_nft_collection_name.view.itemCollectionMedia
import kotlinx.android.synthetic.main.item_nft_collection_name.view.itemCollectionName
import kotlinx.android.synthetic.main.item_nft_collection_name.view.itemCounter
import kotlinx.android.synthetic.main.item_nft_collection_name.view.itemExpanded
import kotlinx.android.synthetic.main.item_nft_grid.view.itemNftContent
import kotlinx.android.synthetic.main.item_nft_grid.view.itemNftMedia
import kotlinx.android.synthetic.main.item_nft_grid.view.itemNftShimmer
import kotlinx.android.synthetic.main.item_nft_grid.view.itemNftTitle
import kotlinx.android.synthetic.main.item_nft_grid.view.nftCollectionName
import kotlinx.android.synthetic.main.item_nft_list_actions.view.nftActionsReceive
import kotlinx.android.synthetic.main.item_nft_list_actions.view.nftActionsSend

class NftGridAdapter(
    private val imageLoader: ImageLoader,
    private val handler: Handler
) : ListAdapter<NftListItem, NftGridListHolder>(DiffCallback) {

    companion object {
        private const val TYPE_DIVIDER = 1
        private const val TYPE_COLLECTION = 2
        private const val TYPE_NFT = 3
    }

    interface Handler {

        fun itemClicked(item: NftListItem.NftListCard)

        fun loadableItemShown(item: NftListItem.NftListCard)

        fun sendClicked()

        fun receiveClicked()

        fun groupClicked(collection: String)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NftListItem.NftCollection -> TYPE_COLLECTION
            NftListItem.Divider -> TYPE_DIVIDER
            is NftListItem.NftListCard -> TYPE_NFT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NftGridListHolder {
        return when (viewType) {
            TYPE_COLLECTION -> {
                CollectionHolder(parent.inflateChild(R.layout.item_nft_collection_name), imageLoader, handler)
            }

            TYPE_DIVIDER -> {
                DividerHolder(parent.inflateChild(R.layout.item_divider))
            }

            TYPE_NFT -> {
                NftHolder(parent.inflateChild(R.layout.item_nft_grid), imageLoader, handler)
            }

            else -> error("No such viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: NftGridListHolder, position: Int) {
        when (holder) {
            is CollectionHolder -> {
                holder.bind(getItem(position) as NftListItem.NftCollection)
            }

            is DividerHolder -> {}
            is NftHolder -> {
                holder.bind(getItem(position) as NftListItem.NftListCard)
            }
        }
    }

    override fun onViewRecycled(holder: NftGridListHolder) {
        if (holder is NftHolder) {
            holder.unbind()
        }
    }
}

private object DiffCallback : DiffUtil.ItemCallback<NftListItem>() {

    override fun areItemsTheSame(oldItem: NftListItem, newItem: NftListItem): Boolean {
        return when {
            oldItem is NftListItem.NftListCard && newItem is NftListItem.NftListCard -> {
                oldItem.identifier == newItem.identifier
            }

            oldItem is NftListItem.NftCollection && newItem is NftListItem.NftCollection -> {
                oldItem.name == newItem.name
            }

            else -> {
                false
            }
        }
    }

    override fun areContentsTheSame(oldItem: NftListItem, newItem: NftListItem): Boolean {
        return oldItem == newItem
    }
}

sealed class NftGridListHolder(containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer

class CollectionHolder(
    override val containerView: View,
    private val imageLoader: ImageLoader,
    private val itemHandler: NftGridAdapter.Handler,
) : NftGridListHolder(containerView) {

    init {
        with(containerView) {
            background = with(context) {
                addRipple(
                    drawable = ColorDrawable(Color.TRANSPARENT),
                    mask = ColorDrawable(Color.WHITE)
                )
            }
        }
    }

    fun bind(item: NftListItem.NftCollection) = with(containerView) {
        if (item.icon != null) {
            itemCollectionMedia.makeVisible()
            itemCollectionMedia.load(item.icon, imageLoader) {
                transformations(RoundedCornersTransformation(4.dpF(context)))
                placeholder(R.drawable.nft_media_progress)
                error(R.drawable.nft_media_error)
                fallback(R.drawable.nft_media_error)
            }
        } else {
            itemCollectionMedia.makeGone()
        }

        itemCollectionName.text = item.name
        itemExpanded.setImageResource(if (item.expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
        itemCounter.text = item.count

        containerView.setOnClickListener {
            itemHandler.groupClicked(item.name)
        }
    }
}

class DividerHolder(
    override val containerView: View,
) : NftGridListHolder(containerView)

class NftHolder(
    override val containerView: View,
    private val imageLoader: ImageLoader,
    private val itemHandler: NftGridAdapter.Handler
) : NftGridListHolder(containerView) {

    init {
        with(containerView) {
            itemNftContent.background = with(context) {
                addRipple(getRoundedCornerDrawable(R.color.block_background, cornerSizeInDp = 12), mask = getRippleMask(cornerSizeDp = 12))
            }
        }
    }

    fun unbind() = with(containerView) {
        itemNftMedia.clear()
    }

    fun bind(item: NftListItem.NftListCard) = with(containerView) {
        when (val content = item.content) {
            is LoadingState.Loading -> {
                itemNftShimmer.makeVisible()
                itemNftShimmer.startShimmer()
                itemNftContent.makeGone()
            }

            is LoadingState.Loaded -> {

                if (!content.data.wholeDetailsLoaded) {
                    itemHandler.loadableItemShown(item)
                }

                itemNftShimmer.makeGone()
                itemNftShimmer.stopShimmer()
                itemNftContent.makeVisible()

                itemNftMedia.load(content.data.media, imageLoader) {
                    transformations(RoundedCornersTransformation(8.dpF(context)))
                    placeholder(R.drawable.nft_media_progress)
                    error(R.drawable.nft_media_error)
                    fallback(R.drawable.nft_media_error)
                    listener(
                        onError = { _, _ ->
                            // so that placeholder would be able to change aspect ratio and fill ImageView entirely
                            itemNftMedia.scaleType = ImageView.ScaleType.FIT_XY
                        },
                        onSuccess = { _, _ ->
                            // set default scale type back
                            itemNftMedia.scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    )
                }

                val collectionName = content.data.collectionName
                if (collectionName != null) {
                    nftCollectionName.makeVisible()
                    nftCollectionName.text = content.data.collectionName
                } else {
                    nftCollectionName.makeGone()
                }
                itemNftTitle.text = content.data.title
            }
        }

        setOnClickListener { itemHandler.itemClicked(item) }
    }
}