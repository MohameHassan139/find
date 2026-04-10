package com.example.myapplication.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiSubCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemTopTabBinding
import com.example.myapplication.utils.LocaleHelper

class SubTabAdapter(
    private var items: List<ApiSubCategory>,
    private var selectedId: Int?,
    private val onSelected: (ApiSubCategory?) -> Unit
) : RecyclerView.Adapter<SubTabAdapter.VH>() {

    private val gold = "#C8A96E".toColorInt()
    private val gray = "#888888".toColorInt()

    class VH(val b: ItemTopTabBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sub = items[position]
        val label = LocaleHelper.localizedName(holder.itemView.context, sub.nameAr, sub.nameEn)
        val isActive = selectedId == position

        holder.b.tvLabel.text = label
        holder.b.tvLabel.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.b.tvLabel.textSize = 14f
        holder.b.tvLabel.setTextColor(if (isActive) Color.BLACK else gray)
        holder.b.underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)

        holder.b.root.setOnClickListener {
            onSelected(sub)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<ApiSubCategory>, newSelectedId: Int?) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }
}
