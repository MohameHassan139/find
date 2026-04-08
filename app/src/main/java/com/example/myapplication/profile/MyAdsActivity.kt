package com.example.myapplication.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.myapplication.MenuActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.AddAdActivity
import com.example.myapplication.R
import com.example.myapplication.auth.AuthRetrofitClient
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.auth.ToggleActiveRequest
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityMyAdsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MyAdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyAdsBinding
    private var allAds: List<ListingItem> = emptyList()
    private var currentFilter = "offer"

    // Reload ads when returning from edit screen
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadMyAds()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyAdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        binding.btnFilterOffer.setOnClickListener { setFilter("offer") }
        binding.btnFilterRequest.setOnClickListener { setFilter("request") }
        binding.rvAds.layoutManager = LinearLayoutManager(this)
        loadMyAds()
    }

    private fun setFilter(type: String) {
        currentFilter = type
        val isPrimary = "#C8A96E"
        val isGray = "#EEEEEE"
        if (type == "offer") {
            binding.btnFilterOffer.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(isPrimary))
            binding.btnFilterOffer.setTextColor(android.graphics.Color.WHITE)
            binding.btnFilterRequest.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(isGray))
            binding.btnFilterRequest.setTextColor(android.graphics.Color.parseColor("#888888"))
        } else {
            binding.btnFilterRequest.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(isPrimary))
            binding.btnFilterRequest.setTextColor(android.graphics.Color.WHITE)
            binding.btnFilterOffer.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(isGray))
            binding.btnFilterOffer.setTextColor(android.graphics.Color.parseColor("#888888"))
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allAds.filter { it.listingType == currentFilter }
        if (filtered.isEmpty()) {
            showEmpty("لا توجد إعلانات")
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvAds.visibility = View.VISIBLE
            binding.rvAds.adapter = MyAdsAdapter(filtered.toMutableList(),
                onDelete = { item -> confirmDelete(item) },
                onToggle = { item, active -> toggleActive(item, active) },
                onEdit = { intent -> editLauncher.launch(intent) }
            )
        }
    }

    private fun loadMyAds() {
        val token = TokenManager.getToken(this) ?: run { showEmpty("سجّل دخولك أولاً"); return }
        showLoading()
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.getMyListings("Bearer $token")
                if (response.isSuccessful) {
                    allAds = response.body()?.data ?: emptyList()
                    binding.progressBar.visibility = View.GONE
                    if (allAds.isEmpty()) showEmpty("لا توجد إعلانات")
                    else applyFilter()
                } else {
                    showEmpty("تعذر التحميل: ${response.code()}")
                }
            } catch (e: Exception) {
                showEmpty("تعذر الاتصال بالخادم")
            }
        }
    }

    private fun confirmDelete(item: ListingItem) {
        AlertDialog.Builder(this)
            .setMessage("هل تريد حذف هذا الإعلان؟")
            .setPositiveButton("حذف") { _, _ -> deleteAd(item) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteAd(item: ListingItem) {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                val response = AuthRetrofitClient.authService.deleteListing("Bearer $token", item.id)
                if (response.isSuccessful || response.code() == 204) {
                    allAds = allAds.filter { it.id != item.id }
                    applyFilter()
                    Toast.makeText(this@MyAdsActivity, "تم الحذف", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MyAdsActivity, "فشل الحذف", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyAdsActivity, "تعذر الاتصال", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleActive(item: ListingItem, active: Boolean) {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                AuthRetrofitClient.authService.toggleListing(
                    "Bearer $token", item.id, ToggleActiveRequest(active)
                )
            } catch (_: Exception) {}
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.rvAds.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvAds.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = msg
    }
}

class MyAdsAdapter(
    private val items: MutableList<ListingItem>,
    private val onDelete: (ListingItem) -> Unit,
    private val onToggle: (ListingItem, Boolean) -> Unit,
    private val onEdit: (Intent) -> Unit
) : RecyclerView.Adapter<MyAdsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivImage)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvSellerName: TextView = view.findViewById(R.id.tvSellerName)
        val ivSellerAvatar: ImageView = view.findViewById(R.id.ivSellerAvatar)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val btnEdit: View = view.findViewById(R.id.btnEdit)
        val btnMessages: View = view.findViewById(R.id.btnMessages)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
        val switchActive: SwitchCompat = view.findViewById(R.id.switchActive)
        val tvActiveLabel: TextView = view.findViewById(R.id.tvActiveLabel)
        val btnPrev: TextView = view.findViewById(R.id.btnPrev)
        val btnNext: TextView = view.findViewById(R.id.btnNext)
        val tvCounter: TextView = view.findViewById(R.id.tvImageCounter)
        var imageIndex = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_my_ad, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val images = item.images ?: emptyList()

        holder.tvTitle.text = item.title ?: "—"
        val priceVal = item.price
        holder.tvPrice.text = if (priceVal != null) {
            val formatted = if (priceVal % 1 == 0.0) priceVal.toLong().toString() else priceVal.toString()
            "$formatted ر.س"
        } else "—"
        holder.tvSellerName.text = item.seller?.name ?: ""
        holder.tvLocation.text = "📍 ${item.region?.nameAr ?: item.city ?: ""}"
        holder.tvTime.text = "🕐 ${formatTime(item.createdAt)}"

        // Image navigation
        holder.imageIndex = 0
        fun loadImage() {
            if (images.isEmpty()) {
                holder.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
                holder.btnPrev.visibility = View.GONE
                holder.btnNext.visibility = View.GONE
                holder.tvCounter.visibility = View.GONE
            } else {
                Glide.with(holder.ivImage.context).load(images[holder.imageIndex])
                    .placeholder(R.drawable.ic_photo_placeholder).centerCrop().into(holder.ivImage)
                val showArrows = images.size > 1
                holder.btnPrev.visibility = if (showArrows) View.VISIBLE else View.GONE
                holder.btnNext.visibility = if (showArrows) View.VISIBLE else View.GONE
                if (showArrows) {
                    holder.tvCounter.visibility = View.VISIBLE
                    holder.tvCounter.text = "${holder.imageIndex + 1}/${images.size}"
                }
            }
        }
        loadImage()

        holder.btnPrev.setOnClickListener {
            if (images.isNotEmpty()) {
                holder.imageIndex = (holder.imageIndex - 1 + images.size) % images.size
                loadImage()
            }
        }
        holder.btnNext.setOnClickListener {
            if (images.isNotEmpty()) {
                holder.imageIndex = (holder.imageIndex + 1) % images.size
                loadImage()
            }
        }

        // Seller avatar
        val avatar = item.seller?.avatar
        if (!avatar.isNullOrEmpty()) {
            Glide.with(holder.ivSellerAvatar.context).load(avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(holder.ivSellerAvatar)
        }

        // Toggle — use status field ("active"/"inactive") since API returns status not is_active
        val isCurrentlyActive = item.isActive || item.status == "active"
        holder.switchActive.setOnCheckedChangeListener(null)
        holder.switchActive.isChecked = isCurrentlyActive
        holder.tvActiveLabel.text = if (isCurrentlyActive) "ظاهر" else "مخفي"
        holder.switchActive.setOnCheckedChangeListener { _, checked ->
            holder.tvActiveLabel.text = if (checked) "ظاهر" else "مخفي"
            onToggle(item, checked)
        }

        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.btnMessages.setOnClickListener {
            val intent = Intent(holder.itemView.context,
                com.example.myapplication.chat.ui.conversations.ListingConversationsActivity::class.java).apply {
                putExtra(com.example.myapplication.chat.ui.conversations.ListingConversationsActivity.EXTRA_LISTING_ID, item.id)
                putExtra(com.example.myapplication.chat.ui.conversations.ListingConversationsActivity.EXTRA_LISTING_TITLE, item.title ?: "رسائل الإعلان")
            }
            holder.itemView.context.startActivity(intent)
        }
        holder.btnEdit.setOnClickListener {
            val intent = Intent(holder.itemView.context, AddAdActivity::class.java).apply {
                putExtra(AddAdActivity.EXTRA_LISTING_ID, item.id)
                putExtra(AddAdActivity.EXTRA_TITLE, item.title ?: "")
                putExtra(AddAdActivity.EXTRA_DESC, item.description ?: "")
                putExtra(AddAdActivity.EXTRA_PRICE, item.price?.let {
                    if (it % 1 == 0.0) it.toLong().toString() else it.toString()
                } ?: "")
                putExtra(AddAdActivity.EXTRA_CITY, item.city ?: "")
                putExtra(AddAdActivity.EXTRA_TYPE, item.listingType ?: "offer")
                putStringArrayListExtra(AddAdActivity.EXTRA_IMAGES, ArrayList(item.images ?: emptyList()))
            }
            onEdit(intent)
        }
    }

    override fun getItemCount() = items.size

    private fun formatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(dateStr) ?: return dateStr
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60 -> "الآن"
                diff < 3600 -> "${diff / 60} دقيقة"
                diff < 86400 -> "${diff / 3600} ساعة"
                diff < 2592000 -> "${diff / 86400} يوم"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { dateStr }
    }
}
