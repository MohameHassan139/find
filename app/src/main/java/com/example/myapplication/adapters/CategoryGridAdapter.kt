package com.example.myapplication.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.ApiCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemCategoryGridBinding
import com.example.myapplication.utils.LocaleHelper

class CategoryGridAdapter(
    private var items: List<ApiCategory>,
    private val onClick: (ApiCategory) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.VH>() {

    class VH(val b: ItemCategoryGridBinding) : RecyclerView.ViewHolder(b.root)

    // The 5 sub-icon WebViews in order
    private fun iconSlots(b: ItemCategoryGridBinding): List<android.webkit.WebView> = listOf(
        b.ivSubAll, b.ivSub1, b.ivSub2,
        b.ivSub3, b.ivSub4
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val slots = iconSlots(b)
        slots.forEach { iv ->
            iv.setBackgroundColor(0) // Transparent
            iv.isClickable = false
            iv.isFocusable = false
            iv.isFocusableInTouchMode = false
            iv.isVerticalScrollBarEnabled = false
            iv.isHorizontalScrollBarEnabled = false
            iv.settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
        
        b.wvSingleIcon.setBackgroundColor(0) // Transparent
        b.wvSingleIcon.isClickable = false
        b.wvSingleIcon.isFocusable = false
        b.wvSingleIcon.isFocusableInTouchMode = false
        b.wvSingleIcon.isVerticalScrollBarEnabled = false
        b.wvSingleIcon.isHorizontalScrollBarEnabled = false
        b.wvSingleIcon.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        holder.b.tvCategoryTitle.text = LocaleHelper.localizedName(holder.itemView.context, cat.nameAr, cat.nameEn)

        val isOther = position == items.size - 1 || cat.nameAr.contains("اخرى") || cat.nameAr.contains("اخر") || cat.nameAr.contains("أخرى")

        if (isOther) {
            holder.b.glSubIcons.visibility = android.view.View.GONE

            val url = cat.iconUrl ?: cat.subCategories.find { !it.iconUrl.isNullOrEmpty() }?.iconUrl
            if (!url.isNullOrEmpty()) {
                holder.b.wvSingleIcon.visibility = android.view.View.VISIBLE
                holder.b.tvSingleDots.visibility = android.view.View.GONE
                val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>*{margin:0;padding:0;box-sizing:border-box;}html,body{width:100%;height:100%;background:transparent;display:flex;justify-content:center;align-items:center;} img{width:70%;height:70%;object-fit:contain;}</style></head><body><img src=\"$url\"/></body></html>"
                holder.b.wvSingleIcon.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
            } else {
                holder.b.wvSingleIcon.visibility = android.view.View.GONE
                holder.b.tvSingleDots.visibility = android.view.View.VISIBLE
            }
        } else {
            holder.b.glSubIcons.visibility = android.view.View.VISIBLE
            holder.b.wvSingleIcon.visibility = android.view.View.GONE
            holder.b.tvSingleDots.visibility = android.view.View.GONE

            val subs = cat.subCategories
            val isElectronics = cat.nameAr.contains("إلكترون") || cat.nameEn?.lowercase()?.contains("electronics") == true

            val slots = iconSlots(holder.b)

            slots.forEachIndexed { i, iv ->
                val sub = when (i) {
                    0 -> subs.getOrNull(0) // ivSubAll
                    1 -> subs.getOrNull(1) // ivSub1
                    2 -> subs.getOrNull(2) // ivSub2
                    3 -> subs.getOrNull(3) // ivSub3
                    4 -> if (isElectronics) subs.getOrNull(5) else subs.getOrNull(4) // ivSub4
                    else -> null
                }
                val url = sub?.iconUrl
                if (!url.isNullOrEmpty()) {
                    val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>*{margin:0;padding:0;box-sizing:border-box;}html,body{width:100%;height:100%;background:transparent;display:flex;justify-content:center;align-items:center;} img{width:78%;height:78%;object-fit:contain;}</style></head><body><img src=\"$url\"/></body></html>"
                    iv.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
                } else {
                    val placeholderHtml = "<html><head><style>*{margin:0;padding:0;}html,body{width:100%;height:100%;background:transparent;}</style></head><body></body></html>"
                    iv.loadDataWithBaseURL("about:blank", placeholderHtml, "text/html", "UTF-8", null)
                }
            }
        }

        holder.b.clickOverlay.setOnClickListener { onClick(cat) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ApiCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
