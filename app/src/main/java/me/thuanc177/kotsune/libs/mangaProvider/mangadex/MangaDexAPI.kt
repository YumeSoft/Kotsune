package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.common.fetchMangaInfoFromBal
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag
import org.json.JSONArray
import java.net.URL
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.Boolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MangaDexAPI (
    private val appConfig: AppConfig
) {
    private val TAG = "MangaDexAPI"
    private val baseUrl = "https://api.mangadex.org"

    suspend fun getPopularManga(limit: Int = 20): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                getContentRatingParams()
//                val url = URL("$baseUrl/manga?limit=$limit&order[followedCount]=desc&includes[]=cover_art$contentRatingParams")
                val url = URL("$baseUrl/manga?limit=$limit&order[followedCount]=desc&includes[]=cover_art&includes[]=author")

                Log.d(TAG, "Fetching popular manga with URL: $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    continuation.resume(Pair(true, jsonObject))
                } else {
                    Log.e(TAG, "Failed to get popular manga, response code: $responseCode")
                    continuation.resume(Pair(false, null))
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting popular manga: ${e.message}", e)
                continuation.resume(Pair(false, null))
            }
        }
    }

    suspend fun getLatestMangaUpdates(
        limit: Int = 50,
        offset: Int = 0
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                val contentRatingParams = getContentRatingParams()
                val url = URL("$baseUrl/manga?limit=$limit&offset=$offset&order[updatedAt]=desc&includes[]=cover_art$contentRatingParams")

                Log.d(TAG, "Fetching latest updates with URL: $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    continuation.resume(Pair(true, jsonObject))
                } else {
                    Log.e(TAG, "Failed to get latest manga updates, response code: $responseCode")
                    continuation.resume(Pair(false, null))
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest manga updates: ${e.message}", e)
                continuation.resume(Pair(false, null))
            }
        }
    }

    suspend fun searchForManga(title: String, vararg args: Any): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val contentRatingParams = getContentRatingParams()
            val url = URL("$baseUrl/manga?title=$encodedTitle&limit=50&includes[]=cover_art&includes[]=author&includes[]=rating$contentRatingParams")

            Log.d(TAG, "Searching manga with URL: $url")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val dataArray = jsonObject.getJSONArray("data")
                val results = mutableListOf<Map<String, Any>>()

                for (i in 0 until dataArray.length()) {
                    val mangaObject = dataArray.getJSONObject(i)
                    val mangaId = mangaObject.getString("id")
                    val attributes = mangaObject.getJSONObject("attributes")

                    // Get title
                    val titleObj = attributes.getJSONObject("title")
                    val title = titleObj.optString("en") ?: titleObj.keys().asSequence()
                        .map { titleObj.getString(it) }.firstOrNull() ?: "Unknown"

                    // Get status
                    val status = attributes.optString("status", "unknown")

                    // Get year from publishAt date
                    var year: Int? = null
                    if (attributes.has("year")) {
                        year = attributes.optInt("year")
                    } else if (attributes.has("publicationDemographic")) {
                        // Try to get year from created date as fallback
                        attributes.optString("createdAt", "")
                            .substringBefore("T")
                            .substringBefore("-")
                            .toIntOrNull()?.let { year = it }
                    }

                    // Get rating
                    val contentRating = attributes.optString("contentRating", "unknown")
                    when (contentRating) {
                        "safe" -> "Safe"
                        "suggestive" -> "Suggestive"
                        "erotica" -> "Erotica"
                        "pornographic" -> "Pornographic"
                        else -> "Unknown"
                    }

                    // Get cover art
                    var coverImage: String? = null
                    val relationships = mangaObject.optJSONArray("relationships") ?: JSONArray()
                    for (j in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(j)
                        if (rel.getString("type") == "cover_art") {
                            val coverAttributes = rel.getJSONObject("attributes")
                            val fileName = coverAttributes.optString("fileName")
                            if (fileName.isNotEmpty()) {
                                coverImage =
                                    "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                                break
                            }
                        }
                    }
                    if (coverImage == null) {
                        Log.w(TAG, "No cover art found for manga ID: $mangaId")
                    }

                    val tags = mutableListOf<MangaTag>()
                    val tagsArray = attributes.getJSONArray("tags")
                    for (j in 0 until tagsArray.length()) {
                        val tag = tagsArray.getJSONObject(j)
                        val tagId = tag.getString("id")
                        val tagAttributes = tag.getJSONObject("attributes")
                        val tagName = tagAttributes.getJSONObject("name")
                        val nameEn = tagName.optString("en")
                        if (!nameEn.isNullOrEmpty()) {
                            tags.add(MangaTag(id = tagId, tagName = nameEn))
                        }
                    }


                    val mangaMap = mapOf(
                        "id" to mangaId,
                        "title" to title,
                        "poster" to (coverImage ?: ""),
                        "type" to status,
                        "genres" to tags,
                        "year" to year,
                        "contentRating" to contentRating
                    )
                    results.add(mangaMap as Map<String, Any>)
                }

                connection.disconnect()
                Log.d(TAG, "Search found ${results.size} manga")
                return@withContext results
            } else {
                Log.e(TAG, "Failed to search manga, response code: $responseCode")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching manga: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Helper function to generate content rating parameters for API calls
     * based on user preferences in AppConfig
     */
    private fun getContentRatingParams(): String {
        val enabledContentTypes = appConfig.contentFilters

        if (enabledContentTypes.isEmpty()) {
            // Default to safe if nothing is selected
            return "&contentRating[]=${AppConfig.CONTENT_FILTER_SAFE}"
        }

        val contentRatingBuilder = StringBuilder()
        enabledContentTypes.forEach { contentType ->
            contentRatingBuilder.append("&contentRating[]=$contentType")
        }

        Log.d(TAG, "Using content filters: $enabledContentTypes")
        return contentRatingBuilder.toString()
    }

    suspend fun getMangaDetails(mangaId: String): Manga? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching manga details for ID: $mangaId")
            val url = "https://api.mangadex.org/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val data = jsonObject.getJSONObject("data")
                val attributes = data.getJSONObject("attributes")

                // Extract title
                val titleObj = attributes.getJSONObject("title")
                val titles = mutableListOf<String>()
                for (key in titleObj.keys()) {
                    titleObj.getString(key)?.let { titles.add(it) }
                }

                // Extract description
                val description = attributes.optJSONObject("description")?.let { descObj ->
                    descObj.optString("en") ?: run {
                        // If English description not available, get the first available description
                        for (key in descObj.keys()) {
                            val desc = descObj.optString(key)
                            if (desc.isNotEmpty()) return@run desc
                        }
                        ""
                    }
                } ?: ""

                val status = attributes.optString("status", "unknown")
                val year = attributes.optInt("year", 0)
                val contentRating = attributes.optString("contentRating", "safe")

                // Get cover image
                var coverImage: String? = null
                val relationships = data.getJSONArray("relationships")
                for (i in 0 until relationships.length()) {
                    val rel = relationships.getJSONObject(i)
                    if (rel.getString("type") == "cover_art") {
                        if (rel.has("attributes")) {
                            val coverAttributes = rel.getJSONObject("attributes")
                            val fileName = coverAttributes.optString("fileName")
                            if (fileName.isNotEmpty()) {
                                coverImage = "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                            }
                        }
                    }
                }

                // Extract tags
                val tagsArray = attributes.getJSONArray("tags")
                val tags = mutableListOf<MangaTag>()
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.getJSONObject(i)
                    val tagId = tag.getString("id")

                    // Extract tag name from attributes
                    val tagAttributes = tag.getJSONObject("attributes")
                    val tagNameObj = tagAttributes.getJSONObject("name")

                    // Try to get English name first, then fallback to any available language
                    val tagName = tagNameObj.optString("en") ?: run {
                        var name = ""
                        val keys = tagNameObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val localizedName = tagNameObj.optString(key)
                            if (localizedName.isNotEmpty()) {
                                name = localizedName
                                break
                            }
                        }
                        name
                    }

                    // Only add tags with non-empty names
                    if (tagName.isNotEmpty()) {
                        tags.add(MangaTag(tagId, tagName))
                    }
                }

                return@withContext Manga(
                    id = mangaId,
                    title = titles,
                    poster = coverImage,
                    status = status,
                    description = description,
                    lastUpdated = attributes.optString("updatedAt"),
                    lastChapter = null,
                    latestUploadedChapterId = attributes.optString("latestUploadedChapter"),
                    year = if (year > 0) year else null,
                    contentRating = contentRating,
                    tags = tags
                )
            }

            // Handle error responses
            val errorMessage = when(connection.responseCode) {
                404 -> "Manga not found"
                429 -> "Rate limit exceeded. Please try again later."
                500, 502, 503, 504 -> "Server error. Please try again later."
                else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
            }

            Log.e(TAG, "API error: $errorMessage")
            throw IOException(errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manga details", e)
            return@withContext null
        }
    }

    suspend fun getChapters(mangaId: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chapters for manga ID: $mangaId")
            val chapters = mutableListOf<ChapterModel>()
            val chapterMap = mutableMapOf<String, MutableList<ChapterModel>>()
            var offset = 0
            var hasMoreChapters = true

            while (hasMoreChapters) {
                val url = "https://api.mangadex.org/manga/$mangaId/feed?limit=500&offset=$offset&includes[]=scanlation_group"
                Log.d(TAG, "Fetching chapters with offset: $offset")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val dataArray = jsonObject.getJSONArray("data")

                    // If we got fewer than 500 chapters, we've reached the end
                    if (dataArray.length() < 500) {
                        hasMoreChapters = false
                    } else {
                        offset += 500
                    }

                    for (i in 0 until dataArray.length()) {
                        val chapterObject = dataArray.getJSONObject(i)
                        val chapterId = chapterObject.getString("id")
                        val attributes = chapterObject.getJSONObject("attributes")

                        val externalUrl = if (attributes.has("externalUrl") && !attributes.isNull("externalUrl"))
                            attributes.getString("externalUrl")
                        else
                            null

                        val isOfficial = !externalUrl.isNullOrBlank()

                        val chapterNumber = attributes.optString("chapter", "")
                        if (chapterNumber.isEmpty()) continue  // Skip chapters without numbers

                        // Get language code
                        val language = attributes.optString("translatedLanguage", "en")

                        // Get scanlation group
                        var translatorGroup: String? = null
                        val relationships = chapterObject.getJSONArray("relationships")
                        for (j in 0 until relationships.length()) {
                            val rel = relationships.getJSONObject(j)
                            if (rel.getString("type") == "scanlation_group" && rel.has("attributes")) {
                                translatorGroup = rel.getJSONObject("attributes").optString("name")
                                break
                            }
                        }

                        val chapterTitle = attributes.optString("title", "")
                        val finalTitle = if (chapterTitle.isBlank()) {
                            "Chapter $chapterNumber"
                        } else {
                            chapterTitle
                        }

                        val chapterModel = ChapterModel(
                            id = chapterId,
                            number = chapterNumber,
                            title = finalTitle,
                            publishedAt = attributes.optString("publishAt", ""),
                            pages = attributes.optInt("pages", 0),
                            volume = attributes.optString("volume", "Unknown"),
                            isRead = false,
                            language = language,
                            translatorGroup = translatorGroup,
                            languageFlag = getLanguageFlag(language),
                            isOfficial = isOfficial,
                            externalUrl = externalUrl
                        )

                        // Add to chapter map
                        if (!chapterMap.containsKey(chapterNumber)) {
                            chapterMap[chapterNumber] = mutableListOf()
                        }
                        chapterMap[chapterNumber]?.add(chapterModel)
                    }
                } else {
                    // Handle error responses
                    val errorMessage = when(connection.responseCode) {
                        404 -> "Chapters not found"
                        429 -> "Rate limit exceeded. Please try again later."
                        500, 502, 503, 504 -> "Server error. Please try again later."
                        else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
                    }

                    Log.e(TAG, "API error: $errorMessage")
                    throw IOException(errorMessage)
                }
            }

            // Sort each chapter's translations by language (English first)
            chapterMap.forEach { (_, translations) ->
                translations.sortWith(compareBy<ChapterModel> { it.language != "en" }
                    .thenBy { it.publishedAt }
                    .thenBy { it.language })
            }

            // Flatten the map back to a list, sorted by chapter number
            val sortedChapterNumbers = chapterMap.keys
                .sortedWith(compareBy {
                    it.toFloatOrNull() ?: Float.MAX_VALUE
                })

            for (chapterNum in sortedChapterNumbers) {
                chapters.addAll(chapterMap[chapterNum] ?: emptyList())
            }

            Log.d(TAG, "Fetched a total of ${chapters.size} chapters for manga ID: $mangaId")
            return@withContext chapters

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapters", e)
            return@withContext emptyList()
        }
    }

    private fun getLanguageFlag(languageCode: String): String {
        // Extract the base language and region if present (e.g., "en-us" -> "en" and "us")
        val parts = languageCode.split("-")
        if (parts.size > 1) parts[1].uppercase() else ""

        // For languages with specific regions, use the region's flag
        return when (languageCode) {
            "en" -> "🇬🇧"  // English (default to UK)
            "en-us" -> "🇺🇸"  // English (US)
            "ja" -> "🇯🇵"  // Japanese
            "ko" -> "🇰🇷"  // Korean
            "zh" -> "🇨🇳"  // Chinese (simplified)
            "zh-hk", "zh-tw" -> "🇹🇼"  // Chinese (traditional)
            "ru" -> "🇷🇺"  // Russian
            "fr" -> "🇫🇷"  // French
            "de" -> "🇩🇪"  // German
            "es" -> "🇪🇸"  // Spanish
            "es-la" -> "🇲🇽"  // Spanish (Latin America)
            "pt-br" -> "🇧🇷"  // Portuguese (Brazil)
            "pt" -> "🇵🇹"  // Portuguese
            "it" -> "🇮🇹"  // Italian
            "pl" -> "🇵🇱"  // Polish
            "tr" -> "🇹🇷"  // Turkish
            "th" -> "🇹🇭"  // Thai
            "vi" -> "🇻🇳"  // Vietnamese
            "id" -> "🇮🇩"  // Indonesian
            "ar" -> "🇸🇦"  // Arabic
            "hi" -> "🇮🇳"  // Hindi
            "bn" -> "🇧🇩"  // Bengali
            "ms" -> "🇲🇾"  // Malay
            "fi" -> "🇫🇮"  // Finnish
            "da" -> "🇩🇰"  // Danish
            "no" -> "🇳🇴"  // Norwegian
            "sv" -> "🇸🇪"  // Swedish
            "cs" -> "🇨🇿"  // Czech
            "sk" -> "🇸🇰"  // Slovak
            "hu" -> "🇭🇺"  // Hungarian
            "ro" -> "🇷🇴"  // Romanian
            "uk" -> "🇺🇦"  // Ukrainian
            "bg" -> "🇧🇬"  // Bulgarian
            "el" -> "🇬🇷"  // Greek
            "he" -> "🇮🇱"  // Hebrew
            "lt" -> "🇱🇹"  // Lithuanian
            "lv" -> "🇱🇻"  // Latvian
            "et" -> "🇪🇪"  // Estonian
            "sl" -> "🇸🇮"  // Slovenian
            "hr" -> "🇭🇷"  // Croatian
            "sr" -> "🇷🇸"  // Serbian
            "mk" -> "🇲🇰"  // Macedonian
            "sq" -> "🇦🇱"  // Albanian
            "bs" -> "🇧🇦"  // Bosnian
            "is" -> "🇮🇸"  // Icelandic
            "ga" -> "🇮🇪"  // Irish
            "cy" -> "🇬🇧"  // Welsh
            "eu" -> "🇪🇸"  // Basque
            "ca" -> "🇪🇸"  // Catalan
            "sw" -> "🇰🇪"  // Swahili
            "tl" -> "🇵🇭"  // Tagalog
            "jw" -> "🇮🇩"  // Javanese
            "su" -> "🇮🇩"  // Sundanese
            "la" -> "🇻🇦"  // Latin
            "tlh" -> "🇰🇷"  // Klingon
            "xh" -> "🇿🇦"  // Xhosa
            "zu" -> "🇿🇦"  // Zulu
            "af" -> "🇿🇦"  // Afrikaans
            "am" -> "🇪🇹"  // Amharic
            "hy" -> "🇦🇲"  // Armenian
            "mn" -> "🇲🇳"  // Mongolian
            "ne" -> "🇳🇵"  // Nepali
            "pa" -> "🇮🇳"  // Punjabi
            "ta" -> "🇮🇳"  // Tamil
            "te" -> "🇮🇳"  // Telugu
            "ml" -> "🇮🇳"  // Malayalam
            "or" -> "🇮🇳"  // Odia
            "si" -> "🇱🇰"  // Sinhala
            "my" -> "🇲🇲"  // Burmese
            "km" -> "🇰🇭"  // Khmer
            "lo" -> "🇱🇦"  // Lao
            else -> "🌐 $languageCode"  // Globe and language code
        }
    }
}