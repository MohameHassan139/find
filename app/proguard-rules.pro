# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable stack traces, and hide the original
# source file name in them (paired with the mapping file Crashlytics uploads
# to de-obfuscate reports server-side — see CrashReporting.kt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson ─────────────────────────────────────────────────────────────────────
# Gson (unlike Retrofit/OkHttp) doesn't ship its own consumer ProGuard rules,
# and it serializes most of this app's request/response models by reflecting
# on the exact Kotlin property name (many have no @SerializedName override).
# Without these, R8 renaming a field silently breaks the JSON contract with
# the backend in release builds only — it still works fine in debug, since
# minification is off there. See https://github.com/google/gson/blob/main/examples/android-proguard-example/proguard.cfg
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# This app's own Gson-serialized request/response models — keep field names
# so (de)serialization survives obfuscation. Covers every package that holds
# Retrofit/Gson DTOs, plus the handful of response models declared inline in
# ListingDetailActivity.kt and MainViewModel.kt (they parse raw ResponseBody
# JSON manually rather than through a typed Retrofit call).
-keep class com.example.myapplication.auth.** { <fields>; }
-keep class com.example.myapplication.chat.model.** { <fields>; }
-keep class com.example.myapplication.favorites.** { <fields>; }
-keep class com.example.myapplication.models.** { <fields>; }
-keep class com.example.myapplication.DetailListing { <fields>; }
-keep class com.example.myapplication.ApiCategory { <fields>; }
-keep class com.example.myapplication.ApiSubCategory { <fields>; }
-keep class com.example.myapplication.ApiFilterOption { <fields>; }
-keep class com.example.myapplication.RegionItem { <fields>; }
-keep class com.example.myapplication.CityItem { <fields>; }
-keep class com.example.myapplication.ApiListing { <fields>; }

# ── Firebase Crashlytics ─────────────────────────────────────────────────────
# Crashlytics 3.0.6 has an optional code path that reflects on
# android.os.ProfilingTrigger for newer Android versions than compileSdk 35
# ships in its android.jar. The app never calls this itself — R8 generated
# this exact suppression in missing_rules.txt.
-dontwarn android.os.ProfilingTrigger$Builder
-dontwarn android.os.ProfilingTrigger

# ── Retrofit ─────────────────────────────────────────────────────────────────
# Retrofit ships consumer rules in its own AAR, but the app's own service
# interface uses Kotlin suspend functions, which R8 full mode can otherwise
# strip the generic/continuation signature from.
-keep,allowobfuscation,allowshrinking interface com.example.myapplication.chat.api.FindApiService
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
