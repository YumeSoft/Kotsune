package me.thuanc177.kotsune.libs.animeProvider.allanime

import me.thuanc177.kotsune.libs.BaseAnimeProvider
import me.thuanc177.kotsune.libs.StreamLink
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistImage
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeTitle
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.API_BASE_URL
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.API_ENDPOINT
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.API_REFERER
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.DEFAULT_COUNTRY_OF_ORIGIN
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.DEFAULT_NSFW
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.DEFAULT_PAGE
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.DEFAULT_PER_PAGE
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.DEFAULT_UNKNOWN
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeConstants.MP4_SERVER_JUICY_STREAM_REGEX
import org.json.JSONArray
import org.json.JSONObject

/**
 * AllAnime API implementation
 */
class AllAnimeAPI(private val httpClient: HttpClient) : BaseAnimeProvider("AllAnime") {

    // Cache for anime info
    private val animeInfoStore = mutableMapOf<String, String>()

    /**
     * Execute a GraphQL query against the AllAnime API
     */
    private suspend fun executeGraphqlQuery(query: String, variables: Map<String, Any>): JSONObject {
        val variablesJson = JSONObject(variables).toString()
        val url = "$API_ENDPOINT?variables=$variablesJson&query=${query.trim()}"
        val headers = mapOf("Referer" to API_REFERER)
        val response = httpClient.get(url, headers)
        return JSONObject(response).getJSONObject("data")
    }

    /**
     * Search for anime on AllAnime
     */
    override suspend fun searchForAnime(
        query: String,
        translationType: String
    ): Result<List<Anime>> = apiRequest {
        logDebug("Searching for anime: $query")

        val variables = mapOf(
            "search" to mapOf(
                "allowAdult" to DEFAULT_NSFW,
                "allowUnknown" to DEFAULT_UNKNOWN,
                "query" to query
            ),
            "limit" to DEFAULT_PER_PAGE,
            "page" to DEFAULT_PAGE,
            "translationType" to translationType,
            "countryOrigin" to DEFAULT_COUNTRY_OF_ORIGIN
        )

        val searchResults = executeGraphqlQuery(GqlQueries.SEARCH_GQL, variables)
        val shows = searchResults.getJSONObject("shows")
        val edges = shows.getJSONArray("edges")

        val animeList = mutableListOf<Anime>()
        for (i in 0 until edges.length()) {
            val result = edges.getJSONObject(i)
            val id = result.getString("_id")
            val title = result.getString("name")

            animeList.add(
                Anime(
                    id = id.hashCode(),
                    allanimeid = id,
                    title = listOf(title),
                    description = null,
                    coverImage = null,
                    bannerImage = null,
                    genres = listOf(),
                    episodes = null,
                    seasonYear = null,
                    status = null,
                    score = null
                )
            )
        }

        animeList
    }

    /**
     * Get detailed anime information
     */
    override suspend fun getAnime(animeId: String): Result<AnimeDetailed?> = apiRequest {
        logDebug("Getting anime details for: $animeId")

        val variables = mapOf("showId" to animeId)
        val animeData = executeGraphqlQuery(GqlQueries.SHOW_GQL, variables)
        val show = animeData.getJSONObject("show")

        val id = show.getString("_id")
        val title = show.getString("name")

        // Cache the anime title for use in episode streams
        animeInfoStore[id] = title

        // Get available episodes detail if present
        val availableEpisodesDetail = if (show.has("availableEpisodesDetail")) {
            val episodesDetail = show.getJSONObject("availableEpisodesDetail")
            val subEpisodes = if (episodesDetail.has("sub")) episodesDetail.getInt("sub") else null
            subEpisodes
        } else null

        AnimeDetailed(
            id = id.hashCode(),
            title = AnimeTitle(
                english = title,
                romaji = title,
                native = null
            ),
            description = null,
            coverImage = AnilistImage(
                large = null,
                extraLarge = null,
                medium = null,
            ),
            bannerImage = null,
            episodes = availableEpisodesDetail,
            duration = null,
            genres = null,
            status = null,
            seasonYear = null
        )
    }

    /**
     * Get streaming links for an anime episode
     */
    override suspend fun getEpisodeStreams(
        animeId: String,
        episode: String,
        translationType: String
    ): Result<List<StreamServer>> = apiRequest {
        logDebug("Getting episode streams for anime: $animeId, episode: $episode")

        val animeTitle = animeInfoStore[animeId] ?: ""
        val episodeData = getAnimeEpisode(animeId, episode, translationType)
        val sourceUrls = episodeData.getJSONArray("sourceUrls")
        val episodeNotes = episodeData.optString("notes", "")
        val episodeTitle = "$episodeNotes$animeTitle; Episode $episode"

        val servers = mutableListOf<StreamServer>()
        for (i in 0 until sourceUrls.length()) {
            val embed = sourceUrls.getJSONObject(i)
            val sourceName = embed.optString("sourceName", "")

            // Filter by preferred server names
            if (sourceName !in listOf(
                    "Sak", "S-mp4", "Luf-mp4", "Default",
                    "Yt-mp4", "Kir", "Mp4"
                )) {
                logDebug("Found $sourceName but ignoring")
                continue
            }

            val server = processServer(embed, animeTitle, episodeTitle, episode)
            if (server != null) {
                servers.add(server)
            }
        }

        servers
    }

    /**
     * Get episode information
     */
    private suspend fun getAnimeEpisode(
        animeId: String,
        episode: String,
        translationType: String
    ): JSONObject {
        val variables = mapOf(
            "showId" to animeId,
            "translationType" to translationType,
            "episodeString" to episode
        )

        return executeGraphqlQuery(GqlQueries.EPISODES_GQL, variables)
            .getJSONObject("episode")
    }

    /**
     * Process server information for a specific embed
     */
    private suspend fun processServer(
        embed: JSONObject,
        animeTitle: String,
        episodeTitle: String,
        episodeNumber: String
    ): StreamServer? {
        var url = embed.optString("sourceUrl")
        if (url.isEmpty()) return null

        if (url.startsWith("--")) {
            url = oneDigitSymmetricXor(56, url.substring(2))
        }

        val sourceName = embed.optString("sourceName", "")

        return when (sourceName) {
            "Yt-mp4" -> processYtServer(url, animeTitle, episodeNumber)
            "Mp4" -> processMp4Server(url, episodeTitle)
            "Luf-mp4" -> processJsonServer(url, "gogoanime", episodeTitle)
            "Kir" -> processJsonServer(url, "weTransfer", episodeTitle)
            "S-mp4" -> processJsonServer(url, "sharepoint", episodeTitle)
            "Sak" -> processJsonServer(url, "dropbox", episodeTitle)
            "Default" -> processJsonServer(url, "wixmp", episodeTitle)
            else -> null
        }
    }

    /**
     * Process YouTube server
     */
    private fun processYtServer(url: String, animeTitle: String, episodeNumber: String): StreamServer {
        logDebug("Found streams from Yt")
        return StreamServer(
            server = "Yt",
            episodeTitle = "$animeTitle; Episode $episodeNumber",
            headers = mapOf("Referer" to "https://$API_BASE_URL/"),
            links = listOf(
                StreamLink(
                    link = url,
                    quality = "1080",
                    translationType = "sub"
                )
            )
        )
    }

    /**
     * Process MP4 server
     */
    private suspend fun processMp4Server(url: String, episodeTitle: String): StreamServer? {
        logDebug("Found streams from Mp4")
        val response = httpClient.get(url, mapOf())
        val embedHtml = response.replace(" ", "").replace("\n", "")
        val matcher = MP4_SERVER_JUICY_STREAM_REGEX.matcher(embedHtml)

        return if (matcher.find()) {
            StreamServer(
                server = "mp4-upload",
                headers = mapOf("Referer" to "https://www.mp4upload.com/"),
                episodeTitle = episodeTitle,
                links = listOf(
                    StreamLink(
                        link = matcher.group(1),
                        quality = "1080"
                    )
                )
            )
        } else null
    }

    /**
     * Process JSON-based servers (most AllAnime servers)
     */
    private suspend fun processJsonServer(
        url: String,
        serverName: String,
        episodeTitle: String
    ): StreamServer {
        logDebug("Found streams from $serverName")
        val apiUrl = "https://$API_BASE_URL${url.replace("clock", "clock.json")}"
        val response = httpClient.get(apiUrl, mapOf())
        val json = JSONObject(response)
        val links = parseStreamLinks(json.getJSONArray("links"))

        return StreamServer(
            server = serverName,
            headers = mapOf("Referer" to "https://$API_BASE_URL/"),
            episodeTitle = episodeTitle,
            links = links
        )
    }

    /**
     * Parse stream links from JSON
     */
    private fun parseStreamLinks(links: JSONArray): List<StreamLink> {
        val result = mutableListOf<StreamLink>()
        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            result.add(
                StreamLink(
                    link = link.getString("link"),
                    quality = link.optString("resolutionStr", "unknown"),
                    translationType = "sub"
                )
            )
        }
        return result
    }

    /**
     * Decode obfuscated URLs
     */
    private fun oneDigitSymmetricXor(key: Int, string: String): String {
        return string.map { char ->
            (char.code xor key).toChar()
        }.joinToString("")
    }
}

/**
 * HTTP client interface
 */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = mapOf()): String
}