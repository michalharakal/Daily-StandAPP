package de.jugda.javaland

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform