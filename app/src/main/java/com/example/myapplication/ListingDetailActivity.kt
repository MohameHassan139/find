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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityListingDetailBinding
import com.example.myapplication.utils.LocaleHelper
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
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        val listingId = intent.getStringExtra(EXTRA_LISTING_ID) ?: run { finish(); return }
        loadListing(listingId)
    }

    // ── Load listing ──────────────────────────────────────────────────────────

    private fun loadListing(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("http://144.126.211.123/api/listings/$id")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                TokenManager.getToken(this@ListingDetailActivity)
                    ?.let { conn.setRequestProperty("Authorization", "Bearer $it") }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val data = JSONObject(conn.inputStream.bufferedReader().readText())
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
        binding.tvTitle.text = l.title ?: ""

        binding.tvPrice.text = l.price?.let {
            val fmt = if (it % 1 == 0.0) it.toLong().toString() else it.toString()
            "$fmt ر.س"
        } ?: "—"

        val isOffer = l.listingType == "offer"
        binding.tvTypeBadge.text = if (isOffer) "عرض" else "طلب"
        binding.tvTypeBadge.setBackgroundColor(
            if (isOffer) Color.parseColor("#34C759") else Color.parseColor("#FF9500")
        )

        val loc = listOfNotNull(l.regionNameAr, l.city).joinToString(" - ")
        binding.tvLocation.text = buildString {
            if (loc.isNotEmpty()) append("📍 $loc")
            val time = formatTime(l.createdAt)
            if (time.isNotEmpty()) {
                if (isNotEmpty()) append("  ")
                append("🕐 $time")
            }
        }

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
        binding.btnCall.setOnClickListener {
            if (!phone.isNullOrEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, getString(R.string.kt_str_5abe0148), Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnWhatsapp.setOnClickListener {
            if (!phone.isNullOrEmpty() && l.whatsappEnabled) {
                val num = phone.replace(Regex("[^\\d+]"), "")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
            } else {
                Toast.makeText(this, getString(R.string.kt_str_d0f8a62d), Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnChat.setOnClickListener {
            startConversation(l.id, l.sellerId?.toString() ?: "")
        }

        binding.btnPrev.isEnabled = false
        binding.btnNext.isEnabled = false
        binding.btnPrev.alpha = 0.3f
        binding.btnNext.alpha = 0.3f

        binding.llImages.removeAllViews()
        l.images.forEach { url ->
            val iv = ImageView(this)
            iv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.widthPixels
            )
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(iv.context).load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_photo_placeholder)
                .transition(withCrossFade(300))
                .into(iv)
            binding.llImages.addView(iv)
        }
    }

    // ── Start conversation ────────────────────────────────────────────────────

    private fun startConversation(listingId: String, sellerId: String) {
        if (!TokenManager.isLoggedIn(this)) {
            startActivity(Intent(this, PhoneAuthActivity::class.java))
            return
        }
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL("http://144.126.211.123/api/conversations")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                val boundary = "----Boundary${System.currentTimeMillis()}"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")

                val formBody = buildString {
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"listing_id\"\r\n\r\n")
                    append("$listingId\r\n")
                    if (sellerId.isNotEmpty()) {
                        append("--$boundary\r\n")
                        append("Content-Disposition: form-data; name=\"seller_id\"\r\n\r\n")
                        append("$sellerId\r\n")
                    }
                    append("--$boundary--\r\n")
                }
                conn.outputStream.write(formBody.toByteArray())

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val rawJson = stream?.bufferedReader()?.readText() ?: ""
                val root = JSONObject(rawJson)
                val convObj = root.optJSONObject("data")

                if (code !in 200..299 || convObj == null) {
                    val msg = root.optString("message").ifEmpty { "تعذر فتح المحادثة" }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ListingDetailActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val conversation = com.example.myapplication.chat.model.Conversation(
                    id = convObj.getString("id"),
                    listingId = convObj.optString("listing_id").ifEmpty { null },
                    listingTitle = convObj.optString("listing_title").ifEmpty { null },
                    buyerId = convObj.optString("buyer_id").ifEmpty { null },
                    sellerId = convObj.optString("seller_id").ifEmpty { null },
                    sellerName = convObj.optString("seller_name").ifEmpty { null },
                    sellerAvatar = convObj.optString("seller_avatar").ifEmpty { null },
                    buyerName = convObj.optString("buyer_name").ifEmpty { null },
                    buyerAvatar = if (convObj.isNull("buyer_avatar")) null else convObj.optString("buyer_avatar"),
                    lastMessage = convObj.optString("last_message").ifEmpty { null },
                    lastMessageAt = convObj.optString("last_message_at").ifEmpty { null },
                    buyerUnread = convObj.optInt("buyer_unread", 0),
                    sellerUnread = convObj.optInt("seller_unread", 0)
                )
                withContext(Dispatchers.Main) {
                    startActivity(
                        Intent(this@ListingDetailActivity,
                            com.example.myapplication.chat.ui.chat.ChatActivity::class.java)
                            .putExtra(
                                com.example.myapplication.chat.ui.chat.ChatActivity.EXTRA_CONVERSATION,
                                conversation
                            )
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ListingDetailActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                diff < 60 -> "الآن"
                diff < 3600 -> "${diff / 60} دقيقة"
                diff < 86400 -> "${diff / 3600} ساعة"
                diff < 2592000 -> "${diff / 86400} يوم"
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
