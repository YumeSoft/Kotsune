package me.thuanc177.kotsune.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import me.thuanc177.kotsune.libs.animeProvider.allanime.HttpClient
import me.thuanc177.kotsune.ui.components.EpisodePagination
import me.thuanc177.kotsune.ui.components.VideoPlayer
import me.thuanc177.kotsune.viewmodel.WatchAnimeViewModel
import kotlin.text.get

/**
 * Factory to create various anime providers
 */
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
            // Add more providers as needed
            else -> throw IllegalArgumentException("Unknown provider: $providerName")
        }
    }
}

@Composable
fun EpisodeButton(
    episodeNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.aspectRatio(1f)
    ) {
        Text(
            text = "$episodeNumber",
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@UnstableApi
@Composable
fun WatchAnimeScreen(
    navController: NavController,
    animeTitle: String,
    episodeNumber: Int = 1,
    anilistId: Int = -1,
    viewModel: WatchAnimeViewModel = viewModel()
) {
    // Existing state variables
    val coroutineScope = rememberCoroutineScope()
    LocalContext.current

    // Add a reference to store the current player position

    // Rest of your existing state variables
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var animeId by remember { mutableStateOf<String?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var currentEpisode by remember { mutableStateOf(episodeNumber) }
    var servers by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf(0) }
    var totalEpisodes by remember { mutableStateOf(100) }
    var episodesList by remember { mutableStateOf((1..totalEpisodes).toList()) }
    var animeProvider by remember { mutableStateOf<AnimeProvider?>(null) }
    var isServerLoading by remember { mutableStateOf(false) }
    var playerPosition by remember { mutableStateOf(0L) } // Track player position

    // Format anime title
    val formattedTitle = remember(animeTitle) {
        animeTitle.replace("_", " ")
    }

    // Track orientation changes
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Only load data when necessary, not on every orientation change
    LaunchedEffect(animeTitle, currentEpisode, anilistId) {
        if (viewModel.getCurrentEpisode() != currentEpisode ||
            viewModel.getCurrentAnimeId() != animeId) {

            isLoading = true
            error = null

            try {
                // Set current state in viewModel to avoid reloading
                viewModel.setCurrentEpisode(currentEpisode)

                // First, check if we have a previously used provider
                animeProvider = viewModel.getProvider() ?: AnimeProviderFactory.create()
                viewModel.setProvider(animeProvider!!)

                // Check if animeId is cached
                animeId = viewModel.getCachedAnimeId(formattedTitle)

                // If no cached ID, search for the anime
                if (animeId == null) {
                    val animeGotFromProvider = animeProvider!!.searchForAnime(anilistId, formattedTitle)

                    if (animeGotFromProvider.isSuccess) {
                        val animeResult = animeGotFromProvider.getOrNull()
                        if (animeResult != null) {
                            animeId = animeResult.alternativeId
                            viewModel.cacheAnimeId(formattedTitle, animeId ?: "")
                            viewModel.setCurrentAnimeId(animeId)
                        }
                    } else {
                        throw Exception("Failed to find anime: ${animeGotFromProvider.exceptionOrNull()?.message ?: "Unknown error"}")
                    }
                }

                // Get episode streams
                val streamsResult = animeProvider!!.getEpisodeStreams(
                    animeId = animeId!!,
                    episode = currentEpisode.toString()
                )

                if (streamsResult.isSuccess) {
                    val streamsList = streamsResult.getOrNull()
                    if (streamsList.isNullOrEmpty()) {
                        throw Exception("No streams available for this episode")
                    }

                    // Store all available servers
                    servers = streamsList

                    // Log the number of servers found
                    Log.d("WatchAnimeScreen", "Found ${servers.size} servers")
                    servers.forEachIndexed { index, server ->
                        Log.d("WatchAnimeScreen", "Server $index: ${server.server} with ${server.links.size} links")
                    }

                    // Reset selected server when changing episodes
                    selectedServer = 0

                    // Get the first stream URL from the first server
                    val firstServer = servers.firstOrNull()
                    val firstStream = firstServer?.links?.firstOrNull()

                    if (firstStream != null) {
                        streamUrl = firstStream.link
                    }
                } else {
                    throw Exception("Failed to get streams: ${streamsResult.exceptionOrNull()?.message ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                Log.e("WatchAnimeScreen", "Error loading anime", e)
                error = "Error: ${e.message}"
                streamUrl = null
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Video Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black)
        ) {
            if (isLoading || isServerLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (streamUrl != null) {
                val subtitles = servers.getOrNull(selectedServer)?.subtitles ?: emptyList()
                val subtitlePairs = subtitles.map { subtitle ->
                    Pair(subtitle.language, subtitle.url)
                }

                val qualityOptions = servers.getOrNull(selectedServer)?.links?.map { link ->
                    Pair(link.quality ?: "Auto", link.link)
                } ?: emptyList<Pair<String, String>>()

                VideoPlayer(
                    streamUrl = streamUrl ?: "",
                    subtitleUrls = subtitlePairs,
                    qualityOptions = qualityOptions,
                    onBackPress = { navController.popBackStack() },
                    customHeaders = mapOf(
                        "Referer" to "https://allanime.to",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.82 Safari/537.36"
                    )
                )
            } else {
                Text(
                    text = "No video stream available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Only show UI controls in portrait mode or if we're not in fullscreen
        if (!isLandscape) {
            // Title and episode info
            Text(
                text = "$formattedTitle - Episode $currentEpisode",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            // Server selection - Make sure we're showing all servers
            if (!isLoading && error == null && servers.isNotEmpty()) {
                Text(
                    text = "Select Server (${servers.size} available)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Server selection in the LazyRow
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(servers.size) { index ->
                        val server = servers[index]
                        Button(
                            onClick = {
                                if (selectedServer != index) {
                                    // Use our state variable instead of direct player reference
                                    selectedServer = index
                                    isServerLoading = true

                                    coroutineScope.launch {
                                        try {
                                            val serverLinks = server.links
                                            if (serverLinks.isNotEmpty()) {
                                                val firstLink = serverLinks.first()
                                                streamUrl = firstLink.link
                                                Log.d("WatchAnimeScreen", "Switched to server: ${server.server}")
                                            } else {
                                                Log.e("WatchAnimeScreen", "No links found for server: ${server.server}")
                                            }
                                        } catch (e: Exception) {
                                            error = "Error loading server: ${e.message}"
                                            Log.e("WatchAnimeScreen", "Error switching server", e)
                                        } finally {
                                            isServerLoading = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedServer == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedServer == index)
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

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Episodes section
            if (!isLoading) {
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

                    // Pagination for episodes
                    val episodesPerPage = 10
                    val episodePages = episodesList.chunked(episodesPerPage)
                    val totalPages = episodePages.size
                    val currentPage = remember(currentEpisode) {
                        (currentEpisode - 1) / episodesPerPage
                    }

                    if (episodePages.isNotEmpty()) {
                        // Episodes grid for current page
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 56.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val episodesForCurrentPage = episodePages.getOrNull(currentPage) ?: emptyList()
                            items(episodesForCurrentPage) { episode ->
                                EpisodeButton(
                                    episodeNumber = episode,
                                    isSelected = episode == currentEpisode,
                                    onClick = {
                                        if (episode != currentEpisode) {
                                            currentEpisode = episode
                                            // Reset player position when changing episodes
                                            playerPosition = 0
                                        }
                                    }
                                )
                            }
                        }

                        // Pagination controls at the bottom
                        EpisodePagination(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageSelected = { page ->
                                // Only update if it's a different page
                                if (page != currentPage) {
                                    // Scroll to the first episode of the selected page
                                    val firstEpisodeInPage = page * episodesPerPage + 1
                                    currentEpisode = firstEpisodeInPage.coerceIn(1, totalEpisodes)
                                    // Reset player position when changing episodes
                                    playerPosition = 0
                                }
                            }
                        )
                    }
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