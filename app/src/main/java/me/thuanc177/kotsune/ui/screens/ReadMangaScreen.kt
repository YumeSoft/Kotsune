package me.thuanc177.kotsune.ui.screens

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.viewmodel.ChapterDetailInfo
import me.thuanc177.kotsune.viewmodel.ChapterModel
import me.thuanc177.kotsune.viewmodel.ReadMangaViewModel
import me.thuanc177.kotsune.viewmodel.ReadingMode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.get
import kotlin.text.get

enum class ReadingMode {
    PAGED,
    CONTINUOUS,
    WEBTOON
}

data class ReadMangaUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val imageUrls: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val chapterInfo: ChapterDetailInfo? = null,
    val currentChapter: ChapterModel? = null,

    // Added fields for chapter navigation
    val availableChapters: List<ChapterModel> = emptyList(),
    val volumeInfo: String? = null,
    val chapterTitle: String? = null,
    val mangaId: String? = null,
    val mangaTitle: String? = null,

    // Reader preferences
    val dataSaver: Boolean = false,
    val autoRotateToWidePage: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.PAGED,
    val showPageProgress: Boolean = true,
    val imageScaleType: ImageScaleType = ImageScaleType.FIT_WIDTH,

    // Chapter navigation state
    val hasNextChapter: Boolean = false,
    val hasPreviousChapter: Boolean = false,

    // Reading progress
    val readProgress: Float = 0f,
    val isChapterCompleted: Boolean = false,
    val chapterId: String = "",
    val pages: List<String> = emptyList(),
    val isControlsVisible: Boolean = true
)

