package me.thuanc177.kotsune.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.ui.screens.TrackedMediaItem
import org.json.JSONObject

data class TrackingState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: AnilistTypes.AnilistUser? = null,
    val animeList: List<TrackedMediaItem> = emptyList(),
    val mangaList: List<TrackedMediaItem> = emptyList(),
    val error: String? = null
)

class TrackingViewModel(
    private val anilistClient: AnilistClient
) : ViewModel() {
    private val TAG = "TrackingViewModel"

    private val _uiState = MutableStateFlow(TrackingState())
    val uiState: StateFlow<TrackingState> = _uiState.asStateFlow()

    /**
     * Check if user is logged in and load user data
     */
    fun checkLoginStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val isLoggedIn = anilistClient.isUserAuthenticated()

                if (isLoggedIn) {
                    // Get user information
                    val userData = anilistClient.getCurrentUser()

                    if (userData != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                user = userData
                            )
                        }

                        // Load user's anime and manga lists
                        loadUserMediaLists()
                    } else {
                        // Invalid token or error fetching user data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = false,
                                error = "Failed to fetch user information"
                            )
                        }
                    }
                } else {
                    // Not logged in
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking login status", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Start Anilist OAuth login
     */
    fun login(context: Context) {
        anilistClient.openAuthPage(context)
    }

    /**
     * Handle OAuth redirect
     */
    suspend fun handleOAuthRedirect(uri: Uri?): Boolean {
        Log.d(TAG, "TrackingViewModel handling OAuth redirect: $uri")

        // Check URI components for debugging
        if (uri != null) {
            Log.d(TAG, "URI scheme: ${uri.scheme}")
            Log.d(TAG, "URI host: ${uri.host}")
            Log.d(TAG, "URI path: ${uri.path}")
            Log.d(TAG, "URI query: ${uri.query}")
            Log.d(TAG, "URI fragment: ${uri.fragment}")
        } else {
            Log.e(TAG, "Received null URI in handleOAuthRedirect")
        }

        val result = anilistClient.handleAuthRedirect(uri)

        Log.d(TAG, "Auth result from AnilistClient: $result")

        if (result == AnilistClient.AUTH_SUCCESS) {
            Log.d(TAG, "Authentication successful, checking login status")
            checkLoginStatus()
            return true
        } else {
            Log.e(TAG, "Authentication failed with result code: $result")
        }

        return false
    }

    /**
     * Logout user
     */
    fun logout() {
        anilistClient.logout()
        _uiState.update {
            TrackingState() // Reset to default state
        }
    }

    /**
     * Load user's anime and manga lists
     */
    private fun loadUserMediaLists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Create query for getting anime list
                val animeQuery = """
                    query {
                      MediaListCollection(userId: ${_uiState.value.user?.id}, type: ANIME) {
                        lists {
                          name
                          entries {
                            id
                            mediaId
                            status
                            progress
                            score
                            media {
                              id
                              title {
                                userPreferred
                              }
                              coverImage {
                                large
                              }
                              episodes
                              status
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                // Create query for getting manga list
                val mangaQuery = """
                    query {
                      MediaListCollection(userId: ${_uiState.value.user?.id}, type: MANGA) {
                        lists {
                          name
                          entries {
                            id
                            mediaId
                            status
                            progress
                            score
                            media {
                              id
                              title {
                                userPreferred
                              }
                              coverImage {
                                large
                              }
                              chapters
                              status
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                // Execute both queries
                val animeResponse = executeQuery(animeQuery)
                val mangaResponse = executeQuery(mangaQuery)

                // Parse anime list
                val animeList = if (animeResponse.first) {
                    parseMediaList(animeResponse.second, true)
                } else {
                    emptyList()
                }

                // Parse manga list
                val mangaList = if (mangaResponse.first) {
                    parseMediaList(mangaResponse.second, false)
                } else {
                    emptyList()
                }

                // Update state
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        animeList = animeList,
                        mangaList = mangaList
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media lists", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error loading lists: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Execute a GraphQL query and return the response
     */
    private suspend fun executeQuery(query: String): Pair<Boolean, JSONObject?> {
        val variables = mapOf<String, Any>()
        val response = anilistClient.executeQuery(query, variables)
        return response
    }

    /**
     * Parse a media list response into a list of TrackedMediaItem
     */
    private fun parseMediaList(responseJson: JSONObject?, isAnime: Boolean): List<TrackedMediaItem> {
        if (responseJson == null) return emptyList()

        val mediaItems = mutableListOf<TrackedMediaItem>()

        try {
            val data = responseJson.getJSONObject("data")
            val collection = data.getJSONObject("MediaListCollection")
            val lists = collection.getJSONArray("lists")

            for (i in 0 until lists.length()) {
                val list = lists.getJSONObject(i)
                val entries = list.getJSONArray("entries")

                for (j in 0 until entries.length()) {
                    val entry = entries.getJSONObject(j)
                    val media = entry.getJSONObject("media")

                    val id = media.getInt("id")
                    val title = media.getJSONObject("title").getString("userPreferred")
                    val imageUrl = media.getJSONObject("coverImage").getString("large")
                    val status = entry.getString("status")
                    val progress = entry.getInt("progress")
                    val score = if (entry.has("score") && !entry.isNull("score"))
                        entry.getDouble("score").toFloat()
                    else null

                    val total = if (isAnime) {
                        if (media.has("episodes") && !media.isNull("episodes"))
                            media.getInt("episodes")
                        else null
                    } else {
                        if (media.has("chapters") && !media.isNull("chapters"))
                            media.getInt("chapters")
                        else null
                    }

                    val trackedItem = TrackedMediaItem(
                        id = id,
                        title = title,
                        imageUrl = imageUrl,
                        status = status,
                        progress = progress,
                        total = total,
                        score = score
                    )

                    mediaItems.add(trackedItem)
                }
            }

            return mediaItems
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing media list", e)
            return emptyList()
        }
    }

    /**
     * Update progress for an anime or manga
     */
    fun updateMediaProgress(mediaId: Int, progress: Int, isAnime: Boolean): Boolean {
        viewModelScope.launch {
            try {
                val mediaType = if (isAnime) "ANIME" else "MANGA"

                val query = """
                    mutation {
                      SaveMediaListEntry(mediaId: $mediaId, progress: $progress) {
                        id
                        progress
                      }
                    }
                """.trimIndent()

                val response = executeQuery(query)

                if (response.first) {
                    // Update the local list
                    val updatedList = if (isAnime) {
                        _uiState.value.animeList.map {
                            if (it.id == mediaId) it.copy(progress = progress) else it
                        }
                    } else {
                        _uiState.value.mangaList.map {
                            if (it.id == mediaId) it.copy(progress = progress) else it
                        }
                    }

                    _uiState.update {
                        if (isAnime) {
                            it.copy(animeList = updatedList)
                        } else {
                            it.copy(mangaList = updatedList)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating media progress", e)
            }
        }

        return false
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch fresh data from Anilist
                val user = anilistClient.getCurrentUser()
                val animeList = anilistClient.getAnimeList()
                val mangaList = anilistClient.getMangaList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = user,
                        animeList = animeList,
                        mangaList = mangaList,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private suspend fun AnilistClient.getAnimeList(): List<TrackedMediaItem> {
        val query = """
        query {
          MediaListCollection(userId: ${_uiState.value.user?.id}, type: ANIME) {
            lists {
              name
              entries {
                id
                mediaId
                status
                progress
                score
                media {
                  id
                  title {
                    userPreferred
                  }
                  coverImage {
                    large
                  }
                  episodes
                  status
                }
              }
            }
          }
        }
    """.trimIndent()

        val response = executeQuery(query, mapOf())
        return if (response.first) {
            parseMediaList(response.second, true)
        } else {
            emptyList()
        }
    }

    private suspend fun AnilistClient.getMangaList(): List<TrackedMediaItem> {
        val query = """
        query {
          MediaListCollection(userId: ${_uiState.value.user?.id}, type: MANGA) {
            lists {
              name
              entries {
                id
                mediaId
                status
                progress
                score
                media {
                  id
                  title {
                    userPreferred
                  }
                  coverImage {
                    large
                  }
                  chapters
                  status
                }
              }
            }
          }
        }
    """.trimIndent()

        val response = executeQuery(query, mapOf())
        return if (response.first) {
            parseMediaList(response.second, false)
        } else {
            emptyList()
        }
    }

    class Factory(private val anilistClient: AnilistClient) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
                return TrackingViewModel(anilistClient) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}