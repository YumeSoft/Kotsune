package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import me.thuanc177.kotsune.model.UiEpisodeModel

class EpisodesViewModel : ViewModel() {
    private val TAG = "EpisodesViewModel"

    private val _episodesList = MutableStateFlow<List<UiEpisodeModel>>(emptyList())
    val episodesList: StateFlow<List<UiEpisodeModel>> = _episodesList.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    private val _episodesError = MutableStateFlow<String?>(null)
    val episodesError: StateFlow<String?> = _episodesError.asStateFlow()

    // Current episode to highlight (used in WatchAnimeScreen)
    private val _currentEpisode = MutableStateFlow<Float?>(null)
    val currentEpisode: StateFlow<Float?> = _currentEpisode.asStateFlow()

    fun fetchEpisodes(
        allAnimeProvider: AllAnimeAPI,
        anilistId: Int,
        title: String,
        coverImage: String? = null
    ) {
        viewModelScope.launch {
            _episodesLoading.value = true
            _episodesError.value = null

            try {
                // Search for the anime in AllAnime to get its ID
                val searchResult = allAnimeProvider.searchForAnime(
                    anilistId = anilistId,
                    query = title,
                    translationType = "sub"
                )

                if (searchResult.isSuccess) {
                    val allAnimeId = searchResult.getOrNull()?.alternativeId

                    if (allAnimeId != null) {
                        // Now get episode information for this anime
                        val episodesResult = allAnimeProvider.getEpisodeList(
                            showId = allAnimeId,
                            episodeNumStart = 0f,
                            episodeNumEnd = 9999f
                        )

                        if (episodesResult.isSuccess) {
                            val episodes = episodesResult.getOrNull() ?: emptyList()

                            // Map to UI model
                            val uiEpisodes = episodes.map { episode ->
                                UiEpisodeModel(
                                    number = episode.episodeIdNum,
                                    thumbnail = episode.getThumbnailUrl() ?: coverImage,
                                    title = episode.notes,
                                    description = episode.description,
                                    uploadDate = episode.getFormattedDate()
                                )
                            }

                            // Sort by episode number
                            _episodesList.value = uiEpisodes.sortedBy { it.number }
                        } else {
                            _episodesError.value = episodesResult.exceptionOrNull()?.message ?:
                                    "Failed to fetch episodes"
                        }
                    } else {
                        _episodesError.value = "Anime not found in AllAnime"
                    }
                } else {
                    _episodesError.value = searchResult.exceptionOrNull()?.message ?:
                            "Failed to search for anime"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching episodes", e)
                _episodesError.value = "Error: ${e.message}"
            } finally {
                _episodesLoading.value = false
            }
        }
    }

    // For WatchAnimeScreen to set current episode
    fun setCurrentEpisode(episode: Float?) {
        _currentEpisode.value = episode
    }

    // When the episode list needs to be set directly (useful for WatchAnimeScreen)
    fun setEpisodesList(episodes: List<UiEpisodeModel>) {
        _episodesList.value = episodes
    }
}