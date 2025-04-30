package me.thuanc177.kotsune.libs.animeProvider.nguonc

import android.util.Log
import me.thuanc177.kotsune.libs.BaseAnimeProvider
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.animeProvider.allanime.EpisodeInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * NguonC API implementation for anime streaming
 */
class NguonCAPI(private val httpClient: NguonCHttpClient = DefaultNguonCHttpClient()) : BaseAnimeProvider("NguonC") {

    override val TAG = "NguonCAPI"
    private val extractor = NguonCExtractor(httpClient)

    // Cache for anime information
    private val animeCache = ConcurrentHashMap<String, NguonCTypes.NguonCFilmDetail>()
    private val slugCache = ConcurrentHashMap<Int, String>()

    /**
     * Search for anime by AniList ID
     */
    override suspend fun searchForAnime(anilistId: Int, query: String, translationType: String): Result<Anime> = apiRequest {
        logDebug("Searching for anime: $query (AniList ID: $anilistId)")

        // Check if we have a cached slug for this anilist ID
        val cachedSlug = slugCache[anilistId]
        if (cachedSlug != null) {
            logDebug("Found cached slug: $cachedSlug for AniList ID: $anilistId")
            // Get the film details using the cached slug
            val cachedFilm = animeCache[cachedSlug] ?: extractor.getFilmDetails(cachedSlug)

            if (cachedFilm != null) {
                animeCache[cachedSlug] = cachedFilm
                return@apiRequest Anime(
                    anilistId = anilistId,
                    alternativeId = cachedSlug,
                    title = listOf(cachedFilm.name, cachedFilm.original_name ?: "").filter { it.isNotEmpty() }
                )
            }
        }

        // Search by title
        val searchResults = extractor.searchAnime(query)
        if (searchResults.isNotEmpty()) {
            // Use the first result
            val film = searchResults.first()

            // Get full details for caching
            val filmDetails = extractor.getFilmDetails(film.slug)
            if (filmDetails != null) {
                // Cache the film details and slug
                animeCache[film.slug] = filmDetails
                slugCache[anilistId] = film.slug

                return@apiRequest Anime(
                    anilistId = anilistId,
                    alternativeId = film.slug,
                    title = listOf(film.name, film.original_name ?: "").filter { it.isNotEmpty() }
                )
            }
        }

        throw Exception("No anime found for query: $query")
    }

    /**
     * Get anime alternative ID (slug) by title
     */
    override suspend fun getAnimeAlternativeId(animeTitle: String, anilistId: Int): Result<Anime> = apiRequest {
        logDebug("Getting alternative ID for: $animeTitle (AniList ID: $anilistId)")

        // First check cache
        val cachedSlug = slugCache[anilistId]
        if (cachedSlug != null) {
            logDebug("Found cached slug: $cachedSlug for AniList ID: $anilistId")
            return@apiRequest Anime(
                anilistId = anilistId,
                alternativeId = cachedSlug,
                title = listOf(animeTitle)
            )
        }

        // Search for anime
        val searchResults = extractor.searchAnime(animeTitle)
        if (searchResults.isNotEmpty()) {
            val film = searchResults.first()

            // Cache the slug
            slugCache[anilistId] = film.slug

            return@apiRequest Anime(
                anilistId = anilistId,
                alternativeId = film.slug,
                title = listOf(film.name, film.original_name ?: "").filter { it.isNotEmpty() }
            )
        }

        throw Exception("No alternative ID found for: $animeTitle")
    }

    /**
     * Get episode list for an anime
     */
    override suspend fun getEpisodeList(
        showId: String,
        episodeNumStart: Float,
        episodeNumEnd: Float
    ): Result<List<EpisodeInfo>> = apiRequest {
        logDebug("Getting episode list for: $showId")

        // Get film details
        val filmDetails = animeCache[showId] ?: extractor.getFilmDetails(showId)
        if (filmDetails != null) {
            // Store in cache
            animeCache[showId] = filmDetails

            // Get episodes from Vietsub server
            val vietsubServer = filmDetails.episodes.find {
                it.server_name.contains("Vietsub", ignoreCase = true)
            }

            if (vietsubServer != null) {
                // Convert to EpisodeInfo objects
                val episodes = vietsubServer.items.map { episode ->
                    val episodeNumber = episode.name.toFloatOrNull() ?: 0f

                    EpisodeInfo(
                        episodeIdNum = episodeNumber,
                        notes = null,
                        description = null,
                        thumbnails = listOf() // NguonC doesn't provide thumbnails for episodes
                    )
                }.filter {
                    // Apply episode range filter if specified
                    it.episodeIdNum >= episodeNumStart && it.episodeIdNum <= episodeNumEnd
                }.sortedBy {
                    // Sort by episode number
                    it.episodeIdNum
                }

                return@apiRequest episodes
            }
        }

        // Return empty list if we couldn't get episodes
        emptyList<EpisodeInfo>()
    }

    /**
     * Get streams for a specific episode
     */
    override suspend fun getEpisodeStreams(
        animeId: String,
        episode: String,
        translationType: String
    ): Result<List<StreamServer>> = apiRequest {
        logDebug("Getting streams for: $animeId, episode: $episode")

        // Get film details
        val filmDetails = animeCache[animeId] ?: extractor.getFilmDetails(animeId)
        if (filmDetails != null) {
            // Store in cache
            animeCache[animeId] = filmDetails

            // Extract streams for episode
            val servers = extractor.extractStreams(filmDetails, episode)

            if (servers.isNotEmpty()) {
                return@apiRequest servers
            } else {
                logDebug("No streams found for episode: $episode")
            }
        } else {
            logDebug("Failed to get film details for: $animeId")
        }

        emptyList<StreamServer>()
    }
}