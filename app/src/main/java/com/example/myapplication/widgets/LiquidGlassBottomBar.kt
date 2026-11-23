package com.example.myapplication.widgets

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.FindFonts
import com.example.myapplication.NavScreen
import com.example.myapplication.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs

/**
 * Tab item descriptor for the bottom navigation bar.
 */
data class BottomTabItem(
    val screen: NavScreen,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val iconBoldRes: Int,
    @param:StringRes val labelRes: Int
)

val BOTTOM_BAR_TABS = listOf(
    BottomTabItem(
        screen = NavScreen.HOME,
        iconRes = R.drawable.ic_nav_home,
        iconBoldRes = R.drawable.ic_nav_home_bold,
        labelRes = R.string.nav_home
    ),
    BottomTabItem(
        screen = NavScreen.ADD,
        iconRes = R.drawable.ic_nav_add,
        iconBoldRes = R.drawable.ic_nav_add_bold,
        labelRes = R.string.add_ad
    ),
    BottomTabItem(
        screen = NavScreen.CHAT,
        iconRes = R.drawable.ic_nav_chat,
        iconBoldRes = R.drawable.ic_nav_chat_bold,
        labelRes = R.string.nav_chats
    )
)

/**
 * iOS-styled "Liquid Glass" bottom navigation tab bar.
 *
 * LAYOUT:
 * - Floating capsule (RoundedCornerShape(50)), height 65.dp, aligned to bottom over content
 * - 50.dp horizontal margin, 8.dp inner padding
 * - 3 equal-weight tabs following LayoutDirection (Arabic: Home on the right)
 * - Each tab: 28.dp icon, 4.dp gap, 10.sp label, maxLines 1
 *   - Selected: Black (White in dark theme), SemiBold
 *   - Not selected: #ADADAD, Normal
 *
 * BAR GLASS:
 * - Frosted blur of content behind the bar via Haze (hazeSource on content, hazeEffect on bar)
 *   blurRadius = 24.dp, tint = Color.White.copy(alpha = 0.2f) (or solid Color.White.copy(0.85f) on API < 31 / fallback)
 * - Rim: 1.dp border with Brush.verticalGradient(White 70% -> White 20%), same capsule shape
 * - Shadow: Modifier.shadow(12.dp, shape, ambientColor/spotColor = Black 50%) applied before clip
 *
 * SELECTION HIGHLIGHT:
 * - Shape: 95.dp x 80.dp, RoundedCornerShape(40.dp), taller than bar (80 vs 65) overflowing top & bottom
 * - Glass: same Haze effect with tint White 45% (fallback White 0.9f), plus 0.5.dp border in White 60%
 * - One shared highlight whose x offset animates with spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
 * - While dragging, animates scale to 1.04f
 * - Hidden when no tab is selected (activeTab == null or NavScreen.NONE)
 *
 * DRAG TO SWITCH:
 * - Horizontal drag on the bar: after 20.dp movement, move highlight one tab in drag direction (mirrored in RTL)
 * - On release, navigate to that tab
 *
 * CHAT BADGE:
 * - Red capsule with white bold 10.sp text, max "99", padding 5.dp horizontal and 2.dp vertical
 * - Offset (x = 20.dp, y = -28.dp) from Chats icon center
 */
