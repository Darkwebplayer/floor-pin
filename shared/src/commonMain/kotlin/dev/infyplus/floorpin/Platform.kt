package dev.infyplus.floorpin

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform