package me.thuanc177.kotsune.libs.animeProvider.allanime

import java.util.regex.Pattern

/**
 * Constants for the AllAnime provider
 */
object Constants {
    // Available servers
    val SERVERS_AVAILABLE = listOf(
        "sharepoint",
        "dropbox",
        "gogoanime",
        "weTransfer",
        "wixmp",
        "Yt",
        "mp4-upload"
    )

    // API URLs
    const val API_BASE_URL = "allanime.day"
    const val API_REFERER = "https://allanime.to/"
    const val API_ENDPOINT = "https://api.$API_BASE_URL/api/"

    // Search constants
    const val DEFAULT_COUNTRY_OF_ORIGIN = "all"
    const val DEFAULT_NSFW = true
    const val DEFAULT_UNKNOWN = true
    const val DEFAULT_PER_PAGE = 40
    const val DEFAULT_PAGE = 1

    // Regex patterns
    val MP4_SERVER_JUICY_STREAM_REGEX: Pattern = Pattern.compile(
        "video/mp4\",src:\"(https?://.*/video\\.mp4)\""
    )
}