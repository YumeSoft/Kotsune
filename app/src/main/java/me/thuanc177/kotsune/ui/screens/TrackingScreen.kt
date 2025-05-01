package me.thuanc177.kotsune.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.navigation.Screen
import me.thuanc177.kotsune.viewmodel.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    navController: NavController,
    viewModel: TrackingViewModel = viewModel(
        factory = TrackingViewModel.Factory(
            anilistClient = AnilistClient(AppConfig.getInstance(LocalContext.current))
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Check login status when the screen is shown
    LaunchedEffect(Unit) {
        viewModel.checkLoginStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anilist Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !uiState.isLoggedIn -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Login Required",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Sign in to Anilist",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Track your anime and manga progress with Anilist",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.login(context)
                            },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text(
                                text = "Sign in",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Error: ${uiState.error}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.checkLoginStatus() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                // User is logged in, display their lists
                val tabTitles = listOf("Anime", "Manga")
                var selectedTabIndex by remember { mutableStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // User profile header
                    uiState.user?.let { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Avatar image
                            AsyncImage(
                                model = user.avatar,
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // User name and stats
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "${uiState.animeList.size} anime",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = " • ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = "${uiState.mangaList.size} manga",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Logout button
                            IconButton(
                                onClick = {
                                    viewModel.logout()
                                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Logout")
                            }
                        }
                    }

                    // Tabs for switching between Anime and Manga lists
                    TabRow(
                        selectedTabIndex = selectedTabIndex
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title) }
                            )
                        }
                    }

                    // Display the appropriate list based on selected tab
                    when (selectedTabIndex) {
                        0 -> AnimeList(
                            animeList = uiState.animeList,
                            onAnimeClick = { animeId ->
                                navController.navigate(Screen.AnimeDetail.createRoute(animeId.toString()))
                            },
                            onProgress = { id, progress ->
                                viewModel.updateMediaProgress(id, progress, true)
                            }
                        )
                        1 -> MangaList(
                            mangaList = uiState.mangaList,
                            onMangaClick = { mangaId ->
                                // For now, we don't have navigation to manga detail by anilist ID
                                // This would need to be implemented with additional data mapping
                                Toast.makeText(context, "Manga details not yet implemented for Anilist IDs", Toast.LENGTH_SHORT).show()
                            },
                            onProgress = { id, progress ->
                                viewModel.updateMediaProgress(id, progress, false)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeList(
    animeList: List<TrackedMediaItem>,
    onAnimeClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    if (animeList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No anime found in your list")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(animeList) { anime ->
                TrackedMediaCard(
                    mediaItem = anime,
                    onClick = { onAnimeClick(anime.id) },
                    onProgressClick = { onProgress(anime.id, anime.progress + 1) }
                )
            }
        }
    }
}

@Composable
fun MangaList(
    mangaList: List<TrackedMediaItem>,
    onMangaClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    if (mangaList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No manga found in your list")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mangaList) { manga ->
                TrackedMediaCard(
                    mediaItem = manga,
                    onClick = { onMangaClick(manga.id) },
                    onProgressClick = { onProgress(manga.id, manga.progress + 1) }
                )
            }
        }
    }
}

@Composable
fun TrackedMediaCard(
    mediaItem: TrackedMediaItem,
    onClick: () -> Unit,
    onProgressClick: () -> Unit
) {
    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        EditProgressDialog(
            currentProgress = mediaItem.progress,
            maxProgress = mediaItem.total,
            onDismiss = { showProgressDialog = false },
            onConfirm = { newProgress ->
                // TODO: Update progress through ViewModel
                showProgressDialog = false
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover image
            AsyncImage(
                model = mediaItem.imageUrl,
                contentDescription = mediaItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                // Title
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status badge
                StatusBadge(status = mediaItem.status)

                Spacer(modifier = Modifier.height(8.dp))

                // Progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Progress: ${mediaItem.progress}${mediaItem.total?.let { "/$it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showProgressDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit progress",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // +1 Progress button
                    IconButton(
                        onClick = onProgressClick
                    ) {
                        Text("+1")
                    }
                }
            }

            // Right arrow
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View details"
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "CURRENT", "WATCHING", "READING" -> Pair(Color(0xFF4CAF50), Color.White) // Green
        "COMPLETED" -> Pair(Color(0xFF2196F3), Color.White) // Blue
        "PLANNING" -> Pair(Color(0xFF9C27B0), Color.White) // Purple
        "DROPPED" -> Pair(Color(0xFFF44336), Color.White) // Red
        "PAUSED" -> Pair(Color(0xFFFF9800), Color.White) // Orange
        "REPEATING" -> Pair(Color(0xFF00BCD4), Color.White) // Cyan
        else -> Pair(Color.Gray, Color.White)
    }

    val displayText = when (status) {
        "CURRENT" -> "Watching/Reading"
        else -> status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    Surface(
        modifier = Modifier.wrapContentSize(),
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = displayText,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProgressDialog(
    currentProgress: Int,
    maxProgress: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var progress by remember { mutableStateOf(currentProgress.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Progress") },
        text = {
            Column {
                Text("Enter new progress value:")

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = progress,
                    onValueChange = { newValue ->
                        // Only allow numeric input
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            progress = newValue
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    label = { Text("Progress") }
                )

                if (maxProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Maximum: $maxProgress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newProgress = progress.toIntOrNull() ?: currentProgress
                    onConfirm(newProgress)
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

