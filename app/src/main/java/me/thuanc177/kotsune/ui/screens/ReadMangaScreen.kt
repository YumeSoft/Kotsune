package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.viewmodel.ChapterModel
import me.thuanc177.kotsune.viewmodel.ReadMangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadMangaScreen(
    navController: NavController,
    chapterId: String,
    mangaDexAPI: MangaDexAPI,
    chaptersList: List<ChapterModel> = emptyList()
) {
    val viewModel: ReadMangaViewModel = viewModel(
        factory = ReadMangaViewModel.Factory(mangaDexAPI, chapterId, chaptersList)
    )
    val uiState by viewModel.uiState.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    val scrollState = rememberLazyListState()

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Chapter ${uiState.chapterNumber}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Chapter selection dropdown would go here
                        IconButton(onClick = {
                            // Open chapter selector
                        }) {
                            Icon(Icons.Default.List, contentDescription = "Chapters")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showControls) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToPreviousChapter() },
                            enabled = viewModel.hasPreviousChapter()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Chapter",
                                tint = if (viewModel.hasPreviousChapter())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        Text(
                            text = "${uiState.currentPage + 1} / ${uiState.totalPages}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        IconButton(
                            onClick = { viewModel.navigateToNextChapter() },
                            enabled = viewModel.hasNextChapter()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Chapter",
                                tint = if (viewModel.hasNextChapter())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures {
                        showControls = !showControls
                    }
                }
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error loading chapter",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "Unknown error",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadChapter(uiState.chapterId) }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.pages.isNotEmpty() -> {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        items(uiState.pages.size) { index ->
                            val page = uiState.pages[index]
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(page)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )

                            LaunchedEffect(key1 = scrollState.firstVisibleItemIndex) {
                                viewModel.setCurrentPage(scrollState.firstVisibleItemIndex)
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "No pages available",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}