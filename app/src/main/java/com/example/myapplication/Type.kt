package com.example.myapplication

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.LayoutInflaterCompat
import androidx.core.widget.TextViewCompat
import java.lang.reflect.Constructor
import java.util.concurrent.ConcurrentHashMap

/*
 * ════════════════════════════════════════════════════════════════════════════
 *  Find typography — single source of truth.
 *
 *  The Figma design sets every text layer to "Inter Regular". Inter has no
 *  Arabic glyphs, so Figma draws Arabic with "Noto Sans Arabic" while Latin
 *  letters, digits ("3,000,000"), spaces and punctuation stay in Inter.
 *  FindFonts reproduces exactly that, per glyph:
 *
 *    API 29+  Typeface.CustomFallbackBuilder: Inter primary → Noto Sans Arabic
 *             fallback → system sans-serif (only for glyphs neither font has).
 *             The fonts are bundled, so Samsung/OEM system-font replacement
 *             can't change them.
 *    API <29  Noto Sans Arabic alone (Arabic stays correct; Latin/digits come
 *             from Noto's own Latin set and look slightly different).
 *
 *  Files: res/font/inter_regular.ttf, res/font/noto_sans_arabic_regular.ttf
 *  XML styles: res/values/styles_text.xml (Find.Text.* / TextAppearance.Find.*)
 * ════════════════════════════════════════════════════════════════════════════
 */

// ── Typeface ────────────────────────────────────────────────────────────────

object FindFonts {

    /** The one and only API-level switch for fonts. */
    val usesPerGlyphFallback: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @Volatile private var cached: Typeface? = null

    /** Inter + Noto Sans Arabic typeface (Regular 400). Built once, then cached. */
    fun typeface(context: Context): Typeface =
        cached ?: synchronized(this) {
            cached ?: build(context.applicationContext ?: context).also { cached = it }
        }

    /** Compose wrapper around the same Typeface. */
    fun composeFamily(context: Context): FontFamily = FontFamily(typeface(context))

    private fun build(ctx: Context): Typeface = try {
        if (usesPerGlyphFallback) buildWithFallback(ctx)
        else ResourcesCompat.getFont(ctx, R.font.noto_sans_arabic_regular) ?: Typeface.DEFAULT
    } catch (t: Throwable) {
        // Never crash inflation because of a font; fall back to the system font.
        Typeface.DEFAULT
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildWithFallback(ctx: Context): Typeface {
        val res = ctx.resources
        val regular = FontStyle(FontStyle.FONT_WEIGHT_NORMAL, FontStyle.FONT_SLANT_UPRIGHT)

        val inter = android.graphics.fonts.FontFamily.Builder(
            Font.Builder(res, R.font.inter_regular)
                .setWeight(FontStyle.FONT_WEIGHT_NORMAL)
                .setSlant(FontStyle.FONT_SLANT_UPRIGHT)
                .build()
        ).build()

        val notoArabic = android.graphics.fonts.FontFamily.Builder(
            Font.Builder(res, R.font.noto_sans_arabic_regular)
                .setWeight(FontStyle.FONT_WEIGHT_NORMAL)
                .setSlant(FontStyle.FONT_SLANT_UPRIGHT)
                .build()
        ).build()

        return Typeface.CustomFallbackBuilder(inter)
            .addCustomFallback(notoArabic)
            .setSystemFallback("sans-serif")
            .setStyle(regular)
            .build()
    }
}

// ── XML views: apply the typeface to every TextView at inflation ─────────────

/**
 * Wraps AppCompat's view inflater so every TextView / EditText / Button (and any
 * custom TextView subclass such as StrokeTextView) gets the Find typeface,
 * Regular weight. Installed for every AppCompatActivity by [install] (API 29+)
 * and by BaseActivity (all API levels). Dialogs inherit it via cloned inflaters.
 */
object FindTypefaceInflater {

    /** Call once from Application.onCreate. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Runs before Activity.onCreate (API 29+) — i.e. before AppCompat installs its factory.
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                (activity as? AppCompatActivity)?.let { install(it) }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Must run BEFORE super.onCreate() of the activity. Safe to call twice. */
    fun install(activity: AppCompatActivity) {
        val inflater = activity.layoutInflater
        if (inflater.factory2 != null || inflater.factory != null) return
        LayoutInflaterCompat.setFactory2(inflater, Factory(activity))
    }

    /** Apply the Find typeface (Regular) to a TextView created in code. */
    fun apply(view: TextView) {
        view.typeface = FindFonts.typeface(view.context)
    }

