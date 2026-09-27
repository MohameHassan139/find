package com.example.myapplication.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.databinding.ActivityProfileBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapplication.utils.toast
import com.example.myapplication.data.AppContainer
import com.example.myapplication.data.ApiResult

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val sharedVm: SharedCategoriesViewModel by viewModels()

    // Cached from the last GET /auth/me — carried through on every
    // PATCH /user/profile call so saving the name never clobbers these
    // (see CommunicationChannelsActivity, which is the other writer).
    private var whatsappEnabled = false
    private var callEnabled = false

    // Modern Activity Result API for image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelected(uri)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)
        BottomNavHelper.setup(this, NavScreen.NONE)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }
        
        binding.profileContainer.setOnClickListener { openGallery() }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnSignOut.setOnClickListener { confirmSignOut() }

        loadProfile()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelected(uri: Uri) {
        // Load the selected image into the ImageView
        binding.ivAvatar.imageTintList = null
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_person_avatar)
            .centerCrop()
            .into(binding.ivAvatar)

        uploadAvatar(uri)
    }

    private fun uploadAvatar(uri: Uri) {
        if (TokenManager.getToken(this) == null) return
        binding.profileContainer.isEnabled = false
        lifecycleScope.launch {
            try {
                val part = withContext(Dispatchers.IO) {
                    // A revoked or missing file shouldn't crash — just report "save failed".
                    val bytes = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                        .getOrNull() ?: return@withContext null
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    MultipartBody.Part.createFormData(
                        "image",
                        "avatar_${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                } ?: run {
                    this@ProfileActivity.toast(R.string.error_save_failed)
                    return@launch
                }

                // uploadAvatar() also saves the new URL to the cached user.
                val result = AppContainer.auth.uploadAvatar(part)
                when {
                    !result.getOrNull().isNullOrEmpty() -> this@ProfileActivity.toast(R.string.saved)
                    result is ApiResult.NetworkError -> this@ProfileActivity.toast(R.string.error_connection_failed)
                    else -> this@ProfileActivity.toast(R.string.error_save_failed)
                }
            } finally {
                binding.profileContainer.isEnabled = true
            }
        }
    }

    private fun formatPhoneNumber(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.replace(Regex("[^0-9]"), "")
        val formatted = when {
            digits.startsWith("966") -> "+$digits"
            digits.startsWith("05")  -> "+966" + digits.removePrefix("0")
            digits.startsWith("5")   -> "+966$digits"
            else                     -> if (raw.startsWith("+")) raw else "+$raw"
        }
        return "\u200E$formatted\u200E"
    }

    private fun loadProfile() {
        if (TokenManager.getToken(this) == null) return
        val name = TokenManager.getName(this)
        val phone = TokenManager.getPhone(this)
        val avatar = TokenManager.getAvatar(this)

        binding.etName.setText(name)
        binding.tvPhone.text = formatPhoneNumber(phone)

        if (avatar.isNotEmpty()) {
            binding.ivAvatar.imageTintList = null
            Glide.with(this).load(avatar)
                .placeholder(R.drawable.ic_person_avatar)
                .centerCrop()
                .into(binding.ivAvatar)
        }

        // Refresh from server (fetchMe() also refreshes the cached user).
        lifecycleScope.launch {
            val user = AppContainer.auth.fetchMe().getOrNull() ?: return@launch
            binding.etName.setText(user.name ?: "")
            binding.tvPhone.text = formatPhoneNumber(user.phone)
            whatsappEnabled = user.whatsappEnabled
            callEnabled = user.callEnabled
            if (!user.avatar.isNullOrEmpty()) {
                binding.ivAvatar.imageTintList = null
                Glide.with(this@ProfileActivity).load(user.avatar)
                    .placeholder(R.drawable.ic_person_avatar)
                    .centerCrop()
                    .into(binding.ivAvatar)
            }
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            toast(R.string.enter_name)
            return
        }
        if (TokenManager.getToken(this) == null) return
        binding.btnSave.isEnabled = false
        binding.btnSave.alpha = 0.5f
        lifecycleScope.launch {
            // updateProfile() also refreshes the cached user.
            when (AppContainer.auth.updateProfile(UpdateProfileRequest(name, whatsappEnabled, callEnabled))) {
                is ApiResult.Success -> this@ProfileActivity.toast(R.string.saved)
                is ApiResult.HttpError -> this@ProfileActivity.toast(R.string.error_save_failed)
                is ApiResult.NetworkError -> this@ProfileActivity.toast(R.string.error_connection_failed)
            }
            binding.btnSave.isEnabled = true
            binding.btnSave.alpha = 1.0f
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.logout_confirm_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ -> signOut() }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun signOut() {
        val isLoggedIn = TokenManager.getToken(this) != null
        lifecycleScope.launch {
            if (isLoggedIn) AppContainer.auth.signOut()
            TokenManager.clear(this@ProfileActivity)
            startActivity(Intent(this@ProfileActivity, PhoneAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
