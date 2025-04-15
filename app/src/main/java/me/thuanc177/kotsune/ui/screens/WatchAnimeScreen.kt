package me.thuanc177.kotsune.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.thuanc177.kotsune.libs.AnimeProvider
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import me.thuanc177.kotsune.libs.animeProvider.allanime.HttpClient
import me.thuanc177.kotsune.navigation.Screen
import me.thuanc177.kotsune.ui.components.VideoPlayer
import me.thuanc177.kotsune.viewmodel.WatchAnimeViewModel

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

@UnstableApi
@Composable
fun WatchAnimeScreen(
    navController: NavController,
    animeTitle: String,
    episodeNumber: Int = 1,
    anilistId: Int = -1,
    viewModel: WatchAnimeViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var animeId by remember { mutableStateOf<String?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var currentEpisode by remember { mutableStateOf(episodeNumber) }
    var servers by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf(0) }
    var totalEpisodes by remember { mutableStateOf(100) } // Default to 100 episodes
    var episodesList by remember { mutableStateOf((1..totalEpisodes).toList()) }
    var animeProvider by remember { mutableStateOf<AnimeProvider?>(null) }

    // Pagination
    val episodesPerPage = 10
    val episodePages = remember(episodesList) {
        episodesList.chunked(episodesPerPage)
    }
    val currentPage = remember(currentEpisode) {
        (currentEpisode - 1) / episodesPerPage
    }

    // Format anime title (replace underscores with spaces)
    val formattedTitle = remember(animeTitle) {
        animeTitle.replace("_", " ")
    }

    // Effect to load episode streams when episode changes
    LaunchedEffect(animeTitle, currentEpisode, anilistId) {
        isLoading = true
        error = null

        try {
            // First, check if we have a previously used provider and reuse it
            animeProvider = viewModel.getProvider() ?: AnimeProviderFactory.create()
            viewModel.setProvider(animeProvider!!)

            // Check if animeId is cached in viewModel
            animeId = viewModel.getCachedAnimeId(formattedTitle)

            // If no cached ID, search for the anime
            if (animeId == null) {
                val animeGotFromProvider = animeProvider!!.searchForAnime(anilistId, formattedTitle)

                if (animeGotFromProvider.isSuccess) {
                    val animeGotFromProviderDeCapsuled = animeGotFromProvider.getOrNull()
                    if (animeGotFromProviderDeCapsuled == null) {
                        throw Exception("No results found for $formattedTitle")
                    }

                    // Set animeId from the search result
                    animeId = animeGotFromProviderDeCapsuled.alternativeId
                    totalEpisodes = animeGotFromProviderDeCapsuled.totalEpisodes ?: 1

                    // Cache the animeId for future use
                    if (!animeId.isNullOrEmpty()) {
                        viewModel.cacheAnimeId(formattedTitle, animeId!!)
                    } else {
                         throw Exception("Invalid anime ID received from provider")
                    }
                } else {
                    throw Exception("Failed to search: ${animeGotFromProvider.exceptionOrNull()?.message ?: "Unknown error"}")
                }
            }

            // Update episodes list based on total episodes
            episodesList = (1..totalEpisodes).toList()

            // Get episode streams
            val streamsResult = animeProvider!!.getEpisodeStreams(
                animeId = animeId!!,
                episode = currentEpisode.toString()
            )

            if (streamsResult.isSuccess) {
                val streamsList = streamsResult.getOrNull()
                if (streamsList.isNullOrEmpty()) {
                    throw Exception("No streams available for episode $currentEpisode")
                }

                servers = streamsList
                selectedServer = 0

                // Get the first stream URL from the first server
                val firstServer = servers.firstOrNull()
                val firstLink = firstServer?.links?.firstOrNull()

                if (firstLink != null) {
                    streamUrl = firstLink.link
                } else {
                    throw Exception("No playable links found in server")
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Video Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (error != null) {
                Text(
                    text = error ?: "Unknown error",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else if (streamUrl != null) {
                // Get subtitles from the current server if available
                val subtitles = servers.getOrNull(selectedServer)?.subtitles ?: emptyList()
                val subtitlePairs = subtitles.map { Pair(it.language, it.url) }

                // Get quality options for the current server if available
                val qualityOptions = servers.getOrNull(selectedServer)?.links?.map {
                    Pair(it.quality ?: "Default", it.link)
                }?.filter { it.second.isNotEmpty() } ?: emptyList()

                VideoPlayer(
                    streamUrl = streamUrl ?: "",
                    subtitleUrls = subtitlePairs,
                    qualityOptions = qualityOptions,
                    onBackPress = { navController.popBackStack() },
                    // Pass the headers from the current server or the default AllAnime headers
                    customHeaders = servers.getOrNull(selectedServer)?.headers ?: mapOf(
                        "Referer" to "https://allanime.site",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.93 Safari/537.36",
                        "Origin" to "https://allanime.site"
                    )
                )
            } else {
                // No stream available
                Text(
                    text = "No stream available",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Title and episode info
        Text(
            text = "$formattedTitle - Episode $currentEpisode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        // Server selection
        if (servers.isNotEmpty()) {
            Text(
                text = "Select Server",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                items(servers) { server ->
                    Button(
                        onClick = {
                            val index = servers.indexOf(server)
                            selectedServer = index
                            streamUrl = server.links.firstOrNull()?.link
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedServer == servers.indexOf(server))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(server.server, maxLines = 1)
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Episodes section
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (episodePages.isNotEmpty()) {
            // Display episodes grid for current page
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (currentPage < episodePages.size) {
                    items(episodePages[currentPage]) { epNumber ->
                        Button(
                            onClick = {
                                // Navigate to the same screen with a different episode number
                                if (epNumber != currentEpisode) {
                                    coroutineScope.launch {
                                        navController.navigate(
                                            Screen.WatchAnime.createRoute(
                                                anilistId = anilistId,
                                                animeTitle = animeTitle,
                                                episodeNumber = epNumber
                                            )
                                        ) {
                                            // Pop up to the current destination to avoid stacking screens
                                            popUpTo(navController.currentBackStackEntry?.destination?.route ?: "") {
                                                inclusive = true
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (epNumber == currentEpisode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("$epNumber", maxLines = 1)
                        }
                    }
                }
            }

            // Pagination controls
            WatchEpisodePagination(
                currentPage = currentPage,
                totalPages = episodePages.size,
                onPageSelected = { page ->
                    // Navigate to first episode of selected page
                    val firstEpisodeInPage = episodePages[page].first()
                    coroutineScope.launch {
                        navController.navigate(
                            Screen.WatchAnime.createRoute(
                                anilistId = anilistId,
                                animeTitle = animeTitle,
                                episodeNumber = firstEpisodeInPage
                            )
                        ) {
                            popUpTo(navController.currentBackStackEntry?.destination?.route ?: "") {
                                inclusive = true
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun WatchEpisodePagination(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous page button
        IconButton(
            onClick = { if (currentPage > 0) onPageSelected(currentPage - 1) },
            enabled = currentPage > 0,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous page"
            )
        }

        // Page numbers
        val visiblePages = 5 // Number of page buttons to show
        val startPage = maxOf(0, minOf(currentPage - visiblePages / 2, totalPages - visiblePages))
        val endPage = minOf(startPage + visiblePages, totalPages)

        for (page in startPage until endPage) {
            OutlinedButton(
                onClick = { onPageSelected(page) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (page == currentPage)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        Color.Transparent
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (page == currentPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = "${page + 1}",
                    color = if (page == currentPage)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Next page button
        IconButton(
            onClick = { if (currentPage < totalPages - 1) onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages - 1,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next page"
            )
        }
    }
}