package me.thuanc177.kotsune.libs.animeProvider.allanime

import android.util.Log
import me.thuanc177.kotsune.libs.BaseAnimeProvider
import me.thuanc177.kotsune.libs.StreamLink
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
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
        try {
            // Create a JSON payload instead of URL parameters
            val payload = JSONObject().apply {
                put("query", query.trim())
                put("variables", JSONObject(variables))
            }.toString()

            // Add comprehensive headers
            val headers = mapOf(
                "Referer" to API_REFERER,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36",
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Origin" to "https://$API_BASE_URL"
            )

            // Assuming you have a post method in HttpClient
            // If not, you'll need to implement it
            val response = try {
                httpClient.post(API_ENDPOINT, payload, headers)
            } catch (e: Exception) {
                // Fallback to GET if POST method isn't available
                val variablesJson = JSONObject(variables).toString()
                val encodedVariables = java.net.URLEncoder.encode(variablesJson, "UTF-8")
                val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val url = "$API_ENDPOINT?variables=$encodedVariables&query=$encodedQuery"
                logDebug("Fallback to GET: $url")
                httpClient.get(url, headers)
            }

            val jsonResponse = JSONObject(response)
            if (jsonResponse.has("errors")) {
                val errors = jsonResponse.getJSONArray("errors")
                logDebug("API returned errors: $errors")
            }

            return jsonResponse.getJSONObject("data")
        } catch (e: Exception) {
            logDebug("GraphQL query failed: ${e.message}")
            // Check if the API endpoint has changed
            logDebug("Checking if API endpoint has changed...")
            try {
                httpClient.get("https://$API_BASE_URL", mapOf())
                logDebug("Base URL is accessible, API endpoint may have changed")
            } catch (e2: Exception) {
                logDebug("Base URL is not accessible: ${e2.message}")
            }
            throw e
        }
    }

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

            Log.d("AllAnimeAPI", "Found anime: $title with ID: $id")
            // Cache the anime title for use in episode streams
            animeInfoStore[id] = title

            animeList.add(
                Anime(
                    anilistId = id.hashCode(),
                    alternativeId = id,
                    title = listOf(title),
                    genres = listOf(),
                )
            )
        }

        animeList
    }

    /**
     * Get detailed anime information
     */
    override suspend fun getAnime(animeId: String): Result<Anime?> = apiRequest {
        logDebug("Getting anime details for: $animeId")

        val variables = mapOf("showId" to animeId)
        val animeData = executeGraphqlQuery(GqlQueries.SHOW_GQL, variables)
        val show = animeData.getJSONObject("show")

        val id = show.getString("_id")
        val aniListId = show.getInt("aniListId")
        val title = show.getString("name")

        // Cache the anime title for use in episode streams
        animeInfoStore[id] = title

        // Get available episodes detail if present
        if (show.has("availableEpisodesDetail")) {
            val episodesDetail = show.getJSONObject("availableEpisodesDetail")
            if (episodesDetail.has("sub")) {
                try {
                    when (val subValue = episodesDetail.get("sub")) {
                        is Int -> subValue
                        is JSONArray -> subValue.length()
                        else -> null
                    }
                } catch (e: Exception) {
                    // If there's an error, attempt to parse as JSONArray
                    try {
                        val jsonArray = episodesDetail.getJSONArray("sub")
                        jsonArray.length()
                    } catch (e2: Exception) {
                        logDebug("Failed to parse 'sub' as either Int or JSONArray: ${e2.message}")
                        null
                    }
                }
            } else null
        } else null

        Anime(
            alternativeId = id,
            anilistId = aniListId,
            title = listOf(title),
            genres = listOf(),
        )
    }

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

        // Sort sourceUrls by priority (higher priority first)
        val sortedSourceUrls = mutableListOf<JSONObject>()
        for (i in 0 until sourceUrls.length()) {
            sortedSourceUrls.add(sourceUrls.getJSONObject(i))
        }
        sortedSourceUrls.sortByDescending { it.optDouble("priority", 0.0) }

        val servers = mutableListOf<StreamServer>()
        for (embed in sortedSourceUrls) {
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

        Log.d("AllAnimeAPI", "Processing server: ${embed.optString("sourceName")} with URL: $url")

        // Use new decode method
        url = decodeUrl(url)
        Log.d("Decoded URL", "$url")

        val sourceName = embed.optString("sourceName", "")
        // Rest of method remains the same...

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
        logDebug("Yt URL: $url")

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
        logDebug("MP4 RawURL: $url")

        val response = httpClient.get(url, mapOf())
        val embedHtml = response.replace(" ", "").replace("\n", "")
        val matcher = MP4_SERVER_JUICY_STREAM_REGEX.matcher(embedHtml)

        return if (matcher.find()) {
            logDebug("MP4 URL: ${matcher.group(1)}")
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
        logDebug("$serverName's stream URL: $url")

        // Convert 'clock' to 'clock.json' in the URL path
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
            // Extract and decode the link
            val linkUrl = link.getString("link")
            val decodedLink = decodeUrl(linkUrl)

            result.add(
                StreamLink(
                    link = decodedLink,
                    quality = link.optString("resolutionStr", "unknown"),
                    translationType = "sub"
                )
            )
        }
        return result
    }

    /**
     * Decode obfuscated URLs using a direct mapping approach instead of XOR
     */
    private fun decodeUrl(input: String): String {
        // Check if URL needs decoding (starts with --)
        if (!input.startsWith("--")) {
            return input
        }

        val encodedPart = input.substring(2)
        val result = StringBuilder()

        // Process the string two characters at a time
        var i = 0
        while (i < encodedPart.length - 1) {
            val hexPair = encodedPart.substring(i, i + 2)
            val decodedChar = when (hexPair) {
                "01" -> "9"
                "08" -> "0"
                "05" -> "="
                "0a" -> "2"
                "0b" -> "3"
                "0c" -> "4"
                "07" -> "?"
                "00" -> "8"
                "5c" -> "d"
                "0f" -> "7"
                "5e" -> "f"
                "17" -> "/"
                "54" -> "l"
                "09" -> "1"
                "48" -> "p"
                "4f" -> "w"
                "0e" -> "6"
                "5b" -> "c"
                "5d" -> "e"
                "0d" -> "5"
                "53" -> "k"
                "1e" -> "&"
                "5a" -> "b"
                "59" -> "a"
                "4a" -> "r"
                "4c" -> "t"
                "4e" -> "v"
                "57" -> "o"
                "51" -> "i"
                "4b" -> "s"
                "4d" -> "u"
                "50" -> "h"
                "52" -> "j"
                "55" -> "m"
                "56" -> "n"
                "58" -> "z"
                "5f" -> "g"
                "1f" -> "'"
                "1a" -> ":"
                "1b" -> ";"
                "1c" -> "<"
                "1d" -> "="
                "16" -> "."
                "14" -> ","
                "15" -> "-"
                "12" -> "*"
                "13" -> "+"
                "10" -> "("
                "11" -> ")"
                "03" -> ";"
                "02" -> ":"
                "04" -> "<"
                "06" -> ">"
                else -> "?" // Unknown character
            }
            result.append(decodedChar)
            i += 2
        }

        // Handle any remaining character (should not happen with valid input)
        if (i < encodedPart.length) {
            result.append("?")
        }

        return result.toString()
    }
}

/**
 * HTTP client interface
 */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = mapOf()): String
    suspend fun post(url: String, body: String, headers: Map<String, String> = mapOf()): String
}