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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.BaseActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.AddAdActivity
import com.example.myapplication.ListingDetailActivity
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.auth.UpdateStatusRequest
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityMyAdsBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.ListingLocationFormatter
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.launch
import com.example.myapplication.utils.PriceFormatter
import com.example.myapplication.utils.RelativeTimeFormatter
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

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
        val openAddAd = {
            startWithPush(Intent(this, com.example.myapplication.AddAdActivity::class.java))
        }
        binding.llEmptyAddAction.setOnClickListener { openAddAd() }
        binding.btnEmptyAdd.setOnClickListener { openAddAd() }
        binding.ivEmptyAdd.setOnClickListener { openAddAd() }
        binding.rvAds.layoutManager = LinearLayoutManager(this)
        loadMyAds()
    }

    private fun setFilter(type: String) {
        currentFilter = type
        val activeBlue = androidx.core.content.ContextCompat.getColor(this, R.color.find_active_blue)
        val textPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        val textInactive = androidx.core.content.ContextCompat.getColor(this, R.color.tab_inactive_text)
        val bgGrey = androidx.core.content.ContextCompat.getColor(this, R.color.bg_switcher)
        val strokeField = androidx.core.content.ContextCompat.getColor(this, R.color.stroke_field)
        val density = resources.displayMetrics.density
        val activeStrokePx = (2 * density).toInt()
        val inactiveStrokePx = (1 * density).toInt()

        if (type == "offer") {
            binding.btnFilterOffer.apply {
                strokeWidth = activeStrokePx
                strokeColor = android.content.res.ColorStateList.valueOf(activeBlue)
                setTextColor(textPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
            binding.btnFilterRequest.apply {
                strokeWidth = inactiveStrokePx
                strokeColor = android.content.res.ColorStateList.valueOf(strokeField)
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
                strokeWidth = inactiveStrokePx
                strokeColor = android.content.res.ColorStateList.valueOf(strokeField)
                setTextColor(textInactive)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allAds.filter { it.listingType == currentFilter }
        if (filtered.isEmpty()) {
            showEmpty(getString(R.string.empty_no_ads))
        } else {
            binding.root.findViewById<View>(R.id.emptyView).visibility = View.GONE
            binding.rvAds.visibility = View.VISIBLE
            binding.rvAds.adapter = MyAdsAdapter(filtered.toMutableList(),
                onDelete = { item -> confirmDelete(item) },
                onToggle = { item, visible -> toggleVisible(item, visible) },
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
            when (val result = AppContainer.listings.myListings()) {
                is ApiResult.Success -> {
                    allAds = result.data
                    binding.progressBar.visibility = View.GONE
                    if (allAds.isEmpty()) showEmpty(getString(R.string.empty_no_ads))
                    else applyFilter()
                }
                is ApiResult.HttpError -> showEmpty("تعذر التحميل: ${result.code}")
                is ApiResult.NetworkError -> showEmpty("تعذر الاتصال بالخادم")
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
            when (AppContainer.listings.delete(item.id)) {
                is ApiResult.Success -> {
                    allAds = allAds.filter { it.id != item.id }
                    applyFilter()
                    this@MyAdsActivity.toast(R.string.deleted)
                }
                is ApiResult.HttpError -> this@MyAdsActivity.toast(R.string.error_delete_failed)
                is ApiResult.NetworkError -> this@MyAdsActivity.toast(R.string.error_connection_failed)
            }
        }
    }

    /**
     * Hide/unhide. PATCHes only `{"status": "active"|"hidden"}` — the contract
     * iOS uses; the previous `{"is_active": …}` body was ignored by the backend.
     *
     * Optimistic like iOS's MyAdsView: flip local state immediately, then revert
     * the row if the call fails, so the switch can't sit in a state the server
     * never accepted.
     */
    private fun toggleVisible(item: ListingItem, visible: Boolean) {
        if (TokenManager.getToken(this) == null) return

        val target = if (visible) UpdateStatusRequest.ACTIVE else UpdateStatusRequest.HIDDEN
        updateLocalStatus(item.id, target)

        lifecycleScope.launch {
            val succeeded = AppContainer.listings.setVisible(item.id, visible).isSuccess

            if (!succeeded) {
                val reverted = if (target == UpdateStatusRequest.HIDDEN) {
                    UpdateStatusRequest.ACTIVE
                } else {
                    UpdateStatusRequest.HIDDEN
                }
                updateLocalStatus(item.id, reverted)
                this@MyAdsActivity.toast(R.string.error_generic)
            }
        }
    }

    private fun updateLocalStatus(id: String, status: String) {
        allAds = allAds.map { if (it.id == id) it.copy(status = status) else it }
        applyFilter()
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
        val flCardImage: View = view.findViewById(R.id.flCardImage)
        val vpCardImages: androidx.viewpager2.widget.ViewPager2 = view.findViewById(R.id.vpCardImages)
        val llCardDots: android.widget.LinearLayout = view.findViewById(R.id.llCardDots)
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

        var currentImageIndex = 0
        var imageUrls: List<String> = emptyList()
        var pageCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null
        val galleryAdapter = com.example.myapplication.adapters.ListingsAdapter.CardImageAdapter()
        var galleryUrls: List<String>? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_my_ad, parent, false)
        val holder = VH(v)
        holder.flCardImage.clipToOutline = true
        holder.vpCardImages.layoutDirection = View.LAYOUT_DIRECTION_LTR
        holder.llCardDots.layoutDirection = View.LAYOUT_DIRECTION_LTR
        holder.vpCardImages.adapter = holder.galleryAdapter
        (holder.vpCardImages.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val images = item.images ?: emptyList()
        val context = holder.itemView.context

        holder.tvTitle.text = item.title ?: "—"
        holder.tvPrice.text = PriceFormatter.display(item.price)
        holder.tvSellerName.text = item.seller?.name ?: ""
        holder.tvLocation.text = ListingLocationFormatter.cityOnly(item.city)
        holder.tvTime.text = RelativeTimeFormatter.format(context, item.createdAt)

        val openDetail = {
            val allIds = ArrayList(items.map { it.id })
            val index = allIds.indexOf(item.id)
            val intent = Intent(context, ListingDetailActivity::class.java).apply {
                putExtra(ListingDetailActivity.EXTRA_LISTING_ID, item.id)
                putStringArrayListExtra(ListingDetailActivity.EXTRA_SIBLING_IDS, allIds)
                putExtra(ListingDetailActivity.EXTRA_CURRENT_INDEX, index)
            }
            if (context is BaseActivity) {
                context.startWithPush(intent)
            } else {
                context.startActivity(intent)
            }
        }

        // Swipeable gallery setup
        if (holder.imageUrls != images) {
            holder.imageUrls = images
            holder.currentImageIndex = 0
        }
        setupCardGallery(holder, openDetail)

        // Seller avatar
        val avatar = item.seller?.avatar
        if (!avatar.isNullOrEmpty()) {
            Glide.with(holder.ivSellerAvatar.context).load(avatar)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(holder.ivSellerAvatar)
        }

        // Visibility comes from `status` alone. The old expression OR'd in an
        // `isActive` field that defaulted to true and was never sent by the
        // API, so every ad — hidden ones included — rendered as visible.
        val isCurrentlyActive = item.isVisible
        holder.switchActive.setOnCheckedChangeListener(null)
        holder.switchActive.isChecked = isCurrentlyActive
        
        holder.itemView.setOnClickListener { openDetail() }
        
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
                putExtra(AddAdActivity.EXTRA_PRICE, item.price?.let(PriceFormatter::plain) ?: "")
                putExtra(AddAdActivity.EXTRA_CITY, item.city ?: "")
                putExtra(AddAdActivity.EXTRA_TYPE, item.listingType ?: "offer")
                putStringArrayListExtra(AddAdActivity.EXTRA_IMAGES, ArrayList(item.images ?: emptyList()))
            }
            onEdit(intent)
        }
    }

    private fun setupCardGallery(holder: VH, onImageClick: () -> Unit) {
        val images = holder.imageUrls
        holder.galleryAdapter.onImageClick = onImageClick

        if (holder.galleryUrls == images) return
        holder.galleryUrls = images

        holder.pageCallback?.let { holder.vpCardImages.unregisterOnPageChangeCallback(it) }
        holder.pageCallback = null

        if (images.isEmpty()) {
            holder.galleryAdapter.submit(emptyList())
            holder.vpCardImages.visibility = View.GONE
            holder.llCardDots.visibility = View.GONE
            holder.ivImage.visibility = View.VISIBLE
            holder.ivImage.setImageResource(R.drawable.ic_photo_placeholder)
            return
        }

        holder.ivImage.visibility = View.GONE
        holder.vpCardImages.visibility = View.VISIBLE
        holder.vpCardImages.isUserInputEnabled = images.size > 1
        holder.galleryAdapter.submit(images)

        val startIndex = holder.currentImageIndex.coerceIn(0, images.size - 1)
        holder.currentImageIndex = startIndex
        holder.vpCardImages.setCurrentItem(startIndex, false)

        buildDots(holder.llCardDots, images.size, startIndex, holder.vpCardImages)

        val callback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                holder.currentImageIndex = position
                updateDots(holder.llCardDots, position)
            }
        }
        holder.vpCardImages.registerOnPageChangeCallback(callback)
        holder.pageCallback = callback
    }

    private fun buildDots(container: android.widget.LinearLayout, count: Int, activePosition: Int, pager: androidx.viewpager2.widget.ViewPager2) {
        container.removeAllViews()
        if (count <= 1) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val density = container.resources.displayMetrics.density
        val dotSize = (6 * density).toInt()
        val dotMargin = (2 * density).toInt()

        for (i in 0 until count) {
            val dot = View(container.context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                setBackgroundResource(
                    if (i == activePosition) R.drawable.bg_carousel_dot_active
                    else R.drawable.bg_carousel_dot_inactive
                )
                scaleX = if (i == activePosition) 1.25f else 1.0f
                scaleY = if (i == activePosition) 1.25f else 1.0f
                setOnClickListener { pager.setCurrentItem(i, true) }
            }
            container.addView(dot)
        }
    }

    private fun updateDots(container: android.widget.LinearLayout, activePosition: Int) {
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val isActive = (i == activePosition)
            dot.setBackgroundResource(
                if (isActive) R.drawable.bg_carousel_dot_active
                else R.drawable.bg_carousel_dot_inactive
            )
            dot.animate()
                .scaleX(if (isActive) 1.25f else 1.0f)
                .scaleY(if (isActive) 1.25f else 1.0f)
                .setDuration(200)
                .start()
        }
    }

    override fun getItemCount() = items.size
}
