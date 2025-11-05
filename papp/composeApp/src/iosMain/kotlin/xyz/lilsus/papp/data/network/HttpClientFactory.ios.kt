package xyz.lilsus.papp.data.network

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

actual fun platformHttpClient(): HttpClient = HttpClient(Darwin)
