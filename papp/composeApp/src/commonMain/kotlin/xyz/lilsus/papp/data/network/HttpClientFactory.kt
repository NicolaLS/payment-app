package xyz.lilsus.papp.data.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json

fun createBaseHttpClient(): HttpClient = platformHttpClient().config {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 2)
        exponentialDelay()
    }
}

fun createNwcHttpClient(): HttpClient = platformHttpClient().config {
    install(HttpTimeout) {
        // Keep NWC connect/read deadlines short to fail fast on dead relays.
        connectTimeoutMillis = 7_000
        socketTimeoutMillis = 12_000
        requestTimeoutMillis = 12_000
    }
    install(WebSockets) {
        // Keep the channel alive and detect half-open TCP connections quickly.
        pingInterval = 5_000.milliseconds
    }
}

expect fun platformHttpClient(): HttpClient
