package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.model.MangaListState
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.viewmodel.MangaViewModel

@Composable
fun MangaScreen(
    navController: NavController,
    mangaDexAPI: MangaDexAPI = MangaDexAPI()
) {
    val viewModel: MangaViewModel = viewModel(
        factory = MangaViewModel.MangaListFactory(mangaDexAPI)
    )
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.latestUpdates.isEmpty() -> LoadingIndicator()
            state.error != null -> ErrorMessage(
                message = state.error!!,
                onRetry = { viewModel.fetchMangaLists() }
            )
            state.popular.isEmpty() && state.latestUpdates.isEmpty() -> {
                Text(
                    text = "No manga available",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> MangaContent(state, navController, viewModel)
        }
    }
}

@Composable
fun MangaContent(
    state: MangaListState,
    navController: NavController,
    viewModel: MangaViewModel
) {
    val scrollState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 48.dp) / 2
    val paginationThreshold = 50

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = scrollState
    ) {
        item { SectionTitle("🔥 Popular Manga") }
        item {
            RectangularMangaCarousel(
                mangaList = state.popular,
                navController = navController
            )
        }

        item { SectionTitle("🆕 Latest Manga") }

        val chunkedLatestUpdates = state.latestUpdates.chunked(2)
        itemsIndexed(chunkedLatestUpdates) { index, mangaPair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                mangaPair.forEach { manga ->
                    MangaCard(
                        manga = manga,
                        onClick = { navController.navigate("manga_detail/${manga.id}") },
                        modifier = Modifier.width(cardWidth)
                    )
                }
                if (mangaPair.size == 1) {
                    Spacer(modifier = Modifier.width(cardWidth))
                }
            }

            if (index >= chunkedLatestUpdates.size - 5 && state.latestUpdates.size >= paginationThreshold && !state.isLoading) {
                LaunchedEffect(Unit) {
                    viewModel.fetchMoreLatestUpdates()
                }
            }
        }

        if (state.isLoading && state.latestUpdates.isNotEmpty()) {
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
fun RectangularMangaCarousel(mangaList: List<Manga>, navController: NavController) {
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val firstVisibleItemIndex = scrollState.firstVisibleItemIndex
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(horizontal = 16.dp)
    ) {
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
                if (firstVisibleItemIndex < mangaList.size - 1) {
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
            itemsIndexed(mangaList, key = { _, manga -> manga.id }) { _, manga ->
                FeaturedMangaCard(
                    manga = manga,
                    onClick = { navController.navigate("manga_detail/${manga.id}") },
                    modifier = Modifier.width(cardWidth)
                )
            }
        }

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
            repeat(mangaList.size) { dotIndex ->
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
fun FeaturedMangaCard(
    manga: Manga,
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
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )

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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
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
                            .data(manga.poster)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title.firstOrNull() ?: "Unknown",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = manga.title.firstOrNull() ?: "Unknown",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = manga.status.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        manga.year?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Rating: ${manga.contentRating.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
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
    if (showPreview) {
        MangaPreviewDialog(
            manga = manga,
            onDismiss = { showPreview = false },
            onViewDetails = {
                showPreview = false
                onClick()
            }
        )
    }
}

@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreview by remember { mutableStateOf(false) }

    val (ageRating, badgeColor) = when (manga.contentRating.lowercase()) {
        "suggestive" -> Pair("16+", Color(0xFFeb36ff))
        "erotica" -> Pair("18+", Color.Red)
        else -> Pair(null, null)
    }

    Card(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showPreview = true }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // Use surface color instead of transparent
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = when (manga.contentRating.lowercase()) {
                "suggestive" -> Color(0xFFeb36ff)
                "erotica" -> Color.Red
                else -> MaterialTheme.colorScheme.primary
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Remove elevation completely
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(manga.poster)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title.firstOrNull() ?: "Unknown",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Age rating badge
                    ageRating?.let {
                        Surface(
                            color = badgeColor ?: Color.Black,
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(1f)
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Status badge in the top-right corner
                    Surface(
                        color = when (manga.status.lowercase()) {
                            "ongoing" -> Color(0xFF4CAF50)
                            "completed" -> Color(0xFF2196F3)
                            "hiatus" -> Color(0xFFFFA500)
                            "cancelled" -> Color(0xFFF44336)
                            else -> Color(0xFF9E9E9E)
                        },
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = manga.status.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = manga.title.firstOrNull() ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Row with year
                    manga.year?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = "Year",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$it",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPreview) {
        MangaPreviewDialog(
            manga = manga,
            onDismiss = { showPreview = false },
            onViewDetails = {
                showPreview = false
                onClick()
            }
        )
    }
}

@Composable
fun MangaPreviewDialog(
    manga: Manga,
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
                // Header section with cover
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Background - use a gradient since manga doesn't have bannerImage
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
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

                    // Title and info overlay
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
                                    .data(manga.poster)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = manga.title.firstOrNull(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Title and basic info
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = manga.title.firstOrNull() ?: "Unknown",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Alternative titles
                            if (manga.title.size > 1) {
                                Text(
                                    text = manga.title.drop(1).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Status and content rating
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (manga.status.lowercase()) {
                                            "ongoing" -> Color(0xFF4CAF50).copy(alpha = 0.8f)
                                            "completed" -> Color(0xFF2196F3).copy(alpha = 0.8f)
                                            "hiatus" -> Color(0xFFFFC107).copy(alpha = 0.8f)
                                            "cancelled" -> Color(0xFFF44336).copy(alpha = 0.8f)
                                            else -> Color(0xFF9E9E9E).copy(alpha = 0.8f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = manga.status.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Content rating badge
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (manga.contentRating.lowercase()) {
                                            "safe" -> Color(0xFF4CAF50).copy(alpha = 0.8f)
                                            "suggestive" -> Color(0xFFFFC107).copy(alpha = 0.8f)
                                            "erotica" -> Color(0xFFF44336).copy(alpha = 0.8f)
                                            else -> Color(0xFF9E9E9E).copy(alpha = 0.8f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = manga.contentRating.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Year and chapter info
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                manga.year?.let {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }

                                manga.lastChapter?.let {
                                    Text(
                                        text = " • Ch. $it",
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
                    // Description with expand/collapse functionality
                    if (manga.description.isNotEmpty()) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
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
                                        text = manga.description,
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
                                    .clickable { isDescriptionExpanded = true }
                            ) {
                                Text(
                                    text = manga.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                if (manga.description.length > 200) {
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

                    // Last updated info
                    manga.lastUpdated?.let {
                        Text(
                            text = "Last updated: ${it.substringBefore('T')}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
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
