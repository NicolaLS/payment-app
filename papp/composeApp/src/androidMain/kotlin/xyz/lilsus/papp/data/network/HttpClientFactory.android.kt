package xyz.lilsus.papp.data.network

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp)
