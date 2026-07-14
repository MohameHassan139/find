package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.annotation.SuppressLint
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.myapplication.ApiListing
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemListingCardBinding
import com.example.myapplication.utils.LocaleHelper
import java.text.SimpleDateFormat
import java.util.*

class ListingsAdapter(
    private var items: List<ApiListing>,
    private val onClick: (ApiListing) -> Unit = {},
    private val onFavoriteClick: ((ApiListing, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var showFooterLoader = false
    private val favoriteIds = mutableSetOf<String>()

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    inner class ItemVH(val b: ItemListingCardBinding) : RecyclerView.ViewHolder(b.root) {
        var currentImageIndex = 0
        var imageUrls: List<String> = emptyList()
    }
    inner class FooterVH(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int) =
        if (showFooterLoader && position == items.size) TYPE_FOOTER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_FOOTER) {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_footer_loader, parent, false)
            FooterVH(v)
        } else {
            ItemVH(ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterVH) return
        val item = items[position]
        val b = (holder as ItemVH).b

        b.tvTitle.text = item.title ?: "—"

        b.tvPrice.text = item.price?.let {
            val fmt = if (it % 1 == 0.0) {
                java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(it.toLong())
            } else {
                java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(it)
            }
            fmt
        } ?: "—"

        b.tvLocation.text = item.regionNameAr ?: item.city ?: ""
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
        
        // Show/hide navigation arrows
        if (item.images.size > 1) {
            b.ivPrevImage.visibility = View.VISIBLE
            b.ivNextImage.visibility = View.VISIBLE
        } else {
            b.ivPrevImage.visibility = View.GONE
            b.ivNextImage.visibility = View.GONE
        }

        // Load current image
        loadImage(b, holder.imageUrls, holder.currentImageIndex)

        // Previous image button
        b.ivPrevImage.setOnClickListener {
            if (holder.imageUrls.isNotEmpty()) {
                holder.currentImageIndex = if (holder.currentImageIndex > 0) {
                    holder.currentImageIndex - 1
                } else {
                    holder.imageUrls.size - 1
                }
                loadImage(b, holder.imageUrls, holder.currentImageIndex)
            }
        }

        // Next image button
        b.ivNextImage.setOnClickListener {
            if (holder.imageUrls.isNotEmpty()) {
                holder.currentImageIndex = (holder.currentImageIndex + 1) % holder.imageUrls.size
                loadImage(b, holder.imageUrls, holder.currentImageIndex)
            }
        }

        // Add swipe gesture support on the main image
        var downX = 0f
        var downY = 0f
        b.ivImage.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upX = event.x
                    val upY = event.y
                    val diffX = upX - downX
                    val diffY = upY - downY
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                        if (holder.imageUrls.isNotEmpty()) {
                            val isRtl = LocaleHelper.isArabic(v.context)
                            if (diffX > 0) {
                                // Swipe right
                                if (isRtl) {
                                    holder.currentImageIndex = (holder.currentImageIndex + 1) % holder.imageUrls.size
                                } else {
                                    holder.currentImageIndex = if (holder.currentImageIndex > 0) holder.currentImageIndex - 1 else holder.imageUrls.size - 1
                                }
                            } else {
                                // Swipe left
                                if (isRtl) {
                                    holder.currentImageIndex = if (holder.currentImageIndex > 0) holder.currentImageIndex - 1 else holder.imageUrls.size - 1
                                } else {
                                    holder.currentImageIndex = (holder.currentImageIndex + 1) % holder.imageUrls.size
                                }
                            }
                            loadImage(b, holder.imageUrls, holder.currentImageIndex)
                        }
                    } else if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        v.performClick()
                        onClick(item)
                    }
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    false
                }
                else -> false
            }
        }

        b.root.setOnClickListener { onClick(item) }
        
        // Favorite icon with red color when favorited
        val isFav = favoriteIds.contains(item.id)
        b.ivFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite_bookmark_filled else R.drawable.ic_favorite_bookmark
        )
        b.ivFavorite.setOnClickListener {
            val nowFav = !favoriteIds.contains(item.id)
            if (nowFav) favoriteIds.add(item.id) else favoriteIds.remove(item.id)
            notifyItemChanged(holder.adapterPosition)
            onFavoriteClick?.invoke(item, nowFav)
        }
    }

    private fun loadImage(binding: ItemListingCardBinding, images: List<String>, index: Int) {
        val imageUrl = images.getOrNull(index)
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(binding.ivImage.context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                .centerCrop()
                .into(binding.ivImage)
        } else {
            binding.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
        }
    }

    override fun getItemCount() = items.size + if (showFooterLoader) 1 else 0

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

    fun setFooterLoading(loading: Boolean) {
        if (showFooterLoader == loading) return
        showFooterLoader = loading
        if (loading) notifyItemInserted(items.size)
        else notifyItemRemoved(items.size)
    }

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
