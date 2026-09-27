package com.example.myapplication.data.remote

import android.content.Context
import com.example.myapplication.App
import com.example.myapplication.BuildConfig
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.utils.LocaleHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's two Retrofit clients, built once and shared (previously a new OkHttp client —
 * with its own connection pool — was created on every single request).
 *
 *  - [authed] attaches the signed-in user's token, read fresh on every request, and shows
 *    the "session expired" prompt on a 401.
 *  - [public] never sends a token, for catalog data (app-data, listings, search) so an
 *    expired token can never log someone out while they're just browsing.
 *
 * Only repositories (data/repository) should use these directly.
 */
object ApiClient {

    private const val BASE_URL = "https://api.finds.sa/api/v1/"
    private const val TIMEOUT_SECONDS = 30L

    private val appContext: Context get() = App.instance

    val authed: FindApiService by lazy { create(authenticated = true) }
    val public: FindApiService by lazy { create(authenticated = false) }

    private fun create(authenticated: Boolean): FindApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(headersInterceptor(authenticated))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FindApiService::class.java)
    }

    private fun headersInterceptor(authenticated: Boolean) = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept-Language", LocaleHelper.getLanguage(appContext))
            .apply {
                if (authenticated) {
                    TokenManager.getToken(appContext)?.takeIf { it.isNotEmpty() }
                        ?.let { header("Authorization", "Bearer $it") }
                }
            }
            .build()
        val response = chain.proceed(request)
        if (authenticated && response.code == 401) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                AuthGuard.onUnauthorized(appContext)
            }
        }
        response
    }
}
