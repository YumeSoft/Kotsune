package me.thuanc177.kotsune.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.StreamServer

class WatchAnimeViewModel : ViewModel() {
    // Cache for anime IDs to avoid repeated searches
    private val animeIdCache = mutableMapOf<String, String>()
    private var currentEpisode: Int = 0
    private var currentAnimeId: String? = null

    fun setCurrentEpisode(episode: Int) {
        currentEpisode = episode
    }

    fun getCurrentEpisode(): Int {
        return currentEpisode
    }

    fun setCurrentAnimeId(id: String?) {
        currentAnimeId = id
    }

    fun getCurrentAnimeId(): String? {
        return currentAnimeId
    }

    // Store last used provider for reuse
    private var lastProvider: AnimeProvider? = null

    // Cache for the current anime ID
    fun cacheAnimeId(title: String, id: String) {
        animeIdCache[title] = id
    }

    // Get cached anime ID if available
    fun getCachedAnimeId(title: String): String? {
        return animeIdCache[title]
    }

    // Remember the last used provider
    fun setProvider(provider: AnimeProvider) {
        lastProvider = provider
    }

    // Get the last used provider
    fun getProvider(): AnimeProvider? {
        return lastProvider
    }
}