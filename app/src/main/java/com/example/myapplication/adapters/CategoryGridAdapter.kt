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

    // The 8 sub-icon ImageViews in order
    private fun iconSlots(b: ItemCategoryGridBinding): List<ImageView> = listOf(
        b.ivSub0, b.ivSub1, b.ivSub2,
        b.ivSub3, b.ivSub4, b.ivSub5,
        b.ivSub6, b.ivSub7
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemCategoryGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        holder.b.tvCategoryTitle.text = cat.nameAr

        val slots = iconSlots(holder.b)
        val subs = cat.subCategories

        slots.forEachIndexed { i, iv ->
            val sub = subs.getOrNull(i)
            val url = sub?.iconUrl
            if (!url.isNullOrEmpty()) {
                android.util.Log.d("CategoryGrid", "Loading icon: $url")
                Glide.with(iv.context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_placeholder_sub)
                    .error(R.drawable.ic_placeholder_sub)
                    .transition(withCrossFade(250))
                    .fitCenter()
                    .into(iv)
            } else {
                // No sub at this slot — show placeholder
                iv.setImageResource(R.drawable.ic_placeholder_sub)
            }
        }

        holder.b.root.setOnClickListener { onClick(cat) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<ApiCategory>) {
        items = newItems
        notifyDataSetChanged()
    }
}