    private class Factory(private val activity: AppCompatActivity) : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
            val view = activity.delegate.createView(parent, name, context, attrs)
                ?: createTextViewSubclass(name, context, attrs)
            if (view is TextView) apply(view)
            return view
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
            onCreateView(null, name, context, attrs)
    }

    private val constructors = ConcurrentHashMap<String, Constructor<out View>>()
    private val notTextViews = ConcurrentHashMap.newKeySet<String>()

    /**
     * AppCompat only maps framework names (TextView, Button, EditText…). For fully
     * qualified custom views we create the view ourselves — but ONLY when it is a
     * TextView subclass; everything else returns null and LayoutInflater (and the
     * Fragment/Activity private factory) handles it exactly as before.
     */
    private fun createTextViewSubclass(name: String, context: Context, attrs: AttributeSet): View? {
        if (!name.contains('.') || name in notTextViews) return null
        return try {
            val ctor = constructors[name] ?: run {
                val cls = Class.forName(name, false, context.classLoader)
                if (!TextView::class.java.isAssignableFrom(cls)) {
                    notTextViews += name
                    return null
                }
                @Suppress("UNCHECKED_CAST")
                (cls as Class<out View>).getConstructor(Context::class.java, AttributeSet::class.java)
                    .also { constructors[name] = it }
            }
            ctor.newInstance(context, attrs)
        } catch (t: Throwable) {
            null
        }
    }
}

// ── Compose: type scale ─────────────────────────────────────────────────────

/** Every text style from the Figma type scale. Regular 400, no letter spacing. */
@Immutable
data class FindTextStyles(
    /** 24 / 29, #000000 */
    val adTitle: TextStyle,
    /** 16 / 22 — ad description */
    val adBody: TextStyle,
    /** 16 / 19 — use [tabsInactive] for unselected tabs (40% opacity) */
    val tabs: TextStyle,
    val tabsInactive: TextStyle,
    /** 16 / 19, #505050 */
    val searchPlaceholder: TextStyle,
    /** 15 / 18 — Call / WhatsApp / Chat */
    val actionLabel: TextStyle,
    /** 11 / 13, black 85% */
    val tabBarLabel: TextStyle,
    /** 10 / 12 — location, time, price, advertiser */
    val caption: TextStyle,
) {
    companion object {
        fun create(family: FontFamily, dark: Boolean = false): FindTextStyles {
            val primary = if (dark) Color.White else Color.Black
            return FindTextStyles(
                adTitle = findStyle(family, 24.sp, 29.sp, primary),
                adBody = findStyle(family, 16.sp, 22.sp),
                tabs = findStyle(family, 16.sp, 19.sp, Color.Black),          // tabs sit on the yellow bar
                tabsInactive = findStyle(family, 16.sp, 19.sp, Color.Black.copy(alpha = 0.40f)),
                searchPlaceholder = findStyle(family, 16.sp, 19.sp,
                    if (dark) Color(0xFF6F6F73) else Color(0xFF505050)),
                actionLabel = findStyle(family, 15.sp, 18.sp),
                tabBarLabel = findStyle(family, 11.sp, 13.sp, Color.Black.copy(alpha = 0.85f)),
                caption = findStyle(family, 10.sp, 12.sp),
            )
        }
    }
}

private fun findStyle(
    family: FontFamily,
    size: TextUnit,
    lineHeight: TextUnit,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight.Normal,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Material 3 Typography where every slot uses the Find family (Regular). */
fun findMaterialTypography(family: FontFamily, s: FindTextStyles): Typography {
    val base = Typography()
    fun TextStyle.find() = merge(
        TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    )
    return base.copy(
        displayLarge = base.displayLarge.find(),
        displayMedium = base.displayMedium.find(),
        displaySmall = base.displaySmall.find(),
        headlineLarge = s.adTitle,
        headlineMedium = base.headlineMedium.find(),
        headlineSmall = base.headlineSmall.find(),
        titleLarge = s.adTitle,
        titleMedium = s.tabs.copy(color = Color.Unspecified),
        titleSmall = s.actionLabel,
        bodyLarge = s.adBody,
        bodyMedium = s.actionLabel,
        bodySmall = s.caption,
        labelLarge = s.actionLabel,
        labelMedium = s.tabBarLabel.copy(color = Color.Unspecified),
        labelSmall = s.caption,
    )
}

val LocalFindTextStyles = staticCompositionLocalOf {
    FindTextStyles.create(FontFamily.Default)
}

/** Wrap any Compose UI in this so it uses the Find fonts and type scale. */
@Composable
fun FindTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val family = remember { FindFonts.composeFamily(context) }
    val styles = remember(family, dark) { FindTextStyles.create(family, dark) }
    val typography = remember(family, styles) { findMaterialTypography(family, styles) }
    MaterialTheme(typography = typography) {
        CompositionLocalProvider(LocalFindTextStyles provides styles, content = content)
    }
}

