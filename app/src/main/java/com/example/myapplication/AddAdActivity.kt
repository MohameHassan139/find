package com.example.myapplication

import com.example.myapplication.R

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.databinding.ActivityAddAdBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class AddAdActivity : BaseActivity() {

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
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    private var editingId: String? = null
    private val existingImageUrls: MutableList<String> = mutableListOf()
    private val newImageUris: MutableList<Uri> = mutableListOf()
    private var selectedLocation = ""
    private var selectedCityName = ""
    private var selectedCategory = ""
    private var adType = "offer"
    private var selectedCategoryId: Int = 1
    private var selectedSubCategoryId: Int = -1
    private var selectedFilterOptionId: Int = -1
    private var selectedRegionId: Int = 1

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
            selectedCityName = data?.getStringExtra("selected_city_name") ?: ""
            binding.tvLocationText.text = selectedLocation.ifEmpty { getString(R.string.location_label) }
            updatePublishButtonState()
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
            selectedFilterOptionId = data?.getIntExtra("selected_filter_option_id", -1) ?: -1
            binding.tvCategoryText.text = selectedCategory.ifEmpty { getString(R.string.category_label) }
            updatePublishButtonState()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAddAdBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(
            appBarId = R.id.llAppBar,
            bottomNavId = R.id.cvBottomNav
        )

        // Wire appbar buttons via findViewById (they live inside <include>)
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }

        BottomNavHelper.setup(this, NavScreen.ADD)
        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)

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
            binding.btnPublish.text = getString(R.string.save_changes)
        }

        binding.etAdTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePublishButtonState() }
        })
        binding.etPrice.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updatePublishButtonState() }
        })

        binding.llAddImageBtn.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.llLocationContainer.setOnClickListener {
            locationPickerLauncher.launch(Intent(this, LocationSelectionActivity::class.java))
        }
        binding.llCategoryContainer.setOnClickListener {
            categoryPickerLauncher.launch(Intent(this, CategorySelectionActivity::class.java))
        }
        binding.btnPublish.setOnClickListener { if (editingId != null) updateAd() else publishAd() }

        updatePublishButtonState()
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

    private fun updatePublishButtonState() {
        val title = binding.etAdTitle.text?.toString()?.trim().orEmpty()
        val price = binding.etPrice.text?.toString()?.trim().orEmpty()
        val canPublish = title.isNotEmpty() && price.isNotEmpty() && selectedLocation.isNotEmpty() && selectedCategory.isNotEmpty()
        binding.btnPublish.isEnabled = canPublish
        binding.btnPublish.alpha = if (canPublish) 1.0f else 0.45f
    }

    private fun publishAd() {
        if (TokenManager.getToken(this) == null) {
            toast(R.string.login_required_first); return
        }
        val title = binding.etAdTitle.text.toString().trim()
        val desc = binding.etAdDescription.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        if (title.isEmpty()) { toast(R.string.ad_title_hint); return }
        if (selectedLocation.isEmpty()) { toast(R.string.choose_location); return }
        if (selectedCategory.isEmpty()) { toast(R.string.choose_category); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                val creatingMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ إنشاء الإعلان..." else "Creating ad..."
                setProgress(creatingMsg)
                val listingId = createListing(title, desc, price)
                    ?: throw Exception(if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "فشل إنشاء الإعلان" else "Failed to create ad")
                newImageUris.forEachIndexed { i, uri ->
                    val uploadMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}..." else "Uploading image ${i + 1} / ${newImageUris.size}..."
                    setProgress(uploadMsg)
                    uploadImage(uri, listingId)
                }
                withContext(Dispatchers.Main) {
                    this@AddAdActivity.toast(R.string.ad_published)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    this@AddAdActivity.toast(e.message ?: "خطأ في النشر", long = true)
                    setPublishing(false)
                }
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private fun updateAd() {
        if (TokenManager.getToken(this) == null) {
            toast(R.string.login_required_first); return
        }
        val id = editingId ?: return
        val title = binding.etAdTitle.text.toString().trim()
        val desc = binding.etAdDescription.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        if (title.isEmpty()) { toast(R.string.ad_title_hint); return }

        setPublishing(true)
        lifecycleScope.launch {
            try {
                val newUrls = mutableListOf<String>()
                newImageUris.forEachIndexed { i, uri ->
                    val uploadMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ رفع الصورة ${i + 1} / ${newImageUris.size}..." else "Uploading image ${i + 1} / ${newImageUris.size}..."
                    setProgress(uploadMsg)
                    uploadImage(uri, id)?.let { newUrls.add(it) }
                }
                val savingMsg = if (com.example.myapplication.utils.LocaleHelper.isArabic(this@AddAdActivity)) "جارٍ الحفظ..." else "Saving..."
                setProgress(savingMsg)
                patchListing(id, title, desc, price, existingImageUrls + newUrls)
                withContext(Dispatchers.Main) {
                    this@AddAdActivity.toast(R.string.ad_updated)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    this@AddAdActivity.toast(e.message ?: "خطأ في التحديث", long = true)
                    setPublishing(false)
                }
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    private fun cityToSend(): String = when {
        selectedCityName.isNotEmpty() -> selectedCityName
        selectedLocation.contains("/") -> selectedLocation.substringAfterLast("/").trim()
        else -> selectedLocation
    }

    /** Creates the listing and returns its id; throws with a readable message on failure. */
    private suspend fun createListing(title: String, desc: String, price: String): String? {
        val body = JSONObject().apply {
            put("title", title); put("description", desc)
            put("listing_type", adType); put("city", cityToSend())
            put("price", price.toDoubleOrNull() ?: 0.0)
            put("category_id", selectedCategoryId)
            put("region_id", selectedRegionId)
            if (selectedSubCategoryId > 0) put("sub_category_id", selectedSubCategoryId)
            if (selectedFilterOptionId > 0) put("filter_option_id", selectedFilterOptionId)
            put("images", JSONArray())
        }
        return when (val result = AppContainer.listings.create(body)) {
            is ApiResult.Success -> result.data
            is ApiResult.HttpError -> throw Exception(
                if (com.example.myapplication.utils.LocaleHelper.isArabic(this)) "فشل إنشاء الإعلان (${result.code})" else "Failed to create ad (${result.code})")
            is ApiResult.NetworkError -> throw Exception(getString(R.string.error_connection_failed))
        }
    }

    /** Saves the edited fields and full image list; throws on failure. */
    private suspend fun patchListing(id: String, title: String, desc: String, price: String, images: List<String>) {
        val body = JSONObject().apply {
            put("title", title); put("description", desc)
            put("listing_type", adType); put("city", cityToSend().ifEmpty { null })
            put("price", price.toDoubleOrNull() ?: 0.0)
            put("images", JSONArray().apply { images.forEach { put(it) } })
        }
        when (val result = AppContainer.listings.update(id, body)) {
            is ApiResult.Success -> Unit
            is ApiResult.HttpError -> throw Exception("فشل التحديث (${result.code})")
            is ApiResult.NetworkError -> throw Exception(getString(R.string.error_connection_failed))
        }
    }

    /** Uploads one picked image to [listingId]; returns its URL, or null if it failed. */
    private suspend fun uploadImage(uri: Uri, listingId: String): String? {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return null
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        return AppContainer.listings.uploadImage(listingId, bytes, mime).getOrNull()
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
