package me.thuanc177.kotsune.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.ui.components.capitalizeWords
import me.thuanc177.kotsune.viewmodel.MangaDetailedViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.core.net.toUri

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailedScreen(
    navController: NavController,
    mangaId: String,
    viewModel: MangaDetailedViewModel,
    onBackPressed: () -> Unit,
    onChapterClick: (ChapterModel, List<ChapterModel>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Add these state variables to track dialog visibility

    // Track if the top app bar should be collapsed
    remember { derivedStateOf { scrollState.firstVisibleItemIndex > 0 } }

    // Handler for chapter clicks
    val handleChapterClick: (ChapterModel) -> Unit = { chapter ->
        if (chapter.isOfficial && chapter.externalUrl != null) {
            // Open browser for official chapters
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(chapter.externalUrl))
            context.startActivity(intent)
        } else {
            // Use the regular navigation for normal chapters
            onChapterClick(chapter, uiState.chapters)
        }
    }

    // Store chapters grouped by volume for better organization
    val groupedChapters = remember(uiState.chapters) {
        uiState.chapters.groupBy {
            // Extract volume number from chapter number if possible
            val chapterNum = it.number.toFloatOrNull() ?: 0f
            val volumeNum = (chapterNum / 10).toInt()
            "Volume ${volumeNum + 1}"
        }
    }

    Scaffold(
        floatingActionButton = {
            // Show FAB to scroll to top when not at the top
            AnimatedVisibility(
                visible = scrollState.firstVisibleItemIndex > 5,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            scrollState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingView()
                }
                uiState.error != null -> {
                    ErrorView(
                        error = uiState.error!!,
                        onRetry = { viewModel.fetchMangaDetails() }
                    )
                }
                uiState.manga != null -> {
                    MangaDetailContent(
                        manga = uiState.manga!!,
                        isFavorite = uiState.isFavorite,
                        onToggleFavorite = { viewModel.toggleFavorite() },
                        groupedChapters = groupedChapters,
                        chaptersLoading = uiState.chaptersLoading,
                        chaptersError = uiState.chaptersError,
                        onChapterClick = { chapter -> onChapterClick(chapter, uiState.chapters) },
                        onRetryChapters = { viewModel.fetchChapters() },
                        chapterSortAscending = uiState.chapterSortAscending,
                        onToggleSort = { viewModel.toggleChapterSorting() },
                        scrollState = scrollState
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading manga details...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MangaDetailContent(
    manga: Manga,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    groupedChapters: Map<String, List<ChapterModel>>,
    chaptersLoading: Boolean,
    chaptersError: String?,
    onChapterClick: (ChapterModel) -> Unit,
    onRetryChapters: () -> Unit,
    chapterSortAscending: Boolean,
    onToggleSort: () -> Unit,
    scrollState: LazyListState
) {
    // Get access to context for launching browser intents
    val context = LocalContext.current

    // Create a handler function that checks for official chapters
    val handleChapterClick: (ChapterModel) -> Unit = { chapter ->
        if (chapter.isOfficial && chapter.externalUrl != null) {
            // Open browser for official chapters
            val intent = Intent(Intent.ACTION_VIEW, chapter.externalUrl.toUri())
            context.startActivity(intent)
        } else {
            // Use regular navigation for normal chapters
            onChapterClick(chapter)
        }
    }

    // Group chapters by their number for collapsible UI
    val chaptersGroupedByNumber = remember(groupedChapters.values.flatten()) {
        groupedChapters.values.flatten().groupBy { it.number }
    }

    // Track which chapter numbers are expanded
    val expandedChapterNumbers = remember { mutableStateListOf<String>() }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize()
    ) {
        // Header items remain the same
        item { CoverHeaderSection(manga) }
        item { MangaInfoSection(manga) }
        item { DescriptionSection(manga.description) }
        item { TagsSection(tags = manga.tags) }
        item {
            // Pass the count of unique chapter numbers instead of total translations
            ChaptersHeader(
                chapterCount = chaptersGroupedByNumber.size,
                isLoading = chaptersLoading,
                sortAscending = chapterSortAscending,
                onToggleSort = onToggleSort
            )
        }

        when {
            chaptersLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            chaptersError != null -> {
                item { ChapterErrorView(error = chaptersError, onRetry = onRetryChapters) }
            }
            chaptersGroupedByNumber.isEmpty() -> {
                item { EmptyChaptersView() }
            }
            else -> {
                // Create a properly sorted list of chapter numbers
                val sortedChapterNumbers = chaptersGroupedByNumber.keys.sortedWith(
                    if (chapterSortAscending)
                        compareBy { it.toFloatOrNull() ?: Float.MAX_VALUE }
                    else
                        compareByDescending { it.toFloatOrNull() ?: Float.MIN_VALUE }
                )

                // For each chapter number, create a collapsible header with translations
                items(sortedChapterNumbers) { chapterNumber ->
                    val translations = chaptersGroupedByNumber[chapterNumber] ?: emptyList()
                    val isExpanded = expandedChapterNumbers.contains(chapterNumber)

                    // Group translations by language for counting
                    val languageGroups = translations.groupBy { it.language }

                    // Chapter group header
                    ChapterGroupHeader(
                        chapterNumber = chapterNumber,
                        languageGroups = languageGroups,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            if (isExpanded) {
                                expandedChapterNumbers.remove(chapterNumber)
                            } else {
                                expandedChapterNumbers.add(chapterNumber)
                            }
                        }
                    )

                    // Show translations if expanded
                    if (isExpanded) {
                        val sortedTranslations = translations.sortedWith(
                            compareBy<ChapterModel> { it.language != "en" }
                                .thenBy { it.publishedAt }
                        )

                        sortedTranslations.forEachIndexed { index, chapter ->
                            val isLast = index == sortedTranslations.size - 1
                            TranslationItem(
                                chapter = chapter,
                                isLast = isLast,
                                onClick = { handleChapterClick(chapter) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChapterGroupHeader(
    chapterNumber: String,
    languageGroups: Map<String, List<ChapterModel>>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            // More subtle color for chapter header - slightly different from the background
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Chapter header with expand icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chapter number
                Text(
                    text = "Chapter $chapterNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Total translations count
                Text(
                    text = "(${languageGroups.values.sumOf { it.size }})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                // Expand/collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Language badges row
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languageGroups.forEach { (_, chapters) ->
                    val languageFlag = chapters.firstOrNull()?.languageFlag ?: "🌐"

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = languageFlag,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${chapters.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationItem(
    chapter: ChapterModel,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (chapter.isRead)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language flag
            Text(
                text = chapter.languageFlag ?: "🌐",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            // Translation info
            Column(modifier = Modifier.weight(1f)) {
                // Group name
                chapter.translatorGroup?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Publication date if available
                if (chapter.publishedAt.isNotBlank()) {
                    Text(
                        text = getTimeAgo(chapter.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Official Publisher badge or read status and page count
            if (chapter.isOfficial) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Official Publisher",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Official",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Read status and page count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chapter.isRead) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Read",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(16.dp)
                        )
                    }

                    // Page count
                    if (chapter.pages > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${chapter.pages} page(s)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverHeaderSection(
    manga: Manga
) {
    val context = LocalContext.current
    var saveTriggered by remember { mutableStateOf(false) } // Track save button click
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && saveTriggered) {
            manga.poster?.let { posterUrl ->
                saveMangaBanner(context, posterUrl, manga.title.firstOrNull() ?: "Manga")
            }
            saveTriggered = false // Reset trigger
        } else if (!isGranted) {
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
            saveTriggered = false
        }
    }

    // Handle permission request when save is triggered
    if (saveTriggered) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        LaunchedEffect(saveTriggered) {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                manga.poster?.let { posterUrl ->
                    saveMangaBanner(context, posterUrl, manga.title.firstOrNull() ?: "Manga")
                }
                saveTriggered = false
            } else {
                permissionLauncher.launch(permission)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        // Cover image (full width)
        manga.poster?.let { posterUrl ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Manga Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay for better text visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        } ?: run {
            // Placeholder if no cover
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Cover Available",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Title and basic info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = manga.title.firstOrNull() ?: "Unknown Title",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Status, year, and buttons in a row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(status = manga.status)

                manga.year?.let {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.2f),
                    ) {
                        Text(
                            text = it.toString(),
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (statusText, statusColor) = when (status.lowercase()) {
        "ongoing" -> "Ongoing" to Color(0xFF4CAF50) // Green
        "completed" -> "Completed" to Color(0xFF2196F3) // Blue
        "hiatus" -> "Hiatus" to Color(0xFFFF9800) // Orange
        "cancelled" -> "Cancelled" to Color(0xFFFF5252) // Red
        else -> status.capitalizeWords() to MaterialTheme.colorScheme.tertiary
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = statusColor.copy(alpha = 0.2f)
    ) {
        Text(
            text = statusText,
            fontSize = 12.sp,
            color = statusColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MangaInfoSection(manga: Manga) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Content Rating
        val contentRatingText = when (manga.contentRating?.lowercase()) {
            "safe" -> "All Ages"
            "suggestive" -> "Teen"
            "erotica" -> "Mature"
            "pornographic" -> "Adult"
            else -> manga.contentRating.capitalizeWords()
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rating: $contentRatingText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Last updated
        manga.lastUpdated?.let { updateDate ->
            val formattedDate = formatUpdateDate(updateDate)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Updated: $formattedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DescriptionSection(description: String) {
    var expanded by remember { mutableStateOf(false) }
    val shouldShowExpandOption = description.length > 300

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (description.isBlank()) {
                Text(
                    text = "No description available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            } else {
                Text(
                    text = if (shouldShowExpandOption && !expanded)
                        description.take(300) + "..."
                    else
                        description,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (shouldShowExpandOption) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (expanded) "Show less" else "Read more",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsSection(tags: List<MangaDexTypes.MangaTag>) {
    if (tags.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = tag.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChaptersHeader(
    chapterCount: Int,
    isLoading: Boolean,
    sortAscending: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "($chapterCount)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        // Sort toggle button
        IconButton(onClick = onToggleSort) {
            Icon(
                imageVector = if (sortAscending)
                    Icons.Default.ArrowUpward
                else
                    Icons.Default.ArrowDownward,
                contentDescription = "Toggle sort order",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
fun ChapterErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Failed to load chapters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onRetry) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Composable
fun EmptyChaptersView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No chapters available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun getTimeAgo(dateString: String): String {
    return try {
        val instant = Instant.parse(dateString)
        val now = Instant.now()

        val days = ChronoUnit.DAYS.between(instant, now)
        val hours = ChronoUnit.HOURS.between(instant, now)
        val minutes = ChronoUnit.MINUTES.between(instant, now)

        when {
            days > 365 -> {
                val years = days / 365
                if (years == 1L) "1 year ago" else "$years years ago"
            }
            days > 30 -> {
                val months = days / 30
                if (months == 1L) "1 month ago" else "$months months ago"
            }
            days > 0 -> {
                if (days == 1L) "Yesterday" else "$days days ago"
            }
            hours > 0 -> {
                if (hours == 1L) "1 hour ago" else "$hours hours ago"
            }
            minutes > 0 -> {
                if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            }
            else -> "Just now"
        }
    } catch (e: Exception) {
        "Unknown date"
    }
}

private fun formatUpdateDate(dateString: String): String {
    return try {
        val instant = Instant.parse(dateString)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        localDateTime.format(formatter)
    } catch (e: Exception) {
        "Unknown date"
    }
}

fun saveMangaBanner(context: android.content.Context, imageUrl: String, mangaTitle: String) {
    val imageLoader = context.imageLoader
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val drawable = imageLoader.execute(request).drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                ?: throw IllegalStateException("Failed to load image")

            val fileName = "${mangaTitle.replace(" ", "_")}_banner.jpg"
            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kotsune")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                outputStream = uri?.let { resolver.openOutputStream(it) }

                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri!!, values, null, null)
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(imagesDir, "Kotsune")
                if (!folder.exists()) folder.mkdirs()
                val file = File(folder, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                it.flush()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Banner saved successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save banner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}