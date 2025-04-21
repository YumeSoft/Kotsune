package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.model.UiEpisodeModel
import me.thuanc177.kotsune.ui.screens.AnimeProviderFactory

data class WatchAnimeState(
    val isLoading: Boolean = false,
    val isServerLoading: Boolean = false,
    val error: String? = null,
    val animeId: String? = null,
    val anilistId: Int = -1,
    val animeTitle: String = "",
    val autoNextEpisode: Boolean = true,
    val isLastEpisode: Boolean = false,
    val currentEpisode: Float = 0f,
    val currentStreamUrl: String? = null,
    val servers: List<StreamServer> = emptyList(),
    val selectedServerIndex: Int = 0,
    val episodes: List<UiEpisodeModel> = emptyList(),
    val totalEpisodes: Int = 100, // Default value
    val playerPosition: Long = 0
)

class WatchAnimeViewModel(
    private val episodesViewModel: EpisodesViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchAnimeState())
    val uiState: StateFlow<WatchAnimeState> = _uiState.asStateFlow()

    private var provider: AnimeProvider? = null
    private val animeIdCache = mutableMapOf<String, String>()
    private val TAG = "WatchAnimeViewModel"

    // Initialize the provider when ViewModel is created
    init {
        provider = AnimeProviderFactory.create()
        Log.d(TAG, "Provider initialized: ${provider?.javaClass?.simpleName}")

        // Get initial episodes list from shared ViewModel
        viewModelScope.launch {
            val episodes = episodesViewModel.episodesList.value
            if (episodes.isNotEmpty()) {
                Log.d(TAG, "Initialized with ${episodes.size} episodes from shared ViewModel")
                _uiState.update { it.copy(episodes = episodes) }
            }
        }
    }

    // Provider management
    fun getProvider(): AnimeProvider? = provider

    fun setProvider(newProvider: AnimeProvider) {
        provider = newProvider
    }

    // State access helpers
    fun getCurrentEpisode() = uiState.value.currentEpisode
    fun getCurrentAnimeId() = uiState.value.animeId

    // Cache management
    fun cacheAnimeId(title: String, id: String) {
        animeIdCache[title] = id
    }

    fun getCachedAnimeId(title: String): String? = animeIdCache[title]

    fun setCurrentAnimeId(id: String?) {
        _uiState.update { it.copy(animeId = id) }
    }

    // Player position tracking
    fun updatePlayerPosition(position: Long) {
        _uiState.update { it.copy(playerPosition = position) }
    }

    // Load a specific episode
    fun loadEpisode(episodeNumber: Float) {
        viewModelScope.launch {
            // Update current episode and set loading state
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    playerPosition = 0,
                    currentEpisode = episodeNumber,
                    currentStreamUrl = null
                )
            }

            try {
                // Get episodes from shared ViewModel if we haven't loaded them yet
                if (_uiState.value.episodes.isEmpty()) {
                    val sharedEpisodes = episodesViewModel.episodesList.value
                    if (sharedEpisodes.isNotEmpty()) {
                        Log.d(TAG, "Using ${sharedEpisodes.size} episodes from shared ViewModel")
                        _uiState.update { it.copy(episodes = sharedEpisodes) }
                    }
                }
                
                // Set current episode in shared view model
                episodesViewModel.setCurrentEpisode(episodeNumber)

                Log.d("TAG", "Loading episode $episodeNumber from ${_uiState.value.animeId} ")

                // If we don't have the animeId yet, we need to search for it first
                if (_uiState.value.animeId == null) {
                    Log.d(TAG, "No animeId found, searching for anime...")
                    // Use the title from the state to search for the anime
                    val animeTitle = _uiState.value.animeTitle
                    if (animeTitle.isNotEmpty()) {
                        // Check cache first
                        val cachedId = getCachedAnimeId(animeTitle)
                        if (cachedId != null) {
                            Log.d(TAG, "Found cached animeId: $cachedId for $animeTitle")
                            setCurrentAnimeId(cachedId)
                        } else {
                            throw Exception("No anime ID available. Please set the anime ID first.")
                        }
                    } else {
                        throw Exception("No anime title available to search for.")
                    }
                }

                // If we have an animeId, load the episode streams
                val currentAnimeId = _uiState.value.animeId
                if (currentAnimeId != null) {
                    // Format episode number properly
                    val formattedEpisodeNumber = formatEpisodeNumber(episodeNumber)
                    Log.d(TAG, "Loading episode: $formattedEpisodeNumber for anime: $currentAnimeId")

                    // Get episode streams with properly formatted episode number

                    val streamsResult = provider?.getEpisodeStreams(
                        animeId = currentAnimeId,
                        episode = formattedEpisodeNumber,
                        translationType = "sub" // Default to sub
                    )

                    if (streamsResult != null && streamsResult.isSuccess) {
                        val streamsList = streamsResult.getOrNull()

                        if (streamsList.isNullOrEmpty()) {
                            throw Exception("No streams available for this episode")
                        }

                        // Update state with servers
                        _uiState.update { state ->
                            state.copy(
                                servers = streamsList,
                                selectedServerIndex = 0,
                                isLoading = false
                            )
                        }

                        // Load episodes list if we haven't already
                        if (_uiState.value.episodes.isEmpty()) {
                            loadEpisodesList(currentAnimeId)
                        }

                        // Set initial stream URL
                        setInitialStreamUrl()
                    } else {
                        throw Exception("Failed to get streams: ${streamsResult?.exceptionOrNull()?.message ?: "Unknown error"}")
                    }
                } else {
                    throw Exception("Failed to find anime. Please check the title.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading episode", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error loading episode: ${e.message}"
                    )
                }
            }
        }
    }

    private fun formatEpisodeNumber(episodeNumber: Float): String {
        // Check if it's a whole number (no decimal part)
        return if (episodeNumber % 1 == 0f) {
            // If it's a whole number, format as integer
            episodeNumber.toInt().toString()
        } else {
            // If it has decimal part, keep the float format
            episodeNumber.toString()
        }
    }

    // Switch to a different server
    fun switchServer(serverIndex: Int) {
        if (serverIndex < 0 || serverIndex >= uiState.value.servers.size) {
            return
        }

        _uiState.update {
            it.copy(
                selectedServerIndex = serverIndex,
                isServerLoading = true,
                currentStreamUrl = null
            )
        }

        viewModelScope.launch {
            try {
                val server = uiState.value.servers[serverIndex]
                val links = server.links

                if (links.isNotEmpty()) {
                    val firstLink = links.first()
                    _uiState.update {
                        it.copy(
                            currentStreamUrl = firstLink.link,
                            isServerLoading = false
                        )
                    }

                    Log.d(TAG, "Switched to server: ${server.server}")
                } else {
                    throw Exception("No links available for this server")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching server", e)
                _uiState.update {
                    it.copy(
                        isServerLoading = false,
                        error = "Error switching server: ${e.message}"
                    )
                }
            }
        }
    }

    // Toggle auto-next episode setting
    fun setAutoNextEpisode(enabled: Boolean) {
        _uiState.update { it.copy(autoNextEpisode = enabled) }
    }

    // Navigate to adjacent episode
    fun navigateToAdjacentEpisode(isNext: Boolean) {
        val currentEpisode = uiState.value.currentEpisode
        val episodes = uiState.value.episodes

        if (episodes.isEmpty()) return

        val currentIndex = episodes.indexOfFirst {
            val diff = it.number - currentEpisode
            diff > -0.001f && diff < 0.001f
        }

        if (currentIndex == -1) return

        val targetIndex = if (isNext) currentIndex + 1 else currentIndex - 1

        if (targetIndex in episodes.indices) {
            val targetEpisode = episodes[targetIndex]

            // Optimized loading - set loading state but maintain current stream
            _uiState.update {
                it.copy(
                    isServerLoading = true,
                    currentEpisode = targetEpisode.number
                )
            }

            // Update position to 0 for the new episode
            updatePlayerPosition(0)

            // Only load the new episode streams, don't reload everything
            loadEpisodeStreams(targetEpisode.number)

            // Update shared view model
            episodesViewModel.setCurrentEpisode(targetEpisode.number)
        }
    }

    // Check if current episode is the last one
    private fun updateIsLastEpisodeFlag() {
        val episodes = uiState.value.episodes
        if (episodes.isEmpty()) return

        val currentEpisode = uiState.value.currentEpisode
        val isLast = episodes.last().number == currentEpisode

        if (uiState.value.isLastEpisode != isLast) {
            _uiState.update { it.copy(isLastEpisode = isLast) }
        }
    }

    // Optimized method to load just episode streams without reloading everything
    private fun loadEpisodeStreams(episodeNumber: Float) {
        viewModelScope.launch {
            try {
                val animeId = uiState.value.animeId ?: return@launch

                // Format episode number properly
                val formattedEpisodeNumber = formatEpisodeNumber(episodeNumber)

                Log.d(TAG, "Loading streams for episode: $formattedEpisodeNumber")

                val streamsResult = provider?.getEpisodeStreams(
                    animeId = animeId,
                    episode = formattedEpisodeNumber,
                    translationType = "sub" // Default to sub
                )

                if (streamsResult != null && streamsResult.isSuccess) {
                    val streamsList = streamsResult.getOrNull()

                    if (streamsList.isNullOrEmpty()) {
                        throw Exception("No streams available for this episode")
                    }

                    // Update state with servers
                    _uiState.update { state ->
                        state.copy(
                            servers = streamsList,
                            selectedServerIndex = 0,
                            isServerLoading = false,
                            error = null
                        )
                    }

                    // Set initial stream URL
                    setInitialStreamUrl()

                    // Update last episode flag
                    updateIsLastEpisodeFlag()

                } else {
                    throw Exception("Failed to get streams: ${streamsResult?.exceptionOrNull()?.message ?: "Unknown error"}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episode streams", e)
                _uiState.update {
                    it.copy(
                        isServerLoading = false,
                        error = "Error loading episode: ${e.message}"
                    )
                }
            }
        }
    }

    // Try switching to next available server (for error recovery)
    fun tryNextServer() {
        val currentIndex = uiState.value.selectedServerIndex
        val serversCount = uiState.value.servers.size

        if (serversCount > 1) {
            val nextIndex = (currentIndex + 1) % serversCount
            switchServer(nextIndex)
        }
    }

    // Set initial stream URL from current server
    private fun setInitialStreamUrl() {
        val state = uiState.value
        val server = state.servers.getOrNull(state.selectedServerIndex) ?: return
        val link = server.links.firstOrNull() ?: return

        _uiState.update { it.copy(currentStreamUrl = link.link) }
    }

    // Load episode list
    private fun loadEpisodesList(animeId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading episodes list for anime ID: $animeId")

                // First check if we already have episodes in shared view model
                val sharedEpisodes = episodesViewModel.episodesList.value
                if (sharedEpisodes.isNotEmpty()) {
                    Log.d(TAG, "Using existing ${sharedEpisodes.size} episodes from shared view model")
                    _uiState.update { it.copy(
                        episodes = sharedEpisodes,
                        totalEpisodes = sharedEpisodes.size
                    )}
                    return@launch
                }

                // Otherwise, fetch new episodes
                val episodeListResult = provider?.getEpisodeList(
                    showId = animeId,
                    episodeNumStart = 0f,
                    episodeNumEnd = 9999f // Use a large value to get all episodes
                )

                if (episodeListResult != null && episodeListResult.isSuccess) {
                    val episodeList = episodeListResult.getOrNull()

                    if (!episodeList.isNullOrEmpty()) {
                        // Convert to UiEpisodeModel
                        val uiEpisodes = episodeList.map { episodeInfo ->
                            UiEpisodeModel(
                                number = episodeInfo.episodeIdNum,
                                title = episodeInfo.notes,
                                thumbnail = episodeInfo.getThumbnailUrl() ?: "",
                                description = episodeInfo.description,
                                uploadDate = episodeInfo.getFormattedDate()
                            )
                        }.sortedBy { it.number }

                        Log.d(TAG, "Loaded ${uiEpisodes.size} episodes")

                        // Update the UI state with episodes
                        _uiState.update { it.copy(
                            episodes = uiEpisodes,
                            totalEpisodes = uiEpisodes.size
                        )}

                        // Also update shared episodes view model
                        episodesViewModel.setEpisodesList(
                            episodes = uiEpisodes
                        )
                    } else {
                        Log.w(TAG, "No episodes found for anime ID: $animeId")
                    }
                } else {
                    Log.e(TAG, "Failed to load episodes: ${episodeListResult?.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes list", e)
                // Don't show error - just log it as this is non-critical
            }
        }
    }

    // Set the initial anime title and ID (called from WatchAnimeScreen)
    fun setInitialAnimeData(showId: String, episodeNumber: Float) {
        val displayTitle = showId.replace("_", " ")
        Log.d(TAG, "Setting initial anime data: title=$displayTitle, id=$showId, episode=$episodeNumber")

        // Set the animeId directly in the state
        _uiState.update {
            it.copy(
                animeTitle = displayTitle,
                animeId = showId,  // Store the actual show ID
                currentEpisode = episodeNumber
            )
        }

        // Cache the ID for future reference
        cacheAnimeId(displayTitle, showId)

        // Immediately try to load the episode
        loadEpisode(episodeNumber)
    }
    class Factory(
        private val episodesViewModel: EpisodesViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WatchAnimeViewModel::class.java)) {
                return WatchAnimeViewModel(episodesViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
