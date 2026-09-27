package com.example.myapplication.data

import com.example.myapplication.App
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.CatalogRepository
import com.example.myapplication.data.repository.ChatRepository
import com.example.myapplication.data.repository.FavoritesRepository
import com.example.myapplication.data.repository.ListingRepository
import com.example.myapplication.data.repository.ModerationRepository
import com.example.myapplication.data.repository.NotificationsRepository

/**
 * Where screens and ViewModels get their repositories from — one shared instance of
 * each, created on first use. (A tiny hand-rolled service locator, so no DI library.)
 */
object AppContainer {
    val auth by lazy { AuthRepository(ApiClient.authed, App.instance) }
    val catalog by lazy { CatalogRepository(ApiClient.public) }
    val listings by lazy { ListingRepository(ApiClient.authed) }
    val favorites by lazy { FavoritesRepository(ApiClient.authed) }
    val chat by lazy { ChatRepository(ApiClient.authed) }
    val notifications by lazy { NotificationsRepository(ApiClient.authed) }
    val moderation by lazy { ModerationRepository(ApiClient.authed) }
}
