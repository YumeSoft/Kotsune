package me.thuanc177.kotsune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.data.model.AnimeListState
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anime.Anime
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
        state.isLoading -> LoadingIndicator()
        state.error != null -> ErrorMessage(message = state.error!!, onRetry = { viewModel.fetchAnimeLists() })
        else -> AnimeContent(state, navController)
    }
}

@Composable
fun AnimeContent(
    state: AnimeListState,
    navController: NavController
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionTitle("🔥 Trending Now") }
        item {
            RectangularTrendingCarousel(
                animeList = state.trending,
                navController = navController
            )
        }

        item { SectionTitle("🆕 New Episodes") }
        item { AnimeRow(animeList = state.newEpisodes, navController = navController) }

        item { SectionTitle("⭐ High Rating") }
        item { AnimeRow(animeList = state.highRating, navController = navController) }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun RectangularTrendingCarousel(animeList: List<Anime>, navController: NavController) {
    // Create and remember scroll state
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Get the first visible item index
    val firstVisibleItemIndex = scrollState.firstVisibleItemIndex

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(animeList, key = { _, anime -> anime.id }) { index, anime ->
                RectangularTrendingCard(
                    anime = anime,
                    index = index,
                    totalItems = animeList.size,
                    onClick = { navController.navigate("anime_detail/${anime.id}") }
                )
            }
        }

        // Pagination Dots - using observed state
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
                            color = if (dotIndex == firstVisibleItemIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
fun RectangularTrendingCard(
    anime: Anime,
    index: Int,
    totalItems: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.coverImage)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title.toString(),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Info overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = 400f
                        )
                    )
            )

            // Text content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    anime.score?.let {
                        Text(
                            text = "⭐ %.1f".format(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = anime.status.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AnimeRow(animeList: List<Anime>, navController: NavController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animeList, key = { it.id }) { anime ->
            AnimeCard(anime = anime, onClick = {
                navController.navigate("anime_detail/${anime.id}")
            })
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(anime.coverImage)
                    .crossfade(true)
                    .build(),
                contentDescription = anime.title.toString(),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                anime.score?.let {
                    Text(
                        text = "⭐ %.1f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = anime.status.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
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