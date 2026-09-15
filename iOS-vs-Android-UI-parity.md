# Finds — iOS vs Android UI parity analysis

Source of truth: `ios code/find/find` (SwiftUI).
Target: `app/src/main` (Android XML views).

Everything below is read directly from both codebases. Values marked **✗** differ, **✓** already match.

---

## 0. Build fix applied

`:app:processDebugResources` was failing on two missing string keys. Both layouts now point at the
iOS-parity strings that already existed in `strings.xml`:

| File | Was | Now |
|---|---|---|
| `layout/activity_favorites.xml:64` | `@string/favorites_title` (undefined) | `@string/ios_favorites_title` |
| `layout/activity_my_ads.xml:167` | `@string/my_ads_empty` (undefined) | `@string/ios_myAds_empty` |

I also swept every layout for dangling `@string` / `@color` / `@dimen` / `@style` references — those
two were the only real ones, so the resource-link step should pass now.

---

## 1. The big one: the font is wrong

| | iOS | Android |
|---|---|---|
| Family | **Inter** (`App/AppFont.swift`) | **Cairo** (`res/font/cairo.xml`, Google Fonts provider) |
| Faces used | Regular 400, Medium 500, SemiBold 600, Bold 700 | 400 only |

Every single `.font(.app(size, weight:))` call in the iOS app resolves to Inter. Android renders
everything in Cairo at one weight. No amount of colour/size matching will make the two look alike
until this is fixed.

**Fix:** bundle `Inter-Regular/Medium/SemiBold/Bold.ttf` into `res/font/`, declare an
`inter.xml` font-family with the four weights, and set `android:fontFamily="@font/inter"` on the
base theme. Keep Cairo only as the Arabic fallback if Inter's Arabic coverage is unacceptable —
but iOS ships Inter for Arabic too, so true parity means Inter everywhere.

---

## 2. Colour tokens

iOS has ~20 semantic tokens in `Core/Stores/AppTheme.swift`. Android has 100+ in `colors.xml`.
This is the authoritative mapping.

### 2.1 Mismatches to fix

| iOS token | iOS light | iOS dark | Android name | Android light | Android dark | |
|---|---|---|---|---|---|---|
| `fullAppText` | `#000000` | `#FFFFFF` | `text_primary` | `#1A1A1A` | `#F0F0F0` | ✗ |
| `secondaryText` | `#6C6C6C` | `#ADADAD` | `text_secondary` | `#888888` | `#AAAAAA` | ✗ |
| `inactive` | `#ADADAD` | `#ADADAD` | `nav_icon_unselected` | `#4D000000` | `#4D000000` | ✗ |
| `cardBackground` | `#FFFFFF` | `#000000` | `surface_primary` | `#FFFFFF` | `#252525` | ✗ dark |
| `background` | `#F0EFEF` | `#1C1C1E` | `bg_screen` | `#F5F5F5` | `#121212` | ✗ |
| `fullAppBackground` | `#F5F5F5` | `#1C1C1E` | *(none)* | — | — | ✗ missing |
| `fieldBackground` | `#F2F2F7` | `#3A3A3C` | `search_bar_bg` | `#F4F4F4` | `#3A3A3C` | ✗ light |
| `fieldBackground` | `#F2F2F7` | `#3A3A3C` | `input_bg` | `#F5F5F5` | `#2A2A2A` | ✗ |
| `fieldBorder` | `#00000033` | `#FFFFFF33` | `stroke_field` | `#DCDCDC` | `#666666` | ✗ |
| `accent` | `#F5C518` | `#F5C518` | `find_primary` | `#C8A96E` | `#C8A96E` | ✗ |
| `deleteAccountButtonBackground` | `#FF3B30` | `#FF453A` | `error_red` | `#E24B4A` | `#CF4444` | ✗ |
| `offerBadge` | `#34C759` | `#30D158` | `toggle_active_green` | `#34C759` | `#4CAF50` | ✗ dark |
| `requestBadge` | `#FF9500` | `#FF9F0A` | *(none)* | — | — | ✗ missing |
| `SettingsNavRowBackground` | `#FFFFFF` | `#2C2C2E` | `surface_menu_item` | `#FFFFFF` | `#252525` | ✗ dark |
| `pickerRowBackground` | `#FFFFFF` | `#2C2C2E` | `surface_field` | `#FFFFFF` | `#252525` | ✗ dark |
| `shadowColor` | `#00000019` (10 %) | `#00000066` (40 %) | — | — | — | ✗ missing |

