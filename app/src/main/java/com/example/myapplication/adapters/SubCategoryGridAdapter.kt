package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.ApiSubCategory
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemSubcategoryGridBinding

/**
 * Displays sub-categories with a "الكل" card at position 0 (represented as null).
 * onClick receives null for "الكل", or the ApiSubCategory for any real item.
 */
class SubCategoryGridAdapter(
    private var items: List<ApiSubCategory>,
    private val onClick: (ApiSubCategory?) -> Unit   // null = "الكل"
) : RecyclerView.Adapter<SubCategoryGridAdapter.VH>() {

    class VH(val b: ItemSubcategoryGridBinding) : RecyclerView.ViewHolder(b.root)

    // position 0 is always "الكل", real items start at 1
    private fun subAt(position: Int): ApiSubCategory? =
        if (position == 0) null else items[position - 1]

    override fun getItemCount() = items.size + 1   // +1 for "الكل"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSubcategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val sub = subAt(position)

        if (sub == null) {
            // "الكل" card
            holder.b.tvSubName.text = "الكل"
            holder.b.ivSubIcon.setImageResource(R.drawable.ic_placeholder_sub)
        } else {
            holder.b.tvSubName.text = sub.nameAr
            val url = sub.iconUrl
            if (!url.isNullOrEmpty()) {
                Glide.with(holder.b.ivSubIcon.context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_placeholder_sub)
                    .error(R.drawable.ic_placeholder_sub)
                    .transition(withCrossFade(250))
                    .fitCenter()
                    .into(holder.b.ivSubIcon)
            } else {
                holder.b.ivSubIcon.setImageResource(R.drawable.ic_placeholder_sub)
            }
        }

        holder.b.root.setOnClickListener { onClick(sub) }
    }

    fun updateData(newItems: List<ApiSubCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
