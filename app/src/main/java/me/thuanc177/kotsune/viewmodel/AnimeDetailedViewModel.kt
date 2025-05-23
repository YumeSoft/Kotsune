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
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistDateObject
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistImage
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistMedia
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistMediaTitle
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistMediaTrailer
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistNextAiringEpisode
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeTitle
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Character
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharacterEdge
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharacterImage
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharacterName
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharactersConnection
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.MediaStats
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.MediaTag
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.RecommendationEdge
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.RecommendationNode
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.RecommendationsConnection
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.ScoreDistribution
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.StatusDistribution
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.StreamingEpisode
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.VoiceActor
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.VoiceActorImage
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.VoiceActorName
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import org.json.JSONArray
import org.json.JSONObject

class AnimeDetailedViewModel(
    private val anilistClient: AnilistClient,
    private val anilistId: Int,
    internal val episodesViewModel: EpisodesViewModel // New dependency
) : ViewModel() {
    private val TAG = "AnimeDetailedViewModel"

    private val _uiState = MutableStateFlow(AnimeDetailedState())
    val uiState: StateFlow<AnimeDetailedState> = _uiState.asStateFlow()

    init {
        fetchAnimeDetails()
        checkTrackingStatus()
    }

    fun fetchEpisodes(allAnimeProvider: AllAnimeAPI) {
        val anime = _uiState.value.anime ?: return

        // Use the shared EpisodesViewModel
        episodesViewModel.fetchEpisodes(
            allAnimeProvider = allAnimeProvider,
            anilistId = anime.id,
            title = anime.title?.romaji ?: anime.title?.english ?: anime.title?.native ?: "",
            bannerImage = anime.bannerImage ?: ""
        )
    }

    fun fetchAnimeDetails() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                Log.d(TAG, "Fetching anime details for ID: $anilistId")

                val (success, responseData) = anilistClient.getAnimeDetailed(anilistId)

                // Log the raw response for debugging
                Log.d(TAG, "Raw API response: $responseData")

                if (!success || responseData == null) {
                    val errorMessage = "Failed to fetch anime details: empty response"
                    Log.e(TAG, errorMessage)
                    _uiState.update { it.copy(isLoading = false, error = "Timed out, Anime not found or API error") }
                    return@launch
                }

                try {
                    val media = parseAnimeDetailedFromJson(responseData)
                    if (media == null) {
                        val errorMessage = "Empty Media object in parsed response"
                        Log.e(TAG, errorMessage)
                        _uiState.update { it.copy(isLoading = false, error = "Error parsing anime data") }
                        return@launch
                    }

                    _uiState.update { it.copy(isLoading = false, anime = media) }

                    // After successfully loading anime details, fetch episodes
                    fetchEpisodesFromProvider(media)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing anime details", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Error: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching anime details", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    // Helper method to fetch episodes after anime details are loaded
    private fun fetchEpisodesFromProvider(anime: AnimeDetailed) {
        viewModelScope.launch {
            try {
                // Create an instance of AllAnimeAPI
                val allAnimeProvider = AllAnimeAPI()

                // Get anime title for search - prioritize romaji, then english, then native
                val animeTitle = anime.title?.romaji
                    ?: anime.title?.english
                    ?: anime.title?.native
                    ?: ""

                Log.d(TAG, "Started episode fetch for anime: $animeTitle (ID: ${anime.id})")

                // Call the fetch episodes method with the provider
                episodesViewModel.fetchEpisodes(
                    allAnimeProvider = allAnimeProvider,
                    anilistId = anime.id,
                    title = animeTitle,
                    bannerImage = anime.bannerImage ?: anime.coverImage?.extraLarge ?: ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch episodes", e)
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val currentAnime = _uiState.value.anime ?: return@launch
            val isFavorite = currentAnime.isFavourite == true

            try {
                val result = anilistClient.toggleFavorite(anilistId, !isFavorite)

                if (result) {
                    // Update the UI state with the new favorite status
                    _uiState.update {
                        it.copy(
                            anime = currentAnime.copy(isFavourite = !isFavorite)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
                // Show error message if needed
            }
        }
    }

    fun changeTab(tab: DetailTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToList(status: String) {
        viewModelScope.launch {
            try {
                val result = anilistClient.addToMediaList(anilistId, status)
                if (result) {
                    // Update UI or show success message
                    fetchAnimeDetails() // Refresh data to get updated list status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding anime to list", e)
                // Show error message if needed
            }
        }
    }

    private fun checkTrackingStatus() {
        if (!anilistClient.isUserAuthenticated()) {
            _uiState.update { it.copy(userAuthenticated = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLoading = true, userAuthenticated = true) }

            try {
                // Query to get media list entry if it exists
                val query = """
                    query {
                      Media(id: $anilistId) {
                        mediaListEntry {
                          id
                          status
                          progress
                        }
                      }
                    }
                """.trimIndent()

                val (success, response) = anilistClient.executeQuery(query, emptyMap())

                if (success && response != null) {
                    val data = response.optJSONObject("data")
                    val media = data?.optJSONObject("Media")
                    val entry = media?.optJSONObject("mediaListEntry")

                    if (entry != null) {
                        val status = entry.optString("status")
                        val progress = entry.optInt("progress")

                        _uiState.update {
                            it.copy(
                                trackingStatus = status,
                                trackingProgress = progress,
                                isTrackingLoading = false
                            )
                        }
                    } else {
                        // No tracking info found
                        _uiState.update {
                            it.copy(
                                trackingStatus = null,
                                trackingProgress = 0,
                                isTrackingLoading = false
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTrackingLoading = false,
                            trackingError = "Failed to load tracking status"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking tracking status", e)
                _uiState.update {
                    it.copy(
                        isTrackingLoading = false,
                        trackingError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateTrackingStatus(status: String) {
        if (!anilistClient.isUserAuthenticated()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLoading = true) }

            try {
                // Mutation to update media list entry
                val query = """
                    mutation {
                      SaveMediaListEntry(mediaId: $anilistId, status: $status) {
                        id
                        status
                        progress
                      }
                    }
                """.trimIndent()

                val (success, response) = anilistClient.executeQuery(query, emptyMap())

                if (success && response != null) {
                    val data = response.optJSONObject("data")
                    val entry = data?.optJSONObject("SaveMediaListEntry")

                    if (entry != null) {
                        val newStatus = entry.optString("status")
                        val progress = entry.optInt("progress")

                        _uiState.update {
                            it.copy(
                                trackingStatus = newStatus,
                                trackingProgress = progress,
                                isTrackingLoading = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isTrackingLoading = false,
                                trackingError = "Failed to update tracking status"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTrackingLoading = false,
                            trackingError = "Failed to update tracking status"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tracking status", e)
                _uiState.update {
                    it.copy(
                        isTrackingLoading = false,
                        trackingError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateTrackingProgress(progress: Int) {
        if (!anilistClient.isUserAuthenticated()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLoading = true) }

            try {
                // If no status is set, default to CURRENT when updating progress
                val currentStatus = _uiState.value.trackingStatus ?: "CURRENT"

                // Mutation to update media list entry
                val query = """
                    mutation {
                      SaveMediaListEntry(mediaId: $anilistId, progress: $progress, status: $currentStatus) {
                        id
                        status
                        progress
                      }
                    }
                """.trimIndent()

                val (success, response) = anilistClient.executeQuery(query, emptyMap())

                if (success && response != null) {
                    val data = response.optJSONObject("data")
                    val entry = data?.optJSONObject("SaveMediaListEntry")

                    if (entry != null) {
                        val status = entry.optString("status")
                        val newProgress = entry.optInt("progress")

                        _uiState.update {
                            it.copy(
                                trackingStatus = status,
                                trackingProgress = newProgress,
                                isTrackingLoading = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isTrackingLoading = false,
                                trackingError = "Failed to update tracking progress"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTrackingLoading = false,
                            trackingError = "Failed to update tracking progress"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tracking progress", e)
                _uiState.update {
                    it.copy(
                        isTrackingLoading = false,
                        trackingError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun removeFromTrackingList() {
        if (!anilistClient.isUserAuthenticated()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTrackingLoading = true) }

            try {
                // First get the media list entry ID
                val getEntryQuery = """
                    query {
                      Media(id: $anilistId) {
                        mediaListEntry {
                          id
                        }
                      }
                    }
                """.trimIndent()

                val (getSuccess, getResponse) = anilistClient.executeQuery(getEntryQuery, emptyMap())

                if (getSuccess && getResponse != null) {
                    val data = getResponse.optJSONObject("data")
                    val media = data?.optJSONObject("Media")
                    val entry = media?.optJSONObject("mediaListEntry")

                    if (entry != null) {
                        val entryId = entry.optInt("id")

                        // Now delete the entry
                        val deleteQuery = """
                            mutation {
                              DeleteMediaListEntry(id: $entryId) {
                                deleted
                              }
                            }
                        """.trimIndent()

                        val (deleteSuccess, deleteResponse) = anilistClient.executeQuery(deleteQuery, emptyMap())

                        if (deleteSuccess && deleteResponse != null) {
                            val deleteData = deleteResponse.optJSONObject("data")
                            val deleteResult = deleteData?.optJSONObject("DeleteMediaListEntry")

                            if (deleteResult != null && deleteResult.optBoolean("deleted")) {
                                _uiState.update {
                                    it.copy(
                                        trackingStatus = null,
                                        trackingProgress = 0,
                                        isTrackingLoading = false
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isTrackingLoading = false,
                                        trackingError = "Failed to remove from tracking list"
                                    )
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isTrackingLoading = false,
                                    trackingError = "Failed to remove from tracking list"
                                )
                            }
                        }
                    } else {
                        // No tracking info found, nothing to remove
                        _uiState.update {
                            it.copy(
                                trackingStatus = null,
                                trackingProgress = 0,
                                isTrackingLoading = false
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTrackingLoading = false,
                            trackingError = "Failed to get tracking info"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from tracking list", e)
                _uiState.update {
                    it.copy(
                        isTrackingLoading = false,
                        trackingError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    private fun parseAnimeDetailedFromJson(json: JSONObject): AnimeDetailed? {
        try {
            val dataObj = json.optJSONObject("data")
            if (dataObj == null) {
                Log.e(TAG, "JSON missing data object")
                return null
            }

            // Check if Media key exists before accessing it
            if (!dataObj.has("Media")) {
                Log.e(TAG, "JSON missing Media key: ${json.toString().take(100)}...")
                return null
            }

            val mediaObj = dataObj.getJSONObject("Media")
            Log.d(TAG, "Successfully found Media object with ID: ${mediaObj.optInt("id", -1)}")

            // Parse title
            val titleObj = mediaObj.optJSONObject("title")
            val title = if (titleObj != null) {
                AnimeTitle(
                    english = titleObj.optString("english", null),
                    romaji = titleObj.optString("romaji", null),
                    native = titleObj.optString("native", null)
                )
            } else null

            // Parse cover image
            val coverImageObj = mediaObj.optJSONObject("coverImage")
            val coverImage = if (coverImageObj != null) {
                AnilistImage(
                    large = coverImageObj.optString("large"),
                    medium = coverImageObj.optString("medium"),
                    extraLarge = coverImageObj.optString("extraLarge")
                )
            } else null

            // Parse stats
            val statsObj = mediaObj.optJSONObject("stats")
            val stats = if (statsObj != null) {
                val statusDistribution = parseStatusDistribution(statsObj.optJSONArray("statusDistribution"))
                val scoreDistribution = parseScoreDistribution(statsObj.optJSONArray("scoreDistribution"))

                MediaStats(
                    statusDistribution = statusDistribution,
                    scoreDistribution = scoreDistribution
                )
            } else null

            // Parse characters
            val charactersObj = mediaObj.optJSONObject("characters")
            val characters = if (charactersObj != null) {
                val edges = mutableListOf<CharacterEdge>()
                val edgesArr = charactersObj.optJSONArray("edges")

                if (edgesArr != null) {
                    for (i in 0 until edgesArr.length()) {
                        val edge = parseCharacterEdge(edgesArr.getJSONObject(i))
                        if (edge != null) {
                            edges.add(edge)
                        }
                    }
                }

                CharactersConnection(edges = edges)
            } else null

            // Parse trailer
            val trailerObj = mediaObj.optJSONObject("trailer")
            val trailer = if (trailerObj != null) {
                AnilistMediaTrailer(
                    id = trailerObj.optString("id", null),
                    site = trailerObj.optString("site", null)
                )
            } else null

            // Parse streaming episodes
            val streamingEpisodesArr = mediaObj.optJSONArray("streamingEpisodes")
            val streamingEpisodes = if (streamingEpisodesArr != null) {
                val episodes = mutableListOf<StreamingEpisode>()
                for (i in 0 until streamingEpisodesArr.length()) {
                    val epObj = streamingEpisodesArr.getJSONObject(i)
                    episodes.add(
                        StreamingEpisode(
                            title = epObj.optString("title"),
                            thumbnail = epObj.optString("thumbnail")
                        )
                    )
                }
                episodes
            } else null

            // Parse recommendations
            val recommendationsObj = mediaObj.optJSONObject("recommendations")
            val recommendations = if (recommendationsObj != null) {
                parseRecommendationsConnection(recommendationsObj)
            } else null

            // Create and return the AnimeDetailed object
            return AnimeDetailed(
                id = mediaObj.optInt("id", -1),
                title = title,
                coverImage = coverImage,
                bannerImage = mediaObj.optString("bannerImage", null),
                averageScore = mediaObj.optInt("averageScore", 0),
                duration = mediaObj.optInt("duration", 0),
                favourites = mediaObj.optInt("favourites", 0),
                isFavourite = mediaObj.optBoolean("isFavourite", false),
                format = mediaObj.optString("format", null),
                genres = parseStringArray(mediaObj.optJSONArray("genres")),
                isAdult = mediaObj.optBoolean("isAdult", false),
                startDate = parseFuzzyDate(mediaObj.optJSONObject("startDate")),
                tags = parseTags(mediaObj.optJSONArray("tags")),
                countryOfOrigin = mediaObj.optString("countryOfOrigin", null),
                status = mediaObj.optString("status", null),
                stats = stats,
                seasonYear = if (mediaObj.has("seasonYear")) mediaObj.optInt("seasonYear") else null,
                description = mediaObj.optString("description", null),
                trailer = trailer,
                characters = characters,
                episodes = if (mediaObj.has("episodes")) mediaObj.optInt("episodes") else null,
                streamingEpisodes = streamingEpisodes,
                nextAiringEpisode = parseNextAiringEpisode(mediaObj.optJSONObject("nextAiringEpisode")),
                recommendations = recommendations
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing anime details", e)
            return null
        }
    }

    // Helper parsing methods
    private fun parseStringArray(jsonArray: JSONArray?): List<String>? {
        if (jsonArray == null) return null
        val result = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            result.add(jsonArray.getString(i))
        }
        return result
    }

    private fun parseTags(tagsArray: JSONArray?): List<MediaTag>? {
        if (tagsArray == null) return null
        val result = mutableListOf<MediaTag>()
        for (i in 0 until tagsArray.length()) {
            val tagObj = tagsArray.getJSONObject(i)
            result.add(
                MediaTag(
                    name = tagObj.getString("name"),
                    description = tagObj.optString("description", null),
                    rank = if (tagObj.has("rank")) tagObj.optInt("rank") else null
                )
            )
        }
        return result
    }

    private fun parseFuzzyDate(dateObj: JSONObject?): AnilistDateObject? {
        return if (dateObj != null) {
            AnilistDateObject(
                year = if (dateObj.has("year")) dateObj.optInt("year") else null,
                month = if (dateObj.has("month")) dateObj.optInt("month") else null,
                day = if (dateObj.has("day")) dateObj.optInt("day") else null
            )
        } else null
    }

    private fun parseStatusDistribution(distributionArr: JSONArray?): List<StatusDistribution>? {
        if (distributionArr == null) return null
        val result = mutableListOf<StatusDistribution>()
        for (i in 0 until distributionArr.length()) {
            val item = distributionArr.getJSONObject(i)
            result.add(
                StatusDistribution(
                    status = item.getString("status"),
                    amount = item.getInt("amount")
                )
            )
        }
        return result
    }

    private fun parseScoreDistribution(distributionArr: JSONArray?): List<ScoreDistribution>? {
        if (distributionArr == null) return null
        val result = mutableListOf<ScoreDistribution>()
        for (i in 0 until distributionArr.length()) {
            val item = distributionArr.getJSONObject(i)
            result.add(
                ScoreDistribution(
                    score = item.getInt("score"),
                    amount = item.getInt("amount")
                )
            )
        }
        return result
    }

    private fun parseCharacterEdge(edgeObj: JSONObject): CharacterEdge? {
        val nodeObj = edgeObj.optJSONObject("node") ?: return null

        // Parse character
        val nameObj = nodeObj.optJSONObject("name")
        val name = if (nameObj != null) {
            CharacterName(
                full = nameObj.optString("full"),
                native = nameObj.optString("native")
            )
        } else null

        val imageObj = nodeObj.optJSONObject("image")
        val image = if (imageObj != null) {
            CharacterImage(large = imageObj.optString("large"))
        } else null

        val character = Character(
            id = nodeObj.getInt("id"),
            name = name,
            image = image,
            age = nodeObj.optString("age", null),
            description = nodeObj.optString("description", null),
            dateOfBirth = null // Parse if needed
        )

        // Parse voice actors
        val voiceActors = mutableListOf<VoiceActor>()
        val vaArr = edgeObj.optJSONArray("voiceActors")
        if (vaArr != null) {
            for (i in 0 until vaArr.length()) {
                val vaObj = vaArr.getJSONObject(i)
                val vaNameObj = vaObj.optJSONObject("name")
                val vaName = if (vaNameObj != null) {
                    VoiceActorName(
                        full = vaNameObj.optString("full"),
                        native = vaNameObj.optString("native")
                    )
                } else null

                val vaImageObj = vaObj.optJSONObject("image")
                val vaImage = if (vaImageObj != null) {
                    VoiceActorImage(large = vaImageObj.optString("large"))
                } else null

                voiceActors.add(
                    VoiceActor(
                        name = vaName,
                        image = vaImage,
                        languageV2 = vaObj.optString("languageV2", null),
                        homeTown = vaObj.optString("homeTown", null),
                        description = vaObj.optString("description", null),
                        bloodType = vaObj.optString("bloodType", null),
                        age = if (vaObj.has("age")) vaObj.optInt("age") else null,
                        characters = null
                    )
                )
            }
        }

        return CharacterEdge(
            node = character,
            role = edgeObj.optString("role"),
            voiceActors = voiceActors
        )
    }

    private fun parseNextAiringEpisode(episodeObj: JSONObject?): AnilistNextAiringEpisode? {
        return if (episodeObj != null) {
            AnilistNextAiringEpisode(
                timeUntilAiring = episodeObj.getInt("timeUntilAiring"),
                episode = episodeObj.getInt("episode")
            )
        } else null
    }

    private fun parseRecommendationsConnection(recommendationsObj: JSONObject): RecommendationsConnection? {
        val edgesArr = recommendationsObj.optJSONArray("edges") ?: return null
        val edges = mutableListOf<RecommendationEdge>()

        for (i in 0 until edgesArr.length()) {
            val edgeObj = edgesArr.getJSONObject(i)
            val nodeObj = edgeObj.optJSONObject("node") ?: continue
            val mediaRecObj = nodeObj.optJSONObject("mediaRecommendation") ?: continue

            // Parse the recommended media
            val titleObj = mediaRecObj.optJSONObject("title")
            val title = if (titleObj != null) {
                AnilistMediaTitle(
                    english = titleObj.optString("english", null),
                    romaji = titleObj.optString("romaji", null),
                    native = titleObj.optString("native", null)
                )
            } else null

            val coverImageObj = mediaRecObj.optJSONObject("coverImage")
            val coverImage = if (coverImageObj != null) {
                AnilistImage(
                    large = coverImageObj.optString("large"),
                    medium = coverImageObj.optString("medium")
                )
            } else null

            val recommendedMedia = AnilistMedia(
                id = mediaRecObj.getInt("id"),
                title = title,
                coverImage = coverImage,
                genres = parseStringArray(mediaRecObj.optJSONArray("genres")),
                seasonYear = if (mediaRecObj.has("seasonYear")) mediaRecObj.optInt("seasonYear") else null,
                episodes = if (mediaRecObj.has("episodes")) mediaRecObj.optInt("episodes") else null,
                averageScore = mediaRecObj.optInt("averageScore", 0),
                status = mediaRecObj.optString("status", null)
            )

            edges.add(
                RecommendationEdge(
                    node = RecommendationNode(
                        mediaRecommendation = recommendedMedia
                    )
                )
            )
        }

        return RecommendationsConnection(edges = edges)
    }

    class Factory(
        private val anilistClient: AnilistClient,
        private val anilistId: Int,
        private val episodesViewModel: EpisodesViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnimeDetailedViewModel::class.java)) {
                return AnimeDetailedViewModel(anilistClient, anilistId, episodesViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class AnimeDetailedState(
    val isLoading: Boolean = false,
    val anime: AnimeDetailed? = null,
    val error: String? = null,
    val activeTab: DetailTab = DetailTab.OVERVIEW,
    val userAuthenticated: Boolean = false,
    val trackingStatus: String? = null,
    val trackingProgress: Int = 0,
    val isTrackingLoading: Boolean = false,
    val trackingError: String? = null
)

enum class DetailTab {
    OVERVIEW, CHARACTERS, EPISODES, RELATED
}