class ReadMangaViewModel(
    private val mangaDexAPI: MangaDexAPI,
    private val chapterId: String,
    private val chaptersList: List<ChapterModel>
) : ViewModel() {
    private val TAG = "ReadMangaViewModel"

    private val _uiState = MutableStateFlow(
        ReadMangaUiState(
            chapterId = chapterId,
            pages = emptyList(),
            isControlsVisible = true
        )
    )
    val uiState = _uiState.asStateFlow()

    // Get current chapter model
    private val currentChapter = chaptersList.find { it.id == chapterId }

    init {
        // Set initial chapter title
        currentChapter?.let { chapter ->
            _uiState.update { it.copy(
                chapterTitle = chapter.title,
                volumeInfo = chapter.volume?.let { "Volume $it" }
            )}
        }

        loadChapterPages()
    }

    fun loadChapterPages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val pages = fetchChapterPages(chapterId)

                if (pages.isEmpty()) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "No pages found for this chapter"
                    )}
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        pages = pages,
                        totalPages = pages.size,
                        currentPage = 0
                    )}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chapter pages", e)
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load chapter: ${e.message}"
                )}
            }
        }
    }

    fun setCurrentPage(page: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentPage = page) }
        }
    }

    fun nextPage() {
        val currentState = _uiState.value
        if (currentState.currentPage < currentState.pages.size - 1) {
            _uiState.update { it.copy(currentPage = it.currentPage + 1) }
        }
    }

    fun previousPage() {
        val currentState = _uiState.value
        if (currentState.currentPage > 0) {
            _uiState.update { it.copy(currentPage = it.currentPage - 1) }
        }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    // Method to navigate to next chapter
    fun loadNextChapter(): Boolean {
        currentChapter?.let { chapter ->
            // Find the index of current chapter
            val currentIndex = chaptersList.indexOfFirst { it.id == chapter.id }

            // If there is a next chapter
            if (currentIndex >= 0 && currentIndex < chaptersList.size - 1) {
                val nextChapter = chaptersList[currentIndex + 1]
                _uiState.update { it.copy(
                    chapterId = nextChapter.id,
                    chapterTitle = nextChapter.title,
                    volumeInfo = nextChapter.volume?.let { vol -> "Volume $vol" },
                    pages = emptyList(),
                    currentPage = 0,
                    isLoading = true
                )}
                loadChapterPages()
                return true
            }
        }
        return false
    }

    // Method to navigate to previous chapter
    fun loadPreviousChapter(): Boolean {
        currentChapter?.let { chapter ->
            // Find the index of current chapter
            val currentIndex = chaptersList.indexOfFirst { it.id == chapter.id }

            // If there is a previous chapter
            if (currentIndex > 0) {
                val prevChapter = chaptersList[currentIndex - 1]
                _uiState.update { it.copy(
                    chapterId = prevChapter.id,
                    chapterTitle = prevChapter.title,
                    volumeInfo = prevChapter.volume?.let { vol -> "Volume $vol" },
                    pages = emptyList(),
                    currentPage = 0,
                    isLoading = true
                )}
                loadChapterPages()
                return true
            }
        }
        return false
    }

    private suspend fun fetchChapterPages(chapterId: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.mangadex.org/at-home/server/$chapterId"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val baseUrl = jsonObject.getString("baseUrl")
                    val chapterData = jsonObject.getJSONObject("chapter")
                    val hash = chapterData.getString("hash")

                    // Try to get data quality images first, then dataSaver if not available
                    val imagesArray = if (chapterData.has("data") && chapterData.getJSONArray("data").toString().isNotEmpty()) {
                        chapterData.getJSONArray("data")
                    } else {
                        chapterData.getJSONArray("dataSaver")
                    }

                    val imageUrls = mutableListOf<String>()
                    for (i in 0 until imagesArray.length()) {
                        val fileName = imagesArray.getString(i)
                        val imageUrl = "$baseUrl/data/$hash/$fileName"
                        imageUrls.add(imageUrl)
                    }

                    // Mark chapter as read here if needed
                    // This can be connected to your repository

                    return@withContext imageUrls
                } else {
                    Log.e(TAG, "Error loading chapter, response code: ${connection.responseCode}")
                    throw Exception("Server error ${connection.responseCode}: ${connection.responseMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching chapter pages", e)
                throw e
            }
        }
    }

    class Factory(
        private val mangaDexAPI: MangaDexAPI,
        private val chapterId: String,
        private val chaptersList: List<ChapterModel>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReadMangaViewModel::class.java)) {
                return ReadMangaViewModel(mangaDexAPI, chapterId, chaptersList) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

@Composable
fun MangaPage(
    imageUrl: String,
    pageNumber: Int,
    totalPages: Int
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Page $pageNumber",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(48.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load image",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }
        )

        // Small page indicator in corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$pageNumber / $totalPages",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ChapterSelectorContent(
    chaptersList: List<ChapterModel>,
    currentChapterId: String,
    onChapterSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Group chapters by their volume
        val chaptersGroupedByVolume = remember(chaptersList) {
            chaptersList.groupBy { it.volume }
        }

        // Sort volumes
        val sortedVolumes = remember(chaptersGroupedByVolume) {
            chaptersGroupedByVolume.keys
                .sortedWith(compareBy(nullsLast()) { it?.toIntOrNull() ?: Int.MAX_VALUE })
        }

        for (volume in sortedVolumes) {
            val volumeChapters = chaptersGroupedByVolume[volume] ?: continue

            Text(
                text = volume?.let { "Volume $it" } ?: "No Volume",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Group by chapter number
            val chaptersGroupedByNumber = volumeChapters.groupBy { it.number }

            for ((chapterNumber, translations) in chaptersGroupedByNumber.toSortedMap(
                compareBy { it.toFloatOrNull() ?: Float.MAX_VALUE }
            )) {
                // For each chapter number, show the best translation
                val bestTranslation = translations.sortedWith(
                    compareBy<ChapterModel> { it.language != "en" }
                        .thenBy { it.publishedAt }
                ).firstOrNull() ?: continue

                TextButton(
                    onClick = { onChapterSelected(bestTranslation.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isCurrentChapter = bestTranslation.id == currentChapterId

                        Text(
                            text = "Chapter $chapterNumber",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentChapter)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        // Show language flag
                        Text(
                            text = bestTranslation.languageFlag ?: "🌐",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(50.dp))
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading chapter...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorView(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Failed to load chapter",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
fun PagedReaderLayout(
    uiState: ReadMangaUiState,
    currentPageIndex: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onImageLoaded: (Int, IntSize) -> Unit,
    imageScaleType: ImageScaleType
) {
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { uiState.pages.size }
    )

    // Sync pager state with external current page state
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPageIndex) {
            onPageChanged(pagerState.currentPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        if (uiState.pages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                MangaPage(
                    imageUrl = uiState.pages.getOrNull(page) ?: "",
                    contentScale = when (imageScaleType) {
                        ImageScaleType.FIT_WIDTH -> ContentScale.FillWidth
                        ImageScaleType.FIT_HEIGHT -> ContentScale.FillHeight
                        ImageScaleType.FIT_BOTH -> ContentScale.Fit
                        ImageScaleType.NO_LIMIT -> ContentScale.None
                    },
                    onImageLoaded = { size -> onImageLoaded(page, size) }
                )
            }
        }
    }
}

@Composable
fun ContinuousVerticalReaderLayout(
    uiState: ReadMangaUiState,
    onVisiblePageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onImageLoaded: (Int, IntSize) -> Unit
) {
    val listState = rememberLazyListState()
    remember { mutableStateListOf<Int>() }

    // Track which page is most visible and report it
    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo) {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            // Find the item with the most visible area
            val mostVisibleItem = visibleItems.maxByOrNull { item ->
                val visibleHeight = item.size - maxOf(0, item.offset) -
                        maxOf(0, item.offset + item.size - listState.layoutInfo.viewportEndOffset)
                visibleHeight
            }

            mostVisibleItem?.let {
                onVisiblePageChanged(it.index)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.pages.size) { index ->
                MangaPage(
                    imageUrl = uiState.pages[index],
                    contentScale = ContentScale.FillWidth,
                    onImageLoaded = { size -> onImageLoaded(index, size) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MangaPage(
    imageUrl: String,
    contentScale: ContentScale,
    onImageLoaded: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Loading placeholder
        if (imageSize == IntSize.Zero) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Manga page",
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (imageSize.height > 0) {
                        Modifier.height((imageSize.height.toFloat() / imageSize.width.toFloat() * LocalConfiguration.current.screenWidthDp).dp)
                    } else {
                        Modifier
                    }
                ),
            onSuccess = { state ->
                val painter = state.painter
                imageSize = IntSize(painter.intrinsicSize.width.toInt(), painter.intrinsicSize.height.toInt())
                onImageLoaded(imageSize)
            }
        )
    }
}

@Composable
fun BottomControls(
    currentPage: Int,
    totalPages: Int,
    currentChapter: ChapterModel?,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onMenuClick: () -> Unit,
    onChapterSelectorClick: () -> Unit,
    onPageSliderChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(bottom = if (WindowInsets.navigationBars.getBottom(LocalDensity.current) > 0)
                WindowInsets.navigationBars.getBottom(LocalDensity.current).dp else 8.dp
            )
        ) {
            // Page progress slider
            if (totalPages > 1) {
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageSliderChange(it.toInt()) },
                    valueRange = 1f..totalPages.toFloat(),
                    steps = totalPages - 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Page indicator text
                Text(
                    text = "$currentPage / $totalPages",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous chapter button
                IconButton(onClick = onPreviousChapter) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Previous page button
                IconButton(onClick = onPreviousPage) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous Page",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Chapter selector button
                OutlinedButton(
                    onClick = onChapterSelectorClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Chapter ${currentChapter?.number ?: ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Chapter"
                    )
                }

                // Menu button
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Reader Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Next page button
                IconButton(onClick = onNextPage) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next Page",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Next chapter button
                IconButton(onClick = onNextChapter) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Chapter",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderMenu(
    uiState: ReadMangaUiState,
    onDismiss: () -> Unit,
    onReadingModeChanged: (ReadingMode) -> Unit,
    onImageScaleTypeChanged: (ImageScaleType) -> Unit,
    onTogglePageProgress: () -> Unit,
    onChapterSelected: (ChapterModel) -> Unit,
    onPageSelected: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Menu header
            Text(
                text = "Reader Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Chapter info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    uiState.currentChapter?.let { chapter ->
                        Text(
                            text = "Chapter ${chapter.number}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (chapter.title?.isNotBlank() == true) {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reading mode section
            Text(
                text = "Reading Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Reading mode selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingMode.values().forEach { mode ->
                    FilterChip(
                        selected = uiState.readingMode == mode,
                        onClick = { onReadingModeChanged(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ReadingMode.PAGED -> "Paged"
                                    ReadingMode.CONTINUOUS -> "Continuous"
                                    ReadingMode.WEBTOON -> "Webtoon"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    ReadingMode.PAGED -> Icons.Default.Book
                                    ReadingMode.CONTINUOUS -> Icons.Default.ViewStream
                                    ReadingMode.WEBTOON -> Icons.Default.ViewStream
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image scale type (only show when in paged mode)
            if (uiState.readingMode == ReadingMode.PAGED) {
                Text(
                    text = "Image Scaling",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scale type selection
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImageScaleType.entries.forEach { scaleType ->
                        FilterChip(
                            selected = uiState.imageScaleType == scaleType,
                            onClick = { onImageScaleTypeChanged(scaleType) },
                            label = {
                                Text(
                                    text = when(scaleType) {
                                        ImageScaleType.FIT_WIDTH -> "Fit Width"
                                        ImageScaleType.FIT_HEIGHT -> "Fit Height"
                                        ImageScaleType.FIT_BOTH -> "Fit Screen"
                                        ImageScaleType.NO_LIMIT -> "Original Size"
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Progress display toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Page Progress",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                Switch(
                    checked = uiState.showPageProgress,
                    onCheckedChange = { onTogglePageProgress() }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            // Pages navigation
            Text(
                text = "Pages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Page grid
            val currentPageIndex = uiState.currentPage
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                items(uiState.pages.size) { index ->
                    val pageNumber = index + 1
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index == currentPageIndex)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onPageSelected(pageNumber) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$pageNumber",
                            color = if (index == currentPageIndex)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (index == currentPageIndex)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )

            // Chapters navigation
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Show a list of nearby chapters
            val currentChapterId = uiState.currentChapter?.id
            val currentChapterNumber = uiState.currentChapter?.number?.toFloatOrNull() ?: 0f
            val currentLanguage = uiState.currentChapter?.language ?: "en"

            // Group chapters by number for better organization
            val chaptersGroupedByNumber = uiState.availableChapters
                .filter {
                    val chapterNum = it.number.toFloatOrNull() ?: 0f
                    (chapterNum >= currentChapterNumber - 5 &&
                            chapterNum <= currentChapterNumber + 5)
                }
                .groupBy { it.number }
                .toSortedMap(compareByDescending { it.toFloatOrNull() ?: 0f })

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                chaptersGroupedByNumber.forEach { (_, chapters) ->
                    val preferredChapters = chapters.filter { it.language == currentLanguage }
                    val chapterToShow = preferredChapters.firstOrNull() ?: chapters.firstOrNull()

                    chapterToShow?.let { chapter ->
                        item {
                            ChapterListItem(
                                chapter = chapter,
                                isCurrentChapter = chapter.id == currentChapterId,
                                hasMultipleTranslations = chapters.size > 1,
                                onClick = { onChapterSelected(chapter) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadMangaScreen(
    navController: NavController,
    chapterId: String,
    mangaDexAPI: MangaDexAPI,
    chaptersList: List<ChapterModel>
) {
    // Get context once at the Composable level
    val context = LocalContext.current
    // State management
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentChapter by remember { mutableStateOf<ChapterModel?>(null) }
    var pageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPageIndex by remember { mutableStateOf(0) }

    // UI control states
    var showControls by remember { mutableStateOf(true) }
    var showChapterSelector by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showProgressBar by remember { mutableStateOf(true) }

    // Reading preferences
    var scaleType by remember { mutableStateOf(ImageScaleType.FIT_BOTH) }
    var continuousScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(chapterId, chaptersList) {
        currentChapter = chaptersList.find { it.id == chapterId }
        if (currentChapter != null) {
            // Load chapter pages
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchChapterPages(mangaDexAPI, chapterId)
                }
                if (result != null) {
                    pageUrls = result
                    isLoading = false
                } else {
                    errorMessage = "Failed to load chapter images"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error occurred"
            }

            // Check page ratio to determine default view mode
            if (pageUrls.isNotEmpty()) {
                scope.launch {
                    val firstPageRatio = getImageRatio(
                        imageUrl = pageUrls.first(),
                        context = context
                    )
                    if (firstPageRatio > 2.5f) { // Portrait/vertical manga detection
                        continuousScrolling = true
                        scaleType = ImageScaleType.FIT_WIDTH
                    }
                }
            }
        } else {
            errorMessage = "Chapter not found"
        }
    }

    // Auto-hide controls after delay
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            LoadingView(modifier = Modifier.fillMaxSize())
        } else if (errorMessage != null) {
            ErrorView(
                error = errorMessage!!,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                fetchChapterPages(mangaDexAPI, chapterId)
                            }
                            if (result != null) {
                                pageUrls = result
                                isLoading = false
                            } else {
                                errorMessage = "Failed to load chapter images"
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Unknown error occurred"
                        }
                    }
                }
            )
        } else {
            // Content - either paged or continuous scrolling
            if (continuousScrolling) {
                ContinuousReaderView(
                    pageUrls = pageUrls,
                    scaleType = scaleType,
                    onTap = { showControls = !showControls },
                    onPageVisible = { index, isVisible ->
                        if (isVisible && index > currentPageIndex) {
                            currentPageIndex = index
                        }
                    }
                )
            } else {
                PagedReaderView(
                    pageUrls = pageUrls,
                    currentPageIndex = currentPageIndex,
                    scaleType = scaleType,
                    onPageChange = { currentPageIndex = it },
                    onTap = { showControls = !showControls }
                )
            }

            // Read progress indicator
            if (showProgressBar && pageUrls.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { (currentPageIndex + 1).toFloat() / pageUrls.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .alpha(0.7f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            }

            // Bottom controls bar that fades in/out
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // In ReadMangaScreen composable where you're using ReaderControlBar:
                ReaderControlBar(
                    currentPage = currentPageIndex + 1,
                    totalPages = pageUrls.size,
                    onPreviousPage = {
                        if (currentPageIndex > 0) {
                            currentPageIndex--
                        }
                    },
                    onNextPage = {
                        if (currentPageIndex < pageUrls.size - 1) {
                            currentPageIndex++
                        } else {
                            // Handle next chapter navigation using non-composable logic
                            val nextChapter = findNextChapter(currentChapter, chaptersList)
                            if (nextChapter != null) {
                                navController.navigate(
                                    "read_manga/${nextChapter.id}?from=reader"
                                ) {
                                    popUpTo("read_manga/${chapterId}") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "No next chapter available",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onShowChapterSelector = { showChapterSelector = true },
                    onShowMenu = { showSettingsMenu = true },
                    canGoBack = currentPageIndex > 0,
                    canGoForward = currentPageIndex < pageUrls.size - 1 ||
                            findNextChapter(currentChapter, chaptersList) != null,
                    chapterTitle = currentChapter?.title ?: "Chapter ${currentChapter?.number}"
                )
            }

            // Top navigation bar (back button and title)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = currentChapter?.title ?: "Chapter ${currentChapter?.number}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                )
            }

            // Chapter selector dialog
            if (showChapterSelector) {
                ChapterSelectorDialog(
                    allChapters = chaptersList,
                    currentChapterId = chapterId,
                    onChapterSelected = { selectedChapter ->
                        // Navigate to selected chapter
                        navController.navigate(
                            "read_manga/${selectedChapter.id}/${selectedChapter.language}"
                        ) {
                            popUpTo("read_manga/${chapterId}/${currentChapter?.language}") {
                                inclusive = true
                            }
                        }
                        showChapterSelector = false
                    },
                    onDismiss = { showChapterSelector = false }
                )
            }

            // Settings menu dialog
            if (showSettingsMenu) {
                ReaderSettingsDialog(
                    currentScaleType = scaleType,
                    isContinuousScrolling = continuousScrolling,
                    showProgressBar = showProgressBar,
                    onScaleTypeChange = { scaleType = it },
                    onScrollingModeChange = {
                        continuousScrolling = it
                        // Reset to page 1 when switching modes
                        currentPageIndex = 0
                    },
                    onProgressBarToggle = { showProgressBar = it },
                    onDismiss = { showSettingsMenu = false },
                    currentChapter = currentChapter,
                    allChapters = chaptersList,
                    onNavigateToChapter = { selectedChapter ->
                        navController.navigate(
                            "read_manga/${selectedChapter.id}/${selectedChapter.language}"
                        ) {
                            popUpTo("read_manga/${chapterId}/${currentChapter?.language}") {
                                inclusive = true
                            }
                        }
                        showSettingsMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.navigateToNextChapter(
    currentChapter: ChapterModel?,
    allChapters: List<ChapterModel>,
    navController: NavController
) {
    val nextChapter = findNextChapter(currentChapter, allChapters)
    if (nextChapter != null) {
        navController.navigate(
            "read_manga/${nextChapter.id}/${nextChapter.language}"
        ) {
            popUpTo("read_manga/${currentChapter?.id}/${currentChapter?.language}") {
                inclusive = true
            }
        }
    } else {
        // No next chapter available
        Toast.makeText(
            LocalContext.current,
            "No next chapter available",
            Toast.LENGTH_SHORT
        ).show()
    }
}

enum class ImageScaleType {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_BOTH,
    NO_LIMIT
}

// Continuous vertical scrolling reader
@Composable
fun ContinuousReaderView(
    pageUrls: List<String>,
    scaleType: ImageScaleType,
    onTap: () -> Unit,
    onPageVisible: (Int, Boolean) -> Unit
) {
    val scrollState = rememberLazyListState()

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        itemsIndexed(pageUrls) { index, url ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        // Detect when page is visible in viewport
                        val visibleRect = Rect()
                        val isVisible = coordinates.boundsInWindow().let { bounds ->
                            visibleRect.set(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt()
                            )
                            visibleRect.height() > coordinates.size.height / 2
                        }
                        onPageVisible(index, isVisible)
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Page ${index + 1}",
                    contentScale = when (scaleType) {
                        ImageScaleType.FIT_WIDTH -> ContentScale.FillWidth
                        ImageScaleType.NO_LIMIT -> ContentScale.None
                        else -> ContentScale.FillWidth // Both not applicable in continuous
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Page number indicator
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(topStart = 8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// Paged reader with horizontal swiping
@Composable
fun PagedReaderView(
    pageUrls: List<String>,
    currentPageIndex: Int,
    scaleType: ImageScaleType,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { pageUrls.size }
    )

    // Sync pager with external state
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Sync external state with pager
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPageIndex) {
            onPageChange(pagerState.currentPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) { page ->
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(pageUrls[page])
                    .crossfade(true)
                    .build(),
                contentDescription = "Page ${page + 1}",
                contentScale = when (scaleType) {
                    ImageScaleType.FIT_WIDTH -> ContentScale.FillWidth
                    ImageScaleType.FIT_HEIGHT -> ContentScale.FillHeight
                    ImageScaleType.FIT_BOTH -> ContentScale.Fit
                    ImageScaleType.NO_LIMIT -> ContentScale.None
                },
                modifier = Modifier.fillMaxSize()
            )

            // Page number indicator
            Text(
                text = "${page + 1}/${pageUrls.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(topStart = 8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ReaderControlBar(
    currentPage: Int,
    totalPages: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onShowChapterSelector: () -> Unit,
    onShowMenu: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    chapterTitle: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous page button
            IconButton(
                onClick = onPreviousPage,
                enabled = canGoBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Page",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Chapter title or page indicator
            Text(
                text = "$currentPage / $totalPages - $chapterTitle",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable { onShowChapterSelector() }
            )

            // Next page button
            IconButton(
                onClick = onNextPage,
                enabled = canGoForward
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Page",
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Settings menu button
            IconButton(onClick = onShowMenu) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ChapterSelectorDialog(
    allChapters: List<ChapterModel>,
    currentChapterId: String,
    onChapterSelected: (ChapterModel) -> Unit,
    onDismiss: () -> Unit
) {
    // Find the current chapter
    allChapters.find { it.id == currentChapterId }

    // Group chapters by their number for easier selection
    val groupedChapters = remember(allChapters) {
        allChapters.groupBy { it.number }
    }

    // Sort chapter numbers in descending order
    val sortedChapterNumbers = remember(groupedChapters) {
        groupedChapters.keys.sortedWith(
            compareByDescending { it.toFloatOrNull() ?: Float.MIN_VALUE }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Chapter",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                // Chapter list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(sortedChapterNumbers.size) { index ->
                        val chapterNumber = sortedChapterNumbers[index]
                        groupedChapters[chapterNumber] ?: emptyList()
                        val chaptersForNumber = groupedChapters[chapterNumber] ?: emptyList()
                        val hasMultipleTranslations = chaptersForNumber.size > 1

                        // If this chapter has multiple translations, add a header
                        if (hasMultipleTranslations) {
                            Text(
                                text = "Chapter $chapterNumber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Show all translations for this chapter number
                        chaptersForNumber.forEach { chapter ->
                            val isCurrentChapter = chapter.id == currentChapterId

                            ChapterListItem(
                                chapter = chapter,
                                isCurrentChapter = isCurrentChapter,
                                hasMultipleTranslations = hasMultipleTranslations,
                                onClick = { onChapterSelected(chapter) },
                                isSelected = isCurrentChapter
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderSettingsDialog(
    currentScaleType: ImageScaleType,
    isContinuousScrolling: Boolean,
    showProgressBar: Boolean,
    onScaleTypeChange: (ImageScaleType) -> Unit,
    onScrollingModeChange: (Boolean) -> Unit,
    onProgressBarToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    currentChapter: ChapterModel?,
    allChapters: List<ChapterModel>,
    onNavigateToChapter: (ChapterModel) -> Unit
) {
    // Find previous and next chapters
    val previousChapter = findPreviousChapter(currentChapter, allChapters)
    val nextChapter = findNextChapter(currentChapter, allChapters)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = "Reader Settings",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Chapter navigation
                Text(
                    text = "Chapter Navigation",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = {
                            previousChapter?.let { onNavigateToChapter(it) }
                        },
                        enabled = previousChapter != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Previous")
                    }

                    OutlinedButton(
                        onClick = {
                            nextChapter?.let { onNavigateToChapter(it) }
                        },
                        enabled = nextChapter != null
                    ) {
                        Text("Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Display Mode
                Text(
                    text = "Display Mode",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !isContinuousScrolling,
                        onClick = { onScrollingModeChange(false) }
                    )
                    Text(
                        text = "Paged",
                        modifier = Modifier
                            .clickable { onScrollingModeChange(false) }
                            .padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(
                        selected = isContinuousScrolling,
                        onClick = { onScrollingModeChange(true) }
                    )
                    Text(
                        text = "Continuous",
                        modifier = Modifier
                            .clickable { onScrollingModeChange(true) }
                            .padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Scale Type
                Text(
                    text = "Image Scale Type",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Conditionally disable options that don't apply to continuous scrolling
                    val options = if (isContinuousScrolling) {
                        listOf(
                            ImageScaleType.FIT_WIDTH to "Fit Width",
                            ImageScaleType.NO_LIMIT to "No Limit"
                        )
                    } else {
                        listOf(
                            ImageScaleType.FIT_WIDTH to "Fit Width",
                            ImageScaleType.FIT_HEIGHT to "Fit Height",
                            ImageScaleType.FIT_BOTH to "Fit Screen",
                            ImageScaleType.NO_LIMIT to "No Limit"
                        )
                    }

                    options.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onScaleTypeChange(type) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentScaleType == type,
                                onClick = { onScaleTypeChange(type) }
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                // Progress Bar Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProgressBarToggle(!showProgressBar) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showProgressBar,
                        onCheckedChange = { onProgressBarToggle(it) }
                    )
                    Text(
                        text = "Show Progress Bar",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ChapterListItem(
    chapter: ChapterModel,
    isSelected: Boolean = false,
    isCurrentChapter: Boolean = false,
    hasMultipleTranslations: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter number and title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chapter ${chapter.number}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )

                if (chapter.title.isNotBlank()) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Language indicator
            Text(
                text = getLanguageName(chapter.language),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Read indicator
            if (chapter.isRead) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Refactored function that doesn't use Composable functions directly
suspend fun getImageRatio(imageUrl: String, context: Context): Float {
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(Size.ORIGINAL)
                .build()

            val result = context.imageLoader.execute(request)
            val drawable = result.drawable

            if (drawable != null) {
                drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth.toFloat()
            } else {
                1.4f // Default ratio for manga pages
            }
        } catch (e: Exception) {
            Log.e("ReadMangaScreen", "Error getting image ratio: ${e.message}", e)
            1.4f // Default ratio
        }
    }
}

fun getLanguageName(languageCode: String): String {
    return when (languageCode) {
        "en" -> "EN"
        "ja" -> "JP"
        "ko" -> "KR"
        "zh" -> "CN"
        "fr" -> "FR"
        "es" -> "ES"
        "de" -> "DE"
        "it" -> "IT"
        "ru" -> "RU"
        "pt-br" -> "BR"
        else -> languageCode.uppercase()
    }
}

fun findNextChapter(currentChapter: ChapterModel?, allChapters: List<ChapterModel>): ChapterModel? {
    if (currentChapter == null) return null
    val currentNumber = currentChapter.number.toFloatOrNull() ?: return null

    // First try to find next chapter from same group
    val nextFromSameGroup = allChapters
        .filter {
            it.translatorGroup == currentChapter.translatorGroup &&
                    it.number.toFloatOrNull()?.let { num -> num > currentNumber } == true
        }
        .minByOrNull { it.number.toFloatOrNull() ?: Float.MAX_VALUE }

    if (nextFromSameGroup != null) return nextFromSameGroup

    // Try from same language
    val nextFromSameLanguage = allChapters
        .filter {
            it.language == currentChapter.language &&
                    it.number.toFloatOrNull()?.let { num -> num > currentNumber } == true
        }
        .minByOrNull { it.number.toFloatOrNull() ?: Float.MAX_VALUE }

    if (nextFromSameLanguage != null) return nextFromSameLanguage

    // Try any chapter
    return allChapters
        .filter { it.number.toFloatOrNull()?.let { num -> num > currentNumber } == true }
        .minByOrNull { it.number.toFloatOrNull() ?: Float.MAX_VALUE }
}

fun findPreviousChapter(currentChapter: ChapterModel?, allChapters: List<ChapterModel>): ChapterModel? {
    if (currentChapter == null) return null
    val currentNumber = currentChapter.number.toFloatOrNull() ?: return null

    // First try to find previous chapter from same group
    val prevFromSameGroup = allChapters
        .filter {
            it.translatorGroup == currentChapter.translatorGroup &&
                    it.number.toFloatOrNull()?.let { num -> num < currentNumber } == true
        }
        .maxByOrNull { it.number.toFloatOrNull() ?: Float.MIN_VALUE }

    if (prevFromSameGroup != null) return prevFromSameGroup

    // Try from same language
    val prevFromSameLanguage = allChapters
        .filter {
            it.language == currentChapter.language &&
                    it.number.toFloatOrNull()?.let { num -> num < currentNumber } == true
        }
        .maxByOrNull { it.number.toFloatOrNull() ?: Float.MIN_VALUE }

    if (prevFromSameLanguage != null) return prevFromSameLanguage

    // Try any chapter
    return allChapters
        .filter { it.number.toFloatOrNull()?.let { num -> num < currentNumber } == true }
        .maxByOrNull { it.number.toFloatOrNull() ?: Float.MIN_VALUE }
}

suspend fun fetchChapterPages(mangaDexAPI: MangaDexAPI, chapterId: String): List<String>? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.mangadex.org/at-home/server/$chapterId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val baseUrl = jsonObject.getString("baseUrl")
                    val chapterData = jsonObject.getJSONObject("chapter")
                    val hash = chapterData.getString("hash")

                    // Use data-saver images for mobile to reduce data usage
                    // Can be configurable in app settings later
                    val useDataSaver = true

                    val images = if (useDataSaver) {
                        val dataSaver = chapterData.getJSONArray("dataSaver")
                        List(dataSaver.length()) { i ->
                            "$baseUrl/data-saver/$hash/${dataSaver.getString(i)}"
                        }
                    } else {
                        val data = chapterData.getJSONArray("data")
                        List(data.length()) { i ->
                            "$baseUrl/data/$hash/${data.getString(i)}"
                        }
                    }

                    connection.disconnect()
                    return@withContext images
                }
            }

            connection.disconnect()
            null
        } catch (e: Exception) {
            Log.e("ReadMangaScreen", "Error fetching chapter pages: ${e.message}", e)
            null
        }
    }
}