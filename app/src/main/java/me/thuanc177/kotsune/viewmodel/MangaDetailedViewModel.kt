package me.thuanc177.kotsune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.repository.FavoritesRepository
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

data class MangaDetailedState(
    val manga: Manga? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val chapters: List<ChapterModel> = emptyList(),
    val chaptersLoading: Boolean = false,
    val chaptersError: String? = null,
    val chapterSortAscending: Boolean = false,
    val selectedChapterIndex: Int?,
    val selectedTranslationGroup: String? = null
)

data class MangaDetailedUiState(
    val manga: Manga? = null,
    val chapters: List<ChapterModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val chaptersLoading: Boolean = false,
    val chaptersError: String? = null,
    val isFavorite: Boolean = false,
    val chapterSortAscending: Boolean = false, // New sorting direction state
    val selectedChapterIndex: Int? = null,      // Selected chapter for reading
    val selectedTranslationGroup: String? = null // Selected translation group preference
)

data class ChapterModel(
    val id: String,
    val number: String,
    val title: String,
    val publishedAt: String,
    val pages: Int = 0,
    val thumbnail: String? = null,
    val isRead: Boolean = false,
    val volume: String? = null,
    val language: String = "en",
    val translatorGroup: String? = null,
    val languageFlag: String? = null
)

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
                val manga = fetchMangaFromAPI(mangaId)
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
                val chapters = fetchChaptersFromAPI(mangaId)
                // Sort the chapters according to current sort direction
                val sortedChapters = sortChapters(chapters, _uiState.value.chapterSortAscending)
                _uiState.update { it.copy(chapters = sortedChapters, chaptersLoading = false) }
            } catch (e: Exception) {
                // Error handling remains the same
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

    private suspend fun fetchMangaFromAPI(id: String): Manga? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.mangadex.org/manga/$id?includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val data = jsonObject.getJSONObject("data")
                    val attributes = data.getJSONObject("attributes")

                    // Extract title
                    val titleObj = attributes.getJSONObject("title")
                    val titles = mutableListOf<String>()
                    for (key in titleObj.keys()) {
                        titleObj.getString(key)?.let { titles.add(it) }
                    }

                    // Extract description
                    val description = attributes.optJSONObject("description")?.let { descObj ->
                        descObj.optString("en") ?: run {
                            // If English description not available, get the first available description
                            for (key in descObj.keys()) {
                                val desc = descObj.optString(key)
                                if (desc.isNotEmpty()) return@run desc
                            }
                            ""
                        }
                    } ?: ""

                    val status = attributes.optString("status", "unknown")

                    val year = attributes.optInt("year", 0)

                    attributes.optString("contentRating", "safe")

                    var coverImage: String? = null
                    val relationships = data.getJSONArray("relationships")
                    for (i in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(i)
                        if (rel.getString("type") == "cover_art") {
                            if (rel.has("attributes")) {
                                val coverAttributes = rel.getJSONObject("attributes")
                                val fileName = coverAttributes.optString("fileName")
                                if (fileName.isNotEmpty()) {
                                    coverImage = "https://uploads.mangadex.org/covers/$id/$fileName"
                                }
                            }
                        }
                    }

                    // Extract tags
                    val tagsArray = attributes.getJSONArray("tags")
                    val tags = mutableListOf<SearchViewModel.MangaTag>()
                    for (i in 0 until tagsArray.length()) {
                        val tag = tagsArray.getJSONObject(i)
                        val tagId = tag.getString("id")

                        // Extract tag name from attributes
                        val tagAttributes = tag.getJSONObject("attributes")
                        val tagNameObj = tagAttributes.getJSONObject("name")

                        // Try to get English name first, then fallback to any available language
                        val tagName = tagNameObj.optString("en") ?: run {
                            var name = ""
                            val keys = tagNameObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val localizedName = tagNameObj.optString(key)
                                if (localizedName.isNotEmpty()) {
                                    name = localizedName
                                    break
                                }
                            }
                            name
                        }

                        // Only add tags with non-empty names
                        if (tagName.isNotEmpty()) {
                            Log.d(TAG, "Adding tag: $tagName with id: $tagId")
                            tags.add(SearchViewModel.MangaTag(tagId, tagName))
                        }
                    }

                    return@withContext Manga(
                        id = id,
                        title = titles,
                        poster = coverImage,
                        status = status,
                        description = description,
                        lastUpdated = attributes.optString("updatedAt"),
                        lastChapter = null,
                        year = if (year > 0) year else null,
                        tags = tags,
                        latestUploadedChapterId = attributes.optString("latestUploadedChapter"),
                        contentRating = attributes.optString("contentRating", null)
                    )
                }

                // Handle error responses
                val errorMessage = when(connection.responseCode) {
                    404 -> "Manga not found"
                    429 -> "Rate limit exceeded. Please try again later."
                    500, 502, 503, 504 -> "Server error. Please try again later."
                    else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
                }

                Log.e(TAG, "API error: $errorMessage")
                throw IOException(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing manga details", e)
                throw e
            }
        }
    }

    private suspend fun fetchChaptersFromAPI(mangaId: String): List<ChapterModel> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all languages but keep them separated for display
                val url = "https://api.mangadex.org/manga/$mangaId/feed?limit=500&order[chapter]=asc&includes[]=scanlation_group"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val dataArray = jsonObject.getJSONArray("data")
                    val chapters = mutableListOf<ChapterModel>()
                    val readChapters = getReadChapters(mangaId)

                    // Map to store chapter number to its translations
                    val chapterMap = mutableMapOf<String, MutableList<ChapterModel>>()

                    for (i in 0 until dataArray.length()) {
                        val chapterObject = dataArray.getJSONObject(i)
                        val chapterId = chapterObject.getString("id")
                        val attributes = chapterObject.getJSONObject("attributes")

                        val chapterNumber = attributes.optString("chapter", "")
                        if (chapterNumber.isEmpty()) continue  // Skip chapters without numbers

                        // Extract volume information
                        val volume = attributes.optString("volume", null)

                        // Get language code
                        val language = attributes.optString("translatedLanguage", "en")

                        // Get language flag emoji
                        val languageFlag = getLanguageFlag(language)

                        // Get scanlation group
                        var translatorGroup: String? = null
                        val relationships = chapterObject.getJSONArray("relationships")
                        for (j in 0 until relationships.length()) {
                            val rel = relationships.getJSONObject(j)
                            if (rel.getString("type") == "scanlation_group" && rel.has("attributes")) {
                                translatorGroup = rel.getJSONObject("attributes").optString("name")
                                break
                            }
                        }

                        val chapterTitle = if (attributes.isNull("title")) null else attributes.getString("title")
                        val finalTitle = if (chapterTitle.isNullOrBlank()) {
                            "Chapter $chapterNumber"
                        } else {
                            chapterTitle
                        }

                        val chapterModel = ChapterModel(
                            id = chapterId,
                            number = chapterNumber,
                            title = finalTitle,
                            publishedAt = attributes.optString("publishAt", ""),
                            pages = attributes.optInt("pages", 0),
                            volume = volume,
                            isRead = readChapters.contains(chapterId),
                            language = language,
                            translatorGroup = translatorGroup,
                            languageFlag = languageFlag
                        )

                        // Add to chapter map
                        if (!chapterMap.containsKey(chapterNumber)) {
                            chapterMap[chapterNumber] = mutableListOf()
                        }
                        chapterMap[chapterNumber]?.add(chapterModel)
                    }

                    // Sort each chapter's translations by language (English first)
                    chapterMap.forEach { (_, translations) ->
                        translations.sortWith(compareBy<ChapterModel> { it.language != "en" }
                            .thenBy { it.publishedAt }
                            .thenBy { it.language })
                    }

                    // Flatten the map back to a list, sorted by chapter number
                    val sortedChapterNumbers = chapterMap.keys
                        .sortedWith(compareBy<String> {
                            it.toFloatOrNull() ?: Float.MAX_VALUE
                        })

                    for (chapterNum in sortedChapterNumbers) {
                        chapters.addAll(chapterMap[chapterNum] ?: emptyList())
                    }

                    return@withContext chapters
                }

                val errorMessage = when(connection.responseCode) {
                    404 -> "Chapters not found"
                    429 -> "Rate limit exceeded. Please try again later."
                    500, 502, 503, 504 -> "Server error. Please try again later."
                    else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
                }

                Log.e(TAG, "API error: $errorMessage")
                throw IOException(errorMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching chapters", e)
                throw e
            }
        }
    }

    private fun getLanguageFlag(languageCode: String): String {
        // Extract the base language and region if present (e.g., "en-us" -> "en" and "us")
        val parts = languageCode.split("-")
        if (parts.size > 1) parts[1].uppercase() else ""

        // For languages with specific regions, use the region's flag
        return when (languageCode) {
            "en" -> "🇬🇧"  // English (default to UK)
            "en-us" -> "🇺🇸"  // English (US)
            "ja" -> "🇯🇵"  // Japanese
            "ko" -> "🇰🇷"  // Korean
            "zh" -> "🇨🇳"  // Chinese (simplified)
            "zh-hk", "zh-tw" -> "🇹🇼"  // Chinese (traditional)
            "ru" -> "🇷🇺"  // Russian
            "fr" -> "🇫🇷"  // French
            "de" -> "🇩🇪"  // German
            "es" -> "🇪🇸"  // Spanish
            "pt-br" -> "🇧🇷"  // Portuguese (Brazil)
            "pt" -> "🇵🇹"  // Portuguese
            "it" -> "🇮🇹"  // Italian
            "pl" -> "🇵🇱"  // Polish
            "tr" -> "🇹🇷"  // Turkish
            "th" -> "🇹🇭"  // Thai
            "vi" -> "🇻🇳"  // Vietnamese
            "ar" -> "🇸🇦"  // Arabic
            "id" -> "🇮🇩"  // Indonesian
            "fi" -> "🇫🇮"  // Finnish
            "da" -> "🇩🇰"  // Danish
            "no" -> "🇳🇴"  // Norwegian
            "sv" -> "🇸🇪"  // Swedish
            "nl" -> "🇳🇱"  // Dutch
            "cs" -> "🇨🇿"  // Czech
            "hu" -> "🇭🇺"  // Hungarian
            "ro" -> "🇷🇴"  // Romanian
            "sk" -> "🇸🇰"  // Slovak
            "bg" -> "🇧🇬"  // Bulgarian
            "hr" -> "🇭🇷"  // Croatian
            "lt" -> "🇱🇹"  // Lithuanian
            "lv" -> "🇱🇻"  // Latvian
            "et" -> "🇪🇪"  // Estonian
            "sl" -> "🇸🇮"  // Slovenian
            "el" -> "🇬🇷"  // Greek
            "iw" -> "🇮🇱"  // Hebrew
            "ms" -> "🇲🇾"  // Malay
            "sw" -> "🇰🇪"  // Swahili
            "tl" -> "🇵🇭"  // Filipino
            "uk" -> "🇺🇦"  // Ukrainian
            "hi" -> "🇮🇳"  // Hindi
            "bn" -> "🇧🇩"  // Bengali
            "pa" -> "🇵🇰"  // Punjabi
            "is" -> "🇮🇸"  // Icelandic
            "ga" -> "🇮🇪"  // Irish
            "cy" -> "🇬🇧"  // Welsh
            "eu" -> "🇪🇸"  // Basque
            "ca" -> "🇪🇸"  // Catalan
            "sw" -> "🇰🇪"  // Swahili
            "am" -> "🇪🇹"  // Amharic
            "fa" -> "🇮🇷"  // Persian
            "zu" -> "🇿🇦"  // Zulu
            "xh" -> "🇿🇦"  // Xhosa
            "af" -> "🇿🇦"  // Afrikaans
            "sq" -> "🇦🇱"  // Albanian
            "mk" -> "🇲🇰"  // Macedonian
            "sr" -> "🇷🇸"  // Serbian
            "bs" -> "🇧🇦"  // Bosnian
            "tl" -> "🇵🇭"  // Tagalog
            "jw" -> "🇮🇩"  // Javanese
            else -> "🌐"  // Default - globe for unknown languages
        }
    }

    private suspend fun getReadChapters(mangaId: String): Set<String> {
        return emptySet()
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