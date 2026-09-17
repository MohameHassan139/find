package com.example.myapplication.adapters

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiCategory
import com.example.myapplication.databinding.ItemCategoryGridBinding
import com.example.myapplication.utils.CategoryIconHelper
import com.example.myapplication.utils.LocaleHelper

class CategoryGridAdapter(
    private var items: List<ApiCategory>,
    private val onClick: (ApiCategory) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.VH>() {

    class VH(val b: ItemCategoryGridBinding) : RecyclerView.ViewHolder(b.root)

    // The 5 sub-icon ImageViews in order
    private fun iconSlots(b: ItemCategoryGridBinding): List<ImageView> = listOf(
        b.ivSubAll, b.ivSub1, b.ivSub2,
        b.ivSub3, b.ivSub4
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        val isDark = isNightMode(holder.itemView.context)
        holder.b.tvCategoryTitle.text = LocaleHelper.localizedName(holder.itemView.context, cat.nameAr, cat.nameEn)

        val isOther = position == items.size - 1 || cat.nameAr.contains("اخرى") || cat.nameAr.contains("اخر") || cat.nameAr.contains("أخرى")

        if (isOther) {
            holder.b.glSubIcons.visibility = android.view.View.GONE

            val url = cat.iconUrl(isDark) ?: cat.subCategories.find { !it.iconUrl(isDark).isNullOrEmpty() }?.iconUrl(isDark)
            if (!url.isNullOrEmpty()) {
                holder.b.wvSingleIcon.visibility = android.view.View.VISIBLE
                holder.b.tvSingleDots.visibility = android.view.View.GONE
                CategoryIconHelper.loadSvg(holder.b.wvSingleIcon, url, isDark)
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
                val url = sub?.iconUrl(isDark)
                if (!url.isNullOrEmpty()) {
                    CategoryIconHelper.loadSvg(iv, url, isDark)
                } else {
                    iv.setImageDrawable(null)
                }
            }
        }

        holder.b.clickOverlay.setOnClickListener { onClick(cat) }
    }

    override fun getItemCount() = items.size

    private fun isNightMode(context: android.content.Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun updateData(newItems: List<ApiCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
