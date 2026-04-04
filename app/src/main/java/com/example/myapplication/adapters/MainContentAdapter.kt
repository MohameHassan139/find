package com.example.myapplication.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemCategoryGridBinding
import com.example.myapplication.databinding.ItemSubcategoryGridBinding
import com.example.myapplication.models.Category

class MainContentAdapter(
    private var items: List<Category>,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_SUBCATEGORY = 1
        private const val ICONS_BASE = "https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/subCatigory/"

        fun iconUrl(icon: String?): String? {
            if (icon.isNullOrEmpty()) return null
            return if (icon.contains(".")) "$ICONS_BASE$icon" else "$ICONS_BASE$icon.png"
        }
    }

    override fun getItemViewType(position: Int) =
        if (items[position].subItems.isNotEmpty() || items[position].isHome) TYPE_CATEGORY
        else TYPE_SUBCATEGORY

    inner class CategoryVH(private val b: ItemCategoryGridBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(cat: Category) {
            b.tvCategoryTitle.text = cat.name
            b.root.setOnClickListener { onItemClick(cat) }

            // Load up to 6 sub-category icons into the 3x3 grid
            val grid = b.glSubItems
            val count = grid.childCount
            val subs = cat.subItems.take(count)

            for (i in 0 until count) {
                val iv = grid.getChildAt(i) as? ImageView ?: continue
                val sub = subs.getOrNull(i)
                val url = iconUrl(sub?.iconUrl)
                if (url != null) {
                    Glide.with(iv.context).load(url)
                        .placeholder(R.drawable.ic_placeholder_sub)
                        .error(R.drawable.ic_placeholder_sub)
                        .into(iv)
                } else {
                    iv.setImageResource(
                        if (i == count - 1 && subs.size >= count) R.drawable.ic_more_dots
                        else R.drawable.ic_placeholder_sub
                    )
                }
            }
        }
    }

    inner class SubCategoryVH(private val b: ItemSubcategoryGridBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(cat: Category) {
            b.tvSubCategoryName.text = cat.name
            b.root.setOnClickListener { onItemClick(cat) }
            val url = iconUrl(cat.iconUrl)
            if (url != null) {
                Glide.with(b.ivSubCategoryIcon.context).load(url)
                    .placeholder(R.drawable.ic_placeholder_sub)
                    .error(R.drawable.ic_placeholder_sub)
                    .into(b.ivSubCategoryIcon)
            } else {
                b.ivSubCategoryIcon.setImageResource(R.drawable.ic_placeholder_sub)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CATEGORY)
            CategoryVH(ItemCategoryGridBinding.inflate(inf, parent, false))
        else
            SubCategoryVH(ItemSubcategoryGridBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is CategoryVH -> holder.bind(item)
            is SubCategoryVH -> holder.bind(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Category>) {
        items = newItems
        notifyDataSetChanged()
    }
}
