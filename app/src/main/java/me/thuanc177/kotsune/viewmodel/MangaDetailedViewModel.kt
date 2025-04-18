package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.repository.FavoritesRepository
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

data class MangaDetailedState(
    val manga: Manga? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val chapters: List<ChapterModel> = emptyList(),
    val chaptersLoading: Boolean = false,
    val chaptersError: String? = null
)

data class ChapterModel(
    val id: String,
    val number: String,
    val title: String,
    val publishedAt: String,
    val pages: Int = 0,
    val thumbnail: String? = null,
    val isRead: Boolean = false // Track read status
)

class MangaDetailedViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val mangaId: String,
    private val favoritesRepository: FavoritesRepository // Add repository for favorites
) : ViewModel() {

    private val _uiState = MutableStateFlow(MangaDetailedState())
    val uiState: StateFlow<MangaDetailedState> = _uiState.asStateFlow()

    private val isInitialized = AtomicBoolean(false)
    private val TAG = "MangaDetailedViewModel"

    init {
        if (isInitialized.compareAndSet(false, true)) {
            checkIfFavorite()
            fetchMangaDetails()
        }
    }

    private fun checkIfFavorite() {
        viewModelScope.launch {
            val isFavorite = favoritesRepository.isMangaFavorite(mangaId)
            _uiState.update { it.copy(isFavorite = isFavorite) }
        }
    }

    fun fetchMangaDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val manga = fetchMangaFromAPI(mangaId)
                if (manga != null) {
                    _uiState.update { it.copy(manga = manga, isLoading = false) }
                    fetchChapters()
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load manga details")
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Network timeout when fetching manga details", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Network timeout. Please check your connection.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error when fetching manga details", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Network error. Please check your connection.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manga details", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error occurred")
                }
            }
        }
    }

    fun fetchChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(chaptersLoading = true, chaptersError = null) }
            try {
                val chapters = fetchChaptersFromAPI(mangaId)
                _uiState.update { it.copy(chapters = chapters, chaptersLoading = false) }
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Network timeout when fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = "Network timeout. Please check your connection.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error when fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = "Network error. Please check your connection.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = e.message ?: "Failed to load chapters")
                }
            }
        }
    }

    private suspend fun fetchMangaFromAPI(id: String): Manga? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.mangadex.org/manga/$id?includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
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

                    var coverImage: String? = null
                    val relationships = data.getJSONArray("relationships")
                    for (i in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(i)
                        if (rel.getString("type") == "cover_art") {
                            if (rel.has("attributes")) {
                                val coverAttributes = rel.getJSONObject("attributes")
                                val fileName = coverAttributes.optString("fileName")
                                if (fileName.isNotEmpty()) {
                                    coverImage = "https://uploads.mangadex.org/covers/$id/$fileName"
                                }
                            }
                        }
                    }

                    // Extract tags
                    val tagsArray = attributes.getJSONArray("tags")
                    val tags = mutableListOf<SearchViewModel.MangaTag>()
                    for (i in 0 until tagsArray.length()) {
                        val tag = tagsArray.getJSONObject(i)
                        val tagAttributes = tag.getJSONObject("attributes")
                        val tagNameObj = tagAttributes.getJSONObject("name")
                        val tagName = tagNameObj.optString("en") ?: run {
                            // Try to get tag name in any available language
                            for (key in tagNameObj.keys()) {
                                val name = tagNameObj.optString(key)
                                if (name.isNotEmpty()) return@run name
                            }
                            ""
                        }

                        if (tagName.isNotEmpty()) {
                            tags.add(SearchViewModel.MangaTag(tag.getString("id"), tagName))
                        }
                    }

                    return@withContext Manga(
                        id = id,
                        title = titles,
                        poster = coverImage,
                        status = status,
                        description = description,
                        lastUpdated = attributes.optString("updatedAt"),
                        lastChapter = null, // Needs separate API call
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
                throw e
            }
        }
    }

    private suspend fun fetchChaptersFromAPI(mangaId: String): List<ChapterModel> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.mangadex.org/manga/$mangaId/feed?limit=500&translatedLanguage[]=en&order[chapter]=asc&includes[]=scanlation_group"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val dataArray = jsonObject.getJSONArray("data")
                    val chapters = mutableListOf<ChapterModel>()
                    val readChapters = getReadChapters(mangaId)

                    for (i in 0 until dataArray.length()) {
                        val chapterObject = dataArray.getJSONObject(i)
                        val chapterId = chapterObject.getString("id")
                        val attributes = chapterObject.getJSONObject("attributes")

                        val chapterNumber = attributes.optString("chapter", "")
                        if (chapterNumber.isEmpty()) continue  // Skip chapters without numbers

                        val chapterTitle = attributes.optString("title")
                        val finalTitle = if (chapterTitle.isNullOrBlank()) {
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
                            isRead = readChapters.contains(chapterId)
                        )
                        chapters.add(chapterModel)
                    }

                    return@withContext chapters
                }

                val errorMessage = when(connection.responseCode) {
                    404 -> "Chapters not found"
                    429 -> "Rate limit exceeded. Please try again later."
                    500, 502, 503, 504 -> "Server error. Please try again later."
                    else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
                }

                Log.e(TAG, "API error: $errorMessage")
                throw IOException(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chapters", e)
                throw e
            }
        }
    }

    private suspend fun getReadChapters(mangaId: String): Set<String> {
        return emptySet()
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val newFavoriteState = !currentState.isFavorite

            if (newFavoriteState) {
                favoritesRepository.addFavorite(
                    mangaId,
                    currentState.manga?.title?.firstOrNull() ?: "Unknown",
                    currentState.manga?.poster
                )
            } else {
                favoritesRepository.removeFavorite(mangaId)
            }

            _uiState.update { it.copy(isFavorite = newFavoriteState) }
        }
    }

    fun markChapterAsRead(chapterId: String) {
        viewModelScope.launch {
            val updatedChapters = _uiState.value.chapters.map { chapter ->
                if (chapter.id == chapterId) {
                    chapter.copy(isRead = true)
                } else {
                    chapter
                }
            }
            _uiState.update { it.copy(chapters = updatedChapters) }
        }
    }

    class Factory(
        private val mangaDexAPI: MangaDexAPI,
        private val mangaId: String,
        private val favoritesRepository: FavoritesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MangaDetailedViewModel::class.java)) {
                return MangaDetailedViewModel(mangaDexAPI, mangaId, favoritesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}