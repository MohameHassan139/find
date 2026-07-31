package com.example.myapplication.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import com.example.myapplication.R

// Global Text Style Helper to prevent vertical clipping of Arabic diacritics
val ArabicTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(
        includeFontPadding = false
    )
)

@Composable
fun MyAdsScreen() {
    val isDark = isSystemInDarkTheme()
    val screenBg = if (isDark) Color(0xFF121212) else Color(0xFFF4F4F4)
    val dividerColor = if (isDark) Color(0xFF2E2E2E) else Color.Black.copy(alpha = 0.15f)

    val offerText = stringResource(id = R.string.filter_offer)
    val requestText = stringResource(id = R.string.filter_request)
    var selectedSegment by remember(offerText) { mutableStateOf(offerText) }
    var isAdVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Component 1: Screen Header Title Row
        HeaderTitleRow()

        // Component 2: Segmented Switcher Pills
        SegmentedSwitcher(
            selectedSegment = selectedSegment,
            onSegmentSelected = { selectedSegment = it }
        )

        // Divider under switcher
        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )

        // Component 3: Ad Card Item View
        AdCardItem(
            title = "عقر لي البيع",
            username = "mMohammmed",
            price = "5000 ريال",
            location = "منطقة مكة المكرمة",
            time = "الآن",
            isAdVisible = isAdVisible,
            onVisibilityChange = { isAdVisible = it }
        )
    }
}

@Composable
fun HeaderTitleRow() {
    val isDark = isSystemInDarkTheme()
    val iconBg = if (isDark) Color(0xFF2A2000) else Color(0xFFFFF4CC)
    val textColor = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Megaphone Icon Box Wrapper
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 42.dp)
                .background(iconBg, shape = RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📢",
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title Text Label
        Text(
            text = stringResource(id = R.string.my_ads_title) + ":",
            style = ArabicTextStyle.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )
    }
}

@Composable
fun SegmentedSwitcher(
    selectedSegment: String,
    onSegmentSelected: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgSwitcher = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF4F4F4)
    val activeBorder = Color(0xFF007AFF)
    val textColor = if (isDark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)

    val offerText = stringResource(id = R.string.filter_offer)
    val requestText = stringResource(id = R.string.filter_request)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf(offerText, requestText).forEach { segment ->
            val isActive = selectedSegment == segment

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(41.dp)
                    .background(bgSwitcher, shape = RoundedCornerShape(50.dp))
                    .then(
                        if (isActive) {
                            Modifier.border(1.dp, activeBorder, RoundedCornerShape(50.dp))
                        } else Modifier
                    )
                    .clickable { onSegmentSelected(segment) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = segment,
                    style = ArabicTextStyle.copy(
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) textColor else textColor.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
fun AdCardItem(
    title: String,
    username: String,
    price: String,
    location: String,
    time: String,
    isAdVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF252525) else Color.White
    val cardBorder = if (isDark) Color(0xFF444444) else Color.Black
    val textColor = if (isDark) Color(0xFFF0F0F0) else Color.Black
    val grayColor = if (isDark) Color(0xFFAAAAAA) else Color.Gray
    val imagePlaceholderBg = if (isDark) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    val editBtnBg = if (isDark) Color(0xFF2A2000) else Color(0xFFFFF4CC)
    val editBtnText = if (isDark) Color(0xFFFFF4CC) else Color.Black
    val cardDividerColor = if (isDark) Color(0xFF48484A) else Color.Black.copy(alpha = 0.1f)
    val verticalDividerColor = if (isDark) Color(0xFF48484A) else Color.Black.copy(alpha = 0.12f)

    // Relative corner shape: topEnd and bottomEnd map to top-left and bottom-left in RTL mode
    // This correctly rounds the outer boundary of the card when the image is rendered on the left
    val imageShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 10.dp,
        bottomEnd = 10.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg, shape = RoundedCornerShape(10.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
    ) {
        // Top Content Section (Row, 130.dp height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            // Right Details Area (Column) — placed first in RTL Row to render on the Right
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                // Line 1: Main ad title text
                Text(
                    text = title,
                    style = ArabicTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ),
                    maxLines = 2,
                    textAlign = TextAlign.Right
                )

                // Line 2: Profile & Price on the exact SAME horizontal baseline row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Price tag
                    Text(
                        text = price,
                        style = ArabicTextStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )

                    // User profile Group
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = username,
                            style = ArabicTextStyle.copy(
                                fontSize = 11.sp,
                                color = grayColor
                            )
                        )
                        // Circular profile picture avatar wrapper
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(0.5.dp, grayColor.copy(alpha = 0.5f), CircleShape)
                                .background(imagePlaceholderBg, shape = CircleShape)
                        )
                    }
                }

                // Line 3: Location and Time Block
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = location,
                        style = ArabicTextStyle.copy(
                            fontSize = 10.sp,
                            color = grayColor
                        )
                    )
                    Text(
                        text = "•",
                        style = ArabicTextStyle.copy(
                            fontSize = 10.sp,
                            color = grayColor
                        )
                    )
                    Text(
                        text = time,
                        style = ArabicTextStyle.copy(
                            fontSize = 10.sp,
                            color = grayColor
                        )
                    )
                }
            }

            // Left Image Viewport Box (renders on the Left in RTL)
            Box(
                modifier = Modifier
                    .size(width = 147.dp, height = 130.dp)
                    .clip(imageShape)
                    .background(imagePlaceholderBg)
            ) {
                Text(
                    text = "صورة الإعلان",
                    style = ArabicTextStyle.copy(fontSize = 12.sp, color = grayColor),
                    modifier = Modifier.align(Alignment.Center)
                )

                // Left Arrow Overlay (<) — Bigger chevron, positioned at bottom-left corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 4.dp)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // Right Arrow Overlay (>) — Bigger chevron, positioned at bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "›",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Bottom panel divider (horizontal line separating top block and bottom panel)
        HorizontalDivider(
            color = cardDividerColor,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )

        // Bottom Panel (60.dp height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Interactive Region (Buttons stack)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Edit Button
                    Box(
                        modifier = Modifier
                            .size(width = 87.dp, height = 24.dp)
                            .background(editBtnBg, shape = RoundedCornerShape(5.dp))
                            .border(0.5.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تعديل",
                            style = ArabicTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = editBtnText
                            )
                        )
                    }

                    // Delete Button
                    Box(
                        modifier = Modifier
                            .size(width = 87.dp, height = 24.dp)
                            .background(Color(0xFFFF3B30), shape = RoundedCornerShape(5.dp))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "حذف",
                            style = ArabicTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // Central Vertical Divider Symmetrical Axis
            VerticalDivider(
                color = verticalDividerColor,
                thickness = 1.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
            )

            // Right Interactive Region (Visibility toggles centered) — text first, switch second
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val greenColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF34C759)
                    val switchTrackColor = if (isDark) Color(0xFF3E3E3E) else Color(0xFFE0E0E0)

                    Text(
                        text = if (isAdVisible) stringResource(id = R.string.ad_visible) else stringResource(id = R.string.ad_hidden),
                        style = ArabicTextStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdVisible) greenColor else grayColor
                        )
                    )

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Switch(
                            checked = isAdVisible,
                            onCheckedChange = onVisibilityChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = greenColor,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = switchTrackColor
                            )
                        )
                    }
                }
            }
        }
    }
}
