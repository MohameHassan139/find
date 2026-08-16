package com.example.myapplication

import com.example.myapplication.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityListingDetailBinding
import com.example.myapplication.favorites.AddFavoriteRequest
import com.example.myapplication.chat.model.CreateConversationRequest
import com.example.myapplication.chat.model.ReportTargetType
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.utils.ModerationDialogs
import com.example.myapplication.utils.ModerationState
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ListingDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityListingDetailBinding
    private var isFavorited = false
    private var currentListingId = ""
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_CURRENT_INDEX = "current_index"
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

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: run { finish(); return }
        currentListingId = listingId
        loadListing(listingId)
        if (TokenManager.isLoggedIn(this)) {
            checkIsFavorited(listingId)
            lifecycleScope.launch { ModerationState.refresh(RetrofitClient.build(this@ListingDetailActivity)) }
        }

        binding.ivFavoriteDetail.setOnClickListener {
            if (!TokenManager.isLoggedIn(this)) {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
                return@setOnClickListener
            }
            val newState = !isFavorited
            isFavorited = newState
            updateFavoriteIcon()
            syncFavorite(currentListingId, newState)
        }
    }

    // ── Load listing ──────────────────────────────────────────────────────────

    private fun loadListing(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.build(this@ListingDetailActivity)
                val response = api.getListingDetail(id)
                if (response.isSuccessful) {
                    val data = JSONObject(response.body()?.string() ?: "")
                        .optJSONObject("data") ?: return@launch
                    val listing = parseListing(data)
                    withContext(Dispatchers.Main) { bindListing(listing) }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListingDetailActivity, getString(R.string.kt_str_21a15161), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListingDetailActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindListing(l: DetailListing) {
        setupModerationMenu(l)
        binding.tvTitle.text = l.title ?: ""

        binding.tvPrice.text = l.price?.let {
            val fmt = if (it % 1 == 0.0) it.toLong().toString() else it.toString()
            fmt
        } ?: "—"

        val loc = listOfNotNull(l.regionNameAr, l.city).joinToString(" - ")
        binding.tvLocation.text = loc.ifEmpty { "—" }
        binding.tvLocation.visibility = if (loc.isNotEmpty()) View.VISIBLE else View.GONE

        val time = formatTime(l.createdAt)
        binding.tvTime.text = time.ifEmpty { "—" }
        binding.tvTime.visibility = if (time.isNotEmpty()) View.VISIBLE else View.GONE

        binding.tvSellerName.text = l.sellerName ?: ""
        if (!l.sellerAvatar.isNullOrEmpty()) {
            Glide.with(binding.ivAvatar.context).load(l.sellerAvatar)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .transition(withCrossFade(200))
                .circleCrop().into(binding.ivAvatar)
        }

        binding.tvDescription.text = l.description ?: ""
        binding.tvDescription.visibility = if (l.description.isNullOrEmpty()) View.GONE else View.VISIBLE

        val phone = l.sellerPhone
        // Call button — grey out if no phone or call disabled
        val callAvailable = !phone.isNullOrEmpty() && l.callEnabled
        setContactButtonState(binding.ivCall, binding.tvCall, callAvailable)
        binding.btnCall.setOnClickListener {
            if (callAvailable) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, getString(R.string.kt_str_5abe0148), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.kt_str_d0f8a62d), Toast.LENGTH_SHORT).show()
            }
        }

        // Chat always available
        binding.btnChat.setOnClickListener {
            startConversation(l.id)
        }

        binding.btnPrev.isEnabled = false
        binding.btnNext.isEnabled = false
        binding.btnPrev.alpha = 1.0f
        binding.btnNext.alpha = 1.0f

        binding.llImages.removeAllViews()
        val gap = (8 * resources.displayMetrics.density).toInt()
        val sidePadding = (32 * resources.displayMetrics.density).toInt()
        val imageSize = resources.displayMetrics.widthPixels - sidePadding
        l.images.forEach { url ->
            val iv = ImageView(this)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                imageSize
            )
            lp.bottomMargin = gap
            iv.layoutParams = lp
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(iv.context).load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .transition(withCrossFade(300))
                .into(iv)
            binding.llImages.addView(iv)
        }

        if (intent.getBooleanExtra("EXTRA_AUTO_START_CONVERSATION", false)) {
            intent.removeExtra("EXTRA_AUTO_START_CONVERSATION")
            startConversation(l.id)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: currentListingId
        currentListingId = listingId
        loadListing(listingId)
    }

    // ── Contact button state ──────────────────────────────────────────────────

    private fun setContactButtonState(icon: android.widget.ImageView, label: android.widget.TextView, available: Boolean) {
        val color = if (available)
            android.graphics.Color.parseColor("#333333")
        else
            android.graphics.Color.parseColor("#AAAAAA")
        icon.setColorFilter(color)
        label.setTextColor(color)
        icon.alpha = if (available) 1f else 0.5f
    }

    // ── Moderation (report/block) ───────────────────────────────────────────

    private fun setupModerationMenu(l: DetailListing) {
        val btn = findViewById<android.widget.ImageButton>(R.id.btnModeration)
        val myId = TokenManager.getUserId(this)
        val isOwnListing = l.sellerId != null && myId.isNotEmpty() && l.sellerId.toString() == myId
        if (l.sellerId == null || isOwnListing) {
            btn.visibility = View.GONE
            return
        }
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            if (!TokenManager.isLoggedIn(this)) {
                startActivity(Intent(this, PhoneAuthActivity::class.java))
                return@setOnClickListener
            }
            showModerationMenu(btn, l)
        }
    }

    private fun showModerationMenu(anchor: View, l: DetailListing) {
        val sellerId = l.sellerId ?: return
        val api = RetrofitClient.build(this)
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "الإبلاغ عن الإعلان")
        popup.menu.add(0, 2, 1, "الإبلاغ عن البائع")
        if (ModerationState.isBlocked(sellerId)) {
            popup.menu.add(0, 3, 2, "إلغاء حظر البائع")
        } else {
            popup.menu.add(0, 4, 2, "حظر البائع")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> ModerationDialogs.showReportDialog(
                    this, api, ReportTargetType.LISTING, l.id, l.title ?: l.id)
                2 -> ModerationDialogs.showReportDialog(
                    this, api, ReportTargetType.USER, sellerId.toString(), l.sellerName ?: "البائع")
                3 -> ModerationDialogs.unblock(this, api, sellerId)
                4 -> ModerationDialogs.showBlockConfirm(this, api, sellerId, l.sellerName, l.sellerAvatar)
            }
            true
        }
        popup.show()
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
            try {
                val api = RetrofitClient.build(this@ListingDetailActivity)
                val response = withContext(Dispatchers.IO) {
                    api.createConversation(CreateConversationRequest(listingId))
                }
                val conversation = response.body()?.data
                if (!response.isSuccessful || conversation == null) {
                    val msg = response.errorBody()?.string()?.let {
                        runCatching { JSONObject(it).optString("message") }.getOrNull()
                    }?.takeIf { it.isNotEmpty() } ?: getString(R.string.error_open_conversation)
                    Toast.makeText(this@ListingDetailActivity, msg, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                startActivity(
                    Intent(this@ListingDetailActivity, com.example.myapplication.chat.ui.chat.ChatActivity::class.java)
                        .putExtra(com.example.myapplication.chat.ui.chat.ChatActivity.EXTRA_CONVERSATION, conversation)
                )
            } catch (e: Exception) {
                Toast.makeText(this@ListingDetailActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    private fun checkIsFavorited(listingId: String) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@ListingDetailActivity)
                val response = withContext(Dispatchers.IO) { api.isFavorited(listingId) }
                if (response.isSuccessful) {
                    // The flag lives at data.is_favorited, not at the top level —
                    // reading it off the root always yielded false, so the heart
                    // opened unfilled even for favorited listings.
                    isFavorited = response.body()?.isFavorited ?: false
                    updateFavoriteIcon()
                }
            } catch (_: Exception) {}
        }
    }

    private fun syncFavorite(listingId: String, add: Boolean) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@ListingDetailActivity)
                withContext(Dispatchers.IO) {
                    if (add) api.addFavorite(AddFavoriteRequest(listingId))
                    else api.removeFavorite(listingId)
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateFavoriteIcon() {
        binding.ivFavoriteDetail.setImageResource(
            if (isFavorited) R.drawable.ic_favorite_filled else R.drawable.ic_favorites
        )
        binding.ivFavoriteDetail.setColorFilter(
            if (isFavorited) android.graphics.Color.parseColor("#E53935")
            else android.graphics.Color.parseColor("#333333")
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseListing(o: JSONObject): DetailListing {
        val images = mutableListOf<String>()
        val imgArr = o.optJSONArray("images")
        if (imgArr != null) for (i in 0 until imgArr.length()) images.add(imgArr.getString(i))
        val seller = o.optJSONObject("seller")
        val region = o.optJSONObject("region")
        return DetailListing(
            id = o.optString("id"),
            title = o.optString("title").ifEmpty { null },
            description = o.optString("description").ifEmpty { null },
            price = if (!o.isNull("price")) o.optDouble("price") else null,
            listingType = o.optString("listing_type").ifEmpty { null },
            createdAt = o.optString("created_at").ifEmpty { null },
            images = images,
            sellerName = seller?.optString("name")?.ifEmpty { null },
            sellerAvatar = seller?.optString("avatar")?.ifEmpty { null },
            sellerPhone = seller?.optString("phone")?.ifEmpty { null },
            sellerId = seller?.optInt("id"),
            whatsappEnabled = seller?.optBoolean("whatsapp_enabled") ?: false,
            callEnabled = seller?.optBoolean("call_enabled") ?: false,
            regionNameAr = region?.optString("name_ar")?.ifEmpty { null },
            city = o.optString("city").ifEmpty { null }
        )
    }

    private fun formatTime(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateStr.take(19)) ?: return ""
            val diff = (System.currentTimeMillis() - date.time) / 1000
            when {
                diff < 60    -> getString(R.string.time_now)
                diff < 3600  -> getString(R.string.time_minutes, diff / 60)
                diff < 86400 -> getString(R.string.time_hours, diff / 3600)
                diff < 2592000 -> getString(R.string.time_days, diff / 86400)
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) { "" }
    }
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