object FindType {
    val styles: FindTextStyles
        @Composable get() = LocalFindTextStyles.current
}

// ── Debug preview ───────────────────────────────────────────────────────────

internal const val PREVIEW_TITLE = "مزرعة للبيع بالقرب من طريق القدية"
internal const val PREVIEW_BODY = "يوجد مزرعة للبيع في المزاحمية بسعر 3,000,000 ريال."

/**
 * Every style, in Compose and in XML (Find.Text.* on a real TextView), with both
 * test sentences. Shown in Android Studio (@Preview) and in FontDebugActivity
 * (debug builds only).
 */
@Composable
fun FindTypeSpecimen(modifier: Modifier = Modifier) {
    val s = FindType.styles
    val rows = listOf(
        Triple("adTitle 24/29", s.adTitle, R.style.Find_Text_AdTitle),
        Triple("adBody 16/22", s.adBody, R.style.Find_Text_AdBody),
        Triple("tabs 16/19", s.tabs, R.style.Find_Text_Tabs),
        Triple("tabs inactive 40%", s.tabsInactive, R.style.Find_Text_Tabs),
        Triple("searchPlaceholder 16/19", s.searchPlaceholder, R.style.Find_Text_SearchPlaceholder),
        Triple("actionLabel 15/18", s.actionLabel, R.style.Find_Text_ActionLabel),
        Triple("tabBarLabel 11/13", s.tabBarLabel, R.style.Find_Text_TabBarLabel),
        Triple("caption 10/12", s.caption, R.style.Find_Text_Caption),
    )
    val mode = if (FindFonts.usesPerGlyphFallback)
        "API ${Build.VERSION.SDK_INT}: Inter + Noto Sans Arabic per-glyph fallback"
    else
        "API ${Build.VERSION.SDK_INT}: Noto Sans Arabic only (below API 29)"

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(mode, style = s.caption.copy(color = Color(0xFF007AFF)))
            rows.forEach { (label, style, xmlStyle) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = s.caption.copy(color = Color(0xFF007AFF), textAlign = TextAlign.Start))
                    Text("Compose", style = s.caption.copy(color = Color.Gray))
                    // Specimen is always on white, so draw it black (40% black for inactive tabs).
                    val ink = if (style == s.tabsInactive) Color.Black.copy(alpha = 0.40f) else Color.Black
                    Text(PREVIEW_TITLE, style = style.copy(color = ink), modifier = Modifier.fillMaxWidth())
                    Text(PREVIEW_BODY, style = style.copy(color = ink), modifier = Modifier.fillMaxWidth())
                    Text("XML (Find.Text.*)", style = s.caption.copy(color = Color.Gray))
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { ctx -> xmlSpecimen(ctx, xmlStyle, inactive = label.contains("inactive")) },
                    )
                }
            }
        }
    }
}

private val xmlLineHeightsSp = mapOf(
    R.style.Find_Text_AdTitle to 29f,
    R.style.Find_Text_AdBody to 22f,
    R.style.Find_Text_Tabs to 19f,
    R.style.Find_Text_SearchPlaceholder to 19f,
    R.style.Find_Text_ActionLabel to 18f,
    R.style.Find_Text_TabBarLabel to 13f,
    R.style.Find_Text_Caption to 12f,
)

/** A real TextView inflated with the XML style, exactly as the layouts use it. */
private fun xmlSpecimen(ctx: Context, @StyleRes style: Int, inactive: Boolean): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        listOf(PREVIEW_TITLE, PREVIEW_BODY).forEach { line ->
            addView(TextView(ctx, null, 0, style).apply {
                text = line
                setTextColor(if (inactive) 0x66000000 else 0xFF000000.toInt())
                // A plain TextView reads android:lineHeight only on API 28+; set it for all levels.
                xmlLineHeightsSp[style]?.let { sp ->
                    TextViewCompat.setLineHeight(
                        this,
                        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()
                    )
                }
                FindTypefaceInflater.apply(this)
            })
        }
    }

@Preview(name = "Find type scale", showBackground = true, widthDp = 400, heightDp = 1600)
@Composable
private fun FindTypeSpecimenPreview() {
    FindTheme { FindTypeSpecimen() }
}
