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
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaDetailedState
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaStatistics
import me.thuanc177.kotsune.repository.FavoritesRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class MangaDetailedViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val mangaId: String,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _uiState = MutableStateFlow(MangaDetailedState(
        manga = null,
        isLoading = false,
        error = null,
        isFavorite = false,
        chapters = emptyList(),
        chaptersLoading = false,
        chaptersError = null,
        chapterSortAscending = false,
        selectedChapterIndex = null,
        selectedTranslationGroup = null
    ))
    val uiState: StateFlow<MangaDetailedState> = _uiState.asStateFlow()

    private val isInitialized = AtomicBoolean(false)
    private val TAG = "MangaDetailedViewModel"

    // Add statistics and reading status state
    private val _statistics = MutableStateFlow<MangaStatistics?>(null)
    val statistics: StateFlow<MangaStatistics?> = _statistics.asStateFlow()

    private val _readingStatus = MutableStateFlow<String?>(null)
    val readingStatus: StateFlow<String?> = _readingStatus.asStateFlow()

    private val _userRating = MutableStateFlow<Int?>(null)
    val userRating: StateFlow<Int?> = _userRating.asStateFlow()

    init {
        if (isInitialized.compareAndSet(false, true)) {
            checkIfFavorite()
            checkAuthentication()
            fetchMangaDetails()
            fetchStatistics()
            fetchReadingStatus()
        }
    }

    private fun checkIfFavorite() {
        viewModelScope.launch {
            val isFavorite = favoritesRepository.isMangaFavorite(mangaId)
            _uiState.update { it.copy(isFavorite = isFavorite) }
        }
    }

    private fun checkAuthentication() {
        viewModelScope.launch {
            _isAuthenticated.value = mangaDexAPI.isAuthenticated()
        }
    }

    fun fetchMangaDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val manga = mangaDexAPI.getMangaDetails(mangaId)
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
            }
        }
    }

    fun fetchChapters() {
        viewModelScope.launch {
            _uiState.update { it.copy(chaptersLoading = true, chaptersError = null) }
            try {
                val chapters = mangaDexAPI.getChapters(mangaId)
                // Sort the chapters according to current sort direction
                val sortedChapters = sortChapters(chapters, _uiState.value.chapterSortAscending)
                _uiState.update { it.copy(chapters = sortedChapters, chaptersLoading = false) }
            } catch (e: Exception) {
                handleChaptersFetchError(e)
            }
        }
    }

    private fun handleChaptersFetchError(e: Exception) {
        when (e) {
            is SocketTimeoutException -> {
                Log.e(TAG, "Network timeout when fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = "Network timeout. Please check your connection.")
                }
            }
            is IOException -> {
                Log.e(TAG, "Network error when fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = "Network error. Please check your connection.")
                }
            }
            else -> {
                Log.e(TAG, "Error fetching chapters", e)
                _uiState.update {
                    it.copy(chaptersLoading = false, chaptersError = e.message ?: "Failed to load chapters")
                }
            }
        }
    }

    fun toggleChapterSorting() {
        _uiState.update { currentState ->
            val newSortDirection = !currentState.chapterSortAscending

            // Get the sorted chapters based on the new direction
            val sortedChapters = sortChapters(currentState.chapters, newSortDirection)

            currentState.copy(
                chapterSortAscending = newSortDirection,
                chapters = sortedChapters
            )
        }
    }

    private fun sortChapters(chapters: List<ChapterModel>, ascending: Boolean): List<ChapterModel> {
        return if (ascending) {
            // Sort chapters by number ascending
            chapters.sortedWith(compareBy {
                it.number.toFloatOrNull() ?: Float.MAX_VALUE
            })
        } else {
            // Sort chapters by number descending
            chapters.sortedWith(compareByDescending {
                it.number.toFloatOrNull() ?: Float.MIN_VALUE
            })
        }
    }

    // New functions for statistics and reading status
    fun fetchStatistics() {
        viewModelScope.launch {
            try {
                val stats = mangaDexAPI.fetchMangaStatistics(mangaId)
                _statistics.value = stats
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching manga statistics", e)
            }
        }
    }

    fun fetchReadingStatus() {
        viewModelScope.launch {
            try {
                if (mangaDexAPI.isAuthenticated()) {
                    val status = mangaDexAPI.getMangaReadingStatus(mangaId)
                    _readingStatus.value = status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching reading status", e)
            }
        }
    }

    fun updateReadingStatus(status: String?) {
        viewModelScope.launch {
            try {
                if (mangaDexAPI.isAuthenticated()) {
                    val success = mangaDexAPI.updateMangaReadingStatus(mangaId, status)
                    if (success) {
                        _readingStatus.value = status
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating reading status", e)
            }
        }
    }

    fun rateManga(rating: Int) {
        viewModelScope.launch {
            try {
                if (mangaDexAPI.isAuthenticated()) {
                    val success = mangaDexAPI.rateManga(mangaId, rating)
                    if (success) {
                        _userRating.value = rating
                        fetchStatistics() // Refresh statistics after rating
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rating manga", e)
            }
        }
    }

    fun deleteRating() {
        viewModelScope.launch {
            try {
                if (mangaDexAPI.isAuthenticated()) {
                    val success = mangaDexAPI.deleteRating(mangaId)
                    if (success) {
                        _userRating.value = null
                        fetchStatistics() // Refresh statistics after removing rating
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting manga rating", e)
            }
        }
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