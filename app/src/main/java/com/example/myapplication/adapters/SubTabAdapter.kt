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
import com.example.myapplication.widgets.StrokeTextView

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

        val tvLabel = holder.b.tvLabel
        tvLabel.text = label
        tvLabel.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        tvLabel.textSize = 14f
        
        if (tvLabel is StrokeTextView) {
            tvLabel.isStrokeEnabled = isActive
            tvLabel.setTextColor(if (isActive) Color.WHITE else holder.itemView.context.getColor(R.color.find_gray_text))
        } else {
            val textColor = if (isActive) {
                holder.itemView.context.getColor(R.color.text_primary)
            } else {
                holder.itemView.context.getColor(R.color.find_gray_text)
            }
            tvLabel.setTextColor(textColor)
        }
        val activeColor = holder.itemView.context.getColor(R.color.find_active_blue)
        holder.b.underline.setBackgroundColor(if (isActive) activeColor else Color.TRANSPARENT)

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
