package xyz.lilsus.papp.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createBaseHttpClient(): HttpClient = platformHttpClient().config {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

fun createNwcHttpClient(): HttpClient = platformHttpClient().config {
    install(WebSockets)
}

expect fun platformHttpClient(): HttpClient
