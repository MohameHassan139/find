package com.example.myapplication

import com.example.myapplication.R

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityAddAdBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AddAdActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LISTING_ID = "listing_id"
        const val EXTRA_TITLE = "listing_title"
        const val EXTRA_DESC = "listing_desc"
        const val EXTRA_PRICE = "listing_price"
        const val EXTRA_CITY = "listing_city"
        const val EXTRA_TYPE = "listing_type"
        const val EXTRA_IMAGES = "listing_images"
    }

    private lateinit var binding: ActivityAddAdBinding
    private val apiBase = "http://144.126.211.123/api"

    private var editingId: String? = null
    private val existingImageUrls: MutableList<String> = mutableListOf()
    private val newImageUris: MutableList<Uri> = mutableListOf()
    private var selectedLocation = ""
    private var selectedCategory = ""
    private var adType = "offer"
    private var selectedCategoryId: Int = 1
    private var selectedSubCategoryId: Int = -1
    private var selectedRegionId: Int = 1

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.filter { it !in newImageUris }.forEach { newImageUris.add(it) }
        refreshImageGallery()
    }

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            selectedLocation = data?.getStringExtra("selected_location") ?: ""
            selectedRegionId = data?.getIntExtra("selected_region_id", 1) ?: 1
            binding.tvLocationText.text = selectedLocation.ifEmpty { getString(R.string.location_label) }
        }
    }

    private val categoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            selectedCategory = data?.getStringExtra("selected_category") ?: ""
            adType = data?.getStringExtra("selected_type") ?: "offer"
            selectedCategoryId = data?.getIntExtra("selected_category_id", 1) ?: 1
            selectedSubCategoryId = data?.getIntExtra("selected_sub_category_id", -1) ?: -1
            binding.tvCategoryText.text = selectedCategory.ifEmpty { getString(R.string.category_label) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Wire appbar buttons via findViewById (they live inside <include>)
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        BottomNavHelper.setup(this, NavScreen.ADD)

        editingId = intent.getStringExtra(EXTRA_LISTING_ID)
        if (editingId != null) {
            binding.etAdTitle.setText(intent.getStringExtra(EXTRA_TITLE) ?: "")
            binding.etAdDescription.setText(intent.getStringExtra(EXTRA_DESC) ?: "")
            binding.etPrice.setText(intent.getStringExtra(EXTRA_PRICE) ?: "")
            selectedLocation = intent.getStringExtra(EXTRA_CITY) ?: ""
            adType = intent.getStringExtra(EXTRA_TYPE) ?: "offer"
            if (selectedLocation.isNotEmpty()) binding.tvLocationText.text = selectedLocation
            val imgs = intent.getStringArrayListExtra(EXTRA_IMAGES) ?: arrayListOf()
            existingImageUrls.addAll(imgs)
            binding.btnPublish.text = getString(R.string.kt_str_91d6db7f)
        }

        binding.llAddImageBtn.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.llLocationContainer.setOnClickListener {
            locationPickerLauncher.launch(Intent(this, LocationSelectionActivity::class.java))
        }
        binding.llCategoryContainer.setOnClickListener {
            categoryPickerLauncher.launch(Intent(this, CategorySelectionActivity::class.java))
        }
        binding.btnPublish.setOnClickListener { if (editingId != null) updateAd() else publishAd() }

        refreshImageGallery()
    }

    // ── Image gallery ─────────────────────────────────────────────────────────

    private fun refreshImageGallery() {
        val gallery = binding.llImageGallery
        val addBtn = gallery.findViewById<View>(R.id.llAddImageBtn)
        gallery.removeAllViews()
        gallery.addView(addBtn)

        val dp = resources.displayMetrics.density
        val size = (72 * dp).toInt()
        val margin = (8 * dp).toInt()
        val closeSize = (22 * dp).toInt()

        existingImageUrls.forEachIndexed { index, url ->
            val frame = makeImageFrame(size, margin, closeSize)
            Glide.with(this).load(url).centerCrop().into(frame.getChildAt(0) as ImageView)
            (frame.getChildAt(1) as ImageView).setOnClickListener {
                existingImageUrls.removeAt(index)
                refreshImageGallery()
            }
            gallery.addView(frame)
        }

        newImageUris.toList().forEach { uri ->
            val frame = makeImageFrame(size, margin, closeSize)
            (frame.getChildAt(0) as ImageView).setImageURI(uri)
            (frame.getChildAt(1) as ImageView).setOnClickListener {
                newImageUris.remove(uri)
                refreshImageGallery()
            }
            gallery.addView(frame)
        }
    }

    private fun makeImageFrame(size: Int, margin: Int, closeSize: Int): FrameLayout {
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, margin, 0) }
        }
        val iv = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_image_placeholder)
            clipToOutline = true
        }
        val removeBtn = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(closeSize, closeSize).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(2, 2, 2, 2)
            }
            setImageResource(R.drawable.ic_remove_image)
        }
        frame.addView(iv)
        frame.addView(removeBtn)
        return frame
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    private fun publishAd() {
        val token = TokenManager.getToken(this) ?: run {
            Toast.makeText(this, getString(R.string.kt_str_2098437d), Toast.LENGTH_SHORT).show(); return
        }
        val title = binding.etAdTitle.text.toString().trim()
        val desc = binding.etAdDescription.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        if (title.isEmpty()) { Toast.makeText(this, getString(R.string.kt_str_fb538cc7), Toast.LENGTH_SHORT).show(); return }
        if (selectedLocation.isEmpty()) { Toast.makeText(this, getString(R.string.kt_str_629e4b86), Toast.LENGTH_SHORT).show(); return }
        if (selectedCategory.isEmpty()) { Toast.makeText(this, getString(R.string.kt_str_113aacf2), Toast.LENGTH_SHORT).show(); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                val creatingMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ إنشاء الإعلان..." else "Creating ad..."
                setProgress(creatingMsg)
                val listingId = createListing(token, title, desc, price)
                    ?: throw Exception(if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "فشل إنشاء الإعلان" else "Failed to create ad")
                newImageUris.forEachIndexed { i, uri ->
                    val uploadMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}..." else "Uploading image ${i + 1} / ${newImageUris.size}..."
                    setProgress(uploadMsg)
                    uploadImage(token, uri, listingId)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, getString(R.string.kt_str_03c580df), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, e.message ?: "خطأ في النشر", Toast.LENGTH_LONG).show()
                    setPublishing(false)
                }
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private fun updateAd() {
        val token = TokenManager.getToken(this) ?: run {
            Toast.makeText(this, getString(R.string.kt_str_2098437d), Toast.LENGTH_SHORT).show(); return
        }
        val id = editingId ?: return
        val title = binding.etAdTitle.text.toString().trim()
        val desc = binding.etAdDescription.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        if (title.isEmpty()) { Toast.makeText(this, getString(R.string.kt_str_fb538cc7), Toast.LENGTH_SHORT).show(); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                val newUrls = mutableListOf<String>()
                newImageUris.forEachIndexed { i, uri ->
                    val uploadMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}..." else "Uploading image ${i + 1} / ${newImageUris.size}..."
                    setProgress(uploadMsg)
                    uploadImageAndGetUrl(token, uri, id)?.let { newUrls.add(it) }
                }
                val savingMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ الحفظ..." else "Saving..."
                setProgress(savingMsg)
                patchListing(token, id, title, desc, price, existingImageUrls + newUrls)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, getString(R.string.kt_str_1e253162), Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, e.message ?: "خطأ في التحديث", Toast.LENGTH_LONG).show()
                    setPublishing(false)
                }
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    private suspend fun createListing(token: String, title: String, desc: String, price: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title); put("description", desc)
                put("listing_type", adType); put("city", selectedLocation)
                put("price", price.toDoubleOrNull() ?: 0.0)
                put("category_id", 1); put("region_id", 1); put("images", JSONArray())
            }
            val resp = httpClient.newCall(Request.Builder()
                .url("$apiBase/listings")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()).execute()
            val respBody = resp.body?.string() ?: return@withContext null
            if (!resp.isSuccessful) throw Exception(
                if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "فشل إنشاء الإعلان (${resp.code})" else "Failed to create ad (${resp.code})")
            JSONObject(respBody).optJSONObject("data")?.optString("id")?.takeIf { it.isNotEmpty() }
        }

    private suspend fun patchListing(token: String, id: String, title: String, desc: String, price: String, images: List<String>) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title); put("description", desc)
                put("listing_type", adType); put("city", selectedLocation.ifEmpty { null })
                put("price", price.toDoubleOrNull() ?: 0.0)
                put("images", JSONArray().apply { images.forEach { put(it) } })
            }
            val resp = httpClient.newCall(Request.Builder()
                .url("$apiBase/listings/$id")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .patch(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()).execute()
            if (!resp.isSuccessful) throw Exception("فشل التحديث (${resp.code})")
        }

    private suspend fun uploadImageAndGetUrl(token: String, uri: Uri, listingId: String): String? =
        withContext(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes().also { stream.close() }
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val resp = httpClient.newCall(Request.Builder()
                .url("$apiBase/upload-image")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("listing_id", listingId)
                    .addFormDataPart("image", "img_${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody(mime.toMediaTypeOrNull()))
                    .build()).build()).execute()
            JSONObject(resp.body?.string() ?: return@withContext null).optString("url").takeIf { it.isNotEmpty() }
        }

    private suspend fun uploadImage(token: String, uri: Uri, listingId: String) =
        withContext(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@withContext
            val bytes = stream.readBytes().also { stream.close() }
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            httpClient.newCall(Request.Builder()
                .url("$apiBase/upload-image")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("listing_id", listingId)
                    .addFormDataPart("image", "img_${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody(mime.toMediaTypeOrNull()))
                    .build()).build()).execute()
        }

    private fun setPublishing(on: Boolean) {
        binding.btnPublish.isEnabled = !on
        val isAr = com.example.myapplication.utils.LocaleHelper.isArabic(this)
        binding.btnPublish.text = when {
            on -> if (isAr) "جارٍ الحفظ..." else "Saving..."
            editingId != null -> if (isAr) "حفظ التعديلات" else "Save Changes"
            else -> getString(R.string.publish_ad)
        }
    }

    private suspend fun setProgress(msg: String) = withContext(Dispatchers.Main) {
        binding.btnPublish.text = msg
    }
}
