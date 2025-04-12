package me.thuanc177.kotsune.libs.animeProvider.allanime

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.libs.animeProvider.ProviderStore
import me.thuanc177.kotsune.libs.animeProvider.Utils
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * AllAnime is a provider class for fetching anime data from the AllAnime API.
 */
class AllAnime(
    cacheRequests: String,
    usePersistentProviderStore: String = "True"
) : AnimeProvider {

    private val HEADERS = mapOf(
        "Referer" to API_REFERER
    )

    // Add HTTP client
    // Add this class to implement the HTTP client functionality
    private inner class HttpClient {
        private val client = OkHttpClient()

        fun get(url: String, params: Map<String, String> = emptyMap(), timeout: Int = 30): Response {
            val urlBuilder = url.toHttpUrlOrNull()!!.newBuilder()
            params.forEach { (key, value) ->
                urlBuilder.addQueryParameter(key, value)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(HEADERS.toHeaders())
                .build()

            return client.newBuilder()
                .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
        }
    }

    // Replace session with proper instance
    private val session = HttpClient()


    // Add ProviderStore instance
    private val store = ProviderStore(
        if (usePersistentProviderStore == "True") "persistent" else "memory",
        "allanime",
        "allanime.db"
    )

    // Add extension functions
    private fun Response.throwIfError() {
        if (!isSuccessful) throw IOException("HTTP error ${code}")
    }

    private val Response.text: String
        get() = body?.string() ?: ""

    companion object {
        private const val TAG = "AllAnime"

        // Constants from constants.py
        private val SERVERS_AVAILABLE = listOf(
            "sharepoint", "dropbox", "gogoanime", "weTransfer",
            "wixmp", "Yt", "mp4-upload"
        )
        private const val API_BASE_URL = "allanime.day"
        private const val API_REFERER = "https://allanime.to/"
        private const val API_ENDPOINT = "https://api.$API_BASE_URL/api/"

        // Search constants
        private const val DEFAULT_COUNTRY_OF_ORIGIN = "all"
        private const val DEFAULT_NSFW = true
        private const val DEFAULT_UNKNOWN = true
        private const val DEFAULT_PER_PAGE = 40
        private const val DEFAULT_PAGE = 1

        // Regex patterns
        private val MP4_SERVER_JUICY_STREAM_REGEX =
            Pattern.compile("video/mp4\",src:\"(https?://.*/video\\.mp4)\"")

        // GraphQL queries from gql_queries.py
        private const val SEARCH_GQL = """
            query (
              ${'$'}search: SearchInput
              ${'$'}limit: Int
              ${'$'}page: Int
              ${'$'}translationType: VaildTranslationTypeEnumType
              ${'$'}countryOrigin: VaildCountryOriginEnumType
            ) {
              shows(
                search: ${'$'}search
                limit: ${'$'}limit
                page: ${'$'}page
                translationType: ${'$'}translationType
                countryOrigin: ${'$'}countryOrigin
              ) {
                pageInfo {
                  total
                }
                edges {
                  _id
                  name
                  availableEpisodes
                  __typename
                }
              }
            }
        """

        private const val EPISODES_GQL = """
            query (
              ${'$'}showId: String!
              ${'$'}translationType: VaildTranslationTypeEnumType!
              ${'$'}episodeString: String!
            ) {
              episode(
                showId: ${'$'}showId
                translationType: ${'$'}translationType
                episodeString: ${'$'}episodeString
              ) {
                episodeString
                sourceUrls
                notes
              }
            }
        """

        private const val SHOW_GQL = """
            query (${'$'}showId: String!) {
              show(_id: ${'$'}showId) {
                _id
                name
                availableEpisodesDetail
              }
            }
        """
    }

    /**
     * Executes a GraphQL query using the provided query string and variables.
     *
     * @param query The GraphQL query string to be executed
     * @param variables Dictionary of variables to be used in the query
     * @return The JSON response data from the GraphQL API
     * @throws Exception if the HTTP request failed
     */
    private fun _executeGraphqlQuery(query: String, variables: Map<String, Any>): Map<String, Any> {
        val jsonVariables = JSONObject(variables).toString()

        val response = session.get(
            API_ENDPOINT,
            mapOf(
                "variables" to jsonVariables,
                "query" to query
            ),
            timeout = 10
        )

        response.throwIfError()
        val jsonResponse = Json.parseToJsonElement(response.text).jsonObject

        @Suppress("UNCHECKED_CAST")
        return jsonResponse["data"]?.let { it as Map<String, Any> }
            ?: throw Exception("No data in response")
    }

    /**
     * Search for anime based on given keywords and filters.
     *
     * @param searchKeywords The keywords to search for
     * @param translationType The type of translation to search for (e.g., "sub" or "dub")
     * @param nsfw Whether to include adult content in the search results
     * @param unknown Whether to include unknown content in the search results
     * @param limit The maximum number of results to return
     * @param page The page number to return
     * @param countryOfOrigin The country of origin filter
     * @return A map containing the page information and a list of search results
     */
    fun searchForAnime(
        searchKeywords: String,
        translationType: String,
        nsfw: Boolean = DEFAULT_NSFW,
        unknown: Boolean = DEFAULT_UNKNOWN,
        limit: Int = DEFAULT_PER_PAGE,
        page: Int = DEFAULT_PAGE,
        countryOfOrigin: String = DEFAULT_COUNTRY_OF_ORIGIN
    ): Map<String, Any> {
        Log.d(TAG, "Searching for anime: $searchKeywords")

        val searchResults = _executeGraphqlQuery(
            SEARCH_GQL,
            mapOf(
                "search" to mapOf(
                    "allowAdult" to nsfw,
                    "allowUnknown" to unknown,
                    "query" to searchKeywords
                ),
                "limit" to limit,
                "page" to page,
                "translationtype" to translationType,
                "countryorigin" to countryOfOrigin
            )
        )

        @Suppress("UNCHECKED_CAST")
        val shows = searchResults["shows"] as Map<String, Any>
        val pageInfo = shows["pageInfo"] as Map<String, Any>
        val edges = shows["edges"] as List<Map<String, Any>>

        val results = edges.map { result ->
            mapOf(
                "id" to result["_id"],
                "title" to result["name"],
                "type" to (result["__typename"] ?: ""),
                "availableEpisodes" to result["availableEpisodes"]
            )
        }

        return mapOf(
            "pageInfo" to pageInfo,
            "results" to results
        )
    }

    /**
     * Fetches anime details using the provided show ID.
     *
     * @param id The ID of the anime show to fetch details for
     * @return A map containing the anime details
     */
    fun getAnime(id: String): Map<String, Any> {
        Log.d(TAG, "Getting anime details for ID: $id")

        val anime = _executeGraphqlQuery(
            SHOW_GQL,
            mapOf("showId" to id)
        )

        @Suppress("UNCHECKED_CAST")
        val show = anime["show"] as Map<String, Any>
        store.set("$id:anime_info", Json.encodeToString(mapOf("title" to show["name"].toString())))

        return mapOf(
            "id" to (show["_id"] ?: ""),
            "title" to (show["name"] ?: ""),
            "availableEpisodesDetail" to (show["availableEpisodesDetail"] ?: ""),
            "type" to (anime["__typename"] ?: "")
        )
    }

    /**
     * Fetches a specific episode of an anime by its ID and episode number.
     *
     * @param animeId The unique identifier of the anime
     * @param episode The episode number or string identifier
     * @param translationType The type of translation for the episode
     * @return The episode details retrieved from the GraphQL query
     */
    private fun _getAnimeEpisode(
        animeId: String,
        episode: String,
        translationType: String = "sub"
    ): Map<String, Any> {
        Log.d(TAG, "Getting episode details for anime ID: $animeId, episode: $episode")

        val episodeData = _executeGraphqlQuery(
            EPISODES_GQL,
            mapOf(
                "showId" to animeId,
                "translationType" to translationType,
                "episodeString" to episode
            )
        )

        @Suppress("UNCHECKED_CAST")
        return episodeData["episode"] as Map<String, Any>
    }

    /**
     * Retrieves the streaming server information for a given anime episode.
     *
     * @param embed A map containing the embed data
     * @param animeTitle The title of the anime
     * @param allanimeEpisode A map representing the episode details
     * @param episodeNumber The episode number
     * @return A map containing server information, or null if no valid URL or stream is found
     */
    private fun _getServer(
        embed: Map<String, Any>,
        animeTitle: String,
        allanimeEpisode: Map<String, Any>,
        episodeNumber: String
    ): Map<String, Any>? {
        var url = embed["sourceUrl"] as String? ?: return null

        if (url.startsWith("--")) {
            url = Utils.oneDigitSymmetricXor(56, url.substring(2))
        }

        // First case: Handle different source names
        when (embed["sourceName"] as String) {
            "Yt-mp4" -> {
                Log.d(TAG, "Found streams from Yt")
                return mapOf(
                    "server" to "Yt",
                    "episode_title" to "$animeTitle; Episode $episodeNumber",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "links" to listOf(
                        mapOf(
                            "link" to url,
                            "quality" to "1080"
                        )
                    )
                )
            }
            "Mp4" -> {
                Log.d(TAG, "Found streams from Mp4")
                val response = session.get(url, timeout = 10)
                response.throwIfError()

                val embedHtml = response.text.replace(" ", "").replace("\n", "")
                val matcher = MP4_SERVER_JUICY_STREAM_REGEX.matcher(embedHtml)

                if (!matcher.find()) return null

                val videoUrl = matcher.group(1)
                return mapOf(
                    "server" to "mp4-upload",
                    "headers" to mapOf("Referer" to "https://www.mp4upload.com/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to listOf(
                        mapOf(
                            "link" to videoUrl,
                            "quality" to "1080"
                        )
                    )
                )
            }
            "Fm-Hls" -> {
                Log.d(TAG, "Found streams from Fm-Hls")
                val response = session.get(url, timeout = 10)
                response.throwIfError()

                val embedHtml = response.text.replace(" ", "").replace("\n", "")
                val matcher = MP4_SERVER_JUICY_STREAM_REGEX.matcher(embedHtml)

                if (!matcher.find()) return null

                val videoUrl = matcher.group(1)
                return mapOf(
                    "server" to "filemoon",
                    "headers" to mapOf("Referer" to "https://www.mp4upload.com/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to listOf(
                        mapOf(
                            "link" to videoUrl,
                            "quality" to "1080"
                        )
                    )
                )
            }
            "Ok", "Vid-mp4", "Ss-Hls" -> {
                Log.d(TAG, "Found streams from ${embed["sourceName"]}")
                val response = session.get(url, timeout = 10)
                response.throwIfError()

                val jsonResponse = Json.parseToJsonElement(response.text).jsonObject

                @Suppress("UNCHECKED_CAST")
                val links = jsonResponse["links"]?.let { it as List<Map<String, Any>> } ?: emptyList()

                return mapOf(
                    "server" to when(embed["sourceName"]) {
                        "Ok" -> "filemoon"
                        "Vid-mp4" -> "Vid-mp4"
                        else -> "StreamSb"
                    },
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
        }

        // Handle the second case - get the stream url for defined source names
        val jsonUrl = "https://$API_BASE_URL${url.replace("clock", "clock.json")}"
        val response = session.get(jsonUrl, timeout = 10)
        response.throwIfError()

        val jsonResponse = Json.parseToJsonElement(response.text).jsonObject

        @Suppress("UNCHECKED_CAST")
        val links = jsonResponse["links"]?.let { it as List<Map<String, Any>> } ?: emptyList()

        // Second case: Handle different source names
        return when (embed["sourceName"] as String) {
            "Luf-mp4" -> {
                Log.d(TAG, "Found streams from gogoanime")
                mapOf(
                    "server" to "gogoanime",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            "Kir" -> {
                Log.d(TAG, "Found streams from wetransfer")
                mapOf(
                    "server" to "weTransfer",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            "S-mp4" -> {
                Log.d(TAG, "Found streams from sharepoint")
                mapOf(
                    "server" to "sharepoint",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            "Sak" -> {
                Log.d(TAG, "Found streams from dropbox")
                mapOf(
                    "server" to "dropbox",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            "Default" -> {
                Log.d(TAG, "Found streams from wixmp")
                mapOf(
                    "server" to "wixmp",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            "Ak" -> {
                Log.d(TAG, "Found streams from Ak")
                mapOf(
                    "server" to "Ak",
                    "headers" to mapOf("Referer" to "https://$API_BASE_URL/"),
                    "subtitles" to emptyList<Any>(),
                    "episode_title" to "${allanimeEpisode["notes"] ?: animeTitle}; Episode $episodeNumber",
                    "links" to Utils.giveRandomQuality(links)
                )
            }
            else -> null
        }
    }

    /**
     * Retrieve streaming information for a specific episode of an anime.
     *
     * @param animeId The unique identifier for the anime
     * @param episodeNumber The episode number to retrieve streams for
     * @param translationType The type of translation for the episode
     * @return A sequence of maps containing streaming information for the episode
     */
    fun getEpisodeStreams(
        animeId: String,
        episodeNumber: String,
        translationType: String = "sub"
    ): Sequence<Map<String, Any>> = sequence {
        Log.d(TAG, "Getting episode streams for anime ID: $animeId, episode: $episodeNumber")

        @Suppress("UNCHECKED_CAST")
        val animeInfoJson = store.get("$animeId:anime_info", "")
        val animeInfo = if (animeInfoJson.isNotEmpty()) {
            Json.decodeFromString<Map<String, String>>(animeInfoJson)
        } else null
        val animeTitle = animeInfo?.get("title") ?: ""

        val allanimeEpisode = _getAnimeEpisode(animeId, episodeNumber, translationType)

        @Suppress("UNCHECKED_CAST")
        val sourceUrls = allanimeEpisode["sourceUrls"] as List<Map<String, Any>>

        for (embed in sourceUrls) {
            val sourceName = embed["sourceName"] as? String ?: ""
            if (sourceName !in listOf(
                    "Sak",      // 7
                    "S-mp4",    // 7.9
                    "Luf-mp4",  // 7.7
                    "Default",  // 8.5
                    "Yt-mp4",   // 7.9
                    "Kir",      // NA
                    "Mp4"       // 4
                )) {
                Log.d(TAG, "Found $sourceName but ignoring")
                continue
            }

            val server = _getServer(embed, animeTitle, allanimeEpisode, episodeNumber)
            if (server != null) {
                yield(server)
            }
        }
    }

    override suspend fun searchForAnime(
        query: String,
        page: Int
    ): Result<List<Anime>> {
        TODO("Not yet implemented")
    }

    override suspend fun getTrendingAnime(page: Int): Result<List<Anime>> {
        TODO("Not yet implemented")
    }

    override suspend fun getAnimeDetailed(animeId: Int): Result<AnilistTypes.AnimeDetailed?> {
        TODO("Not yet implemented")
    }
}