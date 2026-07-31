package com.example.myapplication.chat.api

import android.content.Context
import com.example.myapplication.App
import com.example.myapplication.BuildConfig
import com.example.myapplication.auth.TokenManager
import com.example.myapplication.utils.AuthGuard
import com.example.myapplication.utils.LocaleHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://api.finds.sa/api/v1/"

    fun build(context: Context): FindApiService {
        val token = TokenManager.getToken(context) ?: ""
        val appContext = context.applicationContext

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                var builder = originalRequest.newBuilder()
                    .header("Accept-Language", LocaleHelper.getLanguage(appContext))
                if (token.isNotEmpty()) {
                    builder = builder.header("Authorization", "Bearer $token")
                }
                val response = chain.proceed(builder.build())
                if (response.code == 401) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        AuthGuard.onUnauthorized(appContext)
                    }
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FindApiService::class.java)
    }

    // Fallback singleton for places that don't have context yet
    val apiService: FindApiService by lazy { build_no_token() }

    private fun build_no_token(): FindApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("Accept-Language", LocaleHelper.getLanguage(App.instance))
                    .build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FindApiService::class.java)
    }
}
