package me.thuanc177.kotsune.ui.screens

import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import me.thuanc177.kotsune.libs.animeProvider.allanime.HttpClient
import me.thuanc177.kotsune.model.UiEpisodeModel
import me.thuanc177.kotsune.ui.components.EpisodePagination
import me.thuanc177.kotsune.ui.components.VideoPlayer
import me.thuanc177.kotsune.ui.components.findActivity
import me.thuanc177.kotsune.viewmodel.EpisodesViewModel
import me.thuanc177.kotsune.viewmodel.WatchAnimeViewModel

object AnimeProviderFactory {
    fun create(providerName: String = "allanime"): AnimeProvider {
        return when (providerName.lowercase()) {
            "allanime" -> {
                val httpClient = object : HttpClient {
                    override suspend fun get(url: String, headers: Map<String, String>): String {
                        return java.net.URL(url).openConnection().apply {
                            headers.forEach { (key, value) -> setRequestProperty(key, value) }
                            connectTimeout = 10000
                            readTimeout = 10000
                        }.getInputStream().bufferedReader().use { it.readText() }
                    }

                    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
                        return (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"
                            doOutput = true
                            headers.forEach { (key, value) -> setRequestProperty(key, value) }
                            connectTimeout = 10000
                            readTimeout = 10000
                            outputStream.write(body.toByteArray())
                        }.inputStream.bufferedReader().use { it.readText() }
                    }
                }
                AllAnimeAPI(httpClient)
            }
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
    }
}

@UnstableApi
@Composable
fun WatchAnimeScreen(
    showId: String,
    episodeNumber: Float,
    navController: NavController,
    episodesViewModel: EpisodesViewModel = viewModel(),
    watchViewModel: WatchAnimeViewModel = viewModel(
        factory = WatchAnimeViewModel.Factory(episodesViewModel)
    )
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // UI State
    val uiState by watchViewModel.uiState.collectAsState()

    // Track orientation changes
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Handle screen orientation locking
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Load anime data when component mounts
    LaunchedEffect(Unit) {
        Log.d("WatchAnimeScreen", "Initial load with showId=$showId, episode=$episodeNumber")
        watchViewModel.setInitialAnimeData(showId, episodeNumber)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Video Player container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
        ) {
            if (uiState.isLoading || uiState.isServerLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                watchViewModel.loadEpisode(episodeNumber)
                            }
                        }
                    ) {
                        Text("Retry")
                    }
                }
            } else if (uiState.currentStreamUrl != null) {
                // Get currently selected server and its data
                val selectedServer = uiState.servers.getOrNull(uiState.selectedServerIndex)

                // Parse relevant stream data
                val subtitles = selectedServer?.subtitles ?: emptyList()
                val subtitlePairs = subtitles.map { subtitle ->
                    Pair(subtitle.language, subtitle.url)
                }

                val qualityOptions = selectedServer?.links?.map { link ->
                    Pair(link.quality ?: "Auto", link.link)
                } ?: emptyList()

                VideoPlayer(
                    streamUrl = uiState.currentStreamUrl ?: "",
                    title = "${uiState.animeTitle} - Episode ${uiState.currentEpisode}",
                    subtitleUrls = subtitlePairs,
                    qualityOptions = qualityOptions,
                    onBackPress = { navController.popBackStack() },
                    customHeaders = selectedServer?.headers ?: mapOf(
                        "Referer" to "https://allanime.to",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.82 Safari/537.36"
                    ),
                    onPositionChanged = { position ->
                        watchViewModel.updatePlayerPosition(position)
                    },
                    initialPosition = if (uiState.isServerLoading) uiState.playerPosition else 0L,
                    onServerError = {
                        // Attempt to switch to the next available server
                        watchViewModel.tryNextServer()
                    }
                )
            }
        }

        // Only show UI controls in portrait mode
        if (!isLandscape) {
            // Title and episode info
            Text(
                text = "${uiState.animeTitle} - Episode ${uiState.currentEpisode}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Server selection
            if (!uiState.isLoading && uiState.error == null && uiState.servers.isNotEmpty()) {
                Text(
                    text = "Select Server (${uiState.servers.size} available)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.servers.size) { index ->
                        val server = uiState.servers[index]
                        Button(
                            onClick = {
                                if (uiState.selectedServerIndex != index) {
                                    watchViewModel.switchServer(index)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.selectedServerIndex == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (uiState.selectedServerIndex == index)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = server.server,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Episodes section
            if (!uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Mock AnimeDetailed for the episodes section
                    // Mock AnimeDetailed for the episodes section
                    AnimeDetailed(
                        id = uiState.anilistId,
                        title = AnilistTypes.AnimeTitle(
                            english = uiState.animeTitle,
                            romaji = uiState.animeTitle,
                            native = null
                        ),
                        episodes = uiState.totalEpisodes,
                        coverImage = null,
                        bannerImage = null,
                    )

                    ModifiedEpisodesSection(
                        episodes = uiState.episodes,
                        currentEpisode = uiState.currentEpisode,
                        navController = navController,
                        onEpisodeSelected = { episode ->
                            if (episode.number.toInt() != uiState.currentEpisode.toInt()) {
                                watchViewModel.loadEpisode(episode.number)
                            }
                        }
                    )
                }
            } else {
                // Loading state for episodes section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ModifiedEpisodesSection(
    episodes: List<UiEpisodeModel>,
    currentEpisode: Float,
    navController: NavController,
    onEpisodeSelected: (UiEpisodeModel) -> Unit
) {
    // Display episodes with pagination
    val episodesPerPage = 10
    
    if (episodes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading episodes...")
            }
        }
        return
    }
    
    val episodePages = episodes.chunked(episodesPerPage)
    val pagerState = rememberPagerState { episodePages.size }
    val coroutineScope = rememberCoroutineScope()

    // Calculate initial page based on current episode - convert Float to Int properly
    val initialPage = if (episodes.isNotEmpty()) {
        // Find index of the current episode in the list
        val episodeIndex = episodes.indexOfFirst { 
            // Compare with tolerance for float issues
            val diff = it.number - currentEpisode
            diff > -0.001f && diff < 0.001f
        }
        
        if (episodeIndex >= 0) {
            episodeIndex / episodesPerPage
        } else {
            // Fallback calculation
            val pageNum = ((currentEpisode - 1).toInt() / episodesPerPage).coerceAtLeast(0)
            Log.d("EpisodesSection", "Using fallback page calculation: $pageNum")
            pageNum
        }
    } else 0

    // Set initial page
    LaunchedEffect(initialPage, episodePages.size) {
        if (episodePages.isNotEmpty() && initialPage < episodePages.size) {
            Log.d("EpisodesSection", "Scrolling to page $initialPage")
            pagerState.scrollToPage(initialPage)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (episodePages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No episodes available")
            }
        } else {
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
                        ModifiedEpisodeCard(
                            episode = episode,
                            isCurrentEpisode = episode.number == currentEpisode,
                            onClick = { onEpisodeSelected(episode) }
                        )
                    }
                }
            }

            // Only show pagination if there are multiple pages
            if (episodePages.size > 1) {
                EpisodePagination(
                    currentPage = pagerState.currentPage,
                    totalPages = episodePages.size,
                    onPageSelected = { page ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ModifiedEpisodeCard(
    episode: UiEpisodeModel,
    isCurrentEpisode: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)  // Fixed height to match EnhancedEpisodeCard
            .padding(8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail background image
            episode.thumbnail?.let { thumbnail ->
                if (thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnail)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Add a semi-transparent overlay for better text visibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )
                }
            }

            // Format episode number to remove .0 for whole numbers
            val episodeNumberDisplay = if (episode.number % 1 == 0f) {
                episode.number.toInt().toString()
            } else {
                episode.number.toString()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode number in circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isCurrentEpisode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = episodeNumberDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrentEpisode) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Episode info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Episode $episodeNumberDisplay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (episode.thumbnail != null) Color.White else MaterialTheme.colorScheme.onSurface
                    )

                    // Add episode title if available
                    episode.title?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (episode.thumbnail != null)
                                    Color.White.copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Episode description (if available)
                    episode.description?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (episode.thumbnail != null)
                                    Color.White.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // Upload date at the bottom
                    episode.uploadDate?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (episode.thumbnail != null)
                                    Color.White.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Play icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isCurrentEpisode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isCurrentEpisode)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
