package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.ApiListing
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemListingCardBinding
import java.text.SimpleDateFormat
import java.util.*

class ListingsAdapter(
    private var items: List<ApiListing>
) : RecyclerView.Adapter<ListingsAdapter.VH>() {

    inner class VH(val b: ItemListingCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b

        b.tvTitle.text = item.title ?: "—"

        b.tvPrice.text = item.price?.let {
            val fmt = if (it % 1 == 0.0) it.toLong().toString() else it.toString()
            "$fmt ر.س"
        } ?: "—"

        b.tvSeller.text = item.sellerName ?: ""
        b.tvLocation.text = "📍 ${item.regionNameAr ?: item.city ?: ""}"
        b.tvTime.text = "🕐 ${formatTime(item.createdAt)}"

        val isOffer = item.listingType == "offer"
        b.tvType.text = if (isOffer) "عرض" else "طلب"
        b.tvType.setBackgroundColor(
            if (isOffer) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )

        val imageUrl = item.images.firstOrNull()
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(b.ivImage.context).load(imageUrl)
                .placeholder(R.drawable.ic_photo_placeholder)
                .centerCrop().into(b.ivImage)
        } else {
            b.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
        }

        val avatar = item.sellerAvatar
        if (!avatar.isNullOrEmpty()) {
            Glide.with(b.ivAvatar.context).load(avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(b.ivAvatar)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ApiListing>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(dateStr) ?: return dateStr
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> "الآن"
                diff < 3600 -> "${diff / 60} دقيقة"
                diff < 86400 -> "${diff / 3600} ساعة"
                diff < 2592000 -> "${diff / 86400} يوم"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { dateStr }
    }
}