@Composable
fun LiquidGlassBottomBar(
    selectedTab: NavScreen?,
    onTabSelected: (NavScreen) -> Unit,
    modifier: Modifier = Modifier,
    chatBadge: Int = 0,
    hazeState: HazeState? = null,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val barShape = remember { RoundedCornerShape(percent = 50) }
    val highlightShape = remember { RoundedCornerShape(40.dp) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var isDragging by remember { mutableStateOf(false) }
    var draggingTab by remember { mutableStateOf<NavScreen?>(null) }
    var accumulatedDragPx by remember { mutableFloatStateOf(0f) }

    val activeTab: NavScreen? = if (isDragging) {
        draggingTab ?: selectedTab
    } else {
        if (selectedTab == NavScreen.NONE) null else selectedTab
    }

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 20.dp.toPx() }

    // Outer container height accommodates the 80.dp highlight overflowing the 65.dp bar
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalWidth = maxWidth
        val barMarginHorizontal = 50.dp
        val barWidth = totalWidth - (barMarginHorizontal * 2)
        val innerPaddingHorizontal = 8.dp
        val tabsAreaWidth = barWidth - (innerPaddingHorizontal * 2)
        val singleTabWidth = tabsAreaWidth / 3f
        val highlightWidth = 95.dp
        val halfHighlightWidth = highlightWidth / 2f

        // Exact tab centers relative to the root Box's Start edge.
        // In Compose with Alignment.CenterStart and placeRelative offset,
        // Start is Left in LTR and Right in RTL.
        // In both LTR and RTL, Row lays out children from Start to End:
        // Tab 0 (Home) is at 0.5f * singleTabWidth from Start,
        // Tab 1 (Add) is at 1.5f * singleTabWidth from Start,
        // Tab 2 (Chat) is at 2.5f * singleTabWidth from Start.
        val homeCenter: Dp = barMarginHorizontal + innerPaddingHorizontal + (singleTabWidth * 0.5f)
        val addCenter: Dp = barMarginHorizontal + innerPaddingHorizontal + (singleTabWidth * 1.5f)
        val chatCenter: Dp = barMarginHorizontal + innerPaddingHorizontal + (singleTabWidth * 2.5f)

        val targetCenter: Dp = when (activeTab) {
            NavScreen.HOME -> homeCenter
            NavScreen.ADD -> addCenter
            NavScreen.CHAT -> chatCenter
            else -> homeCenter
        }

        val targetHighlightLeft = targetCenter - halfHighlightWidth

        // Shared highlight X offset animation (no crossfade)
        val animatedHighlightX by animateDpAsState(
            targetValue = targetHighlightLeft,
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "highlightX"
        )

        // Highlight scale while dragging (1.04f during drag, 1.0f otherwise)
        val animatedScale by animateFloatAsState(
            targetValue = if (isDragging) 1.04f else 1.0f,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "highlightScale"
        )

        // Highlight alpha: hide when no tab is selected
        val highlightAlpha by animateFloatAsState(
            targetValue = if (activeTab != null && activeTab != NavScreen.NONE) 1.0f else 0.0f,
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "highlightAlpha"
        )

        val canUseHaze = hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        // ── 1. Floating Capsule Bar Glass Background (height 65.dp, 50.dp horizontal margin) ──
        val barShadowColor = Color.Black.copy(alpha = 0.50f)
        val barRimBrush = remember(isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.20f)
                )
            )
        }
        val barFallbackColor = if (isDark) {
            Color(0xFF1E1E1E).copy(alpha = 0.85f)
        } else {
            Color.White.copy(alpha = 0.85f)
        }
        val barHazeTint = if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.20f)
        }

        Box(
            modifier = Modifier
                .padding(horizontal = barMarginHorizontal)
                .fillMaxWidth()
                .height(65.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = barShape,
                    ambientColor = barShadowColor,
                    spotColor = barShadowColor
                )
                .then(
                    if (canUseHaze) {
                        Modifier.hazeEffect(state = hazeState!!) {
                            blurRadius = 24.dp
                            tints = listOf(HazeTint(barHazeTint))
                        }
                    } else {
                        Modifier.background(barFallbackColor, barShape)
                    }
                )
                .clip(barShape)
                .border(
                    width = 1.dp,
                    brush = barRimBrush,
                    shape = barShape
                )
        )

        // ── 2. Selection Highlight (95.dp x 80.dp, overflows bar by 7.5.dp above & below) ──
        val highlightFallbackColor = if (isDark) {
            Color(0xFF2C2C2C).copy(alpha = 0.90f)
        } else {
            Color.White.copy(alpha = 0.90f)
        }
        val highlightHazeTint = if (isDark) {
            Color.White.copy(alpha = 0.35f)
        } else {
            Color.White.copy(alpha = 0.45f)
        }

        if (highlightAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = animatedHighlightX)
                    .size(width = highlightWidth, height = 80.dp)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        alpha = highlightAlpha
                    }
                    .then(
                        if (canUseHaze) {
                            Modifier.hazeEffect(state = hazeState!!) {
                                blurRadius = 24.dp
                                tints = listOf(HazeTint(highlightHazeTint))
                            }
                        } else {
                            Modifier.background(highlightFallbackColor, highlightShape)
                        }
                    )
                    .clip(highlightShape)
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.60f),
                        shape = highlightShape
                    )
            )
        }

        // ── 3. Tabs Row & Gesture Handling ──
        Row(
            modifier = Modifier
                .padding(horizontal = barMarginHorizontal)
                .fillMaxWidth()
                .height(65.dp)
                .padding(horizontal = innerPaddingHorizontal)
                .pointerInput(selectedTab, isRtl) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            accumulatedDragPx = 0f
                            val initial = if (selectedTab != null && selectedTab != NavScreen.NONE) {
                                selectedTab
                            } else {
                                NavScreen.HOME
                            }
                            draggingTab = initial
                        },
                        onDragEnd = {
                            isDragging = false
                            draggingTab?.let { releasedTab ->
                                if (releasedTab != selectedTab) {
                                    onTabSelected(releasedTab)
                                }
                            }
                            draggingTab = null
                            accumulatedDragPx = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            draggingTab = null
                            accumulatedDragPx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragPx += dragAmount
                            if (abs(accumulatedDragPx) >= dragThresholdPx) {
                                val current = draggingTab ?: selectedTab ?: NavScreen.HOME
                                val currentIndex = BOTTOM_BAR_TABS.indexOfFirst { it.screen == current }
                                    .coerceAtLeast(0)

                                // Drag direction relative to visual layout:
                                // In LTR: drag right (>0) moves to next tab; drag left (<0) moves to prev tab.
                                // In RTL: drag right (>0) moves towards Home (prev tab index);
                                //         drag left (<0) moves towards Chats (next tab index).
                                val goForward = if (isRtl) (accumulatedDragPx < 0) else (accumulatedDragPx > 0)
                                val newIndex = if (goForward) {
                                    minOf(currentIndex + 1, BOTTOM_BAR_TABS.size - 1)
                                } else {
                                    maxOf(currentIndex - 1, 0)
                                }
                                draggingTab = BOTTOM_BAR_TABS[newIndex].screen
                                accumulatedDragPx = 0f
                            }
                        }
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BOTTOM_BAR_TABS.forEach { tabItem ->
                val isSelected = (activeTab == tabItem.screen)
                BottomTabCell(
                    item = tabItem,
                    isSelected = isSelected,
                    chatBadge = if (tabItem.screen == NavScreen.CHAT) chatBadge else 0,
                    isDark = isDark,
                    onClick = { onTabSelected(tabItem.screen) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual tab cell inside the bottom navigation bar.
 */
@Composable
private fun BottomTabCell(
    item: BottomTabItem,
    isSelected: Boolean,
    chatBadge: Int,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontFamily = remember(context) { FindFonts.composeFamily(context) }

    val activeColor = if (isDark) Color.White else Color.Black
    val inactiveColor = Color(0xFFADADAD)
    val contentColor = if (isSelected) activeColor else inactiveColor

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(65.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon container (28.dp x 28.dp) with chat badge support
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isSelected) item.iconBoldRes else item.iconRes
                    ),
                    contentDescription = stringResource(id = item.labelRes),
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )

                // Chat Badge: Red capsule with white bold 10.sp text, max "99+",
                // positioned at top-trailing corner of Chats icon, comfortably inside the bar bounds.
                if (chatBadge > 0) {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Box(
                        modifier = Modifier
                            .align(if (isRtl) Alignment.TopStart else Alignment.TopEnd)
                            .offset(
                                x = if (isRtl) (-3).dp else 3.dp,
                                y = 0.dp
                            )
                            .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                            .background(Color(0xFFFF3B30), shape = RoundedCornerShape(percent = 50))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chatBadge > 99) "99+" else "$chatBadge",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            lineHeight = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Label: 10.sp, maxLines 1
            Text(
                text = stringResource(id = item.labelRes),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = fontFamily,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS & TEST HARNESS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Interactive preview with a colorful list scrolling behind the liquid glass bar,
 * showcasing real-time Haze background blur, tab switching spring animation,
 * drag-to-switch gesture with 1.04x scale, badge counter, and RTL/theme modes.
 */
@Composable
fun LiquidGlassBottomBarTestHarness(
    isDark: Boolean = false,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr
) {
    val hazeState = remember { HazeState() }
    var selectedTab by remember { mutableStateOf<NavScreen?>(NavScreen.HOME) }
    var badgeCount by remember { mutableIntStateOf(3) }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) Color(0xFF121212) else Color(0xFFF7F7F7))
        ) {
            // Scrollable list behind the bar marked with hazeSource
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (layoutDirection == LayoutDirection.Rtl) "تصميم الزجاج السائل الجديد" else "New Liquid Glass Design",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black
                    )
                    Text(
                        text = if (layoutDirection == LayoutDirection.Rtl) "مرر القائمة لمشاهدة تأثير الضباب الزجاجي خلف الشريط" else "Scroll to see the frosted blur effect behind the floating tab bar",
                        fontSize = 14.sp,
                        color = Color(0xFF888888)
                    )
                }

                items(25) { index ->
                    val cardColors = listOf(
                        Color(0xFFE57373),
                        Color(0xFF81C784),
                        Color(0xFF64B5F6),
                        Color(0xFFFFB74D),
                        Color(0xFFBA68C8),
                        Color(0xFF4DB6AC)
                    )
                    val cardBg = cardColors[index % cardColors.size].copy(alpha = if (isDark) 0.35f else 0.85f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(cardBg, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (layoutDirection == LayoutDirection.Rtl) "بطاقة إعلان رقم #${index + 1}" else "Listing Card #${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                }

                item {
                    // Extra padding so bottom item can scroll completely above the floating bar
                    Spacer(modifier = Modifier.height(110.dp))
                }
            }

            // Floating Liquid Glass Bar over the scrolling content
            LiquidGlassBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                chatBadge = badgeCount,
                hazeState = hazeState,
                isDark = isDark,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Preview(name = "Light Mode - English", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassLightEnglish() {
    LiquidGlassBottomBarTestHarness(isDark = false, layoutDirection = LayoutDirection.Ltr)
}

@Preview(name = "Dark Mode - English", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassDarkEnglish() {
    LiquidGlassBottomBarTestHarness(isDark = true, layoutDirection = LayoutDirection.Ltr)
}

@Preview(name = "Light Mode - Arabic RTL", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassLightArabic() {
    LiquidGlassBottomBarTestHarness(isDark = false, layoutDirection = LayoutDirection.Rtl)
}

@Preview(name = "Dark Mode - Arabic RTL", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassDarkArabic() {
    LiquidGlassBottomBarTestHarness(isDark = true, layoutDirection = LayoutDirection.Rtl)
}
