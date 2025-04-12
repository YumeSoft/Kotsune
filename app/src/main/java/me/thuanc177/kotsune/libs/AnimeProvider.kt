package me.thuanc177.kotsune.libs

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeTitle
import org.json.JSONObject

interface AnimeProvider {
    suspend fun searchForAnime(query: String, page: Int = 1): Result<List<Anime>>
    suspend fun getAnime(animeId: Int): Result<Anime?>
    suspend fun getTrendingAnime(page: Int = 1): Result<List<Anime>>
    suspend fun getAnimeDetailed(animeId: Int): Result<AnimeDetailed?>
}

class AniListAnimeProvider : AnimeProvider {
    private val anilistClient = AnilistClient()
    private val TAG = "AniListAnimeProvider"

    override suspend fun searchForAnime(query: String, page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val variables = mutableMapOf<String, Any>(
                "query" to query,
                "page" to page,
                "type" to "ANIME"
            )

            // Only add status_not_in if it's not empty
            // Don't add empty strings to the status_not_in array

            val (success, jsonResponse) = anilistClient.searchAnime(
                query = query,
                page = page,
                type = "ANIME"
            )

            if (!success || jsonResponse == null) {
                return@withContext Result.failure(Exception("Failed to search for anime"))
            }

            return@withContext Result.success(parseAnimeList(jsonResponse))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching anime", e)
            return@withContext Result.failure(e)
        }
    }

    override suspend fun getAnime(animeId: Int): Result<Anime?> = withContext(Dispatchers.IO) {
        try {
            val (success, jsonResponse) = anilistClient.getAnime(animeId)

            if (!success || jsonResponse == null) {
                return@withContext Result.failure(Exception("Failed to get anime with ID: $animeId"))
            }

            val animeList = parseAnimeList(jsonResponse)
            return@withContext Result.success(animeList.firstOrNull())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting anime", e)
            return@withContext Result.failure(e)
        }
    }

    override suspend fun getAnimeDetailed(animeId: Int): Result<AnimeDetailed?> = withContext(Dispatchers.IO) {
        try {
            val (success, jsonResponse) = anilistClient.getAnime(animeId)

            if (!success || jsonResponse == null) {
                return@withContext Result.failure(Exception("Failed to get detailed anime with ID: $animeId"))
            }

            val detailedAnime = parseAnimeDetailed(jsonResponse)
            return@withContext Result.success(detailedAnime)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting detailed anime", e)
            return@withContext Result.failure(e)
        }
    }

    override suspend fun getTrendingAnime(page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val (success, jsonResponse) = anilistClient.getTrendingAnime(page = page)

            if (!success || jsonResponse == null) {
                return@withContext Result.failure(Exception("Failed to get trending anime"))
            }

            return@withContext Result.success(parseAnimeList(jsonResponse))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting trending anime", e)
            return@withContext Result.failure(e)
        }
    }

    private fun parseAnimeList(json: JSONObject): List<Anime> {
        try {
            val result = mutableListOf<Anime>()
            val dataObj = json.getJSONObject("data")
            val pageObj = dataObj.getJSONObject("Page")
            val mediaArray = pageObj.getJSONArray("media")

            for (i in 0 until mediaArray.length()) {
                val mediaObj = mediaArray.getJSONObject(i)
                result.add(parseAnimeFromJson(mediaObj))
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing anime list", e)
            return emptyList()
        }
    }

    private fun parseAnimeFromJson(mediaObj: JSONObject): Anime {
        val id = mediaObj.getInt("id")
        val titleObj = mediaObj.getJSONObject("title")

        // Create a list of titles
        val titleList = mutableListOf<String>()

        // Add the primary title first (prioritize English, then romaji)
        if (!titleObj.isNull("english")) {
            titleObj.getString("english")?.takeIf { it.isNotBlank() }?.let { titleList.add(it) }
        }
        if (!titleObj.isNull("romaji")) {
            titleObj.getString("romaji")?.takeIf { it.isNotBlank() && !titleList.contains(it) }?.let { titleList.add(it) }
        }
        if (!titleObj.isNull("native")) {
            titleObj.getString("native")?.takeIf { it.isNotBlank() && !titleList.contains(it) }?.let { titleList.add(it) }
        }

        // Add synonyms if available
        if (mediaObj.has("synonyms") && !mediaObj.isNull("synonyms")) {
            val synonymsArray = mediaObj.getJSONArray("synonyms")
            for (i in 0 until synonymsArray.length()) {
                val synonym = synonymsArray.getString(i)
                if (synonym.isNotBlank() && !titleList.contains(synonym)) titleList.add(synonym)
            }
        }

        // If no titles were found, add a default
        if (titleList.isEmpty()) {
            titleList.add("Unknown title")
        }

        val description = if (mediaObj.has("description") && !mediaObj.isNull("description"))
            mediaObj.getString("description") else null

        val coverObj = mediaObj.optJSONObject("coverImage")
        val coverImage = coverObj?.optString("large") ?: coverObj?.optString("medium")

        val bannerImage = if (mediaObj.has("bannerImage") && !mediaObj.isNull("bannerImage"))
            mediaObj.getString("bannerImage") else null

        val genres = mutableListOf<String>()
        if (mediaObj.has("genres") && !mediaObj.isNull("genres")) {
            val genresArray = mediaObj.getJSONArray("genres")
            for (i in 0 until genresArray.length()) {
                genres.add(genresArray.getString(i))
            }
        }

        val episodes = if (mediaObj.has("episodes") && !mediaObj.isNull("episodes"))
            mediaObj.getInt("episodes") else null

        val seasonYear = if (mediaObj.has("seasonYear") && !mediaObj.isNull("seasonYear"))
            mediaObj.getInt("seasonYear") else null

        val status = if (mediaObj.has("status") && !mediaObj.isNull("status"))
            mediaObj.getString("status") else null

        val averageScore = if (mediaObj.has("averageScore") && !mediaObj.isNull("averageScore"))
            mediaObj.getInt("averageScore").toFloat() / 10 else null

        return Anime(
            id = id,
            title = titleList,
            description = description,
            coverImage = coverImage,
            bannerImage = bannerImage,
            genres = genres,
            episodes = episodes,
            seasonYear = seasonYear,
            status = status,
            score = averageScore
        )
    }

    private fun parseAnimeDetailed(json: JSONObject): AnimeDetailed? {
        try {
            val dataObj = json.getJSONObject("data")
            val mediaObj = dataObj.getJSONObject("Media")
            val id = mediaObj.getInt("id")

            // Parse title
            val titleObj = mediaObj.getJSONObject("title")
            val title = AnimeTitle(
                english = if (!titleObj.isNull("english")) titleObj.getString("english") else null,
                romaji = if (!titleObj.isNull("romaji")) titleObj.getString("romaji") else null,
                native = if (!titleObj.isNull("native")) titleObj.getString("native") else null
            )

            // Parse other fields
            val description = if (mediaObj.has("description") && !mediaObj.isNull("description"))
                mediaObj.getString("description") else null

            val coverObj = mediaObj.optJSONObject("coverImage")

            // More fields would be parsed here as per the AnimeDetailed class structure

            return AnimeDetailed(
                id = id,
                title = title,
                description = description,
                coverImage = null, // This should be properly parsed from the JSON
                bannerImage = mediaObj.optString("bannerImage"),
                averageScore = mediaObj.optInt("averageScore"),
                duration = mediaObj.optInt("duration"),
                favourites = mediaObj.optInt("favourites"),
                isFavourite = mediaObj.optBoolean("isFavourite"),
                rankings = null, // Parse from JSON
                format = mediaObj.optString("format"),
                genres = mutableListOf(), // Parse from JSON
                isAdult = mediaObj.optBoolean("isAdult"),
                startDate = null, // Parse from JSON
                tags = null, // Parse from JSON
                countryOfOrigin = mediaObj.optString("countryOfOrigin"),
                status = mediaObj.optString("status"),
                stats = null, // Parse from JSON
                seasonYear = mediaObj.optInt("seasonYear"),
                trailer = null, // Parse from JSON
                characters = null, // Parse from JSON
                episodes = mediaObj.optInt("episodes"),
                streamingEpisodes = null, // Parse from JSON
                nextAiringEpisode = null, // Parse from JSON
                recommendations = null // Parse from JSON
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing detailed anime", e)
            return null
        }
    }
}