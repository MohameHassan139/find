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
import com.example.myapplication.auth.ListingItem
import com.example.myapplication.MenuActivity
import com.example.myapplication.R
import com.example.myapplication.SharedCategoriesViewModel
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.chat.api.RetrofitClient
import com.example.myapplication.databinding.ActivityFavoritesBinding
import com.example.myapplication.utils.HomeHeaderHelper
import com.example.myapplication.utils.LocaleHelper
import com.example.myapplication.BottomNavHelper
import com.example.myapplication.NavScreen
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
    private var currentFilter: String? = null // null = show all

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
        BottomNavHelper.setup(this, NavScreen.NONE)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finishWithPop() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startMenuActivity()
        }

        adapter = ListingsAdapter(
            items = emptyList(),
            onClick = { listing ->
                startWithPush(
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

        binding.btnFilterOffer.setOnClickListener {
            currentFilter = if (currentFilter == "offer") null else "offer"
            updateFilterButtons()
            applyFilter()
        }
        binding.btnFilterRequest.setOnClickListener {
            currentFilter = if (currentFilter == "request") null else "request"
            updateFilterButtons()
            applyFilter()
        }

        updateFilterButtons()

        loadFavorites()
    }

    private fun updateFilterButtons() {
        val activeBlue = androidx.core.content.ContextCompat.getColor(this, R.color.chats_chip_selected_border)
        val textPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.black)
        val textInactive = androidx.core.content.ContextCompat.getColor(this, R.color.tab_inactive_text)
        val bgGrey = androidx.core.content.ContextCompat.getColor(this, R.color.bg_switcher)
        val density = resources.displayMetrics.density
        val activeStrokePx = (1 * density).toInt()

        binding.btnFilterOffer.apply {
            val active = currentFilter == "offer"
            strokeWidth = if (active) activeStrokePx else 0
            strokeColor = if (active) android.content.res.ColorStateList.valueOf(activeBlue) else null
            setTextColor(if (active) textPrimary else textInactive)
            backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
        }
        binding.btnFilterRequest.apply {
            val active = currentFilter == "request"
            strokeWidth = if (active) activeStrokePx else 0
            strokeColor = if (active) android.content.res.ColorStateList.valueOf(activeBlue) else null
            setTextColor(if (active) textPrimary else textInactive)
            backgroundTintList = android.content.res.ColorStateList.valueOf(bgGrey)
        }
    }

    private fun applyFilter() {
        val filtered = if (currentFilter == null) allFavorites
                       else allFavorites.filter { it.listingType == currentFilter }
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

                // /favorites is data.items + data.pagination, not a bare data[]
                // array — parsing it as an array always produced an empty list,
                // which is why this screen showed the empty state permanently.
                // Every page is walked so long favorites lists aren't truncated
                // at the server's default per_page of 15.
                val loaded = withContext(Dispatchers.IO) {
                    val collected = mutableListOf<ApiListing>()
                    var page = 1
                    var lastPage = 1
                    var failed = false
                    do {
                        val response = api.getFavorites(page = page)
                        if (!response.isSuccessful) {
                            failed = page == 1
                            break
                        }
                        val data = response.body()?.data ?: break
                        collected += data.items.orEmpty().map { it.toApiListing() }
                        lastPage = data.pagination?.lastPage ?: 1
                        page++
                    } while (page <= lastPage && page <= 20) // hard safety cap
                    if (failed) null else collected
                }

                binding.progressBar.visibility = View.GONE

                if (loaded == null) {
                    showError()
                    return@launch
                }

                allFavorites = loaded
                favoriteIds.clear()
                allFavorites.forEach { favoriteIds.add(it.id) }

                if (allFavorites.isEmpty()) {
                    binding.root.findViewById<View>(R.id.emptyView).visibility = View.VISIBLE
                } else {
                    applyFilter()
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

    /**
     * Maps the typed favorites payload onto the adapter's display model.
     * Replaces the old hand-rolled JSON walk, which is where the wrong
     * `data[]` assumption lived.
     */
    private fun ListingItem.toApiListing() = ApiListing(
        id = id,
        title = title?.takeIf { it.isNotEmpty() },
        price = price,
        listingType = listingType?.takeIf { it.isNotEmpty() },
        createdAt = createdAt?.takeIf { it.isNotEmpty() },
        images = images.orEmpty(),
        sellerName = seller?.name?.takeIf { it.isNotEmpty() },
        sellerAvatar = seller?.avatar?.takeIf { it.isNotEmpty() },
        regionNameAr = region?.nameAr?.takeIf { it.isNotEmpty() },
        city = city?.takeIf { it.isNotEmpty() }
    )
}
