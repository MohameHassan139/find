package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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
        const val EXTRA_IMAGES = "listing_images" // ArrayList<String>
    }

    private lateinit var binding: ActivityAddAdBinding
    private val BASE_URL = "http://144.126.211.123/api"

    // Edit mode state
    private var editingId: String? = null
    private var existingImageUrls: MutableList<String> = mutableListOf()

    // New images picked from gallery
    private var newImageUris: MutableList<Uri> = mutableListOf()

    private var selectedLocation = ""
    private var selectedCategory = ""
    private var adType = "offer"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.filter { !newImageUris.contains(it) }.forEach { newImageUris.add(it) }
        refreshImageGallery()
    }

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedLocation = result.data?.getStringExtra("selected_location") ?: ""
            binding.tvLocationText?.text = selectedLocation.ifEmpty { getString(R.string.location_label) }
        }
    }

    private val categoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedCategory = result.data?.getStringExtra("selected_category") ?: ""
            binding.tvCategoryText?.text = selectedCategory.ifEmpty { getString(R.string.category_label) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if editing
        editingId = intent.getStringExtra(EXTRA_LISTING_ID)
        if (editingId != null) {
            // Pre-fill fields
            binding.etAdTitle?.setText(intent.getStringExtra(EXTRA_TITLE) ?: "")
            binding.etAdDescription?.setText(intent.getStringExtra(EXTRA_DESC) ?: "")
            binding.etPrice?.setText(intent.getStringExtra(EXTRA_PRICE) ?: "")
            selectedLocation = intent.getStringExtra(EXTRA_CITY) ?: ""
            adType = intent.getStringExtra(EXTRA_TYPE) ?: "offer"
            if (selectedLocation.isNotEmpty()) binding.tvLocationText?.text = selectedLocation
            val imgs = intent.getStringArrayListExtra(EXTRA_IMAGES) ?: arrayListOf()
            existingImageUrls.addAll(imgs)
            binding.btnPublish?.text = "حفظ التعديلات"
        }

        binding.btnMenu?.setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
            overridePendingTransition(android.R.anim.slide_in_left, 0)
        }
        binding.llAddImageBtn?.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.llLocationContainer?.setOnClickListener {
            locationPickerLauncher.launch(Intent(this, LocationSelectionActivity::class.java))
        }
        binding.llCategoryContainer?.setOnClickListener {
            categoryPickerLauncher.launch(Intent(this, CategorySelectionActivity::class.java))
        }
        binding.btnPublish?.setOnClickListener { if (editingId != null) updateAd() else publishAd() }

        refreshImageGallery()
    }

    // ── Image gallery ──────────────────────────────────────────────────────────

    private fun refreshImageGallery() {
        val gallery = binding.llImageGallery ?: return
        val addBtn = gallery.findViewById<View>(R.id.llAddImageBtn)
        gallery.removeAllViews()
        gallery.addView(addBtn)

        val dp = resources.displayMetrics.density
        val size = (72 * dp).toInt()
        val margin = (8 * dp).toInt()
        val closeSize = (22 * dp).toInt()

        // Show existing remote images
        for ((index, url) in existingImageUrls.withIndex()) {
            val frame = makeImageFrame(size, margin, closeSize)
            val iv = frame.getChildAt(0) as ImageView
            Glide.with(this).load(url).centerCrop().into(iv)
            val removeBtn = frame.getChildAt(1) as ImageView
            removeBtn.setOnClickListener {
                existingImageUrls.removeAt(index)
                refreshImageGallery()
            }
            gallery.addView(frame)
        }

        // Show new local images
        for (uri in newImageUris.toList()) {
            val frame = makeImageFrame(size, margin, closeSize)
            val iv = frame.getChildAt(0) as ImageView
            iv.setImageURI(uri)
            val removeBtn = frame.getChildAt(1) as ImageView
            removeBtn.setOnClickListener {
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

    // ── Create new listing ─────────────────────────────────────────────────────

    private fun publishAd() {
        val token = TokenManager.getToken(this) ?: run {
            Toast.makeText(this, "يجب تسجيل الدخول أولاً", Toast.LENGTH_SHORT).show(); return
        }
        val title = binding.etAdTitle?.text?.toString()?.trim() ?: ""
        val desc = binding.etAdDescription?.text?.toString()?.trim() ?: ""
        val price = binding.etPrice?.text?.toString()?.trim() ?: "0"
        if (title.isEmpty()) { Toast.makeText(this, "أدخل عنوان الإعلان", Toast.LENGTH_SHORT).show(); return }
        if (selectedLocation.isEmpty()) { Toast.makeText(this, "اختر الموقع", Toast.LENGTH_SHORT).show(); return }
        if (selectedCategory.isEmpty()) { Toast.makeText(this, "اختر التصنيف", Toast.LENGTH_SHORT).show(); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                setProgress("جارٍ إنشاء الإعلان...")
                val listingId = createListing(token, title, desc, price)
                    ?: throw Exception("فشل إنشاء الإعلان")
                newImageUris.forEachIndexed { i, uri ->
                    setProgress("جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}...")
                    uploadImage(token, uri, listingId)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, "✓ تم نشر الإعلان", Toast.LENGTH_SHORT).show()
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

    // ── Update existing listing ────────────────────────────────────────────────

    private fun updateAd() {
        val token = TokenManager.getToken(this) ?: run {
            Toast.makeText(this, "يجب تسجيل الدخول أولاً", Toast.LENGTH_SHORT).show(); return
        }
        val id = editingId ?: return
        val title = binding.etAdTitle?.text?.toString()?.trim() ?: ""
        val desc = binding.etAdDescription?.text?.toString()?.trim() ?: ""
        val price = binding.etPrice?.text?.toString()?.trim() ?: "0"
        if (title.isEmpty()) { Toast.makeText(this, "أدخل عنوان الإعلان", Toast.LENGTH_SHORT).show(); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                // Upload new images and collect their returned URLs
                val newlyUploadedUrls = mutableListOf<String>()
                newImageUris.forEachIndexed { i, uri ->
                    setProgress("جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}...")
                    val url = uploadImageAndGetUrl(token, uri, id)
                    if (url != null) newlyUploadedUrls.add(url)
                }

                // PATCH with existing + newly uploaded URLs
                setProgress("جارٍ الحفظ...")
                val allImages = existingImageUrls + newlyUploadedUrls
                patchListing(token, id, title, desc, price, allImages)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAdActivity, "✓ تم تحديث الإعلان", Toast.LENGTH_SHORT).show()
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

    // ── API calls ──────────────────────────────────────────────────────────────

    private suspend fun createListing(token: String, title: String, desc: String, price: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title)
                put("description", desc)
                put("listing_type", adType)
                put("city", selectedLocation)
                put("price", price.toDoubleOrNull() ?: 0.0)
                put("category_id", 1)
                put("region_id", 1)
                put("images", JSONArray())
            }
            val req = Request.Builder()
                .url("$BASE_URL/listings")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val resp = httpClient.newCall(req).execute()
            val respBody = resp.body?.string() ?: return@withContext null
            if (!resp.isSuccessful) throw Exception("فشل إنشاء الإعلان (${resp.code}): $respBody")
            JSONObject(respBody).optJSONObject("data")?.optString("id")?.takeIf { it.isNotEmpty() }
        }

    private suspend fun patchListing(token: String, id: String, title: String, desc: String, price: String, images: List<String>) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("title", title)
                put("description", desc)
                put("listing_type", adType)
                put("city", selectedLocation.ifEmpty { null })
                put("price", price.toDoubleOrNull() ?: 0.0)
                put("images", JSONArray().apply { images.forEach { put(it) } })
            }
            val req = Request.Builder()
                .url("$BASE_URL/listings/$id")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .patch(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("فشل التحديث (${resp.code})")
        }

    // Upload and return the URL (used in edit mode)
    private suspend fun uploadImageAndGetUrl(token: String, uri: Uri, listingId: String): String? =
        withContext(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes(); stream.close()
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("listing_id", listingId)
                .addFormDataPart("image", "img_${System.currentTimeMillis()}.jpg",
                    bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/upload-image")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(reqBody).build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext null
            JSONObject(body).optString("url").takeIf { it.isNotEmpty() }
        }

    private suspend fun uploadImage(token: String, uri: Uri, listingId: String) =
        withContext(Dispatchers.IO) {
            val stream = contentResolver.openInputStream(uri) ?: return@withContext
            val bytes = stream.readBytes(); stream.close()
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val reqBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("listing_id", listingId)
                .addFormDataPart("image", "img_${System.currentTimeMillis()}.jpg",
                    bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/upload-image")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .post(reqBody).build()
            httpClient.newCall(req).execute()
        }

    private fun setPublishing(on: Boolean) {
        binding.btnPublish?.isEnabled = !on
        binding.btnPublish?.text = if (on) "جارٍ الحفظ..." else
            if (editingId != null) "حفظ التعديلات" else getString(R.string.publish_ad)
    }

    private suspend fun setProgress(msg: String) = withContext(Dispatchers.Main) {
        binding.btnPublish?.text = msg
    }
}
