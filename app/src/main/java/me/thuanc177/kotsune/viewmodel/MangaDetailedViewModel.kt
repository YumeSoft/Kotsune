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
import me.thuanc177.kotsune.repository.FavoritesRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class MangaDetailedViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val mangaId: String,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

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

    fun selectChapterForReading(chapterId: String) {
        val chapters = _uiState.value.chapters
        val chapterIndex = chapters.indexOfFirst { it.id == chapterId }

        if (chapterIndex >= 0) {
            val selectedChapter = chapters[chapterIndex]

            _uiState.update {
                it.copy(
                    selectedChapterIndex = chapterIndex,
                    selectedTranslationGroup = selectedChapter.translatorGroup
                )
            }

            // Mark chapter as read
            markChapterAsRead(chapterId)
        }
    }

    fun getNextChapter(): ChapterModel? {
        val currentState = _uiState.value
        val currentIndex = currentState.selectedChapterIndex ?: return null
        val chapters = currentState.chapters
        val preferredLanguage = currentState.chapters.getOrNull(currentIndex)?.language
        val preferredGroup = currentState.selectedTranslationGroup

        // Look for next chapter number
        val currentChapterNumber = chapters.getOrNull(currentIndex)?.number ?: return null

        // Find chapters with the next chapter number
        val nextChapterNumber = findNextChapterNumber(chapters, currentChapterNumber, currentState.chapterSortAscending)
        val nextChapters = chapters.filter { it.number == nextChapterNumber }

        // If no next chapter found, return null
        if (nextChapters.isEmpty()) return null

        // Try to find a chapter with the same language and translator group
        val nextChapter = nextChapters.find {
            it.language == preferredLanguage && it.translatorGroup == preferredGroup
        } ?: nextChapters.find {
            it.language == preferredLanguage
        } ?: nextChapters.firstOrNull()

        nextChapter?.let { selectChapterForReading(it.id) }
        return nextChapter
    }

    fun getPreviousChapter(): ChapterModel? {
        val currentState = _uiState.value
        val currentIndex = currentState.selectedChapterIndex ?: return null
        val chapters = currentState.chapters
        val preferredLanguage = currentState.chapters.getOrNull(currentIndex)?.language
        val preferredGroup = currentState.selectedTranslationGroup

        // Look for previous chapter number
        val currentChapterNumber = chapters.getOrNull(currentIndex)?.number ?: return null

        // Find chapters with the previous chapter number
        val prevChapterNumber = findPreviousChapterNumber(chapters, currentChapterNumber, currentState.chapterSortAscending)
        val prevChapters = chapters.filter { it.number == prevChapterNumber }

        // If no previous chapter found, return null
        if (prevChapters.isEmpty()) return null

        // Try to find a chapter with the same language and translator group
        val prevChapter = prevChapters.find {
            it.language == preferredLanguage && it.translatorGroup == preferredGroup
        } ?: prevChapters.find {
            it.language == preferredLanguage
        } ?: prevChapters.firstOrNull()

        prevChapter?.let { selectChapterForReading(it.id) }
        return prevChapter
    }

    private fun findNextChapterNumber(chapters: List<ChapterModel>, currentNumber: String, ascending: Boolean): String? {
        // Get all unique chapter numbers and sort them
        val chapterNumbers = chapters.map { it.number }.distinct().sortedBy { it.toFloatOrNull() ?: Float.MAX_VALUE }

        val currentIdx = chapterNumbers.indexOf(currentNumber)
        if (currentIdx < 0 || currentIdx >= chapterNumbers.size - 1) return null

        return if (ascending) {
            // In ascending order, next chapter is the next index
            chapterNumbers.getOrNull(currentIdx + 1)
        } else {
            // In descending order, next chapter is the previous index
            chapterNumbers.getOrNull(currentIdx - 1)
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

    private fun findPreviousChapterNumber(chapters: List<ChapterModel>, currentNumber: String, ascending: Boolean): String? {
        // Get all unique chapter numbers and sort them
        val chapterNumbers = chapters.map { it.number }.distinct().sortedBy { it.toFloatOrNull() ?: Float.MAX_VALUE }

        val currentIdx = chapterNumbers.indexOf(currentNumber)
        if (currentIdx <= 0) return null

        return if (ascending) {
            // In ascending order, previous chapter is the previous index
            chapterNumbers.getOrNull(currentIdx - 1)
        } else {
            // In descending order, previous chapter is the next index
            chapterNumbers.getOrNull(currentIdx + 1)
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