package me.thuanc177.kotsune.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.navigation.Screen
import me.thuanc177.kotsune.libs.anilist.AnilistTrackedMediaItem
import me.thuanc177.kotsune.viewmodel.AnilistTrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnilistTrackingScreen(
    navController: NavController,
    viewModel: AnilistTrackingViewModel = viewModel(
        factory = AnilistTrackingViewModel.Factory(
            anilistClient = AnilistClient(LocalContext.current)
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Set initial loading state and ensure we only check login status once
    var hasCheckedLogin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasCheckedLogin) {
            Log.d("AnilistTrackingScreen", "Checking login status on screen appear")
            viewModel.checkLoginStatus()
            hasCheckedLogin = true
        }
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = { showSearch = false }
                )
            } else {
                TopAppBar(
                    title = { Text("Anilist Profile") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        if (uiState.isLoggedIn && !uiState.isLoading && uiState.error == null) {
                            // Search Icon
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            // Filter Icon with Dropdown
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = "Filter",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All") },
                                        onClick = {
                                            selectedStatusFilter = null
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (selectedStatusFilter == null) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )

                                    val statuses = listOf(
                                        "CURRENT" to "Current",
                                        "COMPLETED" to "Completed",
                                        "PLANNING" to "Planning",
                                        "DROPPED" to "Dropped",
                                        "PAUSED" to "Paused",
                                        "REPEATING" to "Repeating"
                                    )

                                    statuses.forEach { (status, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                selectedStatusFilter = status
                                                showFilterMenu = false
                                            },
                                            leadingIcon = {
                                                if (selectedStatusFilter == status) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingScreen(padding)
            }

            !uiState.isLoggedIn -> {
                LoginScreen(
                    padding = padding,
                    onLoginClick = { viewModel.login(context) }
                )
            }

            uiState.error != null -> {
                ErrorScreen(
                    error = uiState.error ?: "Unknown error",
                    padding = padding,
                    onRetryClick = { viewModel.checkLoginStatus() }
                )
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
                        UserProfileHeader(
                            user = user,
                            animeCount = uiState.animeList.size,
                            mangaCount = uiState.mangaList.size,
                            onLogoutClick = {
                                viewModel.logout()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Logged out successfully")
                                }
                            },
                            onRefreshClick = {
                                viewModel.refreshData()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Refreshing data...")
                                }
                            }
                        )
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

                    // Filter the current list based on search query and status filter
                    val filteredAnimeList = uiState.animeList.filter { anime ->
                        (selectedStatusFilter == null || anime.status == selectedStatusFilter) &&
                                (searchQuery.isEmpty() || anime.title.contains(searchQuery, ignoreCase = true))
                    }

                    val filteredMangaList = uiState.mangaList.filter { manga ->
                        (selectedStatusFilter == null || manga.status == selectedStatusFilter) &&
                                (searchQuery.isEmpty() || manga.title.contains(searchQuery, ignoreCase = true))
                    }

                    // Display the appropriate list based on selected tab
                    when (selectedTabIndex) {
                        0 -> AnimeList(
                            animeList = filteredAnimeList,
                            onAnimeClick = { animeId ->
                                navController.navigate(Screen.AnimeDetail.createRoute(animeId.toString()))
                            },
                            onProgress = { id, progress ->
                                viewModel.updateMediaProgress(id, progress, true)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Updated progress to $progress")
                                }
                            },
                            viewModel = viewModel
                        )
                        1 -> MangaList(
                            mangaList = filteredMangaList,
                            onMangaClick = { mangaId ->
                                // Navigate to manga detail or show a better message about future implementation
                                scope.launch {
                                    snackbarHostState.showSnackbar("Manga details coming soon")
                                }
                            },
                            onProgress = { id, progress ->
                                viewModel.updateMediaProgress(id, progress, false)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Updated progress to $progress")
                                }
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { /* Use the current query */ },
        active = true,
        onActiveChange = { if (!it) onClose() },
        placeholder = { Text("Search titles...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search suggestions could go here if needed
    }
}

@Composable
fun LoadingScreen(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading your Anilist profile...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun LoginScreen(
    padding: PaddingValues,
    onLoginClick: () -> Unit,
    onCloseClick: () -> Unit = {}
) {
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
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(16.dp),
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
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign in with Anilist",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(
    error: String,
    padding: PaddingValues,
    onRetryClick: () -> Unit
) {
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
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
fun UserProfileHeader(
    user: AnilistTypes.AnilistUser,
    animeCount: Int,
    mangaCount: Int,
    onLogoutClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    // This state will track if the banner image loaded successfully
    var isBannerLoaded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Banner image as full background
            if (user.bannerImage?.isNotEmpty() == true) {
                AsyncImage(
                    model = user.bannerImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    alpha = 0.7f,  // Make it slightly transparent so profile info is visible
                    onSuccess = { isBannerLoaded = true }
                )
            }

            // Semi-transparent overlay to improve text readability over the banner
            if (isBannerLoaded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }

            // Content
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .then(
                        if (!isBannerLoaded && user.bannerImage?.isNotEmpty() != true) {
                            Modifier.background(MaterialTheme.colorScheme.surface)
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Avatar image
                AsyncImage(
                    model = user.avatar?.medium,
                    contentDescription = "User Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // User name and stats
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isBannerLoaded) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        StatItem(
                            count = animeCount,
                            label = "Anime",
                            icon = Icons.Default.Tv,
                            tint = if (isBannerLoaded) Color.White.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        StatItem(
                            count = mangaCount,
                            label = "Manga",
                            icon = Icons.Default.Book,
                            tint = if (isBannerLoaded) Color.White.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Action buttons in more compact layout
                Row {
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh data",
                            tint = if (isBannerLoaded) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = if (isBannerLoaded) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = tint
        )
    }
}

@Composable
fun AnimeList(
    animeList: List<AnilistTrackedMediaItem>,
    onAnimeClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit,
    viewModel: AnilistTrackingViewModel
) {
    MediaList(
        mediaList = animeList,
        emptyMessage = "No anime found in your list",
        onMediaClick = onAnimeClick,
        onProgressClick = onProgress,
        viewModel = viewModel,
        isAnime = true
    )
}

@Composable
fun MangaList(
    mangaList: List<AnilistTrackedMediaItem>,
    onMangaClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit,
    viewModel: AnilistTrackingViewModel
) {
    MediaList(
        mediaList = mangaList,
        emptyMessage = "No manga found in your list",
        onMediaClick = onMangaClick,
        onProgressClick = onProgress,
        viewModel = viewModel,
        isAnime = false
    )
}

@Composable
fun MediaList(
    mediaList: List<AnilistTrackedMediaItem>,
    emptyMessage: String,
    onMediaClick: (Int) -> Unit,
    onProgressClick: (Int, Int) -> Unit,
    viewModel: AnilistTrackingViewModel,
    isAnime: Boolean = true
) {
    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = mediaList,
                key = { it.id }
            ) { media ->
                TrackedMediaCard(
                    mediaItem = media,
                    onClick = { onMediaClick(media.id) },
                    onProgressClick = { onProgressClick(media.id, media.progress + 1) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedMediaCard(
    mediaItem: AnilistTrackedMediaItem,
    onClick: () -> Unit,
    onProgressClick: () -> Unit,
    viewModel: AnilistTrackingViewModel
) {
    var showEditor by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Show the tracking editor
    if (showEditor) {
        TrackingEditorDialog(
            mediaItem = mediaItem,
            onDismiss = { showEditor = false },
            onSave = { status, score, progress, startDate, finishDate, rewatches, notes, isPrivate, isFavorite ->
                val isAnime = mediaItem.total?.let { it > 0 && mediaItem.status == "WATCHING" || mediaItem.status == "CURRENT" } != false

                viewModel.updateMediaEntry(
                    mediaItem.id,
                    status,
                    score,
                    progress,
                    startDate,
                    finishDate,
                    rewatches,
                    notes,
                    isPrivate,
                    isFavorite,
                    isAnime
                )

                // Refresh the tracking screen data after updating
                scope.launch {
                    viewModel.refreshData()
                    Toast.makeText(context, "Updated tracking information", Toast.LENGTH_SHORT).show()
                }

                showEditor = false
            },
            onDeleteEntry = {
                val isAnime = mediaItem.total?.let { it > 0 && mediaItem.status == "WATCHING" || mediaItem.status == "CURRENT" } != false

                viewModel.deleteMediaEntry(mediaItem.id, isAnime)

                // Refresh the tracking screen data after deleting
                scope.launch {
                    viewModel.refreshData()
                    Toast.makeText(context, "Removed from tracking list", Toast.LENGTH_SHORT).show()
                }

                showEditor = false
            },
            isAnime = mediaItem.total?.let { it > 0 && mediaItem.status == "WATCHING" || mediaItem.status == "CURRENT" } != false
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Using an adaptable height instead of fixed height
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Cover image
                AsyncImage(
                    model = mediaItem.imageUrl,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(80.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
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
                        LinearProgressIndicator(
                            progress = if (mediaItem.total != null && mediaItem.total > 0) {
                                mediaItem.progress.toFloat() / mediaItem.total
                            } else {
                                // Show indeterminate progress for ongoing media
                                1f
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${mediaItem.progress}${mediaItem.total?.let { "/$it" } ?: ""}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Action buttons - Always visible if expanded
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Edit button - opens full editor
                            TextButton(
                                onClick = { showEditor = true },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit tracking details"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }

                            // +1 Progress button
                            TextButton(
                                onClick = onProgressClick,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("+1")
                            }
                        }
                    }
                }

                // Expand/collapse button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more"
                    )
                }
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
    val isValidInput = progress.isNotEmpty() &&
            progress.all { it.isDigit() } &&
            (maxProgress == null || progress.toIntOrNull()?.let { it <= maxProgress } == true)

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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    label = { Text("Progress") },
                    isError = progress.isNotEmpty() &&
                            (progress.toIntOrNull() == null ||
                                    (maxProgress != null && progress.toIntOrNull()!! > maxProgress))
                )

                if (maxProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Maximum: $maxProgress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Show error message if input is invalid
                if (progress.isNotEmpty() && (progress.toIntOrNull() == null ||
                            (maxProgress != null && progress.toIntOrNull()!! > maxProgress))) {
                    Text(
                        text = "Please enter a valid number${maxProgress?.let { " (0-$it)" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newProgress = progress.toIntOrNull() ?: currentProgress
                    onConfirm(newProgress)
                },
                enabled = isValidInput
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingEditorDialog(
    mediaItem: AnilistTrackedMediaItem,
    onDismiss: () -> Unit,
    onSave: (
        status: String,
        score: Float?,
        progress: Int,
        startDate: String?,
        finishDate: String?,
        rewatches: Int?,
        notes: String?,
        isPrivate: Boolean,
        isFavorite: Boolean
    ) -> Unit,
    onDeleteEntry: () -> Unit,
    isAnime: Boolean = true
) {
    val context = LocalContext.current
    val isAuthenticated = remember { AnilistClient(context).isUserAuthenticated() }

    // State for all editable fields - initialize with existing values from mediaItem
    var selectedStatus by remember { mutableStateOf(mediaItem.status) }
    var scoreValue by remember { mutableStateOf(mediaItem.score ?: 0f) }
    var progressValue by remember { mutableStateOf(mediaItem.progress.toString()) }
    var startDateValue by remember { mutableStateOf(mediaItem.startDate ?: "") }
    var finishDateValue by remember { mutableStateOf(mediaItem.finishDate ?: "") }
    var rewatchesValue by remember { mutableStateOf(mediaItem.rewatches?.toString() ?: "0") }
    var notesValue by remember { mutableStateOf(mediaItem.notes ?: "") }
    var isPrivate by remember { mutableStateOf(mediaItem.isPrivate) }
    var isFavorite by remember { mutableStateOf(mediaItem.isFavorite) }

    // State for dialogs
    var showStatusDialog by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showFinishDatePicker by remember { mutableStateOf(false) }

    // Date picker states
    val startDatePickerState = rememberDatePickerState()
    val finishDatePickerState = rememberDatePickerState()

    // Initialize date pickers with existing dates if they exist
    LaunchedEffect(mediaItem) {
        mediaItem.startDate?.let { dateStr ->
            try {
                val parsedDate = java.time.LocalDate.parse(dateStr)
                val millis = parsedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                startDatePickerState.selectedDateMillis = millis
            } catch (e: Exception) {
                Log.e("TrackingEditor", "Error parsing start date: $dateStr", e)
            }
        }

        mediaItem.finishDate?.let { dateStr ->
            try {
                val parsedDate = java.time.LocalDate.parse(dateStr)
                val millis = parsedDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                finishDatePickerState.selectedDateMillis = millis
            } catch (e: Exception) {
                Log.e("TrackingEditor", "Error parsing finish date: $dateStr", e)
            }
        }
    }

    if (!isAuthenticated) {
        Toast.makeText(
            context,
            "You must log in to Anilist to use this feature",
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
        return
    }

    // Define status options
    val statusOptions = listOf(
        "CURRENT" to if (isAnime) "Watching" else "Reading",
        "COMPLETED" to "Completed",
        "PLANNING" to "Planning",
        "DROPPED" to "Dropped",
        "PAUSED" to "Paused",
        "REPEATING" to "Repeating"
    )

    // Status selection dialog
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Select Status") },
            text = {
                Column {
                    statusOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedStatus = key
                                    showStatusDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = key == selectedStatus,
                                onClick = {
                                    selectedStatus = key
                                    showStatusDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Start date picker
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDatePickerState.selectedDateMillis?.let { millis ->
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            startDateValue = date.toString() // YYYY-MM-DD format
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDatePicker = false }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    // Finish date picker
    if (showFinishDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFinishDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        finishDatePickerState.selectedDateMillis?.let { millis ->
                            val date = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            finishDateValue = date.toString() // YYYY-MM-DD format
                        }
                        showFinishDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishDatePicker = false }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = finishDatePickerState)
        }
    }

    // Main tracking editor dialog
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with media info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = mediaItem.imageUrl,
                    contentDescription = mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = mediaItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (isAnime) "Anime" else "Manga",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Divider(modifier = Modifier.padding(bottom = 16.dp))

            // Status field
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Simple clickable card for status selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showStatusDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusOptions.find { it.first == selectedStatus }?.second
                            ?: statusOptions.first().second,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select status"
                    )
                }
            }

            // Score slider
            Text(
                text = "Score",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val scoreText = if (scoreValue == 0f) "No Score" else "%.1f".format(scoreValue)
            Text(
                text = scoreText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Slider(
                value = scoreValue,
                onValueChange = { scoreValue = it },
                valueRange = 0f..10f,
                steps = 100,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Progress
            Text(
                text = if (isAnime) "Episode Progress" else "Chapter Progress",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = progressValue,
                    onValueChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            progressValue = it
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                mediaItem.total?.let {
                    Text(
                        text = " / $it",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Start Date - implemented as a clickable card
            Text(
                text = "Start Date",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showStartDatePicker = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = startDateValue.ifEmpty { "Select date" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (startDateValue.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )

                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select date"
                    )
                }
            }

            // Finish Date - implemented as a clickable card
            Text(
                text = "Finish Date",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { showFinishDatePicker = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = finishDateValue.ifEmpty { "Select date" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (finishDateValue.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )

                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select date"
                    )
                }
            }

            // Total Rewatches
            Text(
                text = if (isAnime) "Total Rewatches" else "Total Rereads",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            OutlinedTextField(
                value = rewatchesValue,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        rewatchesValue = it
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            // Notes
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            OutlinedTextField(
                value = notesValue,
                onValueChange = { notesValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(100.dp),
                placeholder = { Text("Add personal notes here...") }
            )

            // Private toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Private",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it }
                    )
                }
            }

            // Favorite toggle
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Favorite",
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Switch(
                        checked = isFavorite,
                        onCheckedChange = { isFavorite = it }
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDeleteEntry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }

                Button(
                    onClick = {
                        onSave(
                            selectedStatus,
                            if (scoreValue > 0f) scoreValue else null,
                            progressValue.toIntOrNull() ?: 0,
                            if (startDateValue.isNotEmpty()) startDateValue else null,
                            if (finishDateValue.isNotEmpty()) finishDateValue else null,
                            rewatchesValue.toIntOrNull(),
                            if (notesValue.isNotEmpty()) notesValue else null,
                            isPrivate,
                            isFavorite
                        )
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}