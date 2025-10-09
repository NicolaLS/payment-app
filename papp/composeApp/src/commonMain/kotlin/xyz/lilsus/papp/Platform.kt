package xyz.lilsus.papp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform