package com.example.myapplication.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemTopCategoryBinding
import com.example.myapplication.models.Category

class TopCategoryAdapter(
    private var categories: List<Category>,
    initialSelectedId: String? = null,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<TopCategoryAdapter.ViewHolder>() {

    private var selectedPosition = initialSelectedId?.let { id ->
        categories.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    } ?: 0

    inner class ViewHolder(private val binding: ItemTopCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category, position: Int) {
            binding.tvCategoryName.text = category.name
            
            val isSelected = position == selectedPosition
            binding.vUnderline.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            
            if (isSelected) {
                binding.llCategoryRoot.setBackgroundResource(R.color.find_accent_yellow)
            } else {
                binding.llCategoryRoot.setBackgroundColor(Color.TRANSPARENT)
            }

            binding.root.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onCategoryClick(category)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    override fun getItemCount(): Int = categories.size

    fun updateData(newCategories: List<Category>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }
}
