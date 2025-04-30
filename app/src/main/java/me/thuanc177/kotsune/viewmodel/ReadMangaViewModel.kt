package me.thuanc177.kotsune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI

data class ReadMangaState(
    val mangaId: String = "",
    val chapterId: String = "",
    val chapterNumber: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val pages: List<String> = emptyList(),
    val chaptersList: List<ChapterModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ReadMangaViewModel(
    private val mangaDexAPI: MangaDexAPI,
    initialChapterId: String,
    initialChaptersList: List<ChapterModel> = emptyList()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReadMangaState(
            chapterId = initialChapterId,
            chaptersList = initialChaptersList
        )
    )
    val uiState: StateFlow<ReadMangaState> = _uiState.asStateFlow()

    init {
        loadChapter(initialChapterId)
    }

    fun loadChapter(chapterId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Update current chapter ID
                _uiState.update { it.copy(chapterId = chapterId) }

                // Find current chapter info from the chapters list
                val currentChapter = _uiState.value.chaptersList.find { it.id == chapterId }
                currentChapter?.let { chapter ->
                    _uiState.update { it.copy(chapterNumber = chapter.number) }
                }

                // TODO: Add API call to fetch chapter pages
                // For now, we'll just update the state with dummy data
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPage = 0,
                        totalPages = 10, // This will come from the API
                        pages = List(10) { "https://placeholder.com/page_$it.jpg" }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun navigateToNextChapter(): Boolean {
        val currentChapterId = _uiState.value.chapterId
        val chaptersList = _uiState.value.chaptersList

        val currentIndex = chaptersList.indexOfFirst { it.id == currentChapterId }
        if (currentIndex < chaptersList.size - 1) {
            val nextChapter = chaptersList[currentIndex + 1]
            loadChapter(nextChapter.id)
            return true
        }
        return false
    }

    fun navigateToPreviousChapter(): Boolean {
        val currentChapterId = _uiState.value.chapterId
        val chaptersList = _uiState.value.chaptersList

        val currentIndex = chaptersList.indexOfFirst { it.id == currentChapterId }
        if (currentIndex > 0) {
            val prevChapter = chaptersList[currentIndex - 1]
            loadChapter(prevChapter.id)
            return true
        }
        return false
    }

    fun hasNextChapter(): Boolean {
        val currentChapterId = _uiState.value.chapterId
        val chaptersList = _uiState.value.chaptersList

        val currentIndex = chaptersList.indexOfFirst { it.id == currentChapterId }
        return currentIndex < chaptersList.size - 1
    }

    fun hasPreviousChapter(): Boolean {
        val currentChapterId = _uiState.value.chapterId
        val chaptersList = _uiState.value.chaptersList

        val currentIndex = chaptersList.indexOfFirst { it.id == currentChapterId }
        return currentIndex > 0
    }

    fun setCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    class Factory(
        private val mangaDexAPI: MangaDexAPI,
        private val initialChapterId: String,
        private val initialChaptersList: List<ChapterModel> = emptyList()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReadMangaViewModel::class.java)) {
                return ReadMangaViewModel(mangaDexAPI, initialChapterId, initialChaptersList) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}