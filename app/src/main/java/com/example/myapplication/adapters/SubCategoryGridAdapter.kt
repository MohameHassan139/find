package com.example.myapplication.adapters

import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiSubCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSubcategoryGridBinding
import com.example.myapplication.utils.CategoryIconHelper
import com.example.myapplication.utils.LocaleHelper

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
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sub = items[position]
        val isDark = isNightMode(holder.itemView.context)

        holder.b.tvSubName.text = LocaleHelper.localizedName(holder.itemView.context, sub.nameAr, sub.nameEn)
        val url = sub.iconUrl(isDark) ?: sub.iconName
        CategoryIconHelper.loadSvg(holder.b.ivSubIcon, url, isDark)

        holder.b.clickOverlay.setOnClickListener { onClick(sub) }
    }

    private fun isNightMode(context: android.content.Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun updateData(newItems: List<ApiSubCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
