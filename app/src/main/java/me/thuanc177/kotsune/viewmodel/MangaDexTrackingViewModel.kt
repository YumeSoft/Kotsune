package me.thuanc177.kotsune.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaDexUserProfile
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaWithStatus
import me.thuanc177.kotsune.ui.components.LibraryTab
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.text.get
import kotlin.text.set

class MangaDexTrackingViewModel(
    private val appContext: Context,
    private val appConfig: AppConfig,
    private val mangaDexAPI: MangaDexAPI // Add this parameter
) : ViewModel() {

    private val TAG = "MangaDexVM"

    // MutableStateFlow for login state
    private val _isLoggedIn = MutableStateFlow(appConfig.hasValidMangadexToken())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    // Login state handling
    enum class LoginState {
        IDLE,
        LOGGING_IN,
        INVALID_CLIENT_ID,
        INVALID_CLIENT_SECRET,
        INVALID_USERNAME,
        INVALID_PASSWORD,
        LOGIN_SUCCESS,
        LOGIN_FAILED
    }

    private val _loginState = MutableStateFlow(LoginState.IDLE)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // Form fields
    private val _clientId = MutableStateFlow(appConfig.mangadexClientId)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow(appConfig.mangadexClientSecret)
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val _username = MutableStateFlow(appConfig.mangadexUsername)
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(appConfig.mangadexPassword)
    val password: StateFlow<String> = _password.asStateFlow()

    // User profile
    private val _userProfile = MutableStateFlow<MangaDexUserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    // Library state
    private val _selectedLibraryTab = MutableStateFlow(LibraryTab.ALL)
    val selectedLibraryTab = _selectedLibraryTab.asStateFlow()

    private val _allMangaInLibrary = MutableStateFlow<List<MangaWithStatus>>(emptyList())
    private val _userLibrary = MutableStateFlow<List<MangaWithStatus>>(emptyList())
    val userLibrary = _userLibrary.asStateFlow()

    private val _isLibraryLoading = MutableStateFlow(false)
    val isLibraryLoading = _isLibraryLoading.asStateFlow()

    private val _showGuide = MutableStateFlow(false)
    val showGuide = _showGuide.asStateFlow()

    init {
        // Check token validity on initialization
        viewModelScope.launch {
            checkLoginState()
            if (_isLoggedIn.value) {
                loadUserProfile()
                fetchUserLibrary()
            }
        }
    }

    // Field updaters
    fun updateClientId(value: String) {
        _clientId.value = value
    }

    fun updateClientSecret(value: String) {
        _clientSecret.value = value
    }

    fun updateUsername(value: String) {
        _username.value = value
    }

    fun updatePassword(value: String) {
        _password.value = value
    }

    fun checkLoginState() {
        viewModelScope.launch {
            if (appConfig.hasValidMangadexToken()) {
                if (!verifyToken()) {
                    // Try to refresh if expired
                    if (refreshToken()) {
                        _isLoggedIn.value = true
                    } else {
                        // Token couldn't be refreshed, user needs to log in again
                        _isLoggedIn.value = false
                    }
                } else {
                    _isLoggedIn.value = true
                }
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    /**
     * Login to MangaDex using the provided credentials
     * This uses the password grant flow
     */
    fun login() {
        viewModelScope.launch {
            // Input validation
            if (_clientId.value.isBlank()) {
                _loginState.value = LoginState.INVALID_CLIENT_ID
                _errorMessage.value = "Client ID is required"
                return@launch
            }

            if (_clientSecret.value.isBlank()) {
                _loginState.value = LoginState.INVALID_CLIENT_SECRET
                _errorMessage.value = "Client Secret is required"
                return@launch
            }

            if (_username.value.isBlank()) {
                _loginState.value = LoginState.INVALID_USERNAME
                _errorMessage.value = "Username is required"
                return@launch
            }

            if (_password.value.isBlank()) {
                _loginState.value = LoginState.INVALID_PASSWORD
                _errorMessage.value = "Password is required"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = ""
            _loginState.value = LoginState.LOGGING_IN

            try {
                // Move all network operations to IO dispatcher
                withContext(Dispatchers.IO) {
                    val url = URL("https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                    // Prepare form data
                    val formData = buildString {
                        append("grant_type=password")
                        append("&username=${URLEncoder.encode(_username.value, "UTF-8")}")
                        append("&password=${URLEncoder.encode(_password.value, "UTF-8")}")
                        append("&client_id=${URLEncoder.encode(_clientId.value, "UTF-8")}")
                        append("&client_secret=${URLEncoder.encode(_clientSecret.value, "UTF-8")}")
                    }

                    // Send request
                    val outputWriter = OutputStreamWriter(connection.outputStream)
                    outputWriter.write(formData)
                    outputWriter.flush()
                    outputWriter.close()

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Login response code: $responseCode")

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Parse the response
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonObject = JSONObject(response)

                        // Get tokens
                        val accessToken = jsonObject.getString("access_token")
                        val refreshToken = jsonObject.getString("refresh_token")
                        val expiresIn = jsonObject.optInt("expires_in", 900) // Default to 15 minutes

                        // Calculate token expiry time (current time + expires_in seconds)
                        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)

                        // Save credentials and tokens
                        appConfig.mangadexClientId = _clientId.value
                        appConfig.mangadexClientSecret = _clientSecret.value
                        appConfig.mangadexUsername = _username.value
                        appConfig.mangadexPassword = _password.value
                        appConfig.mangadexAccessToken = accessToken
                        appConfig.mangadexRefreshToken = refreshToken
                        appConfig.mangadexTokenExpiry = expiryTime

                        // Update UI state
                        _isLoggedIn.value = true
                        _loginState.value = LoginState.LOGIN_SUCCESS
                        _errorMessage.value = ""

                        // Load user profile and library after successful login
                        loadUserProfile()
                        fetchUserLibrary()
                    } else {
                        // Handle error response
                        val errorStream = connection.errorStream
                        val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""

                        Log.e(TAG, "Login error: $errorResponse")
                        var errorMsg = "Login failed"

                        try {
                            val errorJson = JSONObject(errorResponse)
                            errorMsg = errorJson.optString("error_description", errorMsg)
                        } catch (e: Exception) {
                            // If we can't parse the JSON, use the default error message
                        }

                        // Update UI state
                        _loginState.value = LoginState.LOGIN_FAILED
                        _errorMessage.value = errorMsg
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login exception", e)
                _loginState.value = LoginState.LOGIN_FAILED
                _errorMessage.value = "Network error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh the access token using the refresh token
     * Returns true if the token was successfully refreshed
     */
    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refreshToken = appConfig.mangadexRefreshToken
            if (refreshToken.isBlank()) {
                Log.e(TAG, "Refresh token is empty")
                return@withContext false
            }

            val clientId = appConfig.mangadexClientId
            val clientSecret = appConfig.mangadexClientSecret

            val url = URL("https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Prepare form data
            val formData = buildString {
                append("grant_type=refresh_token")
                append("&refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}")
                append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
                append("&client_secret=${URLEncoder.encode(clientSecret, "UTF-8")}")
            }

            // Send request
            val outputWriter = OutputStreamWriter(connection.outputStream)
            outputWriter.write(formData)
            outputWriter.flush()
            outputWriter.close()

            val responseCode = connection.responseCode
            Log.d(TAG, "Refresh token response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Parse the response
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                // Get tokens (a refresh may also return a new refresh token)
                val accessToken = jsonObject.getString("access_token")
                val newRefreshToken = jsonObject.optString("refresh_token", refreshToken)
                val expiresIn = jsonObject.optInt("expires_in", 900) // Default to 15 minutes

                // Calculate token expiry time (current time + expires_in seconds)
                val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)

                // Save new tokens
                appConfig.mangadexAccessToken = accessToken
                appConfig.mangadexRefreshToken = newRefreshToken
                appConfig.mangadexTokenExpiry = expiryTime

                return@withContext true
            } else {
                // Handle error response
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Error refreshing token: $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            return@withContext false
        }
    }

    /**
     * Verify if the current token is valid
     * Returns true if the token is valid
     */
    private suspend fun verifyToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First check if token is expired based on timestamp
            if (System.currentTimeMillis() >= appConfig.mangadexTokenExpiry) {
                Log.d(TAG, "Token expired by timestamp check")
                return@withContext false
            }

            // Additional verification by making a simple authenticated request
            val url = URL("https://api.mangadex.org/user/me")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "Token verification response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return@withContext true
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.d(TAG, "Token invalid or expired")
                return@withContext false
            } else {
                // Other error
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Error verifying token: $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying token", e)
            return@withContext false
        }
    }

    /**
     * Try to ensure we have a valid token
     * Returns true if we have a valid token after the operation
     */
    private suspend fun ensureValidToken(): Boolean = withContext(Dispatchers.IO) {
        if (!appConfig.hasValidMangadexToken()) {
            return@withContext refreshToken()
        }

        // Token seems valid by timestamp, but let's verify it with the API
        if (!verifyToken()) {
            return@withContext refreshToken()
        }

        return@withContext true
    }

    /**
     * Logout from MangaDex - clear tokens and update UI state
     */
    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Invalidate tokens on the server if possible
                try {
                    val url = URL("https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/logout")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                    val formData = buildString {
                        append("refresh_token=${URLEncoder.encode(appConfig.mangadexRefreshToken, "UTF-8")}")
                        append("&client_id=${URLEncoder.encode(appConfig.mangadexClientId, "UTF-8")}")
                        append("&client_secret=${URLEncoder.encode(appConfig.mangadexClientSecret, "UTF-8")}")
                    }

                    val outputWriter = OutputStreamWriter(connection.outputStream)
                    outputWriter.write(formData)
                    outputWriter.flush()
                    outputWriter.close()

                    Log.d(TAG, "Logout response code: ${connection.responseCode}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during logout", e)
                }

                // Clear tokens from storage
                appConfig.clearMangadexCredentials()
            }

            // Update UI state
            _isLoggedIn.value = false
            _userProfile.value = null
            _loginState.value = LoginState.IDLE
            _allMangaInLibrary.value = emptyList()
            _userLibrary.value = emptyList()
        }
    }

    // Load user profile using MangaDexAPI
    fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val profile = mangaDexAPI.getUserProfile()
                _userProfile.value = profile
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user profile", e)
            }
        }
    }

    // Fetch user library using MangaDexAPI
    fun fetchUserLibrary() {
        viewModelScope.launch {
            _isLibraryLoading.value = true
            try {
                val library = mangaDexAPI.getUserLibrary()
                _allMangaInLibrary.value = library
                filterLibrary()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching library", e)
            } finally {
                _isLibraryLoading.value = false
            }
        }
    }

    private suspend fun fetchMangaBatch(ids: List<String>): List<Manga> = withContext(Dispatchers.IO) {
        if (!ensureValidToken()) return@withContext emptyList()
        if (ids.isEmpty()) return@withContext emptyList()

        // Construct URL with ids parameter
        val idParam = ids.joinToString("&ids[]=", prefix = "?ids[]=")
        val urlString = "https://api.mangadex.org/manga$idParam&includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

        return@withContext try {
            val responseCode = connection.responseCode
            Log.d(TAG, "Manga batch response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val data = jsonObject.getJSONArray("data")
                    val mangaList = mutableListOf<Manga>()

                    for (i in 0 until data.length()) {
                        val mangaObject = data.getJSONObject(i)
                        try {
                            val manga = parseMangaFromJson(mangaObject)
                            mangaList.add(manga)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing manga: ${e.message}")
                        }
                    }

                    mangaList
                } else {
                    Log.e(TAG, "Error in manga batch response: ${jsonObject.optString("errors", "Unknown error")}")
                    emptyList()
                }
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Error fetching manga batch: $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga batch", e)
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMangaFromJson(mangaObject: JSONObject): Manga {
        val id = mangaObject.getString("id")
        val attributes = mangaObject.getJSONObject("attributes")

        // Extract title
        val titleObj = attributes.getJSONObject("title")
        val titles = mutableListOf<String>()
        val titleKeys = titleObj.keys()
        while (titleKeys.hasNext()) {
            val key = titleKeys.next()
            titles.add(titleObj.getString(key))
        }

        val originalLanguage = attributes.optString("originalLanguage", "en")

        // Extract description
        val description = attributes.optJSONObject("description")?.let { descObj ->
            descObj.optString("en") ?: run {
                val keys = descObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
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
        val relationships = mangaObject.getJSONArray("relationships")
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.getString("type") == "cover_art") {
                if (rel.has("attributes")) {
                    val fileName = rel.getJSONObject("attributes").optString("fileName")
                    if (fileName.isNotEmpty()) {
                        coverImage = "https://uploads.mangadex.org/covers/$id/$fileName"
                    }
                }
                break
            }
        }

        // Extract tags
        val tags = mutableListOf<MangaTag>()
        val tagsArray = attributes.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.getJSONObject(i)
                val tagId = tag.getString("id")
                val tagAttributes = tag.getJSONObject("attributes")
                val tagName = tagAttributes.getJSONObject("name").optString("en", "Unknown")
                tags.add(MangaTag(tagId, tagName))
            }
        }

        return Manga(
            id = id,
            title = titles,
            originalLanguage = originalLanguage,
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

    // Update manga status using MangaDexAPI
    fun updateMangaStatus(mangaId: String, newStatus: String) {
        viewModelScope.launch {
            try {
                val success = mangaDexAPI.updateMangaReadingStatus(mangaId, newStatus)
                if (success) {
                    // Update local state
                    val currentList = _allMangaInLibrary.value.toMutableList()
                    val mangaIndex = currentList.indexOfFirst { it.manga.id == mangaId }

                    if (mangaIndex != -1) {
                        val updatedManga = currentList[mangaIndex].copy(status = newStatus)
                        currentList[mangaIndex] = updatedManga
                        _allMangaInLibrary.value = currentList
                    }
                    filterLibrary()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating manga status", e)
            }
        }
    }

    fun selectLibraryTab(tab: String) {
        try {
            val newTab = LibraryTab.valueOf(tab)
            _selectedLibraryTab.value = newTab
            filterLibrary()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid tab: $tab")
        }
    }

    private fun filterLibrary() {
        val filtered = when (_selectedLibraryTab.value) {
            LibraryTab.ALL -> _allMangaInLibrary.value
            else -> _allMangaInLibrary.value.filter {
                it.status == _selectedLibraryTab.value.toString().lowercase()
            }
        }
        _userLibrary.value = filtered
    }

    /**
     * Toggles the guide visibility
     */
    fun toggleGuide() {
        _showGuide.value = !_showGuide.value
    }

    /**
     * Opens the MangaDex registration page in browser
     */
    fun openMangadexRegistration() {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://mangadex.org/account/settings"))
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        appContext.startActivity(intent)
    }
}