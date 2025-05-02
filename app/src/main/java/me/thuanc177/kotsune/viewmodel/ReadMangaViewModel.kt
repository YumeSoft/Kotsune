package me.thuanc177.kotsune.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.size.Size
import coil.request.ImageRequest
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit

class ReadMangaViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val chapterId: String,
    private val availableChapters: List<ChapterModel>,
    private val appConfig: AppConfig  // Add this parameter
) : ViewModel() {
    private val TAG = "ReadMangaViewModel"

    // UI State
    private val _uiState = MutableStateFlow(ReadMangaUiState())
    val uiState: StateFlow<ReadMangaUiState> = _uiState.asStateFlow()

    // Currently viewed chapter
    private var currentChapter = availableChapters.find { it.id == chapterId }

    // Image display options
    var displayMode by mutableStateOf(
        ImageDisplayMode.valueOf(appConfig.imageScaleType)
    )

    var viewingMode by mutableStateOf(
        ReadingMode.valueOf(appConfig.readerMode)
    )

    var showProgressBar by mutableStateOf(appConfig.showReaderProgressBar)

    // ... existing code

    fun changeDisplayMode(mode: ImageDisplayMode) {
        displayMode = mode
        appConfig.imageScaleType = mode.name  // Save to persistent storage
    }

    fun changeViewingMode(mode: ReadingMode) {
        viewingMode = mode
        appConfig.readerMode = mode.name  // Save to persistent storage
    }

    fun toggleProgressBar() {
        showProgressBar = !showProgressBar
        appConfig.showReaderProgressBar = showProgressBar  // Save to persistent storage
    }

    // Track image aspect ratios to determine default viewing mode
    private val imageRatios = mutableListOf<Float>()

    init {
        loadChapter(chapterId)
    }

    fun loadChapter(chapterId: String) {
        val targetChapter = availableChapters.find { it.id == chapterId }
        if (targetChapter == null) {
            _uiState.update { it.copy(error = "Chapter not found") }
            return
        }

        currentChapter = targetChapter
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                chapterInfo = null,
                currentChapter = targetChapter,
                currentPage = 0,
                imageUrls = emptyList()
            )
        }

        viewModelScope.launch {
            try {
                // Fetch chapter info
                val chapterInfo = fetchChapterInfo(chapterId)
                if (chapterInfo == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load chapter info") }
                    return@launch
                }

                // Fetch image URLs
                val (baseUrl, hash, dataUrls, dataSaverUrls) = fetchChapterImages(chapterId)
                if (baseUrl == null || hash == null || dataUrls == null || dataSaverUrls == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load chapter images") }
                    return@launch
                }

                // Build high-quality image URLs
                val useHighQuality = true // Could be user setting
                val imageList = if (useHighQuality) {
                    dataUrls.map { "$baseUrl/data/$hash/$it" }
                } else {
                    dataSaverUrls.map { "$baseUrl/data-saver/$hash/$it" }
                }

                // Update chapter read status
                markChapterAsRead(targetChapter)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        chapterInfo = chapterInfo,
                        imageUrls = imageList,
                        totalPages = imageList.size,
                        currentChapter = targetChapter
                    )
                }

                // Analyze first few images to determine optimal viewing mode
                detectOptimalViewingMode(imageList)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = "Error: ${e.message}") }
            }
        }
    }

    private suspend fun fetchChapterInfo(chapterId: String): ChapterDetailInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.mangadex.org/chapter/$chapterId?includes[]=scanlation_group&includes[]=manga&includes[]=user")
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            if (jsonResponse.getString("result") != "ok") {
                return@withContext null
            }

            val data = jsonResponse.getJSONObject("data")
            val attributes = data.getJSONObject("attributes")
            val relationships = data.getJSONArray("relationships")

            // Extract basic info
            val title = attributes.optString("title", "")
            val volume = attributes.optString("volume", "")
            val chapterNumber = attributes.optString("chapter", "")
            val pages = attributes.optInt("pages", 0)
            val language = attributes.optString("translatedLanguage", "")
            val publishedAt = attributes.optString("publishAt", "")

            // Extract related entities
            var mangaId: String? = null
            var mangaTitle: String? = null
            var scanlationGroupId: String? = null
            var scanlationGroupName: String? = null
            var uploaderId: String? = null
            var uploaderName: String? = null

            for (i in 0 until relationships.length()) {
                val relation = relationships.getJSONObject(i)
                val relType = relation.getString("type")

                when (relType) {
                    "manga" -> {
                        mangaId = relation.getString("id")
                        if (relation.has("attributes")) {
                            val mangaAttributes = relation.getJSONObject("attributes")
                            if (mangaAttributes.has("title")) {
                                val titleObj = mangaAttributes.getJSONObject("title")
                                mangaTitle = titleObj.optString("en") ?: titleObj.keys().asSequence()
                                    .firstOrNull()?.let { titleObj.getString(it) }
                            }
                        }
                    }
                    "scanlation_group" -> {
                        scanlationGroupId = relation.getString("id")
                        if (relation.has("attributes")) {
                            val groupAttributes = relation.getJSONObject("attributes")
                            scanlationGroupName = groupAttributes.optString("name")
                        }
                    }
                    "user" -> {
                        uploaderId = relation.getString("id")
                        if (relation.has("attributes")) {
                            val userAttributes = relation.getJSONObject("attributes")
                            uploaderName = userAttributes.optString("username")
                        }
                    }
                }
            }

            return@withContext ChapterDetailInfo(
                id = chapterId,
                title = title,
                volume = volume,
                chapterNumber = chapterNumber,
                pages = pages,
                language = language,
                publishedAt = publishedAt,
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                scanlationGroupId = scanlationGroupId,
                scanlationGroupName = scanlationGroupName,
                uploaderId = uploaderId,
                uploaderName = uploaderName
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter info: ${e.message}", e)
            return@withContext null
        }
    }

    private suspend fun fetchChapterImages(chapterId: String): ChapterImages = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.mangadex.org/at-home/server/$chapterId")
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            if (jsonResponse.getString("result") != "ok") {
                return@withContext ChapterImages(null, null, null, null)
            }

            val baseUrl = jsonResponse.getString("baseUrl")
            val chapterData = jsonResponse.getJSONObject("chapter")
            val hash = chapterData.getString("hash")

            val dataArray = chapterData.getJSONArray("data")
            val dataUrls = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                dataUrls.add(dataArray.getString(i))
            }

            val dataSaverArray = chapterData.getJSONArray("dataSaver")
            val dataSaverUrls = mutableListOf<String>()
            for (i in 0 until dataSaverArray.length()) {
                dataSaverUrls.add(dataSaverArray.getString(i))
            }

            return@withContext ChapterImages(baseUrl, hash, dataUrls, dataSaverUrls)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapter images: ${e.message}", e)
            return@withContext ChapterImages(null, null, null, null)
        }
    }

    private fun markChapterAsRead(chapter: ChapterModel) {
        // TODO: Implement proper read status tracking with local database
        // For now we'll just update the UI state
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // In a real implementation, save this to a database
                // For now we're just updating the in-memory model
                chapter.isRead = true
            } catch (e: Exception) {
                Log.e(TAG, "Error marking chapter as read: ${e.message}", e)
            }
        }
    }

    fun navigateToPage(pageIndex: Int) {
        val currentState = _uiState.value
        if (pageIndex in 0 until currentState.totalPages) {
            _uiState.update { it.copy(currentPage = pageIndex) }
        }
    }

    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.currentPage < currentState.totalPages - 1) {
            _uiState.update { it.copy(currentPage = currentState.currentPage + 1) }
        } else {
            // At the last page, attempt to move to next chapter
            nextChapter()
        }
    }

    fun previousPage() {
        val currentState = _uiState.value
        if (currentState.currentPage > 0) {
            _uiState.update { it.copy(currentPage = currentState.currentPage - 1) }
        } else {
            // At the first page, attempt to move to previous chapter
            previousChapter()
        }
    }

    fun nextChapter() {
        val nextChap = findNextChapter()
        if (nextChap != null) {
            loadChapter(nextChap.id)
        }
    }

    fun previousChapter() {
        val prevChap = findPreviousChapter()
        if (prevChap != null) {
            loadChapter(prevChap.id)
        }
    }

    internal fun findNextChapter(): ChapterModel? {
        val current = currentChapter ?: return null
        val currentNumber = current.number.toFloatOrNull() ?: return null
        val currentLang = current.language
        val currentGroup = current.translatorGroup

        // Sort available chapters for consistent selection
        val sortedChapters = availableChapters.sortedWith(
            compareBy<ChapterModel> { it.number.toFloatOrNull() ?: Float.MAX_VALUE }
                .thenByDescending { it.publishedAt } // Prefer newer if same number
        )

        // 1. Same Group, Next Number
        val nextSameGroup = sortedChapters.find {
            it.translatorGroup == currentGroup &&
            (it.number.toFloatOrNull() ?: Float.MIN_VALUE) > currentNumber
        }
        if (nextSameGroup != null) return nextSameGroup

        // 2. Same Language, Next Number (different group)
        val nextSameLang = sortedChapters.find {
            it.language == currentLang &&
            it.translatorGroup != currentGroup && // Ensure different group
            (it.number.toFloatOrNull() ?: Float.MIN_VALUE) > currentNumber
        }
        if (nextSameLang != null) return nextSameLang

        // 3. English Language, Next Number (if current is not English)
        if (currentLang != "en") {
            val nextEnglish = sortedChapters.find {
                it.language == "en" &&
                (it.number.toFloatOrNull() ?: Float.MIN_VALUE) > currentNumber
            }
            if (nextEnglish != null) return nextEnglish
        }

        // 4. Any Other Language, Next Number
        val nextAnyLang = sortedChapters.find {
            it.language != currentLang && // Exclude current language (already checked)
            it.language != "en" && // Exclude English (already checked if applicable)
            (it.number.toFloatOrNull() ?: Float.MIN_VALUE) > currentNumber
        }
        return nextAnyLang // Can be null if no next chapter found
    }

    internal fun findPreviousChapter(): ChapterModel? {
        val current = currentChapter ?: return null
        val currentNumber = current.number.toFloatOrNull() ?: return null
        val currentLang = current.language
        val currentGroup = current.translatorGroup

        // Sort available chapters descending for finding previous
        val sortedChapters = availableChapters.sortedWith(
            compareByDescending<ChapterModel> { it.number.toFloatOrNull() ?: Float.MIN_VALUE }
                .thenByDescending { it.publishedAt } // Prefer newer if same number
        )

        // 1. Same Group, Previous Number
        val prevSameGroup = sortedChapters.find {
            it.translatorGroup == currentGroup &&
            (it.number.toFloatOrNull() ?: Float.MAX_VALUE) < currentNumber
        }
        if (prevSameGroup != null) return prevSameGroup

        // 2. Same Language, Previous Number (different group)
        val prevSameLang = sortedChapters.find {
            it.language == currentLang &&
            it.translatorGroup != currentGroup && // Ensure different group
            (it.number.toFloatOrNull() ?: Float.MAX_VALUE) < currentNumber
        }
        if (prevSameLang != null) return prevSameLang

        // 3. English Language, Previous Number (if current is not English)
        if (currentLang != "en") {
            val prevEnglish = sortedChapters.find {
                it.language == "en" &&
                (it.number.toFloatOrNull() ?: Float.MAX_VALUE) < currentNumber
            }
            if (prevEnglish != null) return prevEnglish
        }

        // 4. Any Other Language, Previous Number
        val prevAnyLang = sortedChapters.find {
            it.language != currentLang && // Exclude current language
            it.language != "en" && // Exclude English
            (it.number.toFloatOrNull() ?: Float.MAX_VALUE) < currentNumber
        }
        return prevAnyLang // Can be null
    }

    private suspend fun detectOptimalViewingMode(imageUrls: List<String>) = withContext(Dispatchers.IO) {
        // Only check a few images to determine the typical aspect ratio
        try {
            val samplesToCheck = minOf(3, imageUrls.size)
            imageRatios.clear()

            for (i in 0 until samplesToCheck) {
                val url = imageUrls[i]
                try {
                    // Open connection to get image dimensions
                    val conn = URL(url).openConnection()
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.connect()

                    if (conn is java.net.HttpURLConnection && conn.responseCode == 200) {
                        // We only need to check if the image is very tall
                        // If ratio > 2.5 (height more than 2.5x width), it's likely a webtoon/manhua format
                        val contentLength = conn.contentLength
                        if (contentLength > 0) {
                            // We don't actually need to download the full image,
                            // just check if it's a format that's typically used for webtoons
                            if (url.endsWith(".jpg", ignoreCase = true) ||
                                url.endsWith(".jpeg", ignoreCase = true) ||
                                url.endsWith(".png", ignoreCase = true)) {

                                // For simplicity, we'll use the file extension to guess if it's a webtoon
                                // In a real app, you'd analyze the actual image dimensions

                                // This is a simplified heuristic - in a real app you'd actually
                                // analyze the image dimensions using BitmapFactory or similar
                                if (url.contains("webtoon") || url.contains("manhua") || url.contains("manhwa")) {
                                    imageRatios.add(3.0f) // Assume a tall ratio for webtoon/manhua/manhwa keywords
                                } else {
                                    imageRatios.add(1.4f) // Assume a typical manga page ratio
                                }
                            }
                        }
                    }
                    conn.inputStream?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error checking image ratio: ${e.message}")
                }
            }

            // If average ratio is > 2.0, default to continuous mode
            val avgRatio = if (imageRatios.isNotEmpty()) imageRatios.average().toFloat() else 1.4f
            if (avgRatio > 2.0f) {
                withContext(Dispatchers.Main) {
                    viewingMode = ReadingMode.CONTINUOUS
                    displayMode = ImageDisplayMode.FIT_WIDTH
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting optimal viewing mode: ${e.message}")
        }
    }

    fun getPreloadImageRequests(context: Context): List<ImageRequest> {
        val currentState = _uiState.value
        val currentPage = currentState.currentPage
        val imageUrls = currentState.imageUrls

        if (imageUrls.isEmpty()) return emptyList()

        val preloadRequests = mutableListOf<ImageRequest>()

        // Preload 4 pages ahead and 4 pages behind (expanded from 3 ahead/1 behind)
        val startIdx = maxOf(0, currentPage - 4)
        val endIdx = minOf(imageUrls.size - 1, currentPage + 4)

        for (i in startIdx..endIdx) {
            if (i != currentPage && i < imageUrls.size) {
                val request = ImageRequest.Builder(context)
                    .data(imageUrls[i])
                    .size(Size.ORIGINAL)
                    .build()
                preloadRequests.add(request)
            }
        }

        return preloadRequests
    }

    fun preloadImages(context: Context) {
        val requests = getPreloadImageRequests(context)
        val imageLoader = context.imageLoader

        requests.forEach { request ->
            imageLoader.enqueue(request)
        }
    }

    /**
     * Preloads images and detects their aspect ratios to automatically determine the optimal viewing mode.
     * If multiple images have a height-to-width ratio greater than 2:1, switches to continuous reading mode.
     *
     * @param context The Android context for accessing the image loader
     */
    fun preloadImagesAndDetectRatio(context: Context) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.imageUrls.isEmpty()) return@launch

            // Number of images to analyze for ratio detection (check more pages but skip the first)
            val startPage = 1 // Skip the first page (often credits)
            val imagesToAnalyze = minOf(4, currentState.imageUrls.size - startPage)
            var tallImagesCount = 0

            Log.d(TAG, "Analyzing ${imagesToAnalyze} images for ratio detection")

            // Use Coil to load and analyze multiple images
            for (i in startPage until startPage + imagesToAnalyze) {
                if (i >= currentState.imageUrls.size) break

                try {
                    val imageUrl = currentState.imageUrls[i]
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(Size.ORIGINAL)
                        .allowHardware(false) // Need pixel access for dimensions
                        .listener(
                            onSuccess = { _, result ->
                                val drawable = result.drawable
                                val width = drawable.intrinsicWidth
                                val height = drawable.intrinsicHeight

                                if (width > 0) {
                                    val ratio = height.toFloat() / width
                                    Log.d(TAG, "Image $i ratio: $ratio (${height}x${width})")

                                    // Count images with ratio > 2.0 (tall images typical for webtoons)
                                    if (ratio > 2.0f) {
                                        tallImagesCount++
                                        Log.d(TAG, "Found tall image #$tallImagesCount")
                                    }

                                    // If we find 2 or more tall images, switch to continuous mode
                                    if (tallImagesCount >= 2) {
                                        Log.d(TAG, "Switching to continuous mode based on image ratios")
                                        viewModelScope.launch {
                                            withContext(Dispatchers.Main) {
                                                viewingMode = ReadingMode.CONTINUOUS
                                                displayMode = ImageDisplayMode.FIT_WIDTH
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        .build()

                    // Execute request and await completion
                    context.imageLoader.enqueue(request)

                    // Wait a short time between requests to avoid overwhelming the system
                    delay(100)
                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing image: ${e.message}", e)
                }
            }
        }
    }
}

// UI state for the ReadManga screen
data class ReadMangaUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val imageUrls: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val chapterInfo: ChapterDetailInfo? = null,
    val currentChapter: ChapterModel? = null
)

// Detailed chapter info model
data class ChapterDetailInfo(
    val id: String,
    val title: String,
    val volume: String,
    val chapterNumber: String,
    val pages: Int,
    val language: String,
    val publishedAt: String,
    val mangaId: String?,
    val mangaTitle: String?,
    val scanlationGroupId: String?,
    val scanlationGroupName: String?,
    val uploaderId: String?,
    val uploaderName: String?
) {
    fun getFormattedPublishDate(): String {
        return try {
            val instant = Instant.parse(publishedAt)
            val now = Instant.now()

            val days = ChronoUnit.DAYS.between(instant, now)

            when {
                days > 365 -> "${days / 365} years ago"
                days > 30 -> "${days / 30} months ago"
                days > 0 -> "$days days ago"
                else -> {
                    val hours = ChronoUnit.HOURS.between(instant, now)
                    if (hours > 0) "$hours hours ago" else "Recently"
                }
            }
        } catch (e: Exception) {
            "Unknown date"
        }
    }
}

// Helper class for chapter images
data class ChapterImages(
    val baseUrl: String?,
    val hash: String?,
    val dataUrls: List<String>?,
    val dataSaverUrls: List<String>?
)

// Image display modes
enum class ImageDisplayMode {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_BOTH,
    NO_LIMIT
}

// Viewing modes
enum class ReadingMode {
    PAGED,      // One page at a time
    CONTINUOUS,  // All pages in a scrollable view
    WEBTOON
}
