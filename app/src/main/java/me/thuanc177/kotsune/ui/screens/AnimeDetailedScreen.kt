package me.thuanc177.kotsune.ui.screens

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharacterEdge
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.StreamingEpisode
import me.thuanc177.kotsune.viewmodel.AnimeDetailedViewModel
import kotlin.text.toFloat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeDetailedScreen(
    navController: NavController,
    animeId: Int
) {
    val anilistClient = remember { AnilistClient() }
    val viewModel: AnimeDetailedViewModel = viewModel(
        factory = AnimeDetailedViewModel.Factory(anilistClient, animeId)
    )
    val uiState by viewModel.uiState.collectAsState()

    // Add state variables for image dialogs
    var showFullBannerImage by remember { mutableStateOf(false) }
    var showFullCoverImage by remember { mutableStateOf(false) }

    // Define sections for tabs
    val sections = listOf("Overview", "Characters", "Episodes", "Related")

    // Define pager state
    val pagerState = rememberPagerState { sections.size }

    // Define coroutine scope for animations
    val coroutineScope = rememberCoroutineScope()

    // Replace isUserAuthenticated call
    val userIsAuthenticated = anilistClient.isUserAuthenticated()

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "An unknown error occurred")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.fetchAnimeDetails() }) {
                        Text("Retry")
                    }
                }
            }
        }
        uiState.anime != null -> {
            val anime = uiState.anime!!

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Section with Banner, Cover, and Action Buttons
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)) {

                    // Banner Image
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(anime.bannerImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showFullBannerImage = true },
                        contentScale = ContentScale.Crop
                    )

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.1f),
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )

                    // Back button
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Cover image & action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        // Cover Image
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp)
                                .clickable { showFullCoverImage = true },
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(anime.coverImage?.large)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Action buttons & info column
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            // Add to List button
                            var listMenuExpanded by remember { mutableStateOf(false) }

                            Box {
                                Button(
                                    onClick = { listMenuExpanded = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add to List")
                                }

                                DropdownMenu(
                                    expanded = listMenuExpanded,
                                    onDismissRequest = { listMenuExpanded = false },
                                    modifier = Modifier.width(200.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Set as Watching") },
                                        onClick = {
                                            if (userIsAuthenticated) {
                                                viewModel.addToList("WATCHING")
                                            } else {
                                                viewModel.redirectToAuthenticationScreen()
                                            }
                                            listMenuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Set as Planning") },
                                        onClick = {
                                            if (userIsAuthenticated) {
                                                viewModel.addToList("PLANNING")
                                            } else {
                                                viewModel.redirectToAuthenticationScreen()
                                            }
                                            listMenuExpanded = false
                                        }
                                    )
                                }
                            }

                            // Favorites button
                            Button(
                                onClick = {
                                    if (userIsAuthenticated) {
                                        viewModel.toggleFavorite()
                                    } else {
                                        viewModel.redirectToAuthenticationScreen()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (anime.isFavourite == true)
                                            Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = Color.Red
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${anime.favourites ?: 0} Favorites")
                                }
                            }
                        }
                    }
                }

                // Section tabs
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 0.dp,
                    divider = { Divider(thickness = 2.dp) }
                ) {
                    sections.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }

                // Section content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> OverviewSection(anime)
                        1 -> CharactersSection(anime, viewModel)
                        2 -> EpisodesSection(anime)
                        3 -> RelatedAnimeSection(anime, navController)
                    }
                }
            }

            // Full cover image dialog
            if (showFullCoverImage) {
                FullImageDialog(
                    imageUrl = anime.coverImage?.extraLarge,
                    onDismiss = { showFullCoverImage = false }
                )
            }

            // Full banner image dialog
            if (showFullBannerImage) {
                FullImageDialog(
                    imageUrl = anime.bannerImage,
                    onDismiss = { showFullBannerImage = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullImageDialog(imageUrl: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        content = {
            Box(modifier = Modifier.fillMaxSize(0.9f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    )
}


@Composable
fun StatusDistributionChart(distribution: List<AnilistTypes.StatusDistribution>, modifier: Modifier = Modifier) {
    // Basic implementation
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        distribution.forEach { status ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(status.amount.toFloat())
                        .width(20.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(text = status.status, style = MaterialTheme.typography.bodySmall)
                Text(text = "${status.amount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ScoreDistributionChart(distribution: List<AnilistTypes.ScoreDistribution>, modifier: Modifier = Modifier) {
    // Basic implementation
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        distribution.forEach { score ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(score.amount.toFloat())
                        .width(20.dp)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Text(text = "${score.score}", style = MaterialTheme.typography.bodySmall)
                Text(text = "${score.amount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EpisodePagination(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit
) {
    if (totalPages <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // First page
        IconButton(
            onClick = { onPageSelected(0) },
            enabled = currentPage > 0
        ) {
            Text("⏮", fontSize = 18.sp)
        }

        // Previous page
        IconButton(
            onClick = { onPageSelected(currentPage - 1) },
            enabled = currentPage > 0
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
        }

        // Page numbers
        Row(horizontalArrangement = Arrangement.Center) {
            for (i in 0 until totalPages) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .clickable { onPageSelected(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (i + 1).toString(),
                        color = if (i == currentPage) Color.White
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Next page
        IconButton(
            onClick = { onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages - 1
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Next")
        }

        // Last page
        IconButton(
            onClick = { onPageSelected(totalPages - 1) },
            enabled = currentPage < totalPages - 1
        ) {
            Text("⏭", fontSize = 18.sp)
        }
    }
}

private fun AnimeDetailedViewModel.redirectToAuthenticationScreen() {
    Log.d("AnimeDetailedScreen", "Redirecting to authentication screen")
}

private fun AnimeDetailedViewModel.showCharacterDetails(characterEdge: CharacterEdge) {
    Log.d("AnimeDetailedScreen", "Show character details: ${characterEdge.node?.name?.full}")
}

@Composable
fun OverviewSection(anime: AnimeDetailed) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Title section
        var isEnglishTitleExpanded by remember { mutableStateOf(false) }
        var isNativeTitleExpanded by remember { mutableStateOf(false) }

        // English title
        anime.title?.english?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = if (isEnglishTitleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { isEnglishTitleExpanded = !isEnglishTitleExpanded }
            )
        }

        // Native title
        anime.title?.native?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                maxLines = if (isNativeTitleExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isNativeTitleExpanded = !isNativeTitleExpanded }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Genres
        if (!anime.genres.isNullOrEmpty()) {
            Text(
                text = "Genres",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(anime.genres!!) { genre ->
                    SuggestionChip(
                        onClick = { /* TODO: Implement genre search */ },
                        label = { Text(genre) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Tags
        if (!anime.tags.isNullOrEmpty()) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(anime.tags!!) { tag ->
                    SuggestionChip(
                        onClick = { /* TODO: Implement tag search */ },
                        label = {
                            Text(
                                text = tag.rank?.let { "${tag.name} ${it}%" } ?: tag.name
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        // Info table
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Display all available information
                anime.averageScore?.let {
                    InfoRow("Score", "$it%")
                }
                anime.format?.let {
                    InfoRow("Format", it)
                }
                anime.duration?.let {
                    InfoRow("Duration", "$it min/ep")
                }
                anime.episodes?.let {
                    InfoRow("Episodes", it.toString())
                }
                anime.startDate?.let { date ->
                    val formattedDate = buildString {
                        date.year?.let { append(it) }
                        date.month?.let { append("-", it) }
                        date.day?.let { append("-", it) }
                    }
                    if (formattedDate.isNotEmpty()) {
                        InfoRow("Start Date", formattedDate)
                    }
                }
                anime.countryOfOrigin?.let {
                    InfoRow("Country", it)
                }
                anime.status?.let {
                    InfoRow("Status", it)
                }
                anime.seasonYear?.let {
                    InfoRow("Season Year", it.toString())
                }
            }
        }

        // Description
        anime.description?.let { description ->
            var isDescriptionExpanded by remember { mutableStateOf(false) }

            Text(
                text = "Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { isDescriptionExpanded = !isDescriptionExpanded }
            )

            if (description.length > 200 && !isDescriptionExpanded) {
                TextButton(
                    onClick = { isDescriptionExpanded = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Read More")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status distribution
        anime.stats?.statusDistribution?.let { distribution ->
            Text(
                text = "Watch Status Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            StatusDistributionChart(distribution, modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 16.dp)
            )
        }

        // Score distribution
        anime.stats?.scoreDistribution?.let { distribution ->
            Text(
                text = "Score Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            ScoreDistributionChart(distribution, modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 16.dp)
            )
        }

        // Trailer
        anime.trailer?.let { trailer ->
            if (trailer.id != null && trailer.site != null) {
                Text(
                    text = "Trailer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )

                val uriHandler = LocalUriHandler.current
                val trailerUrl = when(trailer.site) {
                    "youtube" -> "https://www.youtube.com/watch?v=${trailer.id}"
                    "dailymotion" -> "https://www.dailymotion.com/video/${trailer.id}"
                    else -> null
                }

                trailerUrl?.let { url ->
                    Button(
                        onClick = { uriHandler.openUri(url) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Watch Trailer")
                    }
                }
            }
        }
    }
}

@Composable
fun CharactersSection(anime: AnimeDetailed, viewModel: AnimeDetailedViewModel) {
    if (anime.characters == null || anime.characters.edges.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No characters information available")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(anime.characters.edges) { characterEdge ->
            CharacterCard(characterEdge, viewModel)
        }
    }
}

@Composable
fun CharacterCard(characterEdge: CharacterEdge, viewModel: AnimeDetailedViewModel) {
    val character = characterEdge.node ?: return
    val voiceActor = characterEdge.voiceActors.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Show character details
                    viewModel.showCharacterDetails(characterEdge)
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Character image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(character.image?.large)
                    .crossfade(true)
                    .build(),
                contentDescription = character.name?.full,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp, 112.dp)  // Ratio 1.4:1
                    .clip(RoundedCornerShape(4.dp))
            )

            // Character info
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = character.name?.full ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = characterEdge.role ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Voice actor info if available
                voiceActor?.let { va ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Voice Actor",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            Text(
                                text = va.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Origin",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            Text(
                                text = va.homeTown ?: va.languageV2 ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Voice actor image if available
            voiceActor?.let { va ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(va.image?.large)
                        .crossfade(true)
                        .build(),
                    contentDescription = va.name?.full,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(60.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodesSection(anime: AnimeDetailed) {
    if (anime.streamingEpisodes.isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No episodes information available")
        }
        return
    }

    val episodesPerPage = 5
    val episodePages = anime.streamingEpisodes!!.chunked(episodesPerPage)
    val pagerState = rememberPagerState { episodePages.size }
    val coroutineScope = rememberCoroutineScope()  // Add this for animations

    Column(modifier = Modifier.fillMaxSize()) {
        // Episodes pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(episodePages[page]) { episode ->
                    EpisodeCard(episode)
                }
            }
        }

        // Navigation controls
        EpisodePagination(
            currentPage = pagerState.currentPage,
            totalPages = episodePages.size,
            onPageSelected = { page ->
                // Use coroutineScope to animate to the selected page
                coroutineScope.launch {
                    pagerState.animateScrollToPage(page)
                }
            }
        )
    }
}

@Composable
fun EpisodeCard(episode: StreamingEpisode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            // Thumbnail as background
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(episode.thumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )

            // Episode title
            Text(
                text = episode.title ?: "Unknown Episode",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun RelatedAnimeSection(anime: AnimeDetailed, navController: NavController) {
    if (anime.recommendations == null || anime.recommendations.edges.isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No related anime available")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(anime.recommendations.edges!!) { edge ->
            edge.node?.mediaRecommendation?.let { relatedAnime ->
                RelatedAnimeCard(relatedAnime, navController)
            }
        }
    }
}

@Composable
fun RelatedAnimeCard(
    anime: AnilistTypes.AnilistMedia,
    navController: NavController
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("anime_detailed/${anime.id}") },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.coverImage?.large)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title?.english ?: anime.title?.romaji,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp, 120.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            // Anime information
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                // Title
                Text(
                    text = anime.title?.english ?: anime.title?.romaji ?: "Unknown Title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Info Row (Year, Episodes, Score)
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year
                    anime.seasonYear?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Episodes
                    anime.episodes?.let {
                        Text(
                            text = " • $it eps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Score if available
                    anime.averageScore?.let {
                        if (it > 0) {
                            Text(
                                text = " • ⭐ ${it/10.0}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Genres (first 2-3)
                anime.genres?.let { genres ->
                    if (genres.isNotEmpty()) {
                        Text(
                            text = genres.take(3).joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status chip
                anime.status?.let { status ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (status) {
                                    "RELEASING" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                                    "FINISHED" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    "NOT_YET_RELEASED" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status.replace("_", " "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Arrow icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View details",
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}