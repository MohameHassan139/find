package com.example.myapplication.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.MenuActivity
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.auth.UpdateProfileRequest
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.chat.api.RetrofitClient
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
        binding.btnVerifyNafath.setOnClickListener { handleNafathVerification() }
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
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .into(binding.ivAvatar)

        uploadAvatar(uri)
    }

    private fun uploadAvatar(uri: Uri) {
        val token = TokenManager.getToken(this) ?: return
        binding.profileContainer.isEnabled = false
        lifecycleScope.launch {
            try {
                val part = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext null
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    MultipartBody.Part.createFormData(
                        "image",
                        "avatar_${System.currentTimeMillis()}.jpg",
                        bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                } ?: run {
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_c5572cc3), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val response = RetrofitClient.build(this@ProfileActivity).uploadAvatar(part)
                val url = response.body()?.data?.url
                if (response.isSuccessful && !url.isNullOrEmpty()) {
                    TokenManager.save(
                        this@ProfileActivity, token,
                        TokenManager.getName(this@ProfileActivity),
                        TokenManager.getPhone(this@ProfileActivity),
                        url,
                        TokenManager.getUserId(this@ProfileActivity)
                    )
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_201aed2e), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_c5572cc3), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            } finally {
                binding.profileContainer.isEnabled = true
            }
        }
    }

    private fun handleNafathVerification() {
        // TODO: Integrate with National Single Sign-On (Nafath/IAM) SDK or WebView
        Toast.makeText(this, "Redirecting to Nafath...", Toast.LENGTH_SHORT).show()
        
        // Placeholder for Nafath integration
        // You would typically:
        // 1. Launch Nafath SDK or WebView
        // 2. Handle the callback with verification token
        // 3. Send token to your backend for verification
        // 4. Update user verification status
    }


    private fun loadProfile() {
        val token = TokenManager.getToken(this) ?: return
        val name = TokenManager.getName(this)
        val phone = TokenManager.getPhone(this)
        val avatar = TokenManager.getAvatar(this)

        binding.etName.setText(name)
        binding.tvPhone.text = phone

        if (avatar.isNotEmpty()) {
            Glide.with(this).load(avatar)
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivAvatar)
        }

        // Refresh from server
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@ProfileActivity).getMe()
                if (response.isSuccessful) {
                    val user = response.body()?.user ?: return@launch
                    binding.etName.setText(user.name ?: "")
                    binding.tvPhone.text = user.phone ?: ""
                    whatsappEnabled = user.whatsappEnabled
                    callEnabled = user.callEnabled
                    if (!user.avatar.isNullOrEmpty()) {
                        Glide.with(this@ProfileActivity).load(user.avatar)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(binding.ivAvatar)
                        TokenManager.save(this@ProfileActivity, token,
                            user.name ?: "", user.phone ?: "", user.avatar)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.kt_str_a0139053), Toast.LENGTH_SHORT).show()
            return
        }
        val token = TokenManager.getToken(this) ?: return
        binding.btnSave.isEnabled = false
        binding.btnSave.alpha = 0.5f
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.build(this@ProfileActivity).updateProfile(
                    UpdateProfileRequest(name, whatsappEnabled, callEnabled)
                )
                if (response.isSuccessful) {
                    val user = response.body()?.user
                    TokenManager.save(this@ProfileActivity, token,
                        user?.name ?: name, user?.phone ?: "", user?.avatar ?: "")
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_201aed2e), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_c5572cc3), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, getString(R.string.kt_str_338558d2), Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.btnSave.alpha = 1.0f
            }
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setMessage("هل تريد تسجيل الخروج؟")
            .setPositiveButton("خروج") { _, _ -> signOut() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun signOut() {
        val isLoggedIn = TokenManager.getToken(this) != null
        lifecycleScope.launch {
            try {
                if (isLoggedIn)
                    RetrofitClient.build(this@ProfileActivity).signOut()
            } catch (_: Exception) {}
            TokenManager.clear(this@ProfileActivity)
            startActivity(Intent(this@ProfileActivity, PhoneAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
