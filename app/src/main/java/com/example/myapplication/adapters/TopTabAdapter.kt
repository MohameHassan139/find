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

    private val gold = "#C8A96E".toColorInt()

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

        holder.b.tvLabel.text = label
        holder.b.tvLabel.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.b.tvLabel.textSize = if (isActive) 17f else 15f
        holder.b.underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

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
