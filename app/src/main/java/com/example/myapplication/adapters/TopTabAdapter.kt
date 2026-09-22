package com.example.myapplication.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemTopTabBinding
import com.example.myapplication.utils.LocaleHelper

class TopTabAdapter(
    private var items: List<ApiCategory>,
    private var selectedId: Int,
    private val onSelected: (ApiCategory?) -> Unit
) : RecyclerView.Adapter<TopTabAdapter.VH>() {

    // Selected color is resolved dynamically from resources

    class VH(val b: ItemTopTabBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = if (position == 0) null else items[position - 1]
        val label = if (cat == null) {
            LocaleHelper.localizedName(holder.itemView.context, "الرئيسية", "Home")
        } else {
            LocaleHelper.localizedName(holder.itemView.context, cat.nameAr, cat.nameEn)
        }
        val isActive = selectedId == position

        val tvLabel = holder.b.tvLabel
        tvLabel.text = label
        tvLabel.textSize = if (isActive) 17f else 15f
        tvLabel.applyTabState(isActive)
        // Active indicator: flat bar in light mode, rounded #007AFF pill in dark mode
        if (isActive) holder.b.underline.setBackgroundResource(R.drawable.bg_tab_underline_active)
        else holder.b.underline.background = null

        holder.b.root.setOnClickListener {
            onSelected(cat)
        }
    }

    override fun getItemCount() = items.size + 1

    fun update(newItems: List<ApiCategory>, newSelectedId: Int) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }
}
