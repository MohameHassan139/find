package com.example.myapplication

import com.example.myapplication.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityListingDetailBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.ListingLocationFormatter
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.ModerationState
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.launch
import com.example.myapplication.utils.PriceFormatter
import com.example.myapplication.utils.RelativeTimeFormatter
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult
import com.example.myapplication.chat.ui.chat.ChatActivity

class ListingDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityListingDetailBinding
    private var isFavorited = false
    private var currentListingId = ""
    private var siblingIds: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = -1
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private var currentListing: DetailListing? = null
    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_SIBLING_IDS = "sibling_ids"

        /**
         * Extracts a listing ID from Intent extras or Uri deep link data.
         * Supports:
         * - Extras: EXTRA_LISTING_ID, "id", "listingId"
         * - Web URLs: https://finds.sa/listing/{id}, https://www.finds.sa/listings/{id}, etc.
         * - Custom schemes: finds://listing/{id}, finds://listings/{id}, find://listing/{id}
         * - Query parameters: ?id={id}, ?listing_id={id}, ?listingId={id}
         */
        fun extractListingId(intent: Intent?): String? {
            if (intent == null) return null

            // 1. Direct extras
            val extraId = intent.getStringExtra(EXTRA_LISTING_ID)
                ?: intent.getStringExtra("id")
                ?: intent.getStringExtra("listingId")
            if (!extraId.isNullOrBlank()) return extraId.trim()

            val extraIntId = intent.getIntExtra(EXTRA_LISTING_ID, -1).takeIf { it > 0 }
                ?: intent.getIntExtra("id", -1).takeIf { it > 0 }
            if (extraIntId != null) return extraIntId.toString()

            // 2. Intent data Uri
            val data: Uri = intent.data ?: return null

            // 2a. Query parameter: ?id=123 or ?listing_id=123
            val paramId = data.getQueryParameter("id")
                ?: data.getQueryParameter("listing_id")
                ?: data.getQueryParameter("listingId")
            if (!paramId.isNullOrBlank()) return paramId.trim()

            // 2b. Path segments: /listing/123 or /listings/123
            val segments = data.pathSegments
            val listingIdx = segments.indexOfFirst {
                it.equals("listing", ignoreCase = true) || it.equals("listings", ignoreCase = true)
            }
            if (listingIdx != -1 && listingIdx + 1 < segments.size) {
                val segmentId = segments[listingIdx + 1].trim()
                if (segmentId.isNotEmpty()) return segmentId
            }

            // 2c. Custom scheme where host is "listing" or "listings": e.g. finds://listing/123
            val host = data.host
            if (host != null && (host.equals("listing", ignoreCase = true) || host.equals("listings", ignoreCase = true))) {
                val firstSeg = segments.firstOrNull()?.trim()
                if (!firstSeg.isNullOrEmpty()) return firstSeg
            }

            // 2d. Fallback: last segment if it's not a generic word
            val last = segments.lastOrNull()?.trim()
            if (!last.isNullOrEmpty() &&
                !last.equals("listing", ignoreCase = true) &&
                !last.equals("listings", ignoreCase = true)
            ) {
                return last
            }

            return null
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityListingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(appBarId = R.id.llAppBar)

        // Search bar click
        binding.llHomeSearchContainer.setOnClickListener {
            startWithPush(Intent(this, SearchActivity::class.java))
        }

        // HomeHeaderHelper wires category tabs (rvHomeTopTabs) and search container
        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            handleBackNavigation()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
        val listingId = extractListingId(intent) ?: run {
            if (isTaskRoot) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
            return
        }
        currentListingId = listingId
        siblingIds = intent.getStringArrayListExtra(EXTRA_SIBLING_IDS) ?: arrayListOf()
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, -1)
        if (currentIndex == -1 && siblingIds.isNotEmpty()) {
            currentIndex = siblingIds.indexOf(listingId)
        }
        updateNavigationArrows()
        loadListing(listingId)
        if (TokenManager.isLoggedIn(this)) {
            checkIsFavorited(listingId)
            lifecycleScope.launch { ModerationState.refresh() }
        }

        val toggleFavorite: (View) -> Unit = {
            if (!TokenManager.isLoggedIn(this)) {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
            } else {
                val newState = !isFavorited
                isFavorited = newState
                updateFavoriteIcon()
                syncFavorite(currentListingId, newState)
            }
        }
        binding.btnFavoriteDetail.setOnClickListener(toggleFavorite)
        binding.ivFavoriteDetail.setOnClickListener(toggleFavorite)
        binding.btnShareDetail.setOnClickListener {
            shareListing()
        }
    }

    // ── Load listing ──────────────────────────────────────────────────────────

    private fun loadListing(id: String) {
        lifecycleScope.launch {
            when (val result = AppContainer.listings.detail(id)) {
                is ApiResult.Success -> result.data?.let(::bindListing)
                is ApiResult.HttpError -> {
                    toast(R.string.error_load_listing)
                    finishOrGoHome()
                }
                is ApiResult.NetworkError -> {
                    toast(R.string.error_connection_failed)
                    finishOrGoHome()
                }
            }
        }
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindListing(l: DetailListing) {
        currentListing = l
        binding.tvTitle.text = l.title ?: ""

        binding.tvPrice.text = PriceFormatter.display(l.price)

        val loc = ListingLocationFormatter.cityOnly(l.city)
        val hasLoc = loc.isNotEmpty()
        binding.tvLocation.text = loc.ifEmpty { "—" }
        binding.tvLocation.visibility = if (hasLoc) View.VISIBLE else View.GONE
        binding.ivLocationIcon.visibility = if (hasLoc) View.VISIBLE else View.GONE

        val time = RelativeTimeFormatter.format(this, l.createdAt)
        val hasTime = time.isNotEmpty()
        binding.tvTime.text = time.ifEmpty { "—" }
        binding.tvTime.visibility = if (hasTime) View.VISIBLE else View.GONE
        binding.ivTimeIcon.visibility = if (hasTime) View.VISIBLE else View.GONE

        binding.tvSellerName.text = l.sellerName ?: ""
        if (!l.sellerAvatar.isNullOrEmpty()) {
            Glide.with(binding.ivAvatar.context).load(l.sellerAvatar)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .transition(withCrossFade(200))
                .circleCrop().into(binding.ivAvatar)
        }

        binding.tvDescription.text = l.description ?: ""
        val hasDesc = !l.description.isNullOrEmpty()
        binding.cvDescription.visibility = if (hasDesc) View.VISIBLE else View.GONE

        val phone = l.sellerPhone
        // Call button — grey out if no phone or call disabled
        val callAvailable = !phone.isNullOrEmpty() && l.callEnabled
        setContactButtonState(binding.ivCall, binding.tvCall, callAvailable)
        binding.btnCall.setOnClickListener {
            if (callAvailable) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                toast(R.string.phone_not_available)
            }
        }

        // WhatsApp button — grey out if not enabled
        val waAvailable = !phone.isNullOrEmpty() && l.whatsappEnabled
        setContactButtonState(binding.ivWhatsapp, binding.tvWhatsapp, waAvailable)
        binding.btnWhatsapp.setOnClickListener {
            if (waAvailable) {
                val num = phone!!.replace(Regex("[^\\d+]"), "")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            } else {
                toast(R.string.whatsapp_not_available)
            }
        }

        // Chat always available
        binding.ivChat.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.appbar_icon_tint))
        binding.btnChat.setOnClickListener {
            startConversation(l.id)
        }

        updateNavigationArrows()

        setupImageCarousel(l.images)

        if (intent.getBooleanExtra("EXTRA_AUTO_START_CONVERSATION", false)) {
            intent.removeExtra("EXTRA_AUTO_START_CONVERSATION")
            startConversation(l.id)
        }
    }

    private fun updateNavigationArrows() {
        val canGoPrev = siblingIds.isNotEmpty() && currentIndex > 0
        val canGoNext = siblingIds.isNotEmpty() && currentIndex in 0 until (siblingIds.size - 1)

        binding.btnPrev.isEnabled = canGoPrev
        binding.btnPrev.alpha = if (canGoPrev) 1.0f else 0.22f
        binding.btnPrev.setOnClickListener {
            if (canGoPrev) {
                navigateToSibling(currentIndex - 1)
            }
        }

        binding.btnNext.isEnabled = canGoNext
        binding.btnNext.alpha = if (canGoNext) 1.0f else 0.22f
        binding.btnNext.setOnClickListener {
            if (canGoNext) {
                navigateToSibling(currentIndex + 1)
            }
        }
    }

    private fun navigateToSibling(newIndex: Int) {
        if (newIndex !in siblingIds.indices) return
        currentIndex = newIndex
        val newId = siblingIds[newIndex]
        currentListingId = newId
        updateNavigationArrows()
        loadListing(newId)
        if (TokenManager.isLoggedIn(this)) {
            checkIsFavorited(newId)
        }
        binding.nsvContent.smoothScrollTo(0, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val listingId = extractListingId(intent) ?: currentListingId
        val isDifferent = listingId != currentListingId
        currentListingId = listingId
        siblingIds = intent.getStringArrayListExtra(EXTRA_SIBLING_IDS) ?: siblingIds
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, currentIndex)
        if (currentIndex == -1 && siblingIds.isNotEmpty()) {
            currentIndex = siblingIds.indexOf(listingId)
        }
        updateNavigationArrows()
        loadListing(listingId)
        if (TokenManager.isLoggedIn(this)) {
            checkIsFavorited(listingId)
        }
        if (isDifferent) {
            binding.nsvContent.smoothScrollTo(0, 0)
        }
    }

    private fun handleBackNavigation() {
        finishOrGoHome()
    }

    private fun finishOrGoHome() {
        if (isTaskRoot) {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
            finish()
            applyPopTransition()
        } else {
            finishWithPop()
        }
    }

    // ── Contact button state ──────────────────────────────────────────────────

    private fun setContactButtonState(icon: android.widget.ImageView, label: android.widget.TextView, available: Boolean) {
        val iconColor = androidx.core.content.ContextCompat.getColor(this, R.color.appbar_icon_tint)
        val labelColor = if (available)
            androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        else
            androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        icon.setColorFilter(iconColor)
        label.setTextColor(labelColor)
        val targetAlpha = if (available) 1.0f else 0.40f
        icon.alpha = targetAlpha
        label.alpha = targetAlpha
    }



    // ── Start conversation ────────────────────────────────────────────────────

    private fun startConversation(listingId: String) {
        if (!TokenManager.isLoggedIn(this)) {
            val target = Intent(this, ListingDetailActivity::class.java).apply {
                putExtra(EXTRA_LISTING_ID, listingId)
                putExtra("EXTRA_AUTO_START_CONVERSATION", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val loginIntent = Intent(this, PhoneAuthActivity::class.java).apply {
                putExtra("EXTRA_TARGET_INTENT", target)
            }
            startActivity(loginIntent)
            return
        }
        lifecycleScope.launch {
            when (val result = AppContainer.chat.startConversation(listingId)) {
                is ApiResult.Success -> {
                    val conversation = result.data
                    if (conversation == null) {
                        toast(R.string.error_open_conversation)
                        return@launch
                    }
                    startActivity(
                        Intent(this@ListingDetailActivity, ChatActivity::class.java)
                            .putExtra(ChatActivity.EXTRA_CONVERSATION, conversation)
                    )
                }
                is ApiResult.HttpError -> toast(result.message ?: getString(R.string.error_open_conversation))
                is ApiResult.NetworkError -> toast(R.string.error_connection_failed)
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    private fun checkIsFavorited(listingId: String) {
        lifecycleScope.launch {
            AppContainer.favorites.isFavorited(listingId).getOrNull()?.let {
                isFavorited = it
                updateFavoriteIcon()
            }
        }
    }

    private fun syncFavorite(listingId: String, add: Boolean) {
        lifecycleScope.launch {
            AppContainer.favorites.setFavorite(listingId, add)
        }
    }

    private fun updateFavoriteIcon() {
        binding.ivFavoriteDetail.setImageResource(
            if (isFavorited) R.drawable.ic_favorite_bookmark_selected
            else R.drawable.ic_favorite_bookmark_unselected
        )
        binding.ivFavoriteDetail.clearColorFilter()
    }

    private fun shareListing() {
        val l = currentListing ?: return
        val title = l.title ?: ""
        val price = l.price?.let { "${PriceFormatter.display(it)} ﷼" } ?: ""
        val shareText = buildString {
            if (title.isNotEmpty()) append(title)
            if (price.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(price)
            }
            if (isNotEmpty()) append("\n")
            append("https://finds.sa/listing/${l.id}")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(sendIntent, title.ifEmpty { getString(R.string.menu_share_app) }))
    }

    // ── Image carousel & dots indicator ──────────────────────────────────────

    private var pageChangeCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null

    private fun setupImageCarousel(images: List<String>) {
        // Drop the previous ad's pager state (sibling navigation reuses this screen)
        pageChangeCallback?.let { binding.vpImages.unregisterOnPageChangeCallback(it) }
        pageChangeCallback = null

        if (images.isEmpty()) {
            // Ad without photos: remove the whole image block (no placeholder
            // picture) — favorite/share row + description move right up under
            // the contact bar, same as iOS.
            binding.vpImages.adapter = null
            binding.cvImageContainer.visibility = View.GONE
            binding.ivNoImagePlaceholder.visibility = View.GONE
            binding.llDotsIndicator.visibility = View.GONE
            return
        }

        binding.cvImageContainer.visibility = View.VISIBLE
        binding.vpImages.visibility = View.VISIBLE
        binding.vpImages.layoutDirection = View.LAYOUT_DIRECTION_LTR
        binding.ivNoImagePlaceholder.visibility = View.GONE
        binding.vpImages.adapter = DetailImageAdapter(images) { clickedPosition ->
            com.example.myapplication.utils.FullScreenImageViewerDialog(this, images, clickedPosition).show()
        }

        // Gentle fade + zoom while a photo slides in or out
        binding.vpImages.setPageTransformer { page, position ->
            val closeness = (1f - kotlin.math.abs(position)).coerceIn(0f, 1f)
            page.alpha = 0.45f + closeness * 0.55f
            val scale = 0.90f + closeness * 0.10f
            page.scaleX = scale
            page.scaleY = scale
        }
        // Keep neighbouring photos ready so the slide never stutters
        binding.vpImages.offscreenPageLimit = 1

        setupDotsIndicator(images.size, 0)

        val callback = object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotsIndicator(position)
            }
        }
        pageChangeCallback = callback
        binding.vpImages.registerOnPageChangeCallback(callback)
    }

    private fun setupDotsIndicator(count: Int, activePosition: Int = 0) {
        binding.llDotsIndicator.removeAllViews()
        if (count <= 1) {
            binding.llDotsIndicator.visibility = View.GONE
            return
        }
        binding.llDotsIndicator.visibility = View.VISIBLE
        val dotSize = (7 * resources.displayMetrics.density).toInt()
        val dotMargin = (3 * resources.displayMetrics.density).toInt()

        for (i in 0 until count) {
            val dot = View(this).apply {
                val lp = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                layoutParams = lp
                setBackgroundResource(
                    if (i == activePosition) R.drawable.bg_carousel_dot_active
                    else R.drawable.bg_carousel_dot_inactive
                )
                scaleX = if (i == activePosition) 1.25f else 1.0f
                scaleY = if (i == activePosition) 1.25f else 1.0f
                setOnClickListener {
                    binding.vpImages.setCurrentItem(i, true)
                }
            }
            binding.llDotsIndicator.addView(dot)
        }
    }

    private fun updateDotsIndicator(activePosition: Int) {
        val childCount = binding.llDotsIndicator.childCount
        for (i in 0 until childCount) {
            val dot = binding.llDotsIndicator.getChildAt(i)
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

    // ── Helpers ───────────────────────────────────────────────────────────────
}

data class DetailListing(
    val id: String,
    val title: String?,
    val description: String?,
    val price: Double?,
    val listingType: String?,
    val createdAt: String?,
    val images: List<String>,
    val sellerName: String?,
    val sellerAvatar: String?,
    val sellerPhone: String?,
    val sellerId: Int?,
    val whatsappEnabled: Boolean,
    val callEnabled: Boolean,
    val regionNameAr: String?,
    val city: String?
)

class DetailImageAdapter(
    private val images: List<String>,
    private val onImageClick: ((Int) -> Unit)? = null
) : androidx.recyclerview.widget.RecyclerView.Adapter<DetailImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(val imageView: ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_image, parent, false) as ImageView
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val url = images[position]
        Glide.with(holder.imageView.context)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_photo_placeholder)
            .transition(withCrossFade(200))
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onImageClick?.invoke(position)
        }
    }

    override fun getItemCount(): Int = images.size
}

