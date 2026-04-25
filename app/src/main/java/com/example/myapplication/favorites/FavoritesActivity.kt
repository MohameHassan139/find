package com.example.myapplication.favorites

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import com.example.myapplication.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.ApiListing
import com.example.myapplication.ListingDetailActivity
import com.example.myapplication.MenuActivity
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityFavoritesBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class FavoritesActivity : BaseActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var adapter: ListingsAdapter
    private val favoriteIds = mutableSetOf<String>()
    private val sharedVm: SharedCategoriesViewModel by viewModels()
    private var allFavorites: List<ApiListing> = emptyList()
    private var currentFilter = "offer"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLocale(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        HomeHeaderHelper.attach(this, binding.root, sharedVm.categories)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        adapter = ListingsAdapter(
            items = emptyList(),
            onClick = { listing ->
                startActivity(
                    Intent(this, ListingDetailActivity::class.java)
                        .putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listing.id)
                )
            },
            onFavoriteClick = { listing, isFav ->
                toggleFavorite(listing.id, isFav)
            }
        )

        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = adapter

        binding.btnFilterOffer.setOnClickListener { setFilter("offer") }
        binding.btnFilterRequest.setOnClickListener { setFilter("request") }

        loadFavorites()
    }

    private fun setFilter(type: String) {
        currentFilter = type
        if (type == "offer") {
            binding.btnFilterOffer.apply {
                strokeWidth = 9
                strokeColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.find_active_blue)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            }
            binding.btnFilterRequest.apply {
                strokeWidth = 3
                strokeColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.text_primary)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            }
        } else {
            binding.btnFilterRequest.apply {
                strokeWidth = 9
                strokeColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.find_active_blue)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            }
            binding.btnFilterOffer.apply {
                strokeWidth = 3
                strokeColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(context, R.color.text_primary)
                )
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allFavorites.filter { it.listingType == currentFilter }
        if (filtered.isEmpty()) {
            binding.rvFavorites.visibility = View.GONE
            binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
        } else {
            binding.root.findViewById<View>(R.id.emptyView).visibility = View.GONE
            binding.rvFavorites.visibility = View.VISIBLE
            adapter.updateData(filtered)
            adapter.setFavoriteIds(favoriteIds)
        }
    }

    private fun loadFavorites() {
        binding.progressBar.visibility = View.VISIBLE
        binding.root.findViewById<View>(R.id.emptyView).visibility = View.GONE
        binding.rvFavorites.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@FavoritesActivity)
                val response = withContext(Dispatchers.IO) { api.getFavorites() }

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: ""
                    allFavorites = parseFavorites(body)
                    favoriteIds.clear()
                    allFavorites.forEach { favoriteIds.add(it.id) }

                    if (allFavorites.isEmpty()) {
                        binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
                    } else {
                        applyFilter()
                    }
                } else {
                    showError()
                }
            } catch (_: Exception) {
                binding.progressBar.visibility = View.GONE
                showError()
            }
        }
    }

    private fun toggleFavorite(listingId: String, add: Boolean) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.build(this@FavoritesActivity)
                val response = withContext(Dispatchers.IO) {
                    if (add) api.addFavorite(AddFavoriteRequest(listingId))
                    else api.removeFavorite(listingId)
                }
                // On successful remove, pull item out of the list
                if (!add && (response.isSuccessful || response.code() == 404)) {
                    favoriteIds.remove(listingId)
                    allFavorites = allFavorites.filter { it.id != listingId }
                    applyFilter()
                    if (allFavorites.isEmpty()) {
                        binding.rvFavorites.visibility = View.GONE
                        binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showError() {
        binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
        binding.tvEmpty.text = getString(R.string.kt_str_338558d2)
    }

    // GET /favorites returns data[] where each item IS the listing directly
    private fun parseFavorites(json: String): List<ApiListing> {
        return try {
            val root = JSONObject(json)
            val dataArr = root.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<ApiListing>()
            for (i in 0 until dataArr.length()) {
                val obj = dataArr.getJSONObject(i)
                val images = mutableListOf<String>()
                obj.optJSONArray("images")?.let { arr ->
                    for (j in 0 until arr.length()) images.add(arr.getString(j))
                }
                val seller = obj.optJSONObject("seller")
                val region = obj.optJSONObject("region")
                list.add(
                    ApiListing(
                        id = obj.optString("id"),
                        title = obj.optString("title").takeIf { it.isNotEmpty() },
                        price = obj.optDouble("price").takeIf { !it.isNaN() },
                        listingType = obj.optString("listing_type").takeIf { it.isNotEmpty() },
                        createdAt = obj.optString("created_at").takeIf { it.isNotEmpty() },
                        images = images,
                        sellerName = seller?.optString("name"),
                        sellerAvatar = seller?.optString("avatar"),
                        regionNameAr = region?.optString("name_ar"),
                        city = obj.optString("city").takeIf { it.isNotEmpty() }
                    )
                )
            }
            list
        } catch (_: Exception) { emptyList() }
    }
}
