package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaDexUserProfile
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaStatistics
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaWithStatus
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ReadingHistoryItem
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.dto.*
import java.io.IOException

/**
 * Ktor client implementation for MangaDex API
 */
class MangaDexKtorClient(
    private val appConfig: AppConfig
) {
    private val TAG = "MangaDexKtorClient"
    private val baseUrl = "https://api.mangadex.org"
    private val authUrl = "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"
    
    // Token refresh synchronization
    private val tokenRefreshMutex = Mutex()
    private var isRefreshing = false
    
    // Ktor HttpClient with JSON serialization
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 15000
        }
        
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
    
    /**
     * Refreshes the access token using refresh token
     * @return True if token was successfully refreshed
     */
    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        // Use synchronization to prevent multiple refresh attempts at once
        tokenRefreshMutex.withLock {
            if (isRefreshing) {
                // Wait for the ongoing refresh to complete
                while (isRefreshing) {
                    kotlinx.coroutines.delay(100)
                }
                return@withContext appConfig.hasValidMangadexToken()
            }
            
            isRefreshing = true
        }
        
        try {
            val refreshToken = appConfig.mangadexRefreshToken
            if (refreshToken.isBlank()) {
                Log.e(TAG, "Refresh token is empty")
                isRefreshing = false
                return@withContext false
            }
            
            val clientId = appConfig.mangadexClientId
            val clientSecret = appConfig.mangadexClientSecret
            
            val response = client.submitForm(
                url = authUrl,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                }
            )
            
            Log.d(TAG, "Refresh token response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val tokenResponse: TokenResponse = response.body()
                
                // Get tokens (a refresh may also return a new refresh token)
                val accessToken = tokenResponse.accessToken
                val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
                val expiresIn = tokenResponse.expiresIn ?: 900 // Default to 15 minutes
                
                // Calculate token expiry time (current time + expires_in seconds)
                val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
                
                // Save new tokens
                appConfig.mangadexAccessToken = accessToken
                appConfig.mangadexRefreshToken = newRefreshToken
                appConfig.mangadexTokenExpiry = expiryTime
                
                Log.d(TAG, "Token refreshed successfully, valid for $expiresIn seconds")
                return@withContext true
            } else {
                Log.e(TAG, "Error refreshing token: ${response.bodyAsText()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            return@withContext false
        } finally {
            isRefreshing = false
        }
    }
    
    /**
     * Helper function to execute API requests with automatic token refresh on 401 responses
     * @param requestBlock The API request to execute
     * @return API response
     */
    private suspend fun executeWithTokenRefresh(
        requestBlock: suspend () -> HttpResponse
    ): HttpResponse {
        var response = requestBlock()

        // If unauthorized and we have refresh token, try refreshing and retry the request
        if (response.status == HttpStatusCode.Unauthorized) {
            Log.d(TAG, "Received 401 Unauthorized, attempting token refresh")
            if (refreshToken()) {
                // Retry with new token
                response = requestBlock()

                if (response.status == HttpStatusCode.Unauthorized) {
                    Log.e(TAG, "Still unauthorized after token refresh")
                }
            } else {
                Log.e(TAG, "Token refresh failed")
            }
        }

        return response
    }
    
    /**
     * Checks if the current user is authenticated with MangaDex
     * @return boolean indicating if there's a valid token
     */
    fun isAuthenticated(): Boolean {
        return appConfig.hasValidMangadexToken()
    }
    
    /**
     * Get user profile information with automatic token refresh
     * @return MangaDexUserProfile or null if error occurs
     */
    suspend fun getUserProfile(): MangaDexUserProfile? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserProfile: Not authenticated")
            return@withContext null
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/user/me") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "User profile response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val userResponse: UserResponse = response.body()
                
                if (userResponse.result == "ok") {
                    val data = userResponse.data
                    val attributes = data.attributes
                    
                    return@withContext MangaDexUserProfile(
                        id = data.id,
                        username = attributes.username,
                        avatarUrl = attributes.avatar
                    )
                }
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            return@withContext null
        }
    }
    
    /**
     * Get user's library (manga with reading status)
     * @return List of MangaWithStatus objects
     */
    suspend fun getUserLibrary(): List<MangaWithStatus> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserLibrary: Not authenticated")
            return@withContext emptyList()
        }
        
        try {
            // First, get user's reading statuses
            val statusResponse = executeWithTokenRefresh {
                client.get("$baseUrl/manga/status") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!statusResponse.status.isSuccess()) {
                return@withContext emptyList()
            }
            
            val statusJson: MangaStatusResponse = statusResponse.body()
            if (statusJson.result != "ok" || statusJson.statuses.isEmpty()) {
                return@withContext emptyList()
            }
            
            val mangaStatusMap = statusJson.statuses
            
            // Fetch details for each manga in batches
            val mangaWithStatus = mutableListOf<MangaWithStatus>()
            
            if (mangaStatusMap.isNotEmpty()) {
                val mangaIds = mangaStatusMap.keys.toList()
                val batchSize = 10
                
                for (i in mangaIds.indices step batchSize) {
                    val batch = mangaIds.subList(i, minOf(i + batchSize, mangaIds.size))
                    val mangaBatch = fetchMangaBatch(batch)
                    
                    for (manga in mangaBatch) {
                        val status = mangaStatusMap[manga.id] ?: continue
                        mangaWithStatus.add(MangaWithStatus(status, manga))
                    }
                }
            }
            
            return@withContext mangaWithStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user library", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Fetch batch of manga details
     * @param ids List of manga IDs to fetch
     * @return List of Manga objects
     */
    private suspend fun fetchMangaBatch(ids: List<String>): List<Manga> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        
        try {
            val response = client.get("$baseUrl/manga") {
                parameter("includes[]", "cover_art")
                parameter("includes[]", "author")
                parameter("includes[]", "artist")
                parameter("includes[]", "tag")
                
                ids.forEach { id ->
                    parameter("ids[]", id)
                }
                
                if (isAuthenticated()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!response.status.isSuccess()) {
                return@withContext emptyList()
            }
            
            val mangaResponse: MangaListResponse = response.body()
            if (mangaResponse.result != "ok") {
                return@withContext emptyList()
            }
            
            return@withContext mangaResponse.data.map { parseMangaFromDto(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga batch", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get manga details by ID
     * @param mangaId The manga ID to fetch
     * @return Manga object or null if not found
     */
    suspend fun getMangaDetails(mangaId: String): Manga? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching manga details for ID: $mangaId")
            
            val response = client.get("$baseUrl/manga/$mangaId") {
                parameter("includes[]", "cover_art")
                parameter("includes[]", "author")
                parameter("includes[]", "artist")
                parameter("includes[]", "tag")
                
                if (isAuthenticated()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!response.status.isSuccess()) {
                val errorMessage = when(response.status.value) {
                    404 -> "Manga not found"
                    429 -> "Rate limit exceeded. Please try again later."
                    in 500..599 -> "Server error. Please try again later."
                    else -> "Error ${response.status.value}: ${response.status.description}"
                }
                
                Log.e(TAG, "API error: $errorMessage")
                throw IOException(errorMessage)
            }
            
            val mangaResponse: MangaDetailResponse = response.body()
            if (mangaResponse.result != "ok") {
                return@withContext null
            }
            
            return@withContext parseMangaFromDto(mangaResponse.data)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manga details", e)
            throw e
        }
    }
    
    /**
     * Get chapters for a manga
     * @param mangaId The manga ID to fetch chapters for
     * @return List of ChapterModel objects
     */
    suspend fun getChapters(mangaId: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chapters for manga ID: $mangaId")
            val chapters = mutableListOf<ChapterModel>()
            val chapterMap = mutableMapOf<String, MutableList<ChapterModel>>()
            var offset = 0
            var hasMoreChapters = true
            
            while (hasMoreChapters) {
                val response = client.get("$baseUrl/manga/$mangaId/feed") {
                    parameter("limit", "500")
                    parameter("offset", offset.toString())
                    parameter("includes[]", "scanlation_group")
                    
                    if (isAuthenticated()) {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                        }
                    }
                }
                
                if (!response.status.isSuccess()) {
                    val errorMessage = when(response.status.value) {
                        404 -> "Chapters not found"
                        429 -> "Rate limit exceeded. Please try again later."
                        in 500..599 -> "Server error. Please try again later."
                        else -> "Error ${response.status.value}: ${response.status.description}"
                    }
                    
                    Log.e(TAG, "API error: $errorMessage")
                    throw IOException(errorMessage)
                }
                
                val chapterResponse: ChapterListResponse = response.body()
                if (chapterResponse.result != "ok") {
                    break
                }
                
                val data = chapterResponse.data
                
                // If we got fewer than 500 chapters, we've reached the end
                if (data.size < 500) {
                    hasMoreChapters = false
                } else {
                    offset += 500
                }
                
                for (chapterDto in data) {
                    val attributes = chapterDto.attributes
                    val chapterNumber = attributes.chapter
                    
                    if (chapterNumber == null || chapterNumber.isEmpty()) continue  // Skip chapters without numbers
                    
                    // Get language code
                    val language = attributes.translatedLanguage ?: "en"
                    
                    // Get scanlation group
                    var translatorGroup: String? = null
                    for (rel in chapterDto.relationships) {
                        if (rel.type == "scanlation_group") {
                            translatorGroup = rel.attributes?.name
                            break
                        }
                    }
                    
                    val externalUrl = attributes.externalUrl
                    val isOfficial = !externalUrl.isNullOrBlank()
                    
                    val chapterTitle = attributes.title ?: ""
                    val finalTitle = if (chapterTitle.isBlank()) {
                        "Chapter $chapterNumber"
                    } else {
                        chapterTitle
                    }
                    
                    val chapterModel = ChapterModel(
                        id = chapterDto.id,
                        number = chapterNumber,
                        title = finalTitle,
                        publishedAt = attributes.publishAt ?: "",
                        pages = attributes.pages ?: 0,
                        volume = attributes.volume,
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
            throw e
        }
    }
    
    /**
     * Update reading status for a manga
     * @param mangaId The manga ID to update
     * @param status The reading status to set
     * @return Boolean indicating success
     */
    suspend fun updateReadingStatus(mangaId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "updateReadingStatus: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/manga/$mangaId/status") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(UpdateStatusRequest(status))
                }
            }
            
            Log.d(TAG, "updateReadingStatus response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reading status", e)
            return@withContext false
        }
    }
    
    /**
     * Mark a chapter as read
     * @param chapterId The chapter ID to mark as read
     * @return Boolean indicating success
     */
    suspend fun markChapterRead(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "markChapterRead: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/chapter/$chapterId/read") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "markChapterRead response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter as read", e)
            return@withContext false
        }
    }
    
    /**
     * Mark a chapter as unread
     * @param chapterId The chapter ID to mark as unread
     * @return Boolean indicating success
     */
    suspend fun markChapterUnread(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "markChapterUnread: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.delete("$baseUrl/chapter/$chapterId/read") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "markChapterUnread response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter as unread", e)
            return@withContext false
        }
    }
    
    /**
     * Add a manga to the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating success
     */
    suspend fun addMangaToFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "addMangaToFavorites: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/manga/$mangaId/follow") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "addMangaToFavorites response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding manga to favorites", e)
            return@withContext false
        }
    }
    
    /**
     * Remove a manga from the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating success
     */
    suspend fun removeMangaFromFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "removeMangaFromFavorites: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.delete("$baseUrl/manga/$mangaId/follow") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "removeMangaFromFavorites response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing manga from favorites", e)
            return@withContext false
        }
    }
    
    /**
     * Check if a manga is in the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating if manga is favorited
     */
    suspend fun isMangaInFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "isMangaInFavorites: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/manga/$mangaId/follow") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "isMangaInFavorites response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val followResponse: FollowResponse = response.body()
                return@withContext followResponse.isFollowing
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if manga is in favorites", e)
            return@withContext false
        }
    }
    
    /**
     * Get popular manga
     * @param limit Number of results to return
     * @return List of Manga objects
     */
    suspend fun getPopularManga(limit: Int = 20): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val contentRatingParams = getContentRatingParams()
            
            val response = client.get("$baseUrl/manga") {
                parameter("limit", limit.toString())
                parameter("order[followedCount]", "desc")
                parameter("includes[]", "cover_art")
                parameter("includes[]", "author")
                
                contentRatingParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                
                if (isAuthenticated()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to get popular manga, response code: ${response.status}")
                return@withContext emptyList()
            }
            
            val mangaResponse: MangaListResponse = response.body()
            if (mangaResponse.result != "ok") {
                return@withContext emptyList()
            }
            
            return@withContext mangaResponse.data.map { parseMangaFromDto(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting popular manga", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Get latest manga updates
     * @param limit Number of results to return
     * @param offset Pagination offset
     * @return List of Manga objects
     */
    suspend fun getLatestMangaUpdates(limit: Int = 50, offset: Int = 0): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val contentRatingParams = getContentRatingParams()
            
            val response = client.get("$baseUrl/manga") {
                parameter("limit", limit.toString())
                parameter("offset", offset.toString())
                parameter("order[updatedAt]", "desc")
                parameter("includes[]", "cover_art")
                
                contentRatingParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                
                if (isAuthenticated()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to get latest manga updates, response code: ${response.status}")
                return@withContext emptyList()
            }
            
            val mangaResponse: MangaListResponse = response.body()
            if (mangaResponse.result != "ok") {
                return@withContext emptyList()
            }
            
            return@withContext mangaResponse.data.map { parseMangaFromDto(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest manga updates", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Search for manga by title
     * @param title The title to search for
     * @return List of Manga objects
     */
    suspend fun searchForManga(title: String): List<Manga> = withContext(Dispatchers.IO) {
        try {
            val contentRatingParams = getContentRatingParams()
            
            val response = client.get("$baseUrl/manga") {
                parameter("title", title)
                parameter("limit", "50")
                parameter("includes[]", "cover_art")
                parameter("includes[]", "author")
                parameter("includes[]", "tag")
                
                contentRatingParams.forEach { (key, value) ->
                    parameter(key, value)
                }
                
                if (isAuthenticated()) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to search manga, response code: ${response.status}")
                return@withContext emptyList()
            }
            
            val mangaResponse: MangaListResponse = response.body()
            if (mangaResponse.result != "ok") {
                return@withContext emptyList()
            }
            
            return@withContext mangaResponse.data.map { parseMangaFromDto(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching manga", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Helper function to generate content rating parameters for API calls
     * based on user preferences in AppConfig
     */
    private fun getContentRatingParams(): Map<String, String> {
        val params = mutableMapOf<String, String>()
        val enabledContentTypes = appConfig.contentFilters
        
        if (enabledContentTypes.isEmpty()) {
            // Default to safe if nothing is selected
            params["contentRating[]"] = AppConfig.CONTENT_FILTER_SAFE
            return params
        }
        
        enabledContentTypes.forEach { contentType ->
            params["contentRating[]"] = contentType
        }
        
        Log.d(TAG, "Using content filters: $enabledContentTypes")
        return params
    }
    
    /**
     * Parse a manga dto into a Manga object
     */
    private fun parseMangaFromDto(mangaDto: MangaDto): Manga {
        val id = mangaDto.id
        val attributes = mangaDto.attributes
        
        val titles = attributes.title.values.toMutableList()
        val originalLanguage = attributes.originalLanguage
        val status = attributes.status ?: "unknown"
        
        val description = attributes.description["en"]
            ?: attributes.description.values.firstOrNull()
            ?: ""
        
        val lastUpdated = attributes.updatedAt
        val contentRating = attributes.contentRating ?: "safe"
        val year = attributes.year
        
        // Extract cover art
        var poster: String? = null
        for (rel in mangaDto.relationships) {
            if (rel.type == "cover_art" && rel.attributes != null) {
                val fileName = rel.attributes.fileName
                if (fileName != null) {
                    poster = "https://uploads.mangadex.org/covers/$id/$fileName"
                    break
                }
            }
        }
        
        // Extract tags
        val tags = mutableListOf<MangaTag>()
        for (tag in attributes.tags) {
            val tagId = tag.id
            val tagName = tag.attributes?.name?.get("en") ?: "Unknown"
            tags.add(MangaTag(tagId, tagName))
        }
        
        return Manga(
            id = id,
            title = titles,
            originalLanguage = originalLanguage,
            poster = poster,
            status = status,
            description = description,
            lastUpdated = lastUpdated,
            lastChapter = null,
            latestUploadedChapterId = attributes.latestUploadedChapter,
            year = year,
            contentRating = contentRating,
            tags = tags
        )
    }
    
    /**
     * Get language flag emoji based on language code
     */
    private fun getLanguageFlag(languageCode: String): String {
        val parts = languageCode.split("-")
        if (parts.size > 1) parts[1].uppercase() else ""
        
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
    
    /**
     * Get manga statistics (rating, follows, etc)
     * @param mangaId The manga ID to fetch statistics for
     * @return MangaStatistics or null if error occurs
     */
    suspend fun getMangaStatistics(mangaId: String): MangaStatistics? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching statistics for manga ID: $mangaId")
            
            val response = client.get("$baseUrl/statistics/manga/$mangaId")
            
            if (!response.status.isSuccess()) {
                Log.e(TAG, "Failed to get manga statistics, response code: ${response.status}")
                return@withContext null
            }
            
            val statsResponse: StatisticsResponse = response.body()
            if (statsResponse.result != "ok") {
                return@withContext null
            }
            
            val mangaStats = statsResponse.statistics[mangaId]
            if (mangaStats == null) {
                Log.e(TAG, "Statistics for manga $mangaId not found in response")
                return@withContext null
            }
            
            return@withContext MangaStatistics(
                rating = mangaStats.rating?.average ?: 0.0,
                ratingBayesian = mangaStats.rating?.bayesian ?: 0.0,
                follows = mangaStats.follows ?: 0,
                commentsCount = mangaStats.comments?.repliesCount ?: 0,
                commentsThreadId = mangaStats.comments?.threadId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga statistics", e)
            return@withContext null
        }
    }
    
    /**
     * Get user's reading status for a manga
     * @param mangaId The manga ID to check
     * @return Reading status string or null if not in user's list
     */
    suspend fun getMangaUserStatus(mangaId: String): String? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getMangaUserStatus: Not authenticated")
            return@withContext null
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/manga/$mangaId/status") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "getMangaUserStatus response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val statusResponse: UserMangaStatusResponse = response.body()
                return@withContext statusResponse.status
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga user status", e)
            return@withContext null
        }
    }
    
    /**
     * Update user's reading status for a manga
     * @param mangaId The manga ID to update
     * @param status The reading status to set (null to remove from list)
     * @return Boolean indicating success
     */
    suspend fun updateMangaUserStatus(mangaId: String, status: String?): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "updateMangaUserStatus: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/manga/$mangaId/status") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(UpdateStatusRequest(status ?: ""))
                }
            }
            
            Log.d(TAG, "updateMangaUserStatus response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating manga user status", e)
            return@withContext false
        }
    }
    
    /**
     * Rate a manga
     * @param mangaId The manga ID to rate
     * @param rating The rating to set (1-10)
     * @return Boolean indicating success
     */
    suspend fun rateManga(mangaId: String, rating: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "rateManga: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/rating/$mangaId") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(RatingRequest(rating))
                }
            }
            
            Log.d(TAG, "rateManga response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error rating manga", e)
            return@withContext false
        }
    }
    
    /**
     * Delete user's rating for a manga
     * @param mangaId The manga ID to delete rating for
     * @return Boolean indicating success
     */
    suspend fun deleteMangaRating(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "deleteMangaRating: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.delete("$baseUrl/rating/$mangaId") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "deleteMangaRating response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting manga rating", e)
            return@withContext false
        }
    }
    
    /**
     * Get user's current rating for a manga
     * @param mangaId The manga ID to get rating for
     * @return User's rating (1-10) or null if not rated
     */
    suspend fun getUserMangaRating(mangaId: String): Int? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserMangaRating: Not authenticated")
            return@withContext null
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/rating") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "getUserMangaRating response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val ratingResponse: UserRatingsResponse = response.body()
                if (ratingResponse.result == "ok") {
                    return@withContext ratingResponse.ratings[mangaId]
                }
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user manga rating", e)
            return@withContext null
        }
    }
    
    /**
     * Follow a manga
     * @param mangaId The manga ID to follow
     * @return Boolean indicating success
     */
    suspend fun followManga(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "followManga: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/manga/$mangaId/follow") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "followManga response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error following manga", e)
            return@withContext false
        }
    }
    
    /**
     * Unfollow a manga
     * @param mangaId The manga ID to unfollow
     * @return Boolean indicating success
     */
    suspend fun unfollowManga(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "unfollowManga: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.delete("$baseUrl/manga/$mangaId/follow") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "unfollowManga response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error unfollowing manga", e)
            return@withContext false
        }
    }
    
    /**
     * Get manga read markers (which chapters are marked as read)
     * @param mangaId The manga ID to get read markers for
     * @return List of chapter IDs marked as read
     */
    suspend fun getMangaReadMarkers(mangaId: String): List<String> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getMangaReadMarkers: Not authenticated")
            return@withContext emptyList()
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/manga/$mangaId/read") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                }
            }
            
            Log.d(TAG, "getMangaReadMarkers response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val readResponse: ReadMarkersResponse = response.body()
                if (readResponse.result == "ok") {
                    return@withContext readResponse.data
                }
            }
            
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga read markers", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Update batch manga read markers (mark chapters as read or unread)
     * @param mangaId The manga ID to update read markers for
     * @param chaptersToMarkRead List of chapter IDs to mark as read
     * @param chaptersToMarkUnread List of chapter IDs to mark as unread
     * @return Boolean indicating success
     */
    suspend fun updateBatchMangaReadMarkers(
        mangaId: String, 
        chaptersToMarkRead: List<String>, 
        chaptersToMarkUnread: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "updateBatchMangaReadMarkers: Not authenticated")
            return@withContext false
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.post("$baseUrl/manga/$mangaId/read") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(UpdateReadMarkersRequest(
                        chapterIdsRead = chaptersToMarkRead,
                        chapterIdsUnread = chaptersToMarkUnread
                    ))
                }
            }
            
            Log.d(TAG, "updateBatchMangaReadMarkers response code: ${response.status}")
            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating batch manga read markers", e)
            return@withContext false
        }
    }
    
    /**
     * Get all read chapter markers for the user, optionally filtered by manga IDs
     * @param mangaIds Optional list of manga IDs to filter by
     * @return Map of manga IDs to lists of chapter IDs marked as read
     */
    suspend fun getAllUserReadChapterMarkers(mangaIds: List<String>? = null): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getAllUserReadChapterMarkers: Not authenticated")
            return@withContext emptyMap()
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/manga/read") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    
                    if (mangaIds != null && mangaIds.isNotEmpty()) {
                        mangaIds.forEach { mangaId ->
                            parameter("ids[]", mangaId)
                        }
                    }
                }
            }
            
            Log.d(TAG, "getAllUserReadChapterMarkers response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val readResponse: AllReadMarkersResponse = response.body()
                if (readResponse.result == "ok") {
                    return@withContext readResponse.data
                }
            }
            
            return@withContext emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all user read chapter markers", e)
            return@withContext emptyMap()
        }
    }
    
    /**
     * Get user's reading history
     * @param limit Number of items to return
     * @param offset Pagination offset
     * @return List of ReadingHistoryItem objects
     */
    suspend fun getUserReadingHistory(limit: Int = 20, offset: Int = 0): List<ReadingHistoryItem> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserReadingHistory: Not authenticated")
            return@withContext emptyList()
        }
        
        try {
            val response = executeWithTokenRefresh {
                client.get("$baseUrl/user/history") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${appConfig.mangadexAccessToken}")
                    }
                    parameter("limit", limit.toString())
                    parameter("offset", offset.toString())
                }
            }
            
            Log.d(TAG, "getUserReadingHistory response code: ${response.status}")
            
            if (response.status.isSuccess()) {
                val historyResponse: ReadingHistoryResponse = response.body()
                if (historyResponse.result == "ok") {
                    return@withContext historyResponse.data.map { historyDto ->
                        ReadingHistoryItem(
                            chapterId = historyDto.chapterId,
                            mangaId = historyDto.mangaId,
                            readDate = historyDto.readDate
                        )
                    }
                }
            }
            
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user reading history", e)
            return@withContext emptyList()
        }
    }
}