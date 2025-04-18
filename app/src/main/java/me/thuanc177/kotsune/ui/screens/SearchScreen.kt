package me.thuanc177.kotsune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.R
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
        showFilters = false  // Close filter menu when search is performed

        // First set loading state
        searchViewModel.resetSearch()

        // Then perform the appropriate search based on current tab
        if (selectedTabIndex == 0) {
            searchViewModel.searchAnime(searchQuery, selectedGenres, selectedStatus, sortBy)
        } else {
            searchViewModel.searchManga(searchQuery, selectedGenres, selectedStatus, sortBy)
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
            placeholder = { Text("Search ${if (selectedTabIndex == 0) "anime" else "manga"}...") },
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
                        searchViewModel.searchAnime(searchQuery, selectedGenres, selectedStatus, sortBy)
                    }
                },
                text = { Text("Anime") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = {
                    selectedTabIndex = 1
                    if (searchQuery.isNotEmpty()) {
                        searchViewModel.searchManga(searchQuery, selectedGenres, selectedStatus, sortBy)
                    }
                },
                text = { Text("Manga") }
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
                        AnimeResultsGrid(
                            results = animeResults,
                            onAnimeSelected = { animeId ->
                                navController.navigate(Screen.AnimeDetail.createRoute(animeId.toString()))
                            }
                        )
                    } else {
                        // Display anime results
                        MangaResultsGrid(
                            results = mangaResults,
                            onMangaSelected = { mangaId ->
                                navController.navigate(Screen.MangaDetail.createRoute(mangaId))
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
    val isSmallSet = results.size < 9

    LazyVerticalGrid(
        columns = if (isSmallSet) GridCells.Fixed(1) else GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(results) { manga ->
            if (isSmallSet) {
                HorizontalMangaCard(manga = manga, onClick = { onMangaSelected(manga.id) })
            } else {
                VerticalMangaCard(manga = manga, onClick = { onMangaSelected(manga.id) })
            }
        }
    }
}

@Composable
private fun VerticalMangaCard(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.poster)
                        .crossfade(true)
                        .error(R.drawable.ic_launcher_background)
                        .fallback(R.drawable.ic_launcher_background)
                        .build(),
                    contentDescription = manga.title.firstOrNull() ?: "Manga cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = manga.title.firstOrNull() ?: "Unknown",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(8.dp)
            )

            // Status
            val status = manga.status ?: "unknown"
            Text(
                text = status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = when (status.lowercase()) {
                    "ongoing" -> MaterialTheme.colorScheme.primary
                    "completed" -> MaterialTheme.colorScheme.tertiary
                    "cancelled", "hiatus" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // release year
            manga.year?.let { year ->
                Text(
                    text = "Released: $year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HorizontalMangaCard(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = manga.title.firstOrNull() ?: "Manga cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }


            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = manga.title.firstOrNull() ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                val status = manga.status ?: "unknown"
                Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status.lowercase()) {
                        "ongoing" -> MaterialTheme.colorScheme.primary
                        "completed" -> MaterialTheme.colorScheme.tertiary
                        "cancelled", "hiatus" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (manga.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        manga.tags.take(3).forEach { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(tag.name, maxLines = 1) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                manga.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                manga.year?.let { year ->
                    Text(
                        text = "Released: $year",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeResultsGrid(
    results: List<SearchViewModel.AnimeSearchResult>,
    onAnimeSelected: (Int) -> Unit
) {
    val isSmallSet = results.size < 9

    LazyVerticalGrid(
        columns = if (isSmallSet) GridCells.Fixed(1) else GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(results) { anime ->
            if (isSmallSet) {
                HorizontalAnimeCard(anime = anime, onClick = { onAnimeSelected(anime.id) })
            } else {
                VerticalAnimeCard(anime = anime, onClick = { onAnimeSelected(anime.id) })
            }
        }
    }
}

@Composable
private fun VerticalAnimeCard(anime: SearchViewModel.AnimeSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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

                // Rating badge in top-right corner
                anime.rating?.let { rating ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = anime.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                anime.seasonYear?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalAnimeCard(anime: SearchViewModel.AnimeSearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
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

            // Content section
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp)
                    .weight(1f)
            ) {
                // Title with rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    anime.rating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Release year
                anime.seasonYear?.let { year ->
                    Text(
                        text = "Released: $year",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status
                Text(
                    text = anime.status ?: "Unknown status",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (anime.status?.lowercase()) {
                        "ongoing" -> MaterialTheme.colorScheme.primary
                        "completed" -> MaterialTheme.colorScheme.tertiary
                        "cancelled", "hiatus" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!anime.genres.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        anime.genres.take(3).forEach { genre ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(genre, maxLines = 1) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            }
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
                        .data(manga.poster)
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
            // Cover image
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

                // Rating badge in top-right corner
                anime.rating?.let { rating ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Title and info section
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = anime.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                anime.seasonYear?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
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