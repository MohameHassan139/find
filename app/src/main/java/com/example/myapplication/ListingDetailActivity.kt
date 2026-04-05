package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.databinding.ActivityListingDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ListingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListingDetailBinding

    // Context list for prev/next navigation (passed as listing IDs)
    private var listings: List<ApiListing> = emptyList()
    private var currentIndex: Int = 0

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_LISTINGS_IDS = "listings_ids"   // comma-separated ids
        const val EXTRA_CURRENT_INDEX = "current_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Receive the full listing object via the shared ViewModel cache
        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: run { finish(); return }
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)

        // Load from API
        loadListing(listingId)
    }

    private fun loadListing(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("http://144.126.211.123/api/listings/$id")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val token = TokenManager.getToken(this@ListingDetailActivity)
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = JSONObject(conn.inputStream.bufferedReader().readText())
                    val data = body.optJSONObject("data") ?: body
                    val listing = parseListing(data)
                    withContext(Dispatchers.Main) { bindListing(listing) }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListingDetailActivity,
                            "تعذر تحميل الإعلان", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListingDetailActivity,
                        "تعذر الاتصال", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun bindListing(l: DetailListing) {
        binding.tvTopTitle.text = l.title ?: ""
        binding.tvTitle.text = l.title ?: ""

        // Price
        binding.tvPrice.text = l.price?.let {
            val fmt = if (it % 1 == 0.0) it.toLong().toString() else it.toString()
            "$fmt ر.س"
        } ?: "—"

        // Type badge
        val isOffer = l.listingType == "offer"
        binding.tvTypeBadge.text = if (isOffer) "عرض" else "طلب"
        binding.tvTypeBadge.setBackgroundColor(
            if (isOffer) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )

        // Location + time
        val loc = listOfNotNull(l.regionNameAr, l.city).joinToString(" - ")
        binding.tvLocation.text = buildString {
            if (loc.isNotEmpty()) append("📍 $loc")
            val time = formatTime(l.createdAt)
            if (time.isNotEmpty()) {
                if (isNotEmpty()) append("  ")
                append("🕐 $time")
            }
        }

        // Seller
        binding.tvSellerName.text = l.sellerName ?: ""
        val avatar = l.sellerAvatar
        if (!avatar.isNullOrEmpty()) {
            Glide.with(binding.ivAvatar.context).load(avatar)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .transition(withCrossFade(200))
                .circleCrop().into(binding.ivAvatar)
        }

        // Description
        binding.tvDescription.text = l.description ?: ""
        binding.tvDescription.visibility = if (l.description.isNullOrEmpty()) View.GONE else View.VISIBLE

        // Action buttons
        val phone = l.sellerPhone
        binding.btnCall.setOnClickListener {
            if (!phone.isNullOrEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "رقم الهاتف غير متاح", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnWhatsapp.setOnClickListener {
            if (!phone.isNullOrEmpty() && l.whatsappEnabled) {
                val num = phone.replace(Regex("[^\\d+]"), "")
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$num")))
            } else {
                Toast.makeText(this, "واتساب غير متاح", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnChat.setOnClickListener {
            startConversation(l.id)
        }

        // Prev / Next (disabled for now — single listing view)
        binding.btnPrev.isEnabled = false
        binding.btnNext.isEnabled = false
        binding.btnPrev.alpha = 0.3f
        binding.btnNext.alpha = 0.3f

        // Images — full width, stacked vertically
        binding.llImages.removeAllViews()
        l.images.forEach { url ->
            val iv = ImageView(this)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.widthPixels   // square-ish
            )
            iv.layoutParams = lp
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(iv.context).load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .transition(withCrossFade(300))
                .into(iv)
            binding.llImages.addView(iv)
        }
    }

    private fun startConversation(listingId: String) {
        if (!TokenManager.isLoggedIn(this)) {
            startActivity(Intent(this, com.example.myapplication.auth.PhoneAuthActivity::class.java))
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = TokenManager.getToken(this@ListingDetailActivity) ?: return@launch
                val conn = URL("http://144.126.211.123/api/conversations")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                // API expects listing_id
                val body = """{"listing_id":"$listingId"}"""
                conn.outputStream.write(body.toByteArray())

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = JSONObject(stream.bufferedReader().readText())

                val convData = resp.optJSONObject("data")
                if (convData != null) {
                    val conversation = com.example.myapplication.chat.model.Conversation(
                        id = convData.optString("id"),
                        listingId = convData.optString("listing_id").ifEmpty { null },
                        listingTitle = convData.optString("listing_title").ifEmpty { null },
                        buyerId = convData.optString("buyer_id").ifEmpty { null },
                        sellerId = convData.optString("seller_id").ifEmpty { null },
                        sellerName = convData.optString("seller_name").ifEmpty { null },
                        sellerAvatar = convData.optString("seller_avatar").ifEmpty { null },
                        buyerName = convData.optString("buyer_name").ifEmpty { null },
                        buyerAvatar = convData.optString("buyer_avatar").ifEmpty { null },
                        lastMessage = null,
                        lastMessageAt = convData.optString("last_message_at").ifEmpty { null },
                        buyerUnread = 0,
                        sellerUnread = 0
                    )
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@ListingDetailActivity,
                            com.example.myapplication.chat.ui.chat.ChatActivity::class.java)
                        intent.putExtra(
                            com.example.myapplication.chat.ui.chat.ChatActivity.EXTRA_CONVERSATION,
                            conversation
                        )
                        startActivity(intent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListingDetailActivity,
                            "تعذر فتح المحادثة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListingDetailActivity,
                        "تعذر الاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

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