### 2.2 Already correct — don't touch

| iOS | Value | Android |
|---|---|---|
| `fullAppPrimary` | `#FFF4CC` / `#F4CC40` | `tab_bar_bg` ✓ |
| indicator `#007AFF` | `#007AFF` | `tab_underline_active` ✓ |
| `menuLoginAndSignUpTextButton` | `#007AFF` / `#FFFFFF` | `chats_chip_selected_border` ✓ light |
| `cardBackground` light | `#FFFFFF` | `category_card_bg` ✓ light |
| `cardBackground` dark | `#000000` | `category_card_bg` dark ✓ |

### 2.3 Dark-mode bug in the bottom nav

`values-night/colors.xml` keeps `nav_icon_selected = #000000` and `nav_text_selected = #D9000000`
in dark mode. iOS uses `fullAppText`, which is **`#FFFFFF`** in dark. The Android dark nav is
drawing black glyphs. Same for `nav_icon_unselected` — iOS uses a flat `#ADADAD` in both themes.

---

## 3. Component-by-component geometry

### 3.1 Header / app bar — `AppMainNav.swift` vs `layout_appbar.xml`

| Property | iOS | Android | |
|---|---|---|---|
| Bar height | logo 54 + 12 pad × 2 = **78** | `64dp` | ✗ |
| Logo | `Image("logo")`, height **54**, template-tinted `fullAppIcons` | `ic_app_logo` **38dp** + two 16sp text views | ✗ |
| Horizontal padding | 16 | `16dp` | ✓ |
| Vertical padding | 12 | 0 | ✗ |
| Background | `cardBackground` | `header_bg` (`#FFFFFF` / `#2C2C2E`) | ✗ dark |
| Bottom rule | `Divider()` (hairline) | `0.5dp` `appbar_divider` | ✓ |
| Menu button | `Image("menu")`, **44 × 44**, template, **no circle** | `42dp` ImageButton with `bg_menu_circle` (1.5dp stroke oval) + `10dp` padding → glyph only **22dp** | ✗ |
| Back button | `back_nav` / `back_nav_eng`, 44 × 44 | `ic_back_nav` | check |

> Note the logo on iOS is **one image** rendered as a template. Android splits it into an icon plus
> hand-written `فايندز` / `finds` text views, which is why the lockup looks different in the
> screenshots. Export `logo.imageset/Image.png` from the iOS assets and use it as one drawable.

**Also check the side the logo sits on.** Android's app bar puts `btnMenu` first and the logo last
under `layoutDirection="locale"`; the iOS `HStack` is logo-first, menu-second. Under RTL these two
resolve to opposite sides.

### 3.2 Search bar — `SearchBarView` vs `layout_home_header.xml`

| Property | iOS | Android | |
|---|---|---|---|
| Field height | 24 text + 10 pad × 2 = **44** | `44dp` | ✓ |
| Corner radius | **10** | `10dp` (`bg_search_bar`) | ✓ |
| Background | `fieldBackground` `#F2F2F7` / `#3A3A3C` | `search_bar_bg` `#F4F4F4` / `#3A3A3C` | ✗ light |
| Text size | **16** | `14sp` | ✗ |
| Inner horizontal padding | 12 | `12dp` | ✓ |
| Outer margin | **h 8 / v 4** | `h 12 / v 8` | ✗ |
| Search icon | **28 × 29**, `ic_Main_Search` (ar) / `ic_Main_Search_en` (en), tinted `fullAppIcons` | `28 × 28`, `ic_search`, tinted `search_icon_tint` | ✗ asset + tint |
| Clear button | `xmark.circle.fill` 18pt, `inactive` | none | ✗ missing |

`home_search_radius` in `dimens.xml` is still `22dp` — dead or conflicting; iOS is 10.

### 3.3 Top tab strip — `TopTabBarView.swift` vs `item_top_tab.xml`

| Property | iOS | Android | |
|---|---|---|---|
| Strip background | `fullAppPrimary` | `tab_bar_bg` | ✓ |
| Row height | label 44 + indicator 3 = **47** | `48dp` | ≈ |
| **Active** label | `OutlineText` — size **20**, weight **bold**, stroke **1**, fill `#FFFFFF`, stroke `#000000` | `StrokeTextView`, **15sp** | ✗ |
| **Inactive** label | `.app(16, regular)`, `black @ 40 %` | same 15sp view | ✗ |
| Label horizontal padding | **3** | `14dp` | ✗ |
| Active indicator | Capsule, height **3**, `#007AFF` | `3dp` View, `tab_underline_active` | ✓ |
| Transition | 0.22 s cross-fade between the two label styles | none | ✗ |

This is the most visible difference on the home screen: iOS grows the selected tab from 16 → 20pt
**and** switches it to a white-filled, black-outlined face. Android renders one 15sp outlined label
for every state.

### 3.4 Category card — `CategoryCard.swift` vs `item_category_grid.xml`

| Property | iOS | Android | |
|---|---|---|---|
| Card height | **120** | `wrap_content` | ✗ |
| Corner radius | **16** | `15dp` | ✗ |
| Border | **none** | `1dp` `category_card_stroke` `#7B7B7B` | ✗ |
| Shadow | `dropShadow @ 90 %`, radius 5, offset 0,0 | `bg_category_card_glow` 9-patch | check |
| Title | `.app(14, regular)`, `fullAppText`, ≤ 2 lines, `h 6` / `top 12` | **13sp** | ✗ |
| Icon grid | 2 × 3, spacing **3**, insets `top 14 / h 3 / bottom 14` | GridLayout, padding `5/5/8/6` | ✗ |
| Mini cell | **34 × 34**, radius **4**, stroke `fullAppText @ 40 %` 1pt | radius `4dp`, stroke **0.5dp** `#D97B7B7B` | ✗ |
| Grid columns / spacing | 3 cols, spacing **12**, padding `h 12 / top 12` | check adapter | — |
| Empty-subcategory state | `ic_all_2` 84 × 20, offset y −10 | `22sp` text | ✗ |

Sub-category card (`SubCategoryCard`): **116 × 116**, radius 16, title `.app(16, medium)`,
icon **70 × 70**, grid spacing 12.

### 3.5 Listing card — `ListingCard.swift` vs `item_listing_card.xml`

| Property | iOS |
|---|---|
| Card height | **180** |
| Corner radius | **14** |
| Border | none — shadow only (`dropShadow @ 90 %`, r 5) |
| Image panel width | **155** (rounded on the leading side only) |
| Title | `.app(15, bold)`, `fullAppText`, ≤ 3 lines, `h 10 / top 10` |
| Seller avatar | **28** |
| Seller name | `.app(11)`, `secondaryText` |
| Location icon | `map`, **11 × 13**, tinted `fullAppIcons` |
| Location text | `.app(12)`, `secondaryText` |
| Price icon | `SAR`, **13 × 15**, *not* tinted |
| Price text | `.app(15, bold)`, `fullAppText` |
| Clock icon | SF `clock`, 12pt, `secondaryText` |
| Time text | `.app(12)`, `secondaryText` |
| Favourite button | 30 × 40 box, radius **8**, white @ 50 % stroke; heart **22 × 30** |
| Carousel dots | 5 × 5 circles, spacing 4, bottom 6 |
| Placeholder | `fieldBackground` + `icon` 52 × 52 |
| List spacing | **10**, horizontal padding **10** |

### 3.6 Bottom nav — `CustomTabBar` vs `layout_bottom_nav.xml`

