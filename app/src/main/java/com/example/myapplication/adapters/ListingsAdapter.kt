package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.annotation.SuppressLint
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.ApiListing
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemListingCardBinding
import com.example.myapplication.utils.ListingLocationFormatter
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.utils.PriceFormatter
import com.example.myapplication.utils.RelativeTimeFormatter

class ListingsAdapter(
    private var items: List<ApiListing>,
    private val onClick: (ApiListing) -> Unit = {},
    private val onFavoriteClick: ((ApiListing, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<ListingsAdapter.ItemVH>() {

    private val favoriteIds = mutableSetOf<String>()

    inner class ItemVH(val b: ItemListingCardBinding) : RecyclerView.ViewHolder(b.root) {
        var currentImageIndex = 0
        var imageUrls: List<String> = emptyList()
        var pageCallback: ViewPager2.OnPageChangeCallback? = null
        /** One gallery adapter per card, reused across binds (no re-inflating pages). */
        val galleryAdapter = CardImageAdapter()
        /** URLs the gallery is currently showing; skip the rebuild when unchanged. */
        var galleryUrls: List<String>? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        val holder = ItemVH(ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        val b = holder.b
        // One-time view setup (previously repeated on every bind)
        b.flCardImage.clipToOutline = true
        b.ivPrevImage.visibility = View.GONE
        b.ivNextImage.visibility = View.GONE
        b.vpCardImages.layoutDirection = View.LAYOUT_DIRECTION_LTR
        b.llCardDots.layoutDirection = View.LAYOUT_DIRECTION_LTR
        b.vpCardImages.adapter = holder.galleryAdapter
        // ViewPager2's inner horizontal RecyclerView must not take part in the
        // vertical nested scroll (it fights the list + collapsing filter strips),
        // and its own overscroll glow flashes while flinging the feed.
        (b.vpCardImages.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        return holder
    }

    override fun onBindViewHolder(holder: ItemVH, position: Int, payloads: MutableList<Any>) {
        // Favorite toggles only touch the bookmark icon — no gallery/image rebind.
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_FAVORITE }) {
            bindFavorite(holder, items[position])
            return
        }
        onBindViewHolder(holder, position)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val item = items[position]
        val b = holder.b

        b.tvTitle.text = item.title ?: "—"

        b.tvPrice.text = PriceFormatter.display(item.price)

        b.tvLocation.text = ListingLocationFormatter.cityOnly(item.city)
        b.tvTime.text = RelativeTimeFormatter.format(holder.itemView.context, item.createdAt)

        // Type badge color
        val ctx = holder.itemView.context
        val isOffer = item.listingType == "offer"
        b.tvType.text = if (isOffer) LocaleHelper.localizedName(ctx, "العرض", "Offer") else LocaleHelper.localizedName(ctx, "الطلب", "Request")
        b.tvType.setBackgroundColor(if (isOffer) COLOR_OFFER else COLOR_REQUEST)

        // Seller name + avatar removed from the external card as requested
        b.tvSeller.visibility = View.GONE
        b.ivAvatar.visibility = View.GONE

        // Image gallery setup - preserve index if re-bound
        if (holder.imageUrls != item.images) {
            holder.imageUrls = item.images
            holder.currentImageIndex = 0
        }
        
        // Swipeable image gallery + dots indicator (same as the ad details screen)
        setupCardGallery(holder, item)

        b.root.setOnClickListener { onClick(item) }
        
        bindFavorite(holder, item)
    }

    /** Favorite icon handling matching iOS ListingCard. */
    private fun bindFavorite(holder: ItemVH, item: ApiListing) {
        val b = holder.b
        val isFav = favoriteIds.contains(item.id)
        b.ivFavorite.colorFilter = null
        b.ivFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite_bookmark_filled else R.drawable.ic_favorite_bookmark
        )
        b.ivFavorite.setOnClickListener {
            val context = b.root.context
            if (!TokenManager.isLoggedIn(context)) {
                AuthGuard.requireLogin(context) {}
                return@setOnClickListener
            }
            val nowFav = !favoriteIds.contains(item.id)
            if (nowFav) favoriteIds.add(item.id) else favoriteIds.remove(item.id)
            b.ivFavorite.setImageResource(
                if (nowFav) R.drawable.ic_favorite_bookmark_filled else R.drawable.ic_favorite_bookmark
            )
            onFavoriteClick?.invoke(item, nowFav)
        }
    }

    // ── Image gallery ────────────────────────────────────────────────────────

    private fun setupCardGallery(holder: ItemVH, item: ApiListing) {
        val b = holder.b
        val images = holder.imageUrls
        holder.galleryAdapter.onImageClick = { onClick(item) }

        // Same photos already on this card (re-bind, page append, favorite refresh):
        // leave the pager, its pages and the dots exactly as they are.
        if (holder.galleryUrls == images) return
        holder.galleryUrls = images

        holder.pageCallback?.let { b.vpCardImages.unregisterOnPageChangeCallback(it) }
        holder.pageCallback = null

        if (images.isEmpty()) {
            holder.galleryAdapter.submit(emptyList())
            b.vpCardImages.visibility = View.GONE
            b.llCardDots.visibility = View.GONE
            b.ivImage.visibility = View.VISIBLE
            b.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
            return
        }

        b.ivImage.visibility = View.GONE
        b.vpCardImages.visibility = View.VISIBLE
        // Dots follow the pager, not the locale: page 1 is always the first dot
        b.vpCardImages.isUserInputEnabled = images.size > 1
        holder.galleryAdapter.submit(images)

        val startIndex = holder.currentImageIndex.coerceIn(0, images.size - 1)
        holder.currentImageIndex = startIndex
        b.vpCardImages.setCurrentItem(startIndex, false)

        buildDots(b.llCardDots, images.size, startIndex, b.vpCardImages)

        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                holder.currentImageIndex = position
                updateDots(b.llCardDots, position)
            }
        }
        b.vpCardImages.registerOnPageChangeCallback(callback)
        holder.pageCallback = callback
    }

    private fun buildDots(container: LinearLayout, count: Int, activePosition: Int, pager: ViewPager2) {
        container.removeAllViews()
        if (count <= 1) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val density = container.resources.displayMetrics.density
        val dotSize = (6 * density).toInt()
        val dotMargin = (2 * density).toInt()

        for (i in 0 until count) {
            val dot = View(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                setBackgroundResource(
                    if (i == activePosition) R.drawable.bg_carousel_dot_active
                    else R.drawable.bg_carousel_dot_inactive
                )
                scaleX = if (i == activePosition) 1.25f else 1.0f
                scaleY = if (i == activePosition) 1.25f else 1.0f
                setOnClickListener { pager.setCurrentItem(i, true) }
            }
            container.addView(dot)
        }
    }

    private fun updateDots(container: LinearLayout, activePosition: Int) {
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val isActive = (i == activePosition)
            dot.setBackgroundResource(
                if (isActive) R.drawable.bg_carousel_dot_active
                else R.drawable.bg_carousel_dot_inactive
            )
            dot.animate()
                .scaleX(if (isActive) 1.25f else 1.0f)
                .scaleY(if (isActive) 1.25f else 1.0f)
                .setDuration(200)
                .start()
        }
    }

    /** One photo per page; a tap opens the ad, exactly like tapping the card. */
    class CardImageAdapter : RecyclerView.Adapter<CardImageAdapter.ImageVH>() {

        private var images: List<String> = emptyList()
        var onImageClick: () -> Unit = {}

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newImages: List<String>) {
            if (newImages == images) return
            images = newImages
            notifyDataSetChanged()
        }

        class ImageVH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_detail_image, parent, false) as ImageView
            view.scaleType = ImageView.ScaleType.CENTER_CROP
            return ImageVH(view)
        }

        override fun onBindViewHolder(holder: ImageVH, position: Int) {
            Glide.with(holder.imageView.context)
                .load(images[position])
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                .centerCrop()
                .into(holder.imageView)

            holder.itemView.setOnClickListener { onImageClick() }
        }

        override fun onViewRecycled(holder: ImageVH) {
            // Free the bitmap of pages that scrolled away
            Glide.with(holder.imageView).clear(holder.imageView)
        }

        override fun getItemCount() = images.size
    }

    override fun getItemCount() = items.size

    /**
     * Only animates/binds what actually changed. Loading the next page used to
     * call notifyDataSetChanged(), which re-bound every visible card (galleries,
     * dots, image requests) mid-fling — the main source of scroll stutter.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<ApiListing>) {
        val old = items
        if (old === newItems) return
        items = newItems
        when {
            old.isEmpty() || newItems.isEmpty() -> notifyDataSetChanged()
            // Pagination: same prefix, new rows at the end → pure insert
            newItems.size > old.size && newItems.subList(0, old.size) == old ->
                notifyItemRangeInserted(old.size, newItems.size - old.size)
            else -> DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(o: Int, n: Int) = old[o].id == newItems[n].id
                override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
            }).dispatchUpdatesTo(this)
        }
    }

    fun setFavoriteIds(ids: Set<String>) {
        if (favoriteIds == ids) return
        favoriteIds.clear()
        favoriteIds.addAll(ids)
        // Refresh only the bookmark icons (see payload handling in onBindViewHolder)
        notifyItemRangeChanged(0, items.size, PAYLOAD_FAVORITE)
    }

    fun getItems(): List<ApiListing> = items

    private companion object {
        const val PAYLOAD_FAVORITE = "favorite"
        val COLOR_OFFER = Color.parseColor("#34C759")
        val COLOR_REQUEST = Color.parseColor("#FF9500")
    }
}
