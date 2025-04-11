package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.data.model.AnimeListState
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
            Text(
                text = "No anime available",
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
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

    // Calculate card width more precisely
    val horizontalPadding = 32.dp  // 16.dp on each side
    val spacingBetweenCards = 16.dp
    val availableWidth = screenWidth - horizontalPadding
    val cardWidth = (availableWidth - spacingBetweenCards) / 2

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

        // Group recently updated anime in pairs
        val chunkedRecentAnime = state.recentlyUpdated.chunked(2)
        itemsIndexed(chunkedRecentAnime) { index, animePair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(spacingBetweenCards)
            ) {
                animePair.forEach { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { navController.navigate("anime_detail/${anime.id}") },
                        modifier = Modifier.width(cardWidth)
                    )
                }

                // Add spacer if there's only one item in the row
                if (animePair.size == 1) {
                    Spacer(modifier = Modifier.width(cardWidth))
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
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val firstVisibleItemIndex = scrollState.firstVisibleItemIndex
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Navigation buttons
        IconButton(
            onClick = {
                if (firstVisibleItemIndex > 0) {
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(firstVisibleItemIndex - 1)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .zIndex(1f)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = {
                if (firstVisibleItemIndex < animeList.size - 1) {
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(firstVisibleItemIndex + 1)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .zIndex(1f)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(animeList, key = { _, anime -> anime.id }) { index, anime ->
                ImprovedTrendingCard(
                    anime = anime,
                    onClick = { navController.navigate("anime_detail/${anime.id}") },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }

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
                            color = if (dotIndex == firstVisibleItemIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
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
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
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
                                    style = MaterialTheme.typography.bodySmall,
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
        items(animeList, key = { it.id }) { anime ->
            AnimeCard(
                anime = anime,
                onClick = { navController.navigate("anime_detail/${anime.id}") },
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var showPreview by remember { mutableStateOf(false) }

    Card(
        modifier = modifier      // Use the passed modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showPreview = true }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        // Existing card content remains the same
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.coverImage)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title.firstOrNull() ?: "Unknown",
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = anime.title.firstOrNull() ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                anime.score?.takeIf { it > 0 }?.let {
                    Text(
                        text = "⭐ %.1f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = anime.status ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
                                style = MaterialTheme.typography.headlineSmall,
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

                                anime.episodes?.let {
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
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
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