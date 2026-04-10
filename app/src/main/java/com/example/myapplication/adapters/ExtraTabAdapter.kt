package com.example.myapplication.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiFilterOption
import com.example.myapplication.databinding.ItemTopTabBinding
import com.example.myapplication.utils.LocaleHelper

class ExtraTabAdapter(
    private var items: List<ApiFilterOption>,
    private var selectedId: Int?,
    private val onSelected: (ApiFilterOption?) -> Unit
) : RecyclerView.Adapter<ExtraTabAdapter.VH>() {

    private val gold = "#C8A96E".toColorInt()
    private val gray = "#888888".toColorInt()

    class VH(val b: ItemTopTabBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTopTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val opt = items[position]
        val label = LocaleHelper.localizedName(holder.itemView.context, opt.nameAr, opt.nameEn)
        val isActive = selectedId == position

        holder.b.tvLabel.text = label
        holder.b.tvLabel.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.b.tvLabel.textSize = 13f
        holder.b.tvLabel.setTextColor(if (isActive) Color.BLACK else gray)
        holder.b.underline.setBackgroundColor(if (isActive) gold else Color.TRANSPARENT)
        
        // No icons for extras

        holder.b.root.setOnClickListener {
            onSelected(opt)
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<ApiFilterOption>, newSelectedId: Int?) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }
}
