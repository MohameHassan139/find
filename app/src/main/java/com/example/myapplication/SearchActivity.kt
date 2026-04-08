package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.adapters.ListingsAdapter
import com.example.myapplication.auth.PhoneAuthActivity
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.chat.ui.conversations.ConversationsActivity
import com.example.myapplication.databinding.ActivitySearchBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val vm: SearchViewModel by viewModels()

    private lateinit var resultsAdapter: ListingsAdapter

    private val gold = Color.parseColor("#C8A96E")
    private val gray = Color.parseColor("#888888")

    private var debounceJob: Job? = null

    companion object {
        const val EXTRA_QUERY = "extra_query"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupRecycler()
        setupSearchInput()
        setupChips()
        setupRegionSpinner()
        observeViewModel()
        setupNavigation()

        // Pre-fill query if launched from MainActivity
        val initialQuery = intent.getStringExtra(EXTRA_QUERY)
        if (!initialQuery.isNullOrBlank()) {
            binding.etSearchQuery.setText(initialQuery)
            binding.etSearchQuery.setSelection(initialQuery.length)
            vm.search(initialQuery)
        } else {
            binding.etSearchQuery.requestFocus()
            showKeyboard()
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecycler() {
        resultsAdapter = ListingsAdapter(emptyList()) { listing ->
            val intent = Intent(this, ListingDetailActivity::class.java)
            intent.putExtra(ListingDetailActivity.EXTRA_LISTING_ID, listing.id)
            startActivity(intent)
        }
        val llm = LinearLayoutManager(this)
        binding.rvResults.layoutManager = llm
        binding.rvResults.adapter = resultsAdapter

        binding.rvResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val total = llm.itemCount
                val lastVisible = llm.findLastVisibleItemPosition()
                if (lastVisible >= total - 3 && vm.hasMorePages()) vm.loadNextPage()
            }
        })
    }

    // ── Search input ──────────────────────────────────────────────────────────

    private fun setupSearchInput() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.ImageButton>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, MenuActivity::class.java))
        }

        binding.etSearchQuery.addTextChangedListener { text ->
            val q = text?.toString() ?: ""
            binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
            debounceJob?.cancel()
            if (q.isBlank()) {
                vm.clearSearch()
                return@addTextChangedListener
            }
            debounceJob = lifecycleScope.launch {
                delay(400)
                vm.search(q)
            }
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etSearchQuery.text.toString().trim()
                if (q.isNotBlank()) {
                    debounceJob?.cancel()
                    vm.search(q)
                    hideKeyboard()
                }
                true
            } else false
        }

        binding.btnClear.setOnClickListener {
            binding.etSearchQuery.setText("")
            vm.clearSearch()
            showKeyboard()
        }
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    private fun setupChips() {
        binding.chipAll.setOnClickListener { vm.selectType(null); updateChipStyles(null) }
        binding.chipOffer.setOnClickListener { vm.selectType("offer"); updateChipStyles("offer") }
        binding.chipRequest.setOnClickListener { vm.selectType("request"); updateChipStyles("request") }
    }

    private fun updateChipStyles(active: String?) {
        fun style(tv: TextView, isActive: Boolean) {
            tv.setBackgroundResource(if (isActive) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
            tv.setTextColor(if (isActive) Color.BLACK else gray)
            tv.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }
        style(binding.chipAll, active == null)
        style(binding.chipOffer, active == "offer")
        style(binding.chipRequest, active == "request")
    }

    // ── Region spinner ────────────────────────────────────────────────────────

    private var suppressSpinner = false

    private fun setupRegionSpinner() {
        vm.regions.observe(this) { regions ->
            suppressSpinner = true
            val labels = mutableListOf("كل المناطق") + regions.map { it.nameAr }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerRegion.adapter = adapter
            binding.spinnerRegion.setSelection(0)
            suppressSpinner = false

            binding.spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (suppressSpinner) return
                    val regionId = if (pos == 0) null else regions[pos - 1].id
                    vm.selectRegion(regionId)
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.bodyState.observe(this) { state ->
            binding.llIdleState.visibility       = if (state == SearchViewModel.State.IDLE)    View.VISIBLE else View.GONE
            binding.llListingsShimmer.visibility = if (state == SearchViewModel.State.LOADING) View.VISIBLE else View.GONE
            binding.rvResults.visibility         = if (state == SearchViewModel.State.RESULTS) View.VISIBLE else View.GONE
            binding.llEmptyState.visibility      = if (state == SearchViewModel.State.EMPTY)   View.VISIBLE else View.GONE
            if (state == SearchViewModel.State.LOADING) animateShimmer(binding.llListingsShimmer)
        }

        vm.results.observe(this) { listings ->
            resultsAdapter.updateData(listings)
        }

        vm.isPagingLoading.observe(this) { loading ->
            resultsAdapter.setFooterLoading(loading)
        }

        vm.currentQuery.observe(this) { q ->
            binding.tvEmptyQuery.text = if (!q.isNullOrBlank()) "لا توجد نتائج لـ \"$q\"" else ""
        }

        vm.errorEvent.observe(this) { msg ->
            if (!msg.isNullOrEmpty())
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun setupNavigation() {
        BottomNavHelper.setup(this, NavScreen.NONE)
    }

    // ── Keyboard helpers ──────────────────────────────────────────────────────

    private fun showKeyboard() {
        binding.etSearchQuery.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchQuery.windowToken, 0)
    }

    private fun animateShimmer(view: View) {
        android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.4f, 1f, 0.4f).apply {
            duration = 1200
            repeatCount = android.animation.ValueAnimator.INFINITE
            start()
        }
    }
}
