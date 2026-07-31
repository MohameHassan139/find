package com.example.myapplication.utils

import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.ApiCategory
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.SearchActivity
import com.example.myapplication.adapters.TopTabAdapter

/**
 * Attaches the reusable home-style header (search bar + category tabs) to any activity.
 *
 * Usage in any Activity that includes @layout/layout_home_header:
 *
 *   HomeHeaderHelper.attach(
 *       activity = this,
 *       rootView = binding.root,          // or findViewById(android.R.id.content)
 *       categoriesLiveData = sharedVm.categories
 *   )
 *
 * When the user taps the search bar → SearchActivity opens.
 * When the user taps a category tab → MainActivity opens with that category pre-selected.
 * When the user taps "الرئيسية" (Home tab) → MainActivity opens at home state.
 */
object HomeHeaderHelper {

    /**
     * @param activity      The host activity.
     * @param rootView      The root view that contains layout_home_header views.
     * @param categoriesLiveData  LiveData<List<ApiCategory>> from a shared ViewModel.
     * @param currentCatIdx The currently selected category index (0 = home). Pass -1 to skip selection highlight.
     */
    fun attach(
        activity: AppCompatActivity,
        rootView: View,
        categoriesLiveData: LiveData<List<ApiCategory>>,
        currentCatIdx: Int = -1
    ) {
        val searchContainer = rootView.findViewById<View>(R.id.llHomeSearchContainer) ?: return
        val rvTabs = rootView.findViewById<RecyclerView>(R.id.rvHomeTopTabs) ?: return
        val shimmer = rootView.findViewById<View>(R.id.llHomeTabShimmer)

        // Search bar → open SearchActivity
        searchContainer.setOnClickListener {
            activity.startActivity(Intent(activity, SearchActivity::class.java))
            activity.overridePendingTransition(0, 0)
        }

        // Category tabs
        rvTabs.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)

        val tabAdapter = TopTabAdapter(emptyList(), currentCatIdx) { cat ->
            val cats = categoriesLiveData.value ?: emptyList()
            val idx = if (cat == null) 0 else (cats.indexOf(cat) + 1)
            openMainWithCategory(activity, idx)
        }
        rvTabs.adapter = tabAdapter

        categoriesLiveData.observe(activity as LifecycleOwner) { cats ->
            shimmer?.visibility = View.GONE
            rvTabs.visibility = View.VISIBLE
            tabAdapter.update(cats, currentCatIdx)
        }
    }

    private fun openMainWithCategory(activity: AppCompatActivity, catIdx: Int) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CATEGORY_IDX, catIdx)
        }
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
    }
}
