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

class CategoryGridAdapter(
    private var items: List<ApiCategory>,
    private val onClick: (ApiCategory) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.VH>() {

    class VH(val b: ItemCategoryGridBinding) : RecyclerView.ViewHolder(b.root)

    // The 8 sub-icon WebViews in order
    private fun iconSlots(b: ItemCategoryGridBinding): List<android.webkit.WebView> = listOf(
        b.ivSub0, b.ivSub1, b.ivSub2,
        b.ivSub3, b.ivSub4, b.ivSub5,
        b.ivSub6, b.ivSub7
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
        holder.b.tvCategoryTitle.text = cat.nameAr

        val isOther = position == items.size - 1 || cat.nameAr.contains("اخرى") || cat.nameAr.contains("اخر") || cat.nameAr.contains("أخرى")

        if (isOther) {
            holder.b.glSubIcons.visibility = android.view.View.GONE

            val url = cat.iconUrl ?: cat.subCategories.find { !it.iconUrl.isNullOrEmpty() }?.iconUrl
            if (!url.isNullOrEmpty()) {
                holder.b.wvSingleIcon.visibility = android.view.View.VISIBLE
                holder.b.tvSingleDots.visibility = android.view.View.GONE
                val html = "<html><head><style>body{margin:0;padding:0;overflow:hidden;display:flex;justify-content:center;align-items:center;height:30%;width:30%;background:transparent;} img{width:40%;height:40%;object-fit:contain;}</style></head><body><img src=\"$url\"/></body></html>"
                holder.b.wvSingleIcon.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
            } else {
                holder.b.wvSingleIcon.visibility = android.view.View.GONE
                holder.b.tvSingleDots.visibility = android.view.View.VISIBLE
            }
        } else {
            holder.b.glSubIcons.visibility = android.view.View.VISIBLE
            holder.b.wvSingleIcon.visibility = android.view.View.GONE
            holder.b.tvSingleDots.visibility = android.view.View.GONE

            val slots = iconSlots(holder.b)
            val subs = cat.subCategories

            slots.forEachIndexed { i, iv ->
                val sub = subs.getOrNull(i)
                val url = sub?.iconUrl
                if (!url.isNullOrEmpty()) {
                    val html = "<html><head><style>body{margin:0;padding:0;overflow:hidden;display:flex;justify-content:center;align-items:center;height:22%;width:22%;background:transparent;} img{width:40%;height:40%;object-fit:contain;}</style></head><body><img src=\"$url\"/></body></html>"
                    iv.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
                } else {
                    val placeholderHtml = "<html><head><style>body{margin:0;padding:0;overflow:hidden;background:transparent;}</style></head><body></body></html>"
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
