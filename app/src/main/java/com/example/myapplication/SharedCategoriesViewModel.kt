package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.chat.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight ViewModel that only loads top-level categories.
 * Used by non-home screens to power the HomeHeaderHelper category tabs.
 */
class SharedCategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val _categories = MutableLiveData<List<ApiCategory>>(emptyList())
    val categories: LiveData<List<ApiCategory>> get() = _categories

    init { loadCategories() }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = RetrofitClient.apiService.getCategories()
                if (res.isSuccessful) {
                    val body = res.body()?.string() ?: return@launch
                    val arr = try {
                        val root = JSONObject(body)
                        root.optJSONArray("data") ?: JSONArray().apply { put(root) }
                    } catch (_: Exception) {
                        try { JSONArray(body) } catch (_: Exception) { JSONArray() }
                    }
                    val parsed = mutableListOf<ApiCategory>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.getInt("id")
                        val nameAr = o.optString("name_ar", "")
                        if (id == 0 || id == 1 || nameAr.trim() == "الرئيسية" || nameAr.trim() == "الرئيسيه") continue
                        parsed.add(ApiCategory(
                            id = id,
                            nameAr = nameAr,
                            nameEn = o.optString("name_en", "").ifEmpty { null },
                            iconName = if (o.isNull("icon")) null else o.optString("icon").ifEmpty { null }
                        ))
                    }
                    withContext(Dispatchers.Main) { _categories.value = parsed }
                }
            } catch (_: Exception) {}
        }
    }
}
