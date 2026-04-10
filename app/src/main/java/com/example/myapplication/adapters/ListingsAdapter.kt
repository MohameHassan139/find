package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val onClick: (ApiListing) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var showFooterLoader = false

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    inner class ItemVH(val b: ItemListingCardBinding) : RecyclerView.ViewHolder(b.root)
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterVH) return
        val item = items[position]
        val b = (holder as ItemVH).b

        b.tvTitle.text = item.title ?: "—"

        b.tvPrice.text = item.price?.let {
            val fmt = if (it % 1 == 0.0) it.toLong().toString() else it.toString()
            "$fmt ر.س"
        } ?: "—"
        // IBM Plex Mono style via system monospace
        b.tvPrice.typeface = android.graphics.Typeface.MONOSPACE

        b.tvSeller.text = item.sellerName ?: ""
        b.tvLocation.text = "📍 ${item.regionNameAr ?: item.city ?: ""}"
        b.tvTime.text = "🕐 ${formatTime(item.createdAt, holder.itemView.context)}"

        // Type badge color
        val ctx = holder.itemView.context
        val isOffer = item.listingType == "offer"
        b.tvType.text = if (isOffer) LocaleHelper.localizedName(ctx, "عرض", "Offer") else LocaleHelper.localizedName(ctx, "طلب", "Request")
        b.tvType.setBackgroundColor(
            if (isOffer) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )

        // Image — cached + crossfade
        val imageUrl = item.images.firstOrNull()
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(b.ivImage.context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                .centerCrop()
                .into(b.ivImage)
        } else {
            b.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
        }

        // Avatar — cached + crossfade
        val avatar = item.sellerAvatar
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

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size + if (showFooterLoader) 1 else 0

    fun updateData(newItems: List<ApiListing>) {
        items = newItems
        notifyDataSetChanged()
    }

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
