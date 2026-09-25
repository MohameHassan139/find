package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.annotation.SuppressLint
import androidx.viewpager2.widget.ViewPager2
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
import java.text.SimpleDateFormat
import java.util.*

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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH =
        ItemVH(ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        val item = items[position]
        val b = holder.b

        b.tvTitle.text = item.title ?: "—"

        b.tvPrice.text = item.price?.let {
            val fmt = if (it % 1 == 0.0) {
                java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(it.toLong())
            } else {
                java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(it)
            }
            fmt
        } ?: "—"

        b.tvLocation.text = ListingLocationFormatter.cityOnly(item.city)
        b.tvTime.text = formatTime(item.createdAt, holder.itemView.context)

        // Type badge color
        val ctx = holder.itemView.context
        val isOffer = item.listingType == "offer"
        b.tvType.text = if (isOffer) LocaleHelper.localizedName(ctx, "العرض", "Offer") else LocaleHelper.localizedName(ctx, "الطلب", "Request")
        b.tvType.setBackgroundColor(
            if (isOffer) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )

        // Seller name + avatar (right info panel)
        val sellerName = item.sellerName ?: ""
        val avatar = item.sellerAvatar
        b.tvSeller.text = sellerName

        if (!avatar.isNullOrEmpty()) {
            Glide.with(b.ivAvatar.context)
                .load(avatar)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                .circleCrop()
                .into(b.ivAvatar)
        } else {
            b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
        }

        // Image gallery setup - preserve index if re-bound
        if (holder.imageUrls != item.images) {
            holder.imageUrls = item.images
            holder.currentImageIndex = 0
        }
        
        // Swipeable image gallery + dots indicator (same as the ad details screen)
        setupCardGallery(holder, item)

        b.root.setOnClickListener { onClick(item) }
        
        // Favorite icon handling matching iOS ListingCard
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

        // Round the photo corners the same way the old ShapeableImageView did
        b.flCardImage.clipToOutline = true

        // The old chevrons are gone — swiping replaces them
        b.ivPrevImage.visibility = View.GONE
        b.ivNextImage.visibility = View.GONE

        holder.pageCallback?.let { b.vpCardImages.unregisterOnPageChangeCallback(it) }
        holder.pageCallback = null

        if (images.isEmpty()) {
            b.vpCardImages.adapter = null
            b.vpCardImages.visibility = View.GONE
            b.llCardDots.visibility = View.GONE
            b.ivImage.visibility = View.VISIBLE
            b.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
            return
        }

        b.ivImage.visibility = View.GONE
        b.vpCardImages.visibility = View.VISIBLE
        b.vpCardImages.layoutDirection = View.LAYOUT_DIRECTION_LTR
        // Dots follow the pager, not the locale: page 1 is always the first dot
        b.llCardDots.layoutDirection = View.LAYOUT_DIRECTION_LTR
        b.vpCardImages.isUserInputEnabled = images.size > 1
        b.vpCardImages.adapter = CardImageAdapter(images) { onClick(item) }

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
    private class CardImageAdapter(
        private val images: List<String>,
        private val onImageClick: () -> Unit
    ) : RecyclerView.Adapter<CardImageAdapter.ImageVH>() {

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

        override fun getItemCount() = images.size
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ApiListing>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds.clear()
        favoriteIds.addAll(ids)
        notifyDataSetChanged()
    }

    fun getCurrentItems(): List<ApiListing> = items
    fun getItems(): List<ApiListing> = items

    private fun formatTime(dateStr: String?, ctx: android.content.Context? = null): String {
        if (dateStr.isNullOrEmpty()) return ""
        val isAr = ctx?.let { LocaleHelper.isArabic(it) } ?: true
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(dateStr) ?: return dateStr
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> if (isAr) "الآن" else "Now"
                diff < 3600 -> if (isAr) "${diff / 60} دقيقة" else "${diff / 60}m ago"
                diff < 86400 -> if (isAr) "${diff / 3600} ساعة" else "${diff / 3600}h ago"
                diff < 2592000 -> if (isAr) "${diff / 86400} يوم" else "${diff / 86400}d ago"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { dateStr }
    }
}
