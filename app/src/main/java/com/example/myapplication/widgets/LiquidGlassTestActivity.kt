package com.example.myapplication.widgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.NavScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Interactive test activity for the new iOS Liquid Glass Bottom Navigation Bar.
 * Demonstrates:
 * - Real-time frosted glass blur of list content scrolling behind the bar (Haze)
 * - Shared selection highlight with spring animation (dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
 * - Drag-to-switch gesture with 20.dp threshold and 1.04x scale animation
 * - Chat badge (red capsule, bold text, max 99, offset)
 * - Light & Dark theme toggle
 * - English (LTR) & Arabic (RTL) toggle
 */
class LiquidGlassTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var isDark by remember { mutableStateOf(false) }
                var isArabic by remember { mutableStateOf(false) }

                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    TestScreenContent(
                        isDark = isDark,
                        isArabic = isArabic,
                        onToggleTheme = { isDark = !isDark },
                        onToggleLanguage = { isArabic = !isArabic }
                    )
                }
            }
        }
        setContentView(composeView)
    }
}

@Composable
private fun TestScreenContent(
    isDark: Boolean,
    isArabic: Boolean,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val hazeState = remember { HazeState() }
    var selectedTab by remember { mutableStateOf<NavScreen?>(NavScreen.HOME) }
    var badgeCount by remember { mutableIntStateOf(5) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF121212) else Color(0xFFF7F7F7))
    ) {
        // Content marked as hazeSource so the bar can blur it
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = if (isArabic) "تجربة شريط التنقل الزجاجي السائل" else "Liquid Glass Bottom Bar Test",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isArabic)
                        "مرر القائمة لمشاهدة تأثير الضباب الزجاجي خلف الشريط أثناء التنقل أو السحب."
                    else
                        "Scroll the list to see real-time frosted glass blur behind the bar. Drag or tap to switch tabs.",
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Control panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleTheme,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0),
                            contentColor = if (isDark) Color.White else Color.Black
                        )
                    ) {
                        Text(if (isDark) "☀️ Light" else "🌙 Dark", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onToggleLanguage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0),
                            contentColor = if (isDark) Color.White else Color.Black
                        )
                    ) {
                        Text(if (isArabic) "English (LTR)" else "العربية (RTL)", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { badgeCount = (badgeCount + 1) % 105 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0),
                            contentColor = if (isDark) Color.White else Color.Black
                        )
                    ) {
                        Text("Badge: $badgeCount", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            items(30) { index ->
                val cardColors = listOf(
                    Color(0xFFE57373),
                    Color(0xFF81C784),
                    Color(0xFF64B5F6),
                    Color(0xFFFFB74D),
                    Color(0xFFBA68C8),
                    Color(0xFF4DB6AC),
                    Color(0xFFFF8A65),
                    Color(0xFFAED581)
                )
                val cardColor = cardColors[index % cardColors.size].copy(alpha = if (isDark) 0.35f else 0.85f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .background(cardColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "إعلان تجريبي رقم #${index + 1}" else "Sample Listing Item #${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isArabic) "مرر هذا العنصر تحت الشريط لاختبار الشفافية" else "Scroll this item behind the bar to test frosted glass",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFFE0E0E0) else Color(0xFF333333)
                        )
                    }
                }
            }

            item {
                // Ensure last card can scroll above the floating bottom bar
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // The Floating Liquid Glass Bottom Bar
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
