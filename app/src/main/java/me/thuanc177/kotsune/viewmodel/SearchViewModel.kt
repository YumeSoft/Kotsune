package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import org.json.JSONObject

class SearchViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val anilistClient: AnilistClient
) : ViewModel() {

    // Search state
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Initial)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Manga results
    private val _mangaResults = MutableStateFlow<List<Manga>>(emptyList())
    val mangaResults: StateFlow<List<Manga>> = _mangaResults.asStateFlow()

    // Anime results
    private val _animeResults = MutableStateFlow<List<AnimeSearchResult>>(emptyList())
    val animeResults: StateFlow<List<AnimeSearchResult>> = _animeResults.asStateFlow()

    fun searchManga(query: String, genres: List<String> = emptyList(), status: String = "", sortBy: String = "relevance") {
        viewModelScope.launch {
            try {
                Log.d("SearchViewModel", "Searching manga with query: $query")
                _searchState.value = SearchState.Loading

                val results = mangaDexAPI.searchForManga(query)
                Log.d("SearchViewModel", "API response: $results")

                if (results != null && results.isNotEmpty()) {
                    val mangaList = results.mapNotNull { mangaData ->
                        try {
                            val id = mangaData["id"]?.toString() ?: return@mapNotNull null
                            val title = mangaData["title"]?.toString() ?: "Unknown"
                            val poster = mangaData["poster"]?.toString()?.takeIf { it.isNotEmpty() }

                            val tags = (mangaData["genres"] as? List<*>)?.mapNotNull {
                                it?.toString()?.let { tagName -> MangaTag(
                                    name = tagName,
                                    tagName = tagName
                                ) }
                            } ?: emptyList()

                            val latestUploadedChapter = mangaData["latestUploadedChapter"]?.toString()

                            Manga(
                                id = id,
                                title = listOf(title),
                                poster = poster,
                                status = mangaData["type"]?.toString() ?: "unknown",
                                description = "",
                                lastUpdated = null,
                                year = mangaData["year"] as? Int,  // Added year
                                lastChapter = null,
                                tags = tags,
                                latestUploadedChapterId = latestUploadedChapter,
                                contentRating = (mangaData["rating"] as String)
                            )
                        } catch (e: Exception) {
                            Log.e("SearchViewModel", "Error parsing manga: ${e.message}", e)
                            null
                        }
                    }

                    Log.d("SearchViewModel", "Parsed manga items: ${mangaList.size}")

                    val filteredList = if (genres.isNotEmpty()) {
                        mangaList.filter { manga ->
                            genres.any { genre ->
                                manga.tags.any { tag -> tag.name.equals(genre, ignoreCase = true) }
                            }
                        }
                    } else {
                        mangaList
                    }

                    val statusFilteredList = if (status.isNotEmpty()) {
                        filteredList.filter { it.status.equals(status, ignoreCase = true) }
                    } else {
                        filteredList
                    }

                    val sortedList = when (sortBy) {
                        "title_asc" -> statusFilteredList.sortedBy { it.title.firstOrNull() ?: "" }
                        "title_desc" -> statusFilteredList.sortedByDescending { it.title.firstOrNull() ?: "" }
                        else -> statusFilteredList
                    }

                    _mangaResults.value = sortedList
                    _searchState.value = if (sortedList.isEmpty()) SearchState.Empty else SearchState.Success
                } else {
                    Log.d("SearchViewModel", "No manga results found")
                    _mangaResults.value = emptyList()
                    _searchState.value = SearchState.Empty
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error searching manga: ${e.message}", e)
                _searchState.value = SearchState.Error("Failed to search: ${e.message}")
                _mangaResults.value = emptyList()
            }
        }
    }

    // Helper class to match the Manga data class tags structure
    data class MangaTag(val name: String, val tagName: String)

    fun searchAnime(query: String, genres: List<String> = emptyList(), status: String = "", sortBy: String = "relevance") {
        viewModelScope.launch {
            try {
                Log.d("SearchViewModel", "Searching anime with query: $query, genres: $genres, status: $status, sortBy: $sortBy")
                _searchState.value = SearchState.Loading

                // Map status to AniList-compatible values
                val mappedStatus = when (status.lowercase()) {
                    "ongoing" -> "RELEASING"
                    "completed" -> "FINISHED"
                    "hiatus" -> "HIATUS"
                    "cancelled" -> "CANCELLED"
                    else -> ""
                }

                val sort = when (sortBy.lowercase()) {
                    "latest" -> listOf("UPDATED_AT_DESC")
                    "oldest" -> listOf("UPDATED_AT")
                    "title_asc" -> listOf("TITLE_ROMAJI")
                    "title_desc" -> listOf("TITLE_ROMAJI_DESC")
                    else -> listOf("POPULARITY_DESC")
                }

                // Fix for the destructuring and search method issues
                val result = anilistClient.searchAnime(
                    perPage = 50,
                    query = query.ifEmpty { null },
                    sort = sort.ifEmpty { null },
                    genreIn = genres.ifEmpty { null },
                    type = "ANIME",
                    status_not = mappedStatus
                )

                val success = result.first
                val response = result.second as? JSONObject

                Log.d("SearchViewModel", "AniList API call: success=$success, response=$response")

                if (success && response != null) {
                    val animeList = parseAnimeResults(response)
                    Log.d("SearchViewModel", "Parsed anime items: ${animeList.size}")

                    _animeResults.value = animeList
                    _searchState.value = if (animeList.isEmpty()) {
                        Log.d("SearchViewModel", "Anime list is empty after parsing")
                        SearchState.Empty
                    } else {
                        SearchState.Success
                    }
                } else {
                    Log.e("SearchViewModel", "AniList API call failed: success=$success, response=$response")
                    _animeResults.value = emptyList()
                    _searchState.value = SearchState.Error("No results found or API error")
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error searching anime: ${e.message}", e)
                _searchState.value = SearchState.Error("Failed to search anime: ${e.message}")
                _animeResults.value = emptyList()
            }
        }
    }

    private fun parseAnimeResults(json: JSONObject): List<AnimeSearchResult> {
        try {
            Log.d("SearchViewModel", "Parsing JSON: $json")
            val dataObj = json.optJSONObject("data") ?: run {
                Log.e("SearchViewModel", "No 'data' object in response")
                return emptyList()
            }
            val pageObj = dataObj.optJSONObject("Page") ?: run {
                Log.e("SearchViewModel", "No 'Page' object in response")
                return emptyList()
            }
            val mediaArray = pageObj.optJSONArray("media") ?: run {
                Log.e("SearchViewModel", "No 'media' array in response")
                return emptyList()
            }

            val result = mutableListOf<AnimeSearchResult>()
            for (i in 0 until mediaArray.length()) {
                val media = mediaArray.optJSONObject(i) ?: continue
                val id = media.optInt("id", 0)
                if (id == 0) continue

                val titleObj = media.optJSONObject("title")
                val title = titleObj?.optString("english")
                    ?: titleObj?.optString("native")
                    ?: titleObj?.optString("romaji")
                    ?: "Unknown"

                // In the parseAnimeResults function in SearchViewModel.kt
                val coverImageObj = media.optJSONObject("coverImage")
                val coverImage = coverImageObj?.optString("extraLarge") // Use extraLarge first
                    ?: coverImageObj?.optString("large")
                    ?: coverImageObj?.optString("medium")
                    ?: ""

                val releaseYear = if (!media.isNull("seasonYear")) {
                    media.optInt("seasonYear").toString()
                } else {
                    null
                }

                // Extract average score/rating
                val averageScore = if (!media.isNull("averageScore")) {
                    media.optInt("averageScore") / 10f
                } else {
                    null
                }

                // Extract status
                val status = if (!media.isNull("status")) {
                    when (media.optString("status")) {
                        "RELEASING" -> "Ongoing"
                        "FINISHED" -> "Completed"
                        "HIATUS" -> "Hiatus"
                        "CANCELLED" -> "Cancelled"
                        else -> media.optString("status")
                    }
                } else {
                    null
                }

                // Extract genres
                val genresArray = media.optJSONArray("genres")
                val genres = if (genresArray != null) {
                    List(genresArray.length()) { idx ->
                        genresArray.optString(idx)
                    }
                } else {
                    null
                }

                result.add(
                    AnimeSearchResult(
                        id = id,
                        title = title,
                        coverImage = coverImage,
                        seasonYear = releaseYear,
                        rating = averageScore,
                        status = status,
                        genres = genres
                    )
                )
            }
            Log.d("SearchViewModel", "Parsed ${result.size} anime results")
            return result
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Error parsing anime results: ${e.message}", e)
            return emptyList()
        }
    }

    fun resetSearch() {
        _searchState.value = SearchState.Initial
        _mangaResults.value = emptyList()
        _animeResults.value = emptyList()
    }

    data class AnimeSearchResult(
        val id: Int,
        val title: String,
        val coverImage: String,
        val seasonYear: String? = null,
        val rating: Float? = null,
        val status: String? = null,
        val genres: List<String>? = null
    )

    sealed class SearchState {
        data object Initial : SearchState()
        data object Loading : SearchState()
        data object Success : SearchState()
        data object Empty : SearchState()
        data class Error(val message: String) : SearchState()
    }

    class SearchViewModelFactory(
        private val mangaDexAPI: MangaDexAPI,
        private val anilistClient: AnilistClient
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(mangaDexAPI, anilistClient) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}