package me.thuanc177.kotsune.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf<String?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Check login status when the screen is shown
    LaunchedEffect(Unit) {
        viewModel.checkLoginStatus()
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
                            }
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
                            }
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
    onLoginClick: () -> Unit
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
    animeList: List<TrackedMediaItem>,
    onAnimeClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    MediaList(
        mediaList = animeList,
        emptyMessage = "No anime found in your list",
        onMediaClick = onAnimeClick,
        onProgressClick = onProgress
    )
}

@Composable
fun MangaList(
    mangaList: List<TrackedMediaItem>,
    onMangaClick: (Int) -> Unit,
    onProgress: (Int, Int) -> Unit
) {
    MediaList(
        mediaList = mangaList,
        emptyMessage = "No manga found in your list",
        onMediaClick = onMangaClick,
        onProgressClick = onProgress
    )
}

@Composable
fun MediaList(
    mediaList: List<TrackedMediaItem>,
    emptyMessage: String,
    onMediaClick: (Int) -> Unit,
    onProgressClick: (Int, Int) -> Unit
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
                    onProgressClick = { onProgressClick(media.id, media.progress + 1) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedMediaCard(
    mediaItem: TrackedMediaItem,
    onClick: () -> Unit,
    onProgressClick: () -> Unit
) {
    var showProgressDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        EditProgressDialog(
            currentProgress = mediaItem.progress,
            maxProgress = mediaItem.total,
            onDismiss = { showProgressDialog = false },
            onConfirm = { newProgress ->
                // This will be handled by the viewModel via callback
                showProgressDialog = false
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action buttons - only show when expanded
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Edit Progress
                            TextButton(
                                onClick = { showProgressDialog = true },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit progress"
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
            (maxProgress == null || progress.toIntOrNull()?.let { it <= maxProgress } ?: false)

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