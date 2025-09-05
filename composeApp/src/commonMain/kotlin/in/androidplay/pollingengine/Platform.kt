package `in`.androidplay.pollingengine

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform