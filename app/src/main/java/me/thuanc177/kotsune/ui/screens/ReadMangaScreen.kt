package me.thuanc177.kotsune.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.viewmodel.ChapterModel
import me.thuanc177.kotsune.viewmodel.ImageDisplayMode
import me.thuanc177.kotsune.viewmodel.ReadMangaViewModel
import me.thuanc177.kotsune.viewmodel.ReadingMode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

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
fun SimpleReaderSettingsDialog(
    currentReadingMode: ReadingMode,
    currentScaleType: ImageDisplayMode,
    showProgressBar: Boolean,
    onReadingModeChange: (ReadingMode) -> Unit,
    onScaleTypeChange: (ImageDisplayMode) -> Unit,
    onProgressBarToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                // Reading Mode
                Text(
                    text = "Reading Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    ReadingMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReadingModeChange(mode) }
                                .padding(vertical = 4.dp), // Reduced spacing
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentReadingMode == mode,
                                onClick = { onReadingModeChange(mode) }
                            )
                            Text(
                                text = when (mode) {
                                    ReadingMode.PAGED -> "Paged"
                                    ReadingMode.CONTINUOUS -> "Continuous"
                                    ReadingMode.WEBTOON -> "Webtoon"
                                },
                                modifier = Modifier.padding(start = 4.dp) // Reduced spacing
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp), // Reduced spacing
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                // Image Scale Type
                Text(
                    text = "Image Scale Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Only show relevant scale types for the selected reading mode
                    val options = when (currentReadingMode) {
                        ReadingMode.PAGED -> listOf(
                            ImageDisplayMode.FIT_WIDTH to "Fit Width",
                            ImageDisplayMode.FIT_HEIGHT to "Fit Height",
                            ImageDisplayMode.FIT_BOTH to "Fit Screen",
                            ImageDisplayMode.NO_LIMIT to "No Limit"
                        )
                        else -> listOf(
                            ImageDisplayMode.FIT_WIDTH to "Fit Width",
                            ImageDisplayMode.NO_LIMIT to "No Limit"
                        )
                    }

                    options.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onScaleTypeChange(type) }
                                .padding(vertical = 4.dp), // Reduced spacing
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentScaleType == type,
                                onClick = { onScaleTypeChange(type) }
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 4.dp) // Reduced spacing
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp), // Reduced spacing
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                // Progress Bar Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProgressBarToggle() }
                        .padding(vertical = 4.dp), // Reduced spacing
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showProgressBar,
                        onCheckedChange = { onProgressBarToggle() }
                    )
                    Text(
                        text = "Show Progress Bar",
                        modifier = Modifier.padding(start = 4.dp) // Reduced spacing
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
fun ReadMangaScreen(
    navController: NavController,
    chapterId: String,
    mangaDexAPI: MangaDexAPI,
    chaptersList: List<ChapterModel>
) {
    val context = LocalContext.current
    val appConfig = remember { AppConfig.getInstance(context) }
    val viewModel = remember {
        ReadMangaViewModel(mangaDexAPI, chapterId, chaptersList, appConfig)
    }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Get system accent color
    val accentColor = MaterialTheme.colorScheme.primary
    val progressTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    // UI control states
    var showControls by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // Preload images when the composable is first created
    LaunchedEffect(Unit) {
        viewModel.preloadImagesAndDetectRatio(context)
    }

    // Auto-hide controls after delay (only for continuous mode)
    LaunchedEffect(showControls, viewModel.viewingMode) {
        if (showControls && viewModel.viewingMode != ReadingMode.PAGED) {
            delay(3000)
            showControls = false
        }
    }

    // Main UI
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            ErrorView(
                error = uiState.error ?: "Unknown error",
                onRetry = {
                    viewModel.loadChapter(chapterId)
                }
            )
        } else {
            // Content - either paged or continuous scrolling
            when (viewModel.viewingMode) {
                ReadingMode.PAGED -> {
                    PagedReaderView(
                        pageUrls = uiState.imageUrls,
                        currentPageIndex = uiState.currentPage,
                        scaleType = when(viewModel.displayMode) {
                            ImageDisplayMode.FIT_WIDTH -> ImageScaleType.FIT_WIDTH
                            ImageDisplayMode.FIT_HEIGHT -> ImageScaleType.FIT_HEIGHT
                            ImageDisplayMode.FIT_BOTH -> ImageScaleType.FIT_BOTH
                            ImageDisplayMode.NO_LIMIT -> ImageScaleType.NO_LIMIT
                        },
                        onPageChange = { viewModel.navigateToPage(it) },
                        onTap = { showControls = !showControls },
                        onNavigateToPrevious = { viewModel.previousPage() },
                        onNavigateToNext = { viewModel.nextPage() }
                    )
                }
                ReadingMode.CONTINUOUS, ReadingMode.WEBTOON -> {
                    ContinuousReaderView(
                        pageUrls = uiState.imageUrls,
                        scaleType = when(viewModel.displayMode) {
                            ImageDisplayMode.FIT_WIDTH -> ImageScaleType.FIT_WIDTH
                            ImageDisplayMode.FIT_HEIGHT -> ImageScaleType.FIT_HEIGHT
                            ImageDisplayMode.FIT_BOTH -> ImageScaleType.FIT_BOTH
                            ImageDisplayMode.NO_LIMIT -> ImageScaleType.NO_LIMIT
                        },
                        onTap = { showControls = !showControls },
                        onPageVisible = { index, isVisible ->
                            if (isVisible && index != uiState.currentPage) {
                                viewModel.navigateToPage(index)
                            }
                        }
                    )
                }
            }

            // Read progress indicator (more visible)
            if (viewModel.showProgressBar && uiState.imageUrls.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { (uiState.currentPage + 1).toFloat() / uiState.totalPages },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)  // Slightly taller
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    color = accentColor,
                    trackColor = progressTrackColor
                )
            }

            // Bottom controls bar that fades in/out
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderControlBar(
                    currentPage = uiState.currentPage + 1,
                    totalPages = uiState.totalPages,
                    onPreviousChapter = {
                        scope.launch {
                            viewModel.previousChapter()
                        }
                    },
                    onNextChapter = {
                        scope.launch {
                            viewModel.nextChapter()
                        }
                    },
                    onShowChapterSelector = { /* Removed chapter selector */ },
                    onShowMenu = { showSettingsMenu = true },
                    hasPreviousChapter = uiState.currentChapter?.let {
                        viewModel.findPreviousChapter() != null
                    } == true,
                    hasNextChapter = uiState.currentChapter?.let {
                        viewModel.findNextChapter() != null
                    } == true,
                    chapterTitle = uiState.currentChapter?.title ?:
                    "Chapter ${uiState.currentChapter?.number}"
                )
            }

            // Settings menu dialog with reduced spacing
            if (showSettingsMenu) {
                SimpleReaderSettingsDialog(
                    currentReadingMode = viewModel.viewingMode,
                    currentScaleType = viewModel.displayMode,
                    showProgressBar = viewModel.showProgressBar,
                    onReadingModeChange = {
                        viewModel.changeViewingMode(it)
                    },
                    onScaleTypeChange = {
                        viewModel.changeDisplayMode(it)
                    },
                    onProgressBarToggle = {
                        viewModel.toggleProgressBar()
                    },
                    onDismiss = { showSettingsMenu = false }
                )
            }
        }
    }
}

enum class ImageScaleType {
    FIT_WIDTH,
    FIT_HEIGHT,
    FIT_BOTH,
    NO_LIMIT
}

@Composable
fun ContinuousReaderView(
    pageUrls: List<String>,
    scaleType: ImageScaleType,
    onTap: () -> Unit,
    onPageVisible: (Int, Boolean) -> Unit
) {
    val scrollState = rememberLazyListState()
    val context = LocalContext.current

    // Preload more images for continuous mode
    LaunchedEffect(pageUrls) {
        // Preload 10 images instead of 5
        val imagesToPreload = min(10, pageUrls.size)
        for (i in 0 until imagesToPreload) {
            val request = ImageRequest.Builder(context)
                .data(pageUrls[i])
                .size(coil.size.Size.ORIGINAL)
                .build()
            context.imageLoader.enqueue(request)
        }
    }

    // Track visible items and report to parent
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val layoutInfo = scrollState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@snapshotFlow -1

            // Find the item with the largest visible area
            val mostVisibleItem = visibleItems.maxByOrNull {
                val visibleHeight = minOf(it.offset + it.size, layoutInfo.viewportEndOffset) -
                        maxOf(it.offset, layoutInfo.viewportStartOffset)
                visibleHeight.toFloat() / it.size
            }

            mostVisibleItem?.index ?: -1
        }.collect { mostVisibleIndex ->
            if (mostVisibleIndex >= 0) {
                onPageVisible(mostVisibleIndex, true)

                // When a new page is visible, preload ahead and behind
                val preloadStart = maxOf(0, mostVisibleIndex - 5)
                val preloadEnd = minOf(pageUrls.size - 1, mostVisibleIndex + 10)

                for (i in preloadStart..preloadEnd) {
                    if (i != mostVisibleIndex) {
                        val request = ImageRequest.Builder(context)
                            .data(pageUrls[i])
                            .size(coil.size.Size.ORIGINAL)
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }
            }
        }
    }

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
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Page ${index + 1}",
                    contentScale = when (scaleType) {
                        ImageScaleType.FIT_WIDTH -> ContentScale.FillWidth
                        ImageScaleType.NO_LIMIT -> ContentScale.None
                        else -> ContentScale.FillWidth
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

@Composable
fun PagedReaderView(
    pageUrls: List<String>,
    currentPageIndex: Int,
    scaleType: ImageScaleType,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit,
    onNavigateToPrevious: () -> Unit,
    onNavigateToNext: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }

    // Create state for zooming
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        // Apply zoom limits
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale

        // Apply offset with boundaries
        offset += offsetChange
    }

    // Reset zoom when page changes
    LaunchedEffect(currentPageIndex) {
        scale = 1f
        offset = Offset.Zero
    }

    // Preload images
    LaunchedEffect(currentPageIndex, pageUrls) {
        // Preload images (4 before and 4 after)
        val startIdx = maxOf(0, currentPageIndex - 4)
        val endIdx = minOf(pageUrls.size - 1, currentPageIndex + 4)

        for (i in startIdx..endIdx) {
            if (i != currentPageIndex) {
                val request = ImageRequest.Builder(context)
                    .data(pageUrls[i])
                    .size(coil.size.Size.ORIGINAL)
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Current page with zoom capability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformableState)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(pageUrls.getOrNull(currentPageIndex))
                    .crossfade(true)
                    .build(),
                contentDescription = "Page ${currentPageIndex + 1}",
                contentScale = when (scaleType) {
                    ImageScaleType.FIT_WIDTH -> ContentScale.FillWidth
                    ImageScaleType.FIT_HEIGHT -> ContentScale.FillHeight
                    ImageScaleType.FIT_BOTH -> ContentScale.Fit
                    ImageScaleType.NO_LIMIT -> ContentScale.None
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )

            // Page number indicator
            Text(
                text = "${currentPageIndex + 1}/${pageUrls.size}",
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

        // Touch areas only active when not zoomed in
        if (scale <= 1.01f) {
            // Left tap area for previous page
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (screenWidth * 0.3f).toDp() })
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToPrevious() }
            )

            // Center tap area to show/hide controls
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (screenWidth * 0.4f).toDp() })
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTap() }
            )

            // Right tap area for next page
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (screenWidth * 0.3f).toDp() })
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNavigateToNext() }
            )
        }
    }
}

@Composable
fun ReaderControlBar(
    currentPage: Int,
    totalPages: Int,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onShowChapterSelector: () -> Unit,
    onShowMenu: () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
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
            // Previous chapter button
            IconButton(
                onClick = onPreviousChapter,
                enabled = hasPreviousChapter
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Chapter",
                    tint = if (hasPreviousChapter) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Chapter title and page indicator
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

            // Menu button
            IconButton(onClick = onShowMenu) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Reader Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Next chapter button
            IconButton(
                onClick = onNextChapter,
                enabled = hasNextChapter
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Chapter",
                    tint = if (hasNextChapter) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
    currentReadingMode: ReadingMode,
    currentScaleType: ImageScaleType,
    showProgressBar: Boolean,
    onReadingModeChange: (ReadingMode) -> Unit,
    onScaleTypeChange: (ImageScaleType) -> Unit,
    onProgressBarToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    currentChapter: ChapterModel?,
    allChapters: List<ChapterModel>,
    onNavigateToChapter: (ChapterModel) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = "Reader Settings",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reading Mode
                Text(
                    text = "Reading Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reading mode selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReadingMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReadingModeChange(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentReadingMode == mode,
                                onClick = { onReadingModeChange(mode) }
                            )
                            Text(
                                text = when (mode) {
                                    ReadingMode.PAGED -> "Paged"
                                    ReadingMode.CONTINUOUS -> "Continuous"
                                    ReadingMode.WEBTOON -> "Webtoon"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Scale Type
                Text(
                    text = "Image Scale Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Only show relevant scale types for the selected reading mode
                    val options = when (currentReadingMode) {
                        ReadingMode.PAGED -> listOf(
                            ImageScaleType.FIT_WIDTH to "Fit Width",
                            ImageScaleType.FIT_HEIGHT to "Fit Height",
                            ImageScaleType.FIT_BOTH to "Fit Screen",
                            ImageScaleType.NO_LIMIT to "No Limit"
                        )
                        else -> listOf(
                            ImageScaleType.FIT_WIDTH to "Fit Width",
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                // Chapter Navigation section
                Text(
                    text = "Chapter Navigation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show nearby chapters for easy navigation
                val nearbyChapters = allChapters
                    .filter { it.language == (currentChapter?.language ?: "en") }
                    .sortedBy { it.number.toFloatOrNull() ?: Float.MAX_VALUE }
                    .take(5)

                Column(modifier = Modifier.fillMaxWidth()) {
                    nearbyChapters.forEach { chapter ->
                        val isCurrentChapter = chapter.id == currentChapter?.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrentChapter) {
                                    onNavigateToChapter(chapter)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chapter ${chapter.number}",
                                fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentChapter)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (isCurrentChapter) {
                                Text(
                                    text = "Current",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
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

suspend fun fetchChapterPages(chapterId: String): List<String>? {
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