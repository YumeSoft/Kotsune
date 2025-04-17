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
    private val animeIdCache = mutableMapOf<String, String>()
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
        anilistId: Int,
        query: String,
        translationType: String
    ): Result<Anime> = apiRequest {
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

        // First try to find a perfect match by anilistId
        for (i in 0 until edges.length()) {
            val result = edges.getJSONObject(i)
            val alternativeId = result.getString("_id")
            val title = result.getString("name")

            // Extract episode counts
            result.optString("episodeCount", null)?.toIntOrNull()

            // Extract available episodes by type
            val availableEpisodes = result.optJSONObject("availableEpisodes")
            val subEpisodes = availableEpisodes?.optInt("sub", 0) ?: 0
            val dubEpisodes = availableEpisodes?.optInt("dub", 0) ?: 0
            val rawEpisodes = availableEpisodes?.optInt("raw", 0) ?: 0

            // Determine episode count for requested translation type
            val availableTypeEpisodes = when (translationType.lowercase()) {
                "dub" -> dubEpisodes
                "raw" -> rawEpisodes
                else -> subEpisodes // Default to sub
            }

            val animeAnilistId = try {
                result.getInt("aniListId")
            } catch (e: Exception) {
                // Handle case where aniListId might be a string or missing
                result.optString("aniListId", "0").toIntOrNull() ?: 0
            }

            logDebug("Found anime: $title with ID: $alternativeId, aniListId: $animeAnilistId, " +
                    "availableEpisodes: sub=$subEpisodes, dub=$dubEpisodes, raw=$rawEpisodes")

            // Cache the anime title for use in episode streams
            animeInfoStore[alternativeId] = title

            // If we find a direct match for the requested anilistId, return it immediately
            if (animeAnilistId == anilistId) {
                return@apiRequest Anime(
                    anilistId = anilistId,
                    alternativeId = alternativeId,
                    title = listOf(title),
                    availableEpisodes = availableTypeEpisodes
                )
            }
        }

        // If no direct match was found, fallback to the first result
        if (edges.length() > 0) {
            val result = edges.getJSONObject(0)
            val id = result.getString("_id")
            val title = result.getString("name")

            return@apiRequest Anime(
                anilistId = anilistId, // Keep the originally requested anilistId
                alternativeId = id,
                title = listOf(title)
            )
        }

        throw IllegalStateException("No anime found for query: $query")
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
                    "Yt-mp4", "Kir", "Mp4", "Fm-Hls",  "Ok", "Vid-mp4"
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
     * Performs symmetric XOR decryption on a hex string.
     *
     * @param password Single digit key used for XOR operation
     * @param target Hex string to decrypt
     * @return Decrypted UTF-8 string
     */
    private fun symmetricXor(password: Int, target: String): String {
        // Convert hex string to bytes and XOR each byte with password
        val decodedBytes = target.chunked(2)
            .map { it.toInt(16) xor password }
            .map { it.toByte() }
            .toByteArray()

        // Return as UTF-8 string
        return String(decodedBytes, Charsets.UTF_8)
    }
    /**
     * Decode obfuscated URLs using a simple XOR mechanism
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

            // Map each hex pair to its corresponding character
            val decodedChar = when (hexPair) {
                "17" -> "/"
                "59" -> "a"
                "5b" -> "c"
                "54" -> "l"
                "57" -> "o"
                "53" -> "k"
                "07" -> "?"
                "51" -> "i"
                "5c" -> "d"
                "05" -> "="
                "0f" -> "7"
                "0c" -> "4"
                "0a" -> "2"
                "0b" -> "3"
                "0e" -> "6"
                "5d" -> "e"
                "01" -> "9"
                "08" -> "0"
                "09" -> "1"
                "5e" -> "f"
                "5f" -> "g"
                "0d" -> "5"
                "5a" -> "b"
                "00" -> "8"
                "48" -> "p"
                "4e" -> "v"
                "4c" -> "t"
                "4f" -> "w"
                "4b" -> "s"
                "4a" -> "r"
                "50" -> "h"
                "49" -> "q"
                "52" -> "j"
                "55" -> "m"
                "56" -> "n"
                "58" -> "z"
                "02" -> ":"
                "16" -> "."
                "15" -> "-"
                "14" -> ","
                "20" -> "X"
                "21" -> "Y"
                "22" -> "["
                "23" -> "\\"
                "24" -> "]"
                "25" -> "^"
                "26" -> "`"
                "27" -> "{"
                "28" -> "P"
                "29" -> "Q"
                "2a" -> "R"
                "2b" -> "S"
                "2c" -> "T"
                "2d" -> "U"
                "2e" -> "V"
                "2f" -> "W"
                "30" -> "H"
                "31" -> "I"
                "32" -> "J"
                "33" -> "K"
                "34" -> "L"
                "35" -> "M"
                "36" -> "N"
                "37" -> "O"
                "38" -> "Z"
                "39" -> "A"
                "3a" -> "B"
                "3b" -> "C"
                "3c" -> "D"
                "3d" -> "E"
                "3e" -> "F"
                "3f" -> "G"
                "40" -> "x"
                "41" -> "y"
                "42" -> "|"
                "43" -> "}"
                "44" -> "~"
                "45" -> "%"
                "46" -> "$"
                "47" -> "#"
                "60" -> "@"
                "61" -> "!"
                "62" -> "\""
                "18" -> " "
                "19" -> "_"
                "1a" -> ":"
                "1b" -> ";"
                "1c" -> "<"
                "1d" -> "="
                "1e" -> "&"
                "1f" -> "'"
                "03" -> ";"
                "04" -> "<"
                "06" -> ">"
                "10" -> "("
                "11" -> ")"
                "12" -> "*"
                "13" -> "+"
                else -> {
                    Log.d("URL_DECODE", "Unknown hex pair: $hexPair")
                    "?"  // Unknown character
                }
            }

            result.append(decodedChar)
            i += 2
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