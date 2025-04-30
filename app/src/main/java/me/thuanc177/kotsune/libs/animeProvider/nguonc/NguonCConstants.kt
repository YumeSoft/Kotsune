package me.thuanc177.kotsune.libs.animeProvider.nguonc

/**
 * Constants for the NguonC provider
 */
object NguonCConstants {
    // API URLs
    const val BASE_URL = "https://phim.nguonc.com/api"
    const val SEARCH_API = "$BASE_URL/films/search"
    const val FILM_API = "$BASE_URL/film"

    // Headers for API requests
    val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36",
        "Accept" to "application/json",
        "Referer" to "https://phim.nguonc.com/",
        "Origin" to "https://phim.nguonc.com"
    )

    // Server name to display in UI
    const val SERVER_NAME = "Vietsub"
}