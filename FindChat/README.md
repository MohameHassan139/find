# FindChat — Android Kotlin Chat Module

A fully-native Android chat module for the **Find** app, built in Kotlin with Retrofit,
Coroutines, ViewModel, LiveData, and Facebook Shimmer.

---

## Screens

| Screen | Description |
|---|---|
| `ConversationsActivity` | Lists all conversations with filter chips (All / Unread / Favorite), shimmer loading, empty state, error state with retry |
| `ChatActivity` | Full chat thread with sent/received RTL bubbles, shimmer loading, read receipts, send button |

---

## Project Structure

```
app/src/main/
├── java/com/find/chat/
│   ├── api/
│   │   ├── FindApiService.kt       ← Retrofit interface (all 5 endpoints)
│   │   └── RetrofitClient.kt       ← OkHttp + Retrofit singleton
│   ├── model/
│   │   └── Models.kt               ← All data classes (Conversation, Message, etc.)
│   ├── ui/
│   │   ├── conversations/
│   │   │   ├── ConversationsActivity.kt
│   │   │   ├── ConversationsAdapter.kt
│   │   │   └── ConversationsViewModel.kt
│   │   └── chat/
│   │       ├── ChatActivity.kt
│   │       ├── MessagesAdapter.kt
│   │       └── ChatViewModel.kt
│   └── utils/
│       ├── Result.kt               ← Sealed class: Loading / Success / Error
│       └── DateUtils.kt            ← Arabic-formatted timestamps
└── res/
    ├── layout/
    │   ├── activity_conversations.xml
    │   ├── activity_chat.xml
    │   ├── item_conversation.xml
    │   ├── item_message_sent.xml
    │   ├── item_message_received.xml
    │   ├── item_shimmer_conversation.xml
    │   ├── item_shimmer_message_sent.xml
    │   └── item_shimmer_message_received.xml
    ├── drawable/        ← All vector icons + shape backgrounds
    └── values/
        ├── colors.xml
        ├── strings.xml
        └── themes.xml
```

---

## Setup

### 1. Add to your existing project

Copy the following into your project:
- `app/src/main/java/com/find/chat/` → merge into your source set
- `app/src/main/res/` → merge drawables, layouts, values

### 2. Set your API base URL

Open `api/RetrofitClient.kt` and replace:
```kotlin
private const val BASE_URL = "https://your-api-base-url.com/api/"
```

### 3. Set your Bearer token

The token is currently hardcoded in `FindApiService.kt` for each endpoint:
```kotlin
@Header("Authorization") token: String = "Bearer AbCd1234efgh5678ijkl90MnOpQrStUvWxYzAbCdEfGhIjKl"
```
For production, move this to a secure token store (e.g. `EncryptedSharedPreferences`).

### 4. Add dependencies to your `build.gradle`

```groovy
// Retrofit
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

// Coroutines + ViewModel
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

// Shimmer
implementation 'com.facebook.shimmer:shimmer:0.5.0'

// Glide
implementation 'com.github.bumptech.glide:glide:4.16.0'

// SwipeRefresh
implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
```

### 5. Navigate to ConversationsActivity

From anywhere in your app:
```kotlin
startActivity(Intent(this, ConversationsActivity::class.java))
```

---

## API Endpoints Used

| Method | Endpoint | Used In |
|---|---|---|
| `GET` | `conversations` | ConversationsViewModel.loadConversations() |
| `POST` | `conversations` | (ready — CreateConversationRequest) |
| `GET` | `conversations/{id}/messages` | ChatViewModel.loadMessages() |
| `POST` | `conversations/{id}/messages` | ChatViewModel.sendMessage() |
| `PATCH` | `conversations/{id}/read` | ChatViewModel.markRead() — auto-called on open |

---

## State Handling

Every screen handles three states via `sealed class Result<T>`:

- **Loading** → Shimmer skeleton animation
- **Success** → RecyclerView with real data
- **Error** → Icon + Arabic error message + Retry button

Pull-to-refresh is also supported on the conversations list.

---

## RTL Support

The app is fully RTL. Set in:
- `AndroidManifest.xml`: `android:supportsRtl="true"`
- `themes.xml`: `android:layoutDirection="rtl"`
- All layouts use `layoutDirection="rtl"` and Arabic text

---

## Notes

- Arabic timestamps are formatted via `DateUtils.kt` (e.g. `م 4:26`)
- Message bubbles: sent = warm cream (`#F5EDD8`), received = white
- Unread badge appears in red on conversation rows
- `markRead` is called automatically when opening a chat
