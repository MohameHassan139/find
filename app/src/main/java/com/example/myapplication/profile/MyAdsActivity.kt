package com.example.myapplication.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.myapplication.MenuActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.AddAdActivity
import com.example.myapplication.ListingDetailActivity
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.auth.ToggleActiveRequest
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityMyAdsBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MyAdsActivity : BaseActivity() {

    private lateinit var binding: ActivityMyAdsBinding
    private var allAds: List<ListingItem> = emptyList()
    private var currentFilter = "offer"
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    // Reload ads when returning from edit screen
    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadMyAds()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMyAdsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
        binding.btnFilterOffer.setOnClickListener { setFilter("offer") }
        binding.btnFilterRequest.setOnClickListener { setFilter("request") }
        binding.rvAds.layoutManager = LinearLayoutManager(this)
        loadMyAds()
    }

    private fun setFilter(type: String) {
        currentFilter = type
        val activeBlue = androidx.core.content.ContextCompat.getColor(this, R.color.chats_chip_selected_border)
        val textPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.black)
        val textInactive = androidx.core.content.ContextCompat.getColor(this, R.color.tab_inactive_text)
        val bgGrey = androidx.core.content.ContextCompat.getColor(this, R.color.bg_switcher)
        val density = resources.displayMetrics.density
        val activeStrokePx = (1 * density).toInt()

        if (type == "offer") {
            binding.btnFilterOffer.apply {
                strokeWidth = activeStrokePx
                strokeColor = android.content.res.ColorStateList.valueOf(activeBlue)
                setTextColor(textPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
            binding.btnFilterRequest.apply {
                strokeWidth = 0
                strokeColor = null
                setTextColor(textInactive)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
        } else {
            binding.btnFilterRequest.apply {
                strokeWidth = activeStrokePx
                strokeColor = android.content.res.ColorStateList.valueOf(activeBlue)
                setTextColor(textPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
            binding.btnFilterOffer.apply {
                strokeWidth = 0
                strokeColor = null
                setTextColor(textInactive)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allAds.filter { it.listingType == currentFilter }
        if (filtered.isEmpty()) {
            showEmpty("لا توجد إعلانات")
        } else {
            binding.root.findViewById<View>(R.id.emptyView).visibility = View.GONE
            binding.rvAds.visibility = View.VISIBLE
            binding.rvAds.adapter = MyAdsAdapter(filtered.toMutableList(),
                onDelete = { item -> confirmDelete(item) },
                onToggle = { item, active -> toggleActive(item, active) },
                onEdit = { intent -> 
                    editLauncher.launch(intent)
                    applyPushTransition()
                }
            )
        }
    }

    private fun loadMyAds() {
        if (TokenManager.getToken(this) == null) { showEmpty("سجّل دخولك أولاً"); return }
        showLoading()
        lifecycleScope.launch {
            try {
                val collected = mutableListOf<ListingItem>()
                var page = 1
                var lastPage = 1
                do {
                    val response = RetrofitClient.build(this@MyAdsActivity).getMyListings(page = page)
                    if (!response.isSuccessful) {
                        showEmpty("تعذر التحميل: ${response.code()}")
                        return@launch
                    }
                    val data = response.body()?.data
                    collected += data?.items ?: emptyList()
                    lastPage = data?.pagination?.lastPage ?: 1
                    page += 1
                } while (page <= lastPage && page <= 20) // safety cap, matches per_page=50

                allAds = collected
                binding.progressBar.visibility = View.GONE
                if (allAds.isEmpty()) showEmpty("لا توجد إعلانات")
                else applyFilter()
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
        if (TokenManager.getToken(this) == null) return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@MyAdsActivity).deleteListing(item.id)
                if (response.isSuccessful || response.code() == 204) {
                    allAds = allAds.filter { it.id != item.id }
                    applyFilter()
                    Toast.makeText(this@MyAdsActivity, getString(R.string.kt_str_3569a87c), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MyAdsActivity, getString(R.string.kt_str_eb88417b), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyAdsActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleActive(item: ListingItem, active: Boolean) {
        if (TokenManager.getToken(this) == null) return
        lifecycleScope.launch {
            try {
                RetrofitClient.build(this@MyAdsActivity).toggleListing(
                    item.id, ToggleActiveRequest(active)
                )
            } catch (_: Exception) {}
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.root.findViewById<View>(R.id.emptyView).visibility = View.GONE
        binding.rvAds.visibility = View.GONE
    }

    private fun showEmpty(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.rvAds.visibility = View.GONE
        binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
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
        val btnDelete: View = view.findViewById(R.id.btnDelete)
        val switchActive: SwitchCompat = view.findViewById(R.id.switchActive)
        val tvActiveLabel: TextView = view.findViewById(R.id.tvActiveLabel)
        val btnPrev: ImageView = view.findViewById(R.id.btnPrev)
        val btnNext: ImageView = view.findViewById(R.id.btnNext)
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
            formatted
        } else "—"
        holder.tvSellerName.text = item.seller?.name ?: ""
        holder.tvLocation.text = item.region?.nameAr ?: item.city ?: ""
        holder.tvTime.text = formatTime(item.createdAt)

        // Image navigation
        holder.imageIndex = 0
        fun loadImage() {
            if (images.isEmpty()) {
                holder.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
                holder.btnPrev.visibility = View.GONE
                holder.btnNext.visibility = View.GONE
            } else {
                Glide.with(holder.ivImage.context).load(images[holder.imageIndex])
                    .placeholder(R.drawable.ic_photo_placeholder).centerCrop().into(holder.ivImage)
                val showArrows = images.size > 1
                holder.btnPrev.visibility = if (showArrows) View.VISIBLE else View.GONE
                holder.btnNext.visibility = if (showArrows) View.VISIBLE else View.GONE
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

        // Toggle — use status field ("active"/"hidden") since API returns status not is_active
        val isCurrentlyActive = item.isActive || item.status == "active"
        holder.switchActive.setOnCheckedChangeListener(null)
        holder.switchActive.isChecked = isCurrentlyActive
        
        val context = holder.itemView.context
        
        holder.itemView.setOnClickListener {
            val intent = Intent(context, ListingDetailActivity::class.java).apply {
                putExtra(ListingDetailActivity.EXTRA_LISTING_ID, item.id)
            }
            if (context is BaseActivity) {
                context.startWithPush(intent)
            } else {
                context.startActivity(intent)
            }
        }
        
        val greenColor = androidx.core.content.ContextCompat.getColor(context, R.color.toggle_active_green)
        val greyColor = androidx.core.content.ContextCompat.getColor(context, R.color.switch_inactive_track)

        if (isCurrentlyActive) {
            holder.tvActiveLabel.text = context.getString(R.string.ad_visible)
            holder.tvActiveLabel.setTextColor(greenColor)
            holder.switchActive.trackTintList = android.content.res.ColorStateList.valueOf(greenColor)
        } else {
            holder.tvActiveLabel.text = context.getString(R.string.ad_hidden)
            holder.tvActiveLabel.setTextColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary)
            )
            holder.switchActive.trackTintList = android.content.res.ColorStateList.valueOf(greyColor)
        }
        
        holder.switchActive.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                holder.tvActiveLabel.text = context.getString(R.string.ad_visible)
                holder.tvActiveLabel.setTextColor(greenColor)
                holder.switchActive.trackTintList = android.content.res.ColorStateList.valueOf(greenColor)
            } else {
                holder.tvActiveLabel.text = context.getString(R.string.ad_hidden)
                holder.tvActiveLabel.setTextColor(
                    androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary)
                )
                holder.switchActive.trackTintList = android.content.res.ColorStateList.valueOf(greyColor)
            }
            onToggle(item, checked)
        }

        holder.btnDelete.setOnClickListener { onDelete(item) }
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
