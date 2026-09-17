package com.example.myapplication.utils

import android.content.Context
import android.content.res.Configuration
import android.widget.ImageView
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/**
 * Helper to construct URLs and load SVG icons for categories and subcategories
 * from the Supabase bucket:
 * Base: https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/Finds-media/
 * Light mode path: Finds-media/light/{icon_name}.svg
 * Dark mode path:  Finds-media/dark/{icon_name}.svg
 */
object CategoryIconHelper {

    private const val BASE_URL =
        "https://ocebfvgwgpebjxetnixc.supabase.co/storage/v1/object/public/listings-images/Finds-media/"

    @Volatile
    private var imageLoader: ImageLoader? = null

    fun getImageLoader(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(SvgDecoder.Factory())
                }
                .build().also { imageLoader = it }
        }
    }

    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Resolves the full URL for an icon name and theme mode.
     */
    fun getSvgUrl(iconName: String?, isDark: Boolean): String? {
        if (iconName.isNullOrBlank()) return null
        val trimmed = iconName.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }

        val folder = if (isDark) "dark" else "light"
        val baseName = trimmed
            .removeSuffix(".png")
            .removeSuffix(".PNG")
            .removeSuffix(".svg")
            .removeSuffix(".SVG")

        return "$BASE_URL$folder/$baseName.svg"
    }

    /**
     * Loads an SVG icon into the target ImageView using Coil.
     */
    fun loadSvg(
        imageView: ImageView,
        iconNameOrUrl: String?,
        isDark: Boolean = isNightMode(imageView.context),
        placeholderRes: Int? = null
    ) {
        if (iconNameOrUrl.isNullOrBlank()) {
            imageView.setImageDrawable(null)
            return
        }

        val url = if (iconNameOrUrl.startsWith("http://", ignoreCase = true) ||
            iconNameOrUrl.startsWith("https://", ignoreCase = true)
        ) {
            iconNameOrUrl
        } else {
            getSvgUrl(iconNameOrUrl, isDark)
        } ?: run {
            imageView.setImageDrawable(null)
            return
        }

        val loader = getImageLoader(imageView.context)
        val request = ImageRequest.Builder(imageView.context)
            .data(url)
            .target(imageView)
            .apply {
                if (placeholderRes != null) {
                    placeholder(placeholderRes)
                    error(placeholderRes)
                }
            }
            .build()

        loader.enqueue(request)
    }
}
