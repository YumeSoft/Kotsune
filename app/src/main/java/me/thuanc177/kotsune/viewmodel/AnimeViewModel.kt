package me.thuanc177.kotsune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.data.model.AnimeListState
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import org.json.JSONObject
import android.util.Log

class AnimeViewModel(
    private val anilistClient: AnilistClient,

) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeListState())
    val uiState: StateFlow<AnimeListState> = _uiState

    init {
        fetchAnimeLists()
    }

    fun fetchAnimeLists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val (trendingListSuccess, trendingListData) = anilistClient.getTrendingAnime()
                Log.d("AnimeViewModel", "Trending API success: $trendingListSuccess")
                val trendingList = if (trendingListSuccess && trendingListData != null) {
                    parseAnimeList(trendingListData)
                } else emptyList()
                Log.d("AnimeViewModel", "Parsed trending list size: ${trendingList.size}")

                // Get recently updated anime
                val (recentSuccess, recentData) = anilistClient.getRecentAnime()
                val recentList = if (recentSuccess && recentData != null) {
                    parseAnimeList(recentData)
                } else emptyList()

                // Get highly rated anime
                val (ratedSuccess, ratedData) = anilistClient.getHighlyRatedAnime()
                val ratedList = if (ratedSuccess && ratedData != null) {
                    parseAnimeList(ratedData)
                } else emptyList()

                // Update UI state with all fetched data
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trending = trendingList,
                    recentlyUpdated = recentList,
                    highRating = ratedList
                )
            } catch (e: Exception) {
                Log.e("AnimeViewModel", "Fetch error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load anime: ${e.message}"
                )
            }
        }
    }

    private fun parseAnimeList(json: JSONObject): List<Anime> {
        val mediaList = json.getJSONObject("data")
            .getJSONObject("Page")
            .getJSONArray("media")
        return (0 until mediaList.length()).map { i ->
            val media = mediaList.getJSONObject(i)
            val titles = media.getJSONObject("title")
            val titleList = mutableListOf<String>()

            titles.optString("english")?.takeIf { it != "null" && it.isNotEmpty() }?.let { titleList.add(it) }
            titles.optString("romaji")?.takeIf { it != "null" && it.isNotEmpty() }?.let { titleList.add(it) }
            titles.optString("native")?.takeIf { it != "null" && it.isNotEmpty() }?.let { titleList.add(it) }

            if (titleList.isEmpty()) {
                titleList.add("Unknown Title")
            }

            Anime(
                id = media.getInt("id"),
                title = titleList,
                coverImage = media.getJSONObject("coverImage").getString("large"),
                status = media.getString("status"),
                score = if (media.has("averageScore") && !media.isNull("averageScore"))
                    media.getInt("averageScore").toFloat() / 10
                else null,
                bannerImage = if (media.has("bannerImage") && !media.isNull("bannerImage"))
                    media.getString("bannerImage")
                else null,
                seasonYear = if (media.has("seasonYear") && !media.isNull("seasonYear"))
                    media.getInt("seasonYear")
                else null,
                description = if (media.has("description") && !media.isNull("description"))
                    media.getString("description")
                else null,
                genres = if (media.has("genres") && !media.isNull("genres"))
                    media.getJSONArray("genres").let { genresArray ->
                        (0 until genresArray.length()).map { j ->
                            genresArray.getString(j)
                        }
                    }
                else emptyList(),
                episodes = if (media.has("episodes") && !media.isNull("episodes"))
                    media.getInt("episodes")
                else null
            )
        }
    }

    class AnimeListFactory(private val anilistClient: AnilistClient) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnimeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AnimeViewModel(anilistClient) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}