package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiSubCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSubcategoryGridBinding

/**
 * Displays sub-categories with a "الكل" card at position 0 (represented as null).
 * onClick receives null for "الكل", or the ApiSubCategory for any real item.
 */
class SubCategoryGridAdapter(
    private var items: List<ApiSubCategory>,
    private val onClick: (ApiSubCategory) -> Unit
) : RecyclerView.Adapter<SubCategoryGridAdapter.VH>() {

    class VH(val b: ItemSubcategoryGridBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSubcategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        b.ivSubIcon.setBackgroundColor(0) // Transparent
        b.ivSubIcon.isClickable = false
        b.ivSubIcon.isFocusable = false
        b.ivSubIcon.isFocusableInTouchMode = false
        b.ivSubIcon.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sub = items[position]

        holder.b.tvSubName.text = sub.nameAr
        val url = sub.iconUrl
        if (!url.isNullOrEmpty()) {
            val html = "<html><head><style>body{margin:0;padding:0;display:flex;justify-content:center;align-items:center;height:30%;width:30%;background:transparent;} img{width:80%;height:80%;object-fit:contain;}</style></head><body><img src=\"$url\"/></body></html>"
            holder.b.ivSubIcon.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        } else {
            holder.b.ivSubIcon.loadUrl("about:blank")
        }

        holder.b.clickOverlay.setOnClickListener { onClick(sub) }
    }

    fun updateData(newItems: List<ApiSubCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
