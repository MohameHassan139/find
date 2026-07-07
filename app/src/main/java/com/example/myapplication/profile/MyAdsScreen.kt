package com.example.myapplication.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle

// Global Text Style Helper to prevent vertical clipping of Arabic diacritics
val ArabicTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(
        includeFontPadding = false
    )
)

@Composable
fun MyAdsScreen() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        var selectedSegment by remember { mutableStateOf("العرض") }
        var isAdVisible by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F4F4))
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

            // Divider under switcher (15% black)
            HorizontalDivider(
                color = Color.Black.copy(alpha = 0.15f),
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
}

@Composable
fun HeaderTitleRow() {
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
                .background(Color(0xFFFFF4CC), shape = RoundedCornerShape(5.dp)),
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
            text = "إعلاناتي:",
            style = ArabicTextStyle.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        )
    }
}

@Composable
fun SegmentedSwitcher(
    selectedSegment: String,
    onSegmentSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf("العرض", "الطلب").forEach { segment ->
            val isActive = selectedSegment == segment

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(41.dp)
                    .background(Color(0xFFF4F4F4), shape = RoundedCornerShape(50.dp))
                    .then(
                        if (isActive) {
                            Modifier.border(1.dp, Color(0xFF007AFF), RoundedCornerShape(50.dp))
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
                        color = if (isActive) Color.Black else Color.Black.copy(alpha = 0.4f)
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
            .background(Color.White, shape = RoundedCornerShape(10.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(10.dp))
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
                        color = Color.Black
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
                            color = Color.Black
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
                                color = Color.Gray
                            )
                        )
                        // Circular profile picture avatar wrapper
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(0.5.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                                .background(Color(0xFFE0E0E0), shape = CircleShape)
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
                            color = Color.Gray
                        )
                    )
                    Text(
                        text = "•",
                        style = ArabicTextStyle.copy(
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    )
                    Text(
                        text = time,
                        style = ArabicTextStyle.copy(
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    )
                }
            }

            // Left Image Viewport Box (renders on the Left in RTL)
            Box(
                modifier = Modifier
                    .size(width = 147.dp, height = 130.dp)
                    .clip(imageShape)
                    .background(Color(0xFFE0E0E0))
            ) {
                Text(
                    text = "صورة الإعلان",
                    style = ArabicTextStyle.copy(fontSize = 12.sp, color = Color.Gray),
                    modifier = Modifier.align(Alignment.Center)
                )

                // Left Arrow Overlay (<) — Thin, crisp chevron without blocky background
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                }

                // Right Arrow Overlay (>) — Thin, crisp chevron without blocky background
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "›",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraLight
                    )
                }
            }
        }

        // Bottom panel divider (horizontal line separating top block and bottom panel)
        HorizontalDivider(
            color = Color.Black.copy(alpha = 0.1f),
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
                            .background(Color(0xFFFFF4CC), shape = RoundedCornerShape(5.dp))
                            .border(0.5.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تعديل",
                            style = ArabicTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
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
                color = Color.Black.copy(alpha = 0.12f),
                thickness = 1.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
            )

            // Right Interactive Region (Visibility toggles centered)
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
                    Switch(
                        checked = isAdVisible,
                        onCheckedChange = onVisibilityChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE9E9EB)
                        )
                    )

                    Text(
                        text = if (isAdVisible) "عرض" else "مخفي",
                        style = ArabicTextStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}