| Property | iOS | Android | |
|---|---|---|---|
| Pill height | **65** | `65dp` | ✓ |
| Corner | Capsule (32.5) | `32.5dp` card / `30dp` glass | ✗ minor |
| Fill | `.regularMaterial` (real blur) | white gradient `#66FFFFFF → #8CFFFFFF` | ✗ |
| Rim | `black @ 5 %`, 1pt | `nav_glass_rim` `#40000000` + white inner highlight | ✗ |
| Shadow | `shadowColor`, radius 10, offset 0,4 | `bottom_nav_elevation = 0dp` | ✗ |
| Side inset | **50** each side | `24dp` | ✗ |
| Bottom offset | **24** | `16dp` | ✗ |
| Inner padding | h 8 / v 8 | h 8 / v 4 | ✗ |
| Icon | **28 × 28**, `ic_tab_home` / `ic_tab_add` / `ic_tab_chats` | `28dp`, `ic_nav_home` / `ic_nav_add` / `ic_nav_chat` | asset ✗ |
| Icon colour | `fullAppText` selected / `inactive` `#ADADAD` | `#000000` / `#4D000000` | ✗ |
| Label | `.app(10, semibold)` selected, `.app(10, regular)` otherwise | `10sp`, no weight change | ✗ |
| Icon→label gap | 4 | `2dp` | ✗ |
| Selection highlight | RoundedRect **95 × 80**, radius **40**, white @ 5 % fill, black @ 25 % stroke 0.5, `matchedGeometryEffect` slide | `bg_nav_item_selected` radius `26dp`, opaque `nav_item_selected_bg`, no animation | ✗ |
| Badge | `.app(10, bold)`, white on red capsule, pad h 5 / v 2, offset (20, −28) | — | check |
| Gesture | horizontal drag switches tabs | none | ✗ |

---

## 4. Icons — use the iOS assets

`Assets.xcassets` has 40 image sets. These should be exported and dropped into `res/drawable`
rather than re-authored, so the two apps are pixel-identical:

`ic_tab_home` · `ic_tab_add` · `ic_tab_chats` · `ic_Main_Search` · `ic_Main_Search_en` ·
`menu` · `blue_menu` · `back_nav` · `back_nav_eng` · `logo` · `icon` · `ic_all_2` · `ic_AddAds` ·
`ic_adsempty` · `map` · `SAR` · `pen` · `person` · `call` · `arrow` · `chevron_left` ·
`heart` · `heart.fill` · `blocked_users` · `bubble_received` · `NafazBanner` ·
`ic_channel_call` · `ic_channel_chat` · `ic_channel_whatsapp` ·
`ic_menu_about` · `ic_menu_contact` · `ic_menu_favorites` · `ic_menu_myads` ·
`ic_menu_notifications` · `ic_menu_profile` · `ic_menu_settings` · `ic_menu_share` ·
`ic_settings_contact` · `ic_settings_darkmode` · `ic_settings_language`

Fourteen of them are already SVG (`ic_menu_*`, `ic_channel_*`, `ic_settings_*`, `ic_AddAds`,
`chevron_left`, `back_nav_eng`, `blocked_users`) — those convert straight to Android vector
drawables with no quality loss. The rest are single-scale PNGs and should go into `drawable-xxhdpi`.

Two rendering rules from the iOS side that Android currently gets wrong in places:

1. Almost every icon is drawn with `.renderingMode(.template)` and tinted `AppColor.fullAppIcons`
   (`#000000` light / `#FFFFFF` dark). On Android that means `app:tint="@color/icon_tint"` with a
   `values-night` override — not a baked-in colour.
2. Two icons are deliberately **not** templated: `SAR` (keeps its own colour) and `blue_menu`
   (the open-menu state).

---

## 5. Suggested order of work

1. **Fonts** — bundle Inter, wire the four weights, set it on the theme. Biggest single win.
2. **Colour tokens** — add the ~8 missing tokens, correct the ~14 wrong ones, fix the dark-mode
   nav icon/text colours.
3. **Icons** — export the 40 asset sets, replace the Android equivalents, switch to tinting.
4. **Header + search bar** — heights, logo as one image, 44dp menu glyph with no circle, 16sp
   search text, h8/v4 margins.
5. **Top tab strip** — 20sp bold outlined active / 16sp regular 40 % inactive, 3dp padding,
   cross-fade.
6. **Bottom nav** — side inset 50dp, bottom 24dp, 95×80 r40 highlight, `#ADADAD` unselected,
   semibold selected label, blur/shadow.
7. **Cards** — category card fixed 120dp, radius 16, drop the border; listing card 180dp,
   radius 14, 155dp image panel, all the type sizes above.

---

## 6. Two things I could not read

- **Files nested more than 7 folders deep can't be pulled from the connected folder.** That blocks
  `java/com/example/myapplication/adapters/*.kt`, `widgets/StrokeTextView.kt`, and
  `chat/ui/**`. To let me work on those, connect `find-main/app/src/main/java` as a folder in the
  desktop app as well.
- The iOS project's Arabic/English `.lproj` strings and `Resources/` were not compared — this pass
  was colours, sizes, icons and fonts only, as asked.
