package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.model.AnimeListState
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.viewmodel.AnimeViewModel

@Composable
fun AnimeScreen(
    navController: NavController,
    anilistClient: AnilistClient = AnilistClient()
) {
    val viewModel: AnimeViewModel = viewModel(
        factory = AnimeViewModel.Factory(anilistClient)
    )
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading && state.trending.isEmpty() -> LoadingIndicator()
        state.error != null -> ErrorMessage(
            message = state.error!!,
            onRetry = { viewModel.fetchAnimeLists() }
        )
        state.trending.isEmpty() && state.recentlyUpdated.isEmpty() && state.highRating.isEmpty() -> {
            Row {
                Text(
                    text = "No anime available",
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Check for internet connection ?",
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        else -> AnimeContent(state, navController, viewModel) // Pass viewModel
    }
}

@Composable
fun AnimeContent(
    state: AnimeListState,
    navController: NavController,
    viewModel: AnimeViewModel
) {
    val scrollState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isWideScreen = screenWidth > 600.dp  // Threshold for wide screen

    // Calculate how many columns to display
    val columns = if (isWideScreen) 3 else 2

    // Calculate card width based on columns
    val horizontalPadding = 32.dp  // 16.dp on each side
    val spacingBetweenCards = 16.dp
    val totalSpacing = spacingBetweenCards * (columns - 1)
    val availableWidth = screenWidth - horizontalPadding
    (availableWidth - totalSpacing) / columns

    val paginationThreshold = 5

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState
    ) {
        item { SectionTitle("🔥 Trending Now") }
        item {
            RectangularTrendingCarousel(
                animeList = state.trending,
                navController = navController
            )
        }

        item { SectionTitle("⭐ High Rating") }
        item { AnimeRow(animeList = state.highRating, navController = navController) }

        item { SectionTitle("🆕 Recently Updated") }

        // Group recently updated anime based on columns (2 or 3)
        val chunkedRecentAnime = state.recentlyUpdated.chunked(columns)
        itemsIndexed(chunkedRecentAnime) { index, animeRow ->
            // Use a Box with exact width constraints to ensure consistent sizing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Use weights for exact distribution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacingBetweenCards)
                ) {
                    // Display actual anime cards
                    for (i in 0 until columns) {
                        Box(
                            modifier = Modifier.weight(1f, fill = true)
                        ) {
                            if (i < animeRow.size) {
                                // Show actual card
                                AnimeCard(
                                    anime = animeRow[i],
                                    onClick = { navController.navigate("anime_detailed/${animeRow[i].anilistId}") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // Empty space with same dimensions
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            // Load more when near the end
            if (index >= chunkedRecentAnime.size - paginationThreshold &&
                state.recentlyUpdated.isNotEmpty() &&
                !state.isLoading) {
                LaunchedEffect(Unit) {
                    viewModel.fetchMoreRecentAnime()
                }
            }
        }

        // Loading indicator at bottom
        if (state.isLoading && state.recentlyUpdated.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun RectangularTrendingCarousel(animeList: List<Anime>, navController: NavController) {
    if (animeList.isEmpty()) return

    LocalConfiguration.current.screenWidthDp.dp
    val pagerState = rememberPagerState(pageCount = { animeList.size })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(250.dp)
    ) {
        // Main carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ImprovedTrendingCard(
                anime = animeList[page],
                onClick = { navController.navigate("anime_detailed/${animeList[page].anilistId}") },
                modifier = Modifier.fillMaxWidth()
            )
        }

//        // Left navigation arrow
//        if (pagerState.currentPage > 0) {
//            IconButton(
//                onClick = {
//                    coroutineScope.launch {
//                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
//                    }
//                },
//                modifier = Modifier
//                    .align(Alignment.CenterStart)
//                    .padding(start = 8.dp)
//                    .size(40.dp)
//                    .background(
//                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
//                        shape = CircleShape
//                    )
//            ) {
//                Icon(
//                    Icons.AutoMirrored.Filled.ArrowBack,
//                    contentDescription = "Previous",
//                    tint = MaterialTheme.colorScheme.onSurface
//                )
//            }
//        }
//
//        // Right navigation arrow
//        if (pagerState.currentPage < animeList.size - 1) {
//            IconButton(
//                onClick = {
//                    coroutineScope.launch {
//                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
//                    }
//                },
//                modifier = Modifier
//                    .align(Alignment.CenterEnd)
//                    .padding(end = 8.dp)
//                    .size(40.dp)
//                    .background(
//                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
//                        shape = CircleShape
//                    )
//            ) {
//                Icon(
//                    Icons.AutoMirrored.Filled.ArrowForward,
//                    contentDescription = "Next",
//                    tint = MaterialTheme.colorScheme.onSurface
//                )
//            }
//        }

        // Pagination dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(animeList.size) { dotIndex ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (dotIndex == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(dotIndex)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun ImprovedTrendingCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .height(240.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showPreview = true }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        // Keep existing card content
        Box(modifier = Modifier.fillMaxSize()) {
            // Banner image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.bannerImage)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Card content (cover image, title, info)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cover image with shadow
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(120.dp)
                        .height(180.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(y = 4.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(anime.coverImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = anime.title.firstOrNull() ?: "Unknown",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // Anime information
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = anime.title.firstOrNull() ?: "Unknown",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            anime.score?.takeIf { it > 0 }?.let {
                                Text(
                                    text = "⭐ %.1f".format(it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            anime.status?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        anime.seasonYear?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("View Details")
                    }
                }
            }
        }
    }

    // Show preview dialog when long-pressed
    if (showPreview) {
        AnimePreviewDialog(
            anime = anime,
            onDismiss = { showPreview = false },
            onViewDetails = {
                showPreview = false
                onClick()
            }
        )
    }
}

@Composable
fun AnimeRow(
    animeList: List<Anime>,
    navController: NavController,
    cardWidth: Dp = 150.dp
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList, key = { it.anilistId }) { anime ->
            AnimeCard(
                anime = anime,
                onClick = { navController.navigate("anime_detailed/${anime.anilistId}") },
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var showPreview by remember { mutableStateOf(false) }

    // Calculate height based on width with 1.4 ratio
    val coverImageRatio = 1.4f

    Card(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showPreview = true }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.coverImage)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title.firstOrNull() ?: "Unknown",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1/coverImageRatio), // Height = 1.4 * Width
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = anime.title.firstOrNull() ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Row with star rating (left) and status (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Season Year
                    anime.seasonYear?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    // Status (right aligned)
                    anime.status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } ?: Spacer(Modifier.width(0.dp))
                }
                // Score on the second row, sometimes missing.
                anime.score?.takeIf { it > 0 }?.let {
                    Text(
                        text = "⭐ %.1f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } ?: Spacer(Modifier.width(0.dp))
            }
        }
    }

    if (showPreview) {
        AnimePreviewDialog(
            anime = anime,
            onDismiss = { showPreview = false },
            onViewDetails = {
                showPreview = false
                onClick()
            }
        )
    }
}

@Composable
fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun AnimePreviewDialog(
    anime: Anime,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column {
                // Banner/Cover header section remains the same
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(anime.bannerImage ?: anime.coverImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.2f),
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )

                    // Title and info overlay - same as before
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Cover image
                        Card(
                            modifier = Modifier.size(100.dp, 150.dp),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(anime.coverImage)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = anime.title.firstOrNull(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Title and basic info - keep existing implementation
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = anime.title.firstOrNull() ?: "Unknown",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Alternative titles
                            if (anime.title.size > 1) {
                                Text(
                                    text = anime.title.drop(1).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Rating and status row
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                anime.score?.let {
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                        )
                                    ) {
                                        Text(
                                            text = "⭐ %.1f".format(it),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical =4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                anime.status?.let {
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when (it) {
                                                "RELEASING" -> Color(0xFF4CAF50).copy(alpha = 0.8f)
                                                "FINISHED" -> Color(0xFF2196F3).copy(alpha = 0.8f)
                                                "NOT_YET_RELEASED" -> Color(0xFFFFC107).copy(alpha = 0.8f)
                                                "CANCELLED" -> Color(0xFFF44336).copy(alpha = 0.8f)
                                                else -> Color(0xFF9E9E9E).copy(alpha = 0.8f)
                                            }
                                        )
                                    ) {
                                        Text(
                                            text = it.replace("_", " "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Year & Episodes info
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                anime.seasonYear?.let {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }

                                anime.totalEpisodes?.let {
                                    Text(
                                        text = " • $it episodes",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Content section with scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Genres
                    if (anime.genres.isNotEmpty()) {
                        Text(
                            text = "Genres",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        LazyRow(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(anime.genres) { genre ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(genre) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer, // very cool thing that should be reused
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Description with expand/collapse functionality
                    anime.description?.let { description ->
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        if (isDescriptionExpanded) {
                            // Expanded description in scrollable container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .padding(top = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            TextButton(
                                onClick = { isDescriptionExpanded = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Show Less")
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Collapse"
                                )
                            }
                        } else {
                            // Collapsed description with "Read More" prompt
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDescriptionExpanded = true }  // Correct placement in the modifier chain
                            ) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                if (description.length > 200) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "Read More",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Expand",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close")
                        }

                        Button(
                            onClick = onViewDetails,
                            modifier = Modifier.weight(2f)
                        ) {
                            Text("View Details")
                        }
                    }
                }
            }
        }
    }
}