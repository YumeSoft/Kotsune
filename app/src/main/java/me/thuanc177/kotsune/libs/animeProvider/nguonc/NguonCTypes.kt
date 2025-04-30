package me.thuanc177.kotsune.libs.animeProvider.nguonc

/**
 * Data models for NguonC API responses
 */
object NguonCTypes {
    /**
     * Search result item
     */
    data class NguonCSearchResult(
        val id: String,
        val name: String,
        val slug: String,
        val original_name: String?,
        val thumb_url: String?
    )

    /**
     * Film episode server
     */
    data class NguonCServer(
        val server_name: String,
        val items: List<NguonCEpisode>
    )

    /**
     * Episode item
     */
    data class NguonCEpisode(
        val name: String,
        val slug: String,
        val embed: String,
        val m3u8: String
    )

    /**
     * Full film details including episodes
     */
    data class NguonCFilmDetail(
        val id: String,
        val name: String,
        val slug: String,
        val original_name: String?,
        val thumb_url: String?,
        val poster_url: String?,
        val created: String?,
        val modified: String?,
        val description: String?,
        val total_episodes: Int?,
        val current_episode: String?,
        val time: String?,
        val quality: String?,
        val language: String?,
        val episodes: List<NguonCServer>
    )

    /**
     * Film API response
     */
    data class NguonCFilmResponse(
        val status: String,
        val movie: NguonCFilmDetail
    )

    /**
     * Search API response
     */
    data class NguonCSearchResponse(
        val status: String,
        val data: List<NguonCSearchResult>?
    )
}