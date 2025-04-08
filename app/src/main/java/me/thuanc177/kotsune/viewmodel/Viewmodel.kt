package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.data.model.AnimeListState
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anime.Anime
import org.json.JSONObject

class AnimeViewModel(
    private val anilistClient: AnilistClient
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
                val (trendingSuccess, trendingData) = anilistClient.getTrending(perPage = 10)
                val trendingList = if (trendingSuccess && trendingData != null) {
                    parseAnimeList(trendingData)
                } else emptyList()

                val (newEpisodesSuccess, newEpisodesData) = anilistClient.search(
                    maxResults = 10,
                    sort = "START_DATE_DESC"
                )
                val newEpisodesList = if (newEpisodesSuccess && newEpisodesData != null) {
                    parseAnimeList(newEpisodesData)
                } else emptyList()

                val (highRatingSuccess, highRatingData) = anilistClient.search(
                    maxResults = 10,
                    sort = "SCORE_DESC"
                )
                val highRatingList = if (highRatingSuccess && highRatingData != null) {
                    parseAnimeList(highRatingData)
                } else emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    trending = trendingList,
                    newEpisodes = newEpisodesList,
                    highRating = highRatingList
                )
            } catch (e: Exception) {
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
            Anime(
                id = media.getInt("id"),
                title = titles.optString("english") ?: titles.getString("native"),
                coverImage = media.getJSONObject("coverImage").getString("large"),
                score = media.optInt("averageScore")?.toFloat()?.div(10),
                status = media.getString("status"),
            )
        }
    }

    class Factory(private val anilistClient: AnilistClient) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnimeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AnimeViewModel(anilistClient) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}