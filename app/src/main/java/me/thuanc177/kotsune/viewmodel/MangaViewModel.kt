package me.thuanc177.kotsune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.model.MangaListState
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import android.util.Log
import org.json.JSONObject

class MangaViewModel(
    private val mangaDexAPI: MangaDexAPI
) : ViewModel() {

    private val _uiState = MutableStateFlow(MangaListState())
    val uiState: StateFlow<MangaListState> = _uiState
    private var latestUpdatesOffset = 0
    private val pageSize = 50 // Consistent with your API default limit

    init {
        fetchMangaLists()
    }

    fun fetchMangaLists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Get popular manga
                val (popularSuccess, popularData) = mangaDexAPI.getPopularManga()
                Log.d("MangaViewModel", "Popular API success: $popularSuccess, data: $popularData")
                val popularList = if (popularSuccess && popularData != null) {
                    val list = parseMangaList(popularData)
                    Log.d("MangaViewModel", "Popular manga count: ${list.size}")
                    list
                } else {
                    Log.d("MangaViewModel", "No popular manga data received")
                    emptyList()
                }

                // Get latest manga updates (initial fetch)
                val (latestSuccess, latestData) = mangaDexAPI.getLatestMangaUpdates(limit = pageSize, offset = 0)
                Log.d("MangaViewModel", "Latest API success: $latestSuccess, data: $latestData")
                val latestList = if (latestSuccess && latestData != null) {
                    val list = parseMangaList(latestData)
                    Log.d("MangaViewModel", "Latest manga count: ${list.size}")
                    list
                } else {
                    Log.d("MangaViewModel", "No latest manga data received")
                    emptyList()
                }

                // Update UI state
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        popular = popularList,
                        latestUpdates = latestList,
                        error = if (popularList.isEmpty() && latestList.isEmpty()) "No manga data available" else null
                    )
                }
                latestUpdatesOffset = latestList.size // Set initial offset
            } catch (e: Exception) {
                Log.e("MangaViewModel", "Fetch error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load manga: ${e.message}"
                    )
                }
            }
        }
    }

    fun fetchMoreLatestUpdates() {
        viewModelScope.launch {
            if (_uiState.value.isLoading) return@launch
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (success, jsonData) = mangaDexAPI.getLatestMangaUpdates(
                    limit = pageSize,
                    offset = latestUpdatesOffset
                )
                Log.d("MangaViewModel", "Fetch more API success: $success, data: $jsonData")
                if (success && jsonData != null) {
                    val newLatest = parseMangaList(jsonData)
                    Log.d("MangaViewModel", "New latest manga count: ${newLatest.size}")
                    _uiState.update {
                        it.copy(
                            latestUpdates = it.latestUpdates + newLatest,
                            isLoading = false
                        )
                    }
                    latestUpdatesOffset += newLatest.size
                } else {
                    Log.d("MangaViewModel", "No additional latest manga data received")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load more manga"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MangaViewModel", "Fetch more error: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load more manga: ${e.message}"
                    )
                }
            }
        }
    }

    private fun parseMangaList(json: JSONObject): List<Manga> {
        val mangaArray = json.getJSONArray("data")
        return (0 until mangaArray.length()).mapNotNull { i ->
            try {
                val mangaObj = mangaArray.getJSONObject(i)
                val id = mangaObj.getString("id")
                val attributes = mangaObj.getJSONObject("attributes")

                // Extract titles
                val titleObj = attributes.getJSONObject("title")
                val titleList = mutableListOf<String>()

                // Try to get title in different languages (en, ja, ko)
                titleObj.optString("en")?.takeIf { it.isNotEmpty() }?.let { titleList.add(it) }
                titleObj.optString("ja")?.takeIf { it.isNotEmpty() }?.let { titleList.add(it) }
                titleObj.optString("ko")?.takeIf { it.isNotEmpty() }?.let { titleList.add(it) }

                if (titleList.isEmpty()) {
                    // If no primary languages, get any available language
                    val keys = titleObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        titleObj.optString(key)?.takeIf { it.isNotEmpty() }?.let {
                            titleList.add(it)
                        }
                    }

                    // If still empty, use fallback
                    if (titleList.isEmpty()) {
                        titleList.add("Unknown Title")
                    }
                }

                // Get cover art
                val coverFileName = try {
                    val relationships = mangaObj.getJSONArray("relationships")
                    var coverArtFileName: String? = null

                    for (j in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(j)
                        if (rel.getString("type") == "cover_art") {
                            coverArtFileName = rel.getJSONObject("attributes").getString("fileName")
                            break
                        }
                    }

                    coverArtFileName
                } catch (e: Exception) {
                    null
                }

                val coverImageUrl = if (coverFileName != null) {
                    "https://uploads.mangadex.org/covers/$id/$coverFileName"
                } else {
                    null
                }

                // Create and return Manga object
                Manga (
                    id = id,
                    title = titleList,
                    poster = coverImageUrl,
                    status = attributes.optString("status", "unknown"),
                    description = try {
                        val descObj = attributes.getJSONObject("description")
                        descObj.optString("en") ?: descObj.optString("ja") ?: ""
                    } catch (e: Exception) {
                        ""
                    },
                    lastUpdated = attributes.optString("updatedAt"),
                    year = try {
                        attributes.optString("year").toIntOrNull()
                    } catch (e: Exception) {
                        null
                    },
                    lastChapter = try {
                        attributes.optString("lastChapter").toIntOrNull()
                    } catch (e: Exception) {
                        null
                    },
                    contentRating = attributes.optString("contentRating", "unknown")
                )
            } catch (e: Exception) {
                Log.e("MangaViewModel", "Error parsing manga: ${e.message}")
                null
            }
        }
    }

    class MangaListFactory(private val mangaDexAPI: MangaDexAPI) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MangaViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MangaViewModel(mangaDexAPI) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}