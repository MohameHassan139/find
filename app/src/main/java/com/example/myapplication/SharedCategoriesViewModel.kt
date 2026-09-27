package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.myapplication.data.AppContainer

/**
 * Lightweight ViewModel that only loads top-level categories.
 * Used by non-home screens to power the HomeHeaderHelper category tabs.
 */
class SharedCategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val _categories = MutableLiveData<List<ApiCategory>>(emptyList())
    val categories: LiveData<List<ApiCategory>> get() = _categories

    init { loadCategories() }

    private fun loadCategories() {
        viewModelScope.launch {
            val data = AppContainer.catalog.appData().getOrNull() ?: return@launch
            val arr = data.optJSONArray("categories") ?: return@launch
            _categories.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optInt("id")
                val nameAr = o.optString("name_ar", "")
                if (id == 0 || id == 1 || nameAr.trim() == "الرئيسية" || nameAr.trim() == "الرئيسيه") return@mapNotNull null
                ApiCategory(
                    id = id,
                    nameAr = nameAr,
                    nameEn = o.optString("name_en", "").ifEmpty { null },
                    iconName = if (o.isNull("icon")) null else o.optString("icon").ifEmpty { null }
                )
            }
        }
    }
}
