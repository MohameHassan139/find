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
    private var isTitleExpanded = false
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
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FOCUS_SEARCH, true)
            }
            startWithPush(intent)
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
        binding.stickyBtnFavoriteDetail.setOnClickListener(toggleFavorite)
        binding.stickyIvFavoriteDetail.setOnClickListener(toggleFavorite)

        val onShare: (View) -> Unit = {
            shareListing()
        }
        binding.btnShareDetail.setOnClickListener(onShare)
        binding.stickyBtnShareDetail.setOnClickListener(onShare)

        setupStickyScroll()
    }

    private fun setupStickyScroll() {
        binding.nsvContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val cardTop = binding.cardDetails.top
            val headerBottom = binding.clShadedHeader.bottom
            val row1Start = if (headerBottom > 0) cardTop + headerBottom else (cardTop + binding.llActionBar.top)
            val shouldShowSticky = row1Start > 0 && scrollY >= row1Start

            if (shouldShowSticky) {
                if (binding.cvStickyActionBars.visibility != View.VISIBLE) {
                    binding.cvStickyActionBars.alpha = 0f
                    binding.cvStickyActionBars.visibility = View.VISIBLE
                    binding.cvStickyActionBars.animate().alpha(1f).setDuration(120).start()
                }

                // Check if the ad has an image
                val hasImages = binding.cvImageContainer.visibility == View.VISIBLE
                val row1Height = if (binding.stickyLlActionBar.height > 0) {
                    binding.stickyLlActionBar.height
                } else {
                    (60 * resources.displayMetrics.density).toInt()
                }

                val row2Start = cardTop + binding.rlFavoriteAndIndicator.top
                val shouldShowRow2 = !hasImages || (scrollY >= (row2Start - row1Height))

                if (shouldShowRow2) {
                    if (binding.stickyRlFavoriteAndIndicator.visibility != View.VISIBLE) {
                        binding.stickyDivider.visibility = View.VISIBLE
                        binding.stickyRlFavoriteAndIndicator.alpha = 0f
                        binding.stickyRlFavoriteAndIndicator.visibility = View.VISIBLE
                        binding.stickyRlFavoriteAndIndicator.animate().alpha(1f).setDuration(150).start()
                    }
                } else {
                    if (binding.stickyRlFavoriteAndIndicator.visibility == View.VISIBLE) {
                        binding.stickyDivider.visibility = View.GONE
                        binding.stickyRlFavoriteAndIndicator.visibility = View.GONE
                        binding.stickyRlFavoriteAndIndicator.alpha = 1f
                    }
                }
            } else {
                if (binding.cvStickyActionBars.visibility != View.GONE) {
                    binding.cvStickyActionBars.visibility = View.GONE
                    binding.cvStickyActionBars.alpha = 1f
                    binding.stickyDivider.visibility = View.GONE
                    binding.stickyRlFavoriteAndIndicator.visibility = View.GONE
                    binding.stickyRlFavoriteAndIndicator.alpha = 1f
                }
            }
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

    private fun setupExpandableTitle(fullTitle: String) {
        isTitleExpanded = false
        val tv = binding.tvTitle
        tv.minLines = 2
        tv.maxLines = 2
        tv.ellipsize = android.text.TextUtils.TruncateAt.END
        tv.text = fullTitle
        tv.setOnClickListener(null)
        tv.isClickable = false

        if (fullTitle.isEmpty()) return

        tv.post {
            val layout = tv.layout ?: return@post
            val isTruncated = layout.lineCount > 2 ||
                (layout.lineCount == 2 && layout.getEllipsisCount(1) > 0)
            if (!isTruncated) return@post

            val seeMoreLabel = getString(R.string.see_more)
            val seeLessLabel = getString(R.string.see_less)
            val accentColor = androidx.core.content.ContextCompat.getColor(this, R.color.find_primary)
            val scale = 0.65f

            fun buildStyledSuffix(prefix: String, label: String): android.text.SpannableStringBuilder {
                return android.text.SpannableStringBuilder(prefix).apply {
                    val start = length
                    append(label)
                    setSpan(
                        android.text.style.RelativeSizeSpan(scale),
                        start, length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        android.text.style.ForegroundColorSpan(accentColor),
                        start, length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Compute exact 2nd-line cutoff so "... المزيد" / "... See more" sits inline on line 2
            val line1End = layout.getLineEnd(0).coerceIn(0, fullTitle.length)
            val line2Start = layout.getLineStart(1).coerceIn(0, fullTitle.length)
            val line2End = layout.getLineEnd(1).coerceIn(line2Start, fullTitle.length)
            val line1Part = fullTitle.substring(0, line1End)
            val line2Raw = fullTitle.substring(line2Start, line2End).trimEnd('\n', ' ')

            val availWidth = (tv.width - tv.paddingStart - tv.paddingEnd).toFloat()
            val paint = tv.paint
            val ellipsisWidth = paint.measureText("... ")
            val labelWidth = paint.measureText(seeMoreLabel) * scale
            val safetyPad = 8f * resources.displayMetrics.density
            val maxLine2TextWidth = (availWidth - ellipsisWidth - labelWidth - safetyPad).coerceAtLeast(0f)

            var cut = line2Raw.length
            while (cut > 0 && paint.measureText(line2Raw, 0, cut) > maxLine2TextWidth) {
                cut--
            }
            val line2Trimmed = line2Raw.substring(0, cut).trimEnd()
            val collapsedSpannable = buildStyledSuffix("$line1Part$line2Trimmed... ", seeMoreLabel)
            val expandedSpannable = buildStyledSuffix("$fullTitle ", seeLessLabel)

            tv.ellipsize = null
            tv.text = collapsedSpannable
            tv.isClickable = true
            tv.setOnClickListener {
                isTitleExpanded = !isTitleExpanded
                if (isTitleExpanded) {
                    tv.minLines = 1
                    tv.maxLines = Int.MAX_VALUE
                    tv.text = expandedSpannable
                } else {
                    tv.minLines = 2
                    tv.maxLines = 2
                    tv.text = collapsedSpannable
                }
            }
        }
    }

    private fun bindListing(l: DetailListing) {
        currentListing = l
        setupExpandableTitle(l.title ?: "")

        binding.tvPrice.text = PriceFormatter.display(l.price)
        binding.ivRiyalSymbol.visibility = if (l.price != null) View.VISIBLE else View.GONE

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
        setContactButtonState(binding.stickyIvCall, binding.stickyTvCall, callAvailable)
        val onCall: (View) -> Unit = {
            if (callAvailable) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                toast(R.string.phone_not_available)
            }
        }
        binding.btnCall.setOnClickListener(onCall)
        binding.stickyBtnCall.setOnClickListener(onCall)

        // WhatsApp button — grey out if not enabled
        val waAvailable = !phone.isNullOrEmpty() && l.whatsappEnabled
        setContactButtonState(binding.ivWhatsapp, binding.tvWhatsapp, waAvailable)
        setContactButtonState(binding.stickyIvWhatsapp, binding.stickyTvWhatsapp, waAvailable)
        val onWhatsapp: (View) -> Unit = {
            if (waAvailable) {
                val num = phone!!.replace(Regex("[^\\d+]"), "")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            } else {
                toast(R.string.whatsapp_not_available)
            }
        }
        binding.btnWhatsapp.setOnClickListener(onWhatsapp)
        binding.stickyBtnWhatsapp.setOnClickListener(onWhatsapp)

        // Chat always available
        val chatColor = androidx.core.content.ContextCompat.getColor(this, R.color.appbar_icon_tint)
        binding.ivChat.setColorFilter(chatColor)
        binding.stickyIvChat.setColorFilter(chatColor)
        val onChat: (View) -> Unit = {
            startConversation(l.id)
        }
        binding.btnChat.setOnClickListener(onChat)
        binding.stickyBtnChat.setOnClickListener(onChat)

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

        val onPrev: (View) -> Unit = {
            if (canGoPrev) {
                navigateToSibling(currentIndex - 1)
            }
        }
        val onNext: (View) -> Unit = {
            if (canGoNext) {
                navigateToSibling(currentIndex + 1)
            }
        }

        binding.btnPrev.isEnabled = canGoPrev
        binding.btnPrev.alpha = if (canGoPrev) 1.0f else 0.22f
        binding.btnPrev.setOnClickListener(onPrev)

        binding.stickyBtnPrev.isEnabled = canGoPrev
        binding.stickyBtnPrev.alpha = if (canGoPrev) 1.0f else 0.22f
        binding.stickyBtnPrev.setOnClickListener(onPrev)

        binding.btnNext.isEnabled = canGoNext
        binding.btnNext.alpha = if (canGoNext) 1.0f else 0.22f
        binding.btnNext.setOnClickListener(onNext)

        binding.stickyBtnNext.isEnabled = canGoNext
        binding.stickyBtnNext.alpha = if (canGoNext) 1.0f else 0.22f
        binding.stickyBtnNext.setOnClickListener(onNext)
    }

    private fun navigateToSibling(newIndex: Int) {
        if (newIndex !in siblingIds.indices) return
        currentIndex = newIndex
        val newId = siblingIds[newIndex]
        currentListingId = newId
        binding.cvStickyActionBars.visibility = View.GONE
        binding.stickyDivider.visibility = View.GONE
        binding.stickyRlFavoriteAndIndicator.visibility = View.GONE
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
        binding.cvStickyActionBars.visibility = View.GONE
        binding.stickyDivider.visibility = View.GONE
        binding.stickyRlFavoriteAndIndicator.visibility = View.GONE
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
            // First check if an existing conversation with this advertiser already exists
            val sId = currentListing?.sellerId
            val sName = currentListing?.sellerName?.trim()
            if ((sId != null && sId != 0) || !sName.isNullOrBlank()) {
                val existing = AppContainer.chat.conversations().let { res ->
                    if (res is ApiResult.Success) {
                        res.data.firstOrNull { conv ->
                            (sId != null && sId != 0 && conv.otherUser?.id == sId) ||
                            (!sName.isNullOrBlank() && conv.otherUser?.name?.trim().equals(sName, ignoreCase = true))
                        }
                    } else null
                }
                if (existing != null) {
                    startActivity(
                        Intent(this@ListingDetailActivity, ChatActivity::class.java)
                            .putExtra(ChatActivity.EXTRA_CONVERSATION, existing)
                    )
                    return@launch
                }
            }

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
        val res = if (isFavorited) R.drawable.ic_favorite_bookmark_selected
                  else R.drawable.ic_favorite_bookmark_unselected
        binding.ivFavoriteDetail.setImageResource(res)
        binding.ivFavoriteDetail.clearColorFilter()
        binding.stickyIvFavoriteDetail.setImageResource(res)
        binding.stickyIvFavoriteDetail.clearColorFilter()
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
        binding.vpImages.setCurrentItem(0, false)
        binding.vpImages.post {
            binding.vpImages.setCurrentItem(0, false)
            updateDotsIndicator(0)
        }

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

