package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.navigation.Screen
import me.thuanc177.kotsune.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(navController: NavHostController) {
    // Initialize APIs and ViewModel
    val mangaDexAPI = MangaDexAPI()
    val anilistClient = AnilistClient()
    val viewModelFactory = SearchViewModel.SearchViewModelFactory(mangaDexAPI, anilistClient)
    val searchViewModel: SearchViewModel = viewModel(factory = viewModelFactory)
    val focusManager = LocalFocusManager.current

    // UI state
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }

    // Filter states
    val selectedGenres = remember { mutableStateListOf<String>() }
    var selectedStatus by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("relevance") }

    // Collect state from ViewModel
    val searchState by searchViewModel.searchState.collectAsState()
    val mangaResults by searchViewModel.mangaResults.collectAsState()
    val animeResults by searchViewModel.animeResults.collectAsState()

    // Function to perform search based on current tab
    val performSearch = {
        focusManager.clearFocus()
        if (selectedTabIndex == 0) {
            searchViewModel.searchManga(searchQuery, selectedGenres, selectedStatus, sortBy)
        } else {
            searchViewModel.searchAnime(searchQuery, selectedGenres, selectedStatus, sortBy)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar with filters button
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search ${if (selectedTabIndex == 0) "manga" else "anime"}...") },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchViewModel.resetSearch()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = performSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { performSearch() }
            )
        )

        // Filter status indicator
        if (selectedGenres.isNotEmpty() || selectedStatus.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${selectedGenres.size + (if (selectedStatus.isEmpty()) 0 else 1)} filters active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Filters panel
        AnimatedVisibility(visible = showFilters) {
            FilterPanel(
                selectedGenres = selectedGenres,
                selectedStatus = selectedStatus,
                selectedSort = sortBy,
                onStatusChanged = { selectedStatus = it },
                onSortByChanged = { sortBy = it },
                onGenreToggled = { genre, selected ->
                    if (selected) {
                        if (!selectedGenres.contains(genre)) selectedGenres.add(genre)
                    } else {
                        selectedGenres.remove(genre)
                    }
                }
            )
        }

        // Tabs for switching between manga and anime
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = {
                    selectedTabIndex = 0
                    if (searchQuery.isNotEmpty()) {
                        searchViewModel.searchManga(searchQuery, selectedGenres, selectedStatus, sortBy)
                    }
                },
                text = { Text("Manga") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = {
                    selectedTabIndex = 1
                    if (searchQuery.isNotEmpty()) {
                        searchViewModel.searchAnime(searchQuery, selectedGenres, selectedStatus, sortBy)
                    }
                },
                text = { Text("Anime") }
            )
        }

        // Content based on selected tab and search state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when (searchState) {
                is SearchViewModel.SearchState.Initial -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Enter search terms or select filters")
                    }
                }
                is SearchViewModel.SearchState.Loading -> {
                    CircularProgressIndicator()
                }
                is SearchViewModel.SearchState.Empty -> {
                    Text("No results found matching your criteria")
                }
                is SearchViewModel.SearchState.Error -> {
                    Text("Error: ${(searchState as SearchViewModel.SearchState.Error).message}")
                }
                is SearchViewModel.SearchState.Success -> {
                    if (selectedTabIndex == 0) {
                        // Display manga results
                        MangaResultsGrid(
                            results = mangaResults,
                            onMangaSelected = { mangaId ->
                                navController.navigate(Screen.MangaDetail.createRoute(mangaId))
                            }
                        )
                    } else {
                        // Display anime results
                        AnimeResultsGrid(
                            results = animeResults,
                            onAnimeSelected = { animeId ->
                                navController.navigate(Screen.AnimeDetail.createRoute(animeId.toString()))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaResultsGrid(
    results: List<Manga>,
    onMangaSelected: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(results) { manga ->
            MangaCard(manga = manga, onClick = { onMangaSelected(manga.id) })
        }
    }
}

@Composable
private fun AnimeResultsGrid(
    results: List<SearchViewModel.AnimeSearchResult>,
    onAnimeSelected: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(results) { anime ->
            AnimeCard(anime = anime, onClick = { onAnimeSelected(anime.id) })
        }
    }
}

@Composable
private fun MangaCard(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.coverImage)
                        .crossfade(true)
                        .build(),
                    contentDescription = manga.title.firstOrNull() ?: "Manga cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = manga.title.firstOrNull() ?: "Unknown",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun AnimeCard(anime: SearchViewModel.AnimeSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(anime.coverImage)
                        .crossfade(true)
                        .build(),
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = anime.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    selectedGenres: List<String>,
    selectedStatus: String,
    selectedSort: String,
    onStatusChanged: (String) -> Unit,
    onSortByChanged: (String) -> Unit,
    onGenreToggled: (String, Boolean) -> Unit
) {
    val genres = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Horror", "Mystery", "Romance", "Sci-Fi", "Slice of Life",
        "Sports", "Thriller", "Psychological", "Supernatural"
    )

    val statuses = listOf("", "ongoing", "completed", "hiatus", "cancelled")
    val sortOptions = listOf("relevance", "latest", "oldest", "title_asc", "title_desc")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Genres section
        Text("Genres", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                FilterChip(
                    selected = isSelected,
                    onClick = { onGenreToggled(genre, !isSelected) },
                    label = { Text(genre) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Status filter
        Spacer(modifier = Modifier.height(16.dp))
        Text("Status", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statuses.forEach { status ->
                val displayName = when (status) {
                    "" -> "Any"
                    else -> status.replaceFirstChar { it.uppercase() }
                }

                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onStatusChanged(status) },
                    label = { Text(displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Sort options
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sort By", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        var showSortDropdown by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { showSortDropdown = true }) {
                val sortName = when (selectedSort) {
                    "relevance" -> "Relevance"
                    "latest" -> "Latest Update"
                    "oldest" -> "Oldest Update"
                    "title_asc" -> "Title (A-Z)"
                    "title_desc" -> "Title (Z-A)"
                    else -> "Relevance"
                }
                Text(sortName)
            }

            DropdownMenu(
                expanded = showSortDropdown,
                onDismissRequest = { showSortDropdown = false }
            ) {
                sortOptions.forEach { option ->
                    val sortName = when (option) {
                        "relevance" -> "Relevance"
                        "latest" -> "Latest Update"
                        "oldest" -> "Oldest Update"
                        "title_asc" -> "Title (A-Z)"
                        "title_desc" -> "Title (Z-A)"
                        else -> option.replaceFirstChar { it.uppercase() }
                    }

                    DropdownMenuItem(
                        text = { Text(sortName) },
                        onClick = {
                            onSortByChanged(option)
                            showSortDropdown = false
                        }
                    )
                }
            }
        }
    }
}