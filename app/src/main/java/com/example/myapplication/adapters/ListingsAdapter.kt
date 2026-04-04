package com.example.myapplication.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemListingCardBinding
import com.example.myapplication.models.Listing
import java.text.SimpleDateFormat
import java.util.*

class ListingsAdapter(
    private var items: List<Listing>
) : RecyclerView.Adapter<ListingsAdapter.VH>() {

    inner class VH(val b: ItemListingCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemListingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b

        b.tvTitle.text = item.title ?: "—"

        val price = item.price
        b.tvPrice.text = if (price != null) {
            val fmt = if (price % 1 == 0.0) price.toLong().toString() else price.toString()
            "$fmt ر.س"
        } else "—"

        b.tvSeller.text = item.seller?.name ?: ""
        b.tvLocation.text = "📍 ${item.region?.nameAr ?: item.city ?: ""}"
        b.tvTime.text = "🕐 ${formatTime(item.createdAt)}"

        // Type badge
        val isOffer = item.listingType == "offer"
        b.tvType.text = if (isOffer) "عرض" else "طلب"
        b.tvType.setBackgroundColor(
            if (isOffer) android.graphics.Color.parseColor("#34C759")
            else android.graphics.Color.parseColor("#FF9500")
        )

        // Image
        val imageUrl = item.images?.firstOrNull()
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(b.ivImage.context).load(imageUrl)
                .placeholder(R.drawable.ic_photo_placeholder)
                .centerCrop().into(b.ivImage)
        } else {
            b.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
        }

        // Avatar
        val avatar = item.seller?.avatar
        if (!avatar.isNullOrEmpty()) {
            Glide.with(b.ivAvatar.context).load(avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(b.ivAvatar)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Listing>) {
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
