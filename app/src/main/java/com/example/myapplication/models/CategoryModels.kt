package com.example.myapplication.models

data class Category(
    val id: String,
    val name: String,
    var subItems: List<Category> = emptyList(),
    val filterOptions: List<FilterOption> = emptyList(),
    val iconUrl: String? = null,
    val isHome: Boolean = false,
    val isFilterOption: Boolean = false  // True for items derived from filter_options (e.g. car brands)
)

data class FilterOption(
    val id: String,
    val name: String
)

data class FilterType(
    val id: String,
    val name: String
)

data class Region(
    val id: String,
    val name: String
)
