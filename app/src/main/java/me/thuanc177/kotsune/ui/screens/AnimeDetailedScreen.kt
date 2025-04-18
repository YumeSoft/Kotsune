package me.thuanc177.kotsune.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AnilistClient
import me.thuanc177.kotsune.libs.anilist.AnilistTypes
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.CharacterEdge
import me.thuanc177.kotsune.libs.animeProvider.allanime.AllAnimeAPI
import me.thuanc177.kotsune.model.UiEpisodeModel
import me.thuanc177.kotsune.navigation.Screen
import me.thuanc177.kotsune.viewmodel.AnimeDetailedViewModel
import me.thuanc177.kotsune.viewmodel.EpisodesViewModel
import kotlin.math.sqrt

@Composable
fun AnimeDetailedScreen(
    navController: NavController,
    anilistId: Int,
    episodesViewModel: EpisodesViewModel = viewModel(), // Shared view model
    viewModel: AnimeDetailedViewModel = viewModel(
        factory = AnimeDetailedViewModel.Factory(
            anilistClient = AnilistClient(),
            anilistId = anilistId,
            episodesViewModel = episodesViewModel
        )
    )
) {
    // Use the episodes state from the shared view model
    val anilistClient = remember { AnilistClient() }
    val viewModel: AnimeDetailedViewModel = viewModel(
        factory = AnimeDetailedViewModel.Factory(anilistClient, anilistId, episodesViewModel)
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

                    // Cover image, title, and action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
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
                                    .data(anime.coverImage?.extraLarge)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Column for title and buttons
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            // Title
                            var isEnglishTitleExpanded by remember { mutableStateOf(false) }
                            anime.title?.english?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = if (isEnglishTitleExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isEnglishTitleExpanded = !isEnglishTitleExpanded
                                        }
                                )
                            }

                            // jp title
                            anime.title?.native?.let {
                                var isNativeTitleExpanded by remember { mutableStateOf(false) }
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = if (isNativeTitleExpanded) Int.MAX_VALUE else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            isNativeTitleExpanded = !isNativeTitleExpanded
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Add to List button
                                var listMenuExpanded by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier.weight(1f) // Explicitly apply weight to Box
                                ) {
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

                                Spacer(modifier = Modifier.width(8.dp))

                                // Favorites button
                                Box(
                                    modifier = Modifier.weight(1f) // Explicitly apply weight to Box
                                ) {
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
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (anime.isFavourite == true)
                                                    Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorite",
                                                tint = Color.Red
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatNumber(anime.favourites),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section tabs
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 0.dp,
                    divider = { HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    ) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    sections.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            text = {
                                Text(
                                    title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        )
                    }
                }

                // Section content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> OverviewSection(anime)
                        1 -> CharactersSection(anime, viewModel)
                        2 -> EpisodesSection(anime, navController, viewModel)
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

fun formatNumber(number: Int?): String {
    return when {
        number == null -> "0"
        number >= 1_000_000 -> "${(number / 1_000_000.0).toInt()}M"
        number >= 1_000 -> "${(number / 1_000.0).toInt()}K"
        else -> number.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullImageDialog(imageUrl: String?, onDismiss: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true),
        content = {
            Box(modifier = Modifier.fillMaxSize(0.9f)
                .fillMaxHeight(0.9f)) {
                LongPressWrapper(imageUrl = imageUrl) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ZoomableImage(imageUrl = imageUrl)

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
fun StatusDistributionChart(
    distribution: List<AnilistTypes.StatusDistribution>,
    modifier: Modifier = Modifier
) {
    val total = distribution.sumOf { it.amount }.toFloat()
    val statusColors = mapOf(
        "CURRENT" to Color(0xFF3DB4F2),      // Blue
        "PLANNING" to Color(0xFFC063FF),     // Purple
        "COMPLETED" to Color(0xFF4CAF50),    // Green
        "DROPPED" to Color(0xFFF44336),      // Red
        "PAUSED" to Color(0xFFFF9800),       // Orange
        "REPEATING" to Color(0xFF2196F3)     // Light Blue
    )

    BoxWithConstraints(modifier = modifier.padding(8.dp)) {
        val maxWidth = this.maxWidth
        val barHeight = 30.dp
        val textSize = (maxWidth / 20).coerceAtLeast(12.dp).value.sp
        Column {
            // Stacked Bar Chart with Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                distribution.forEach { status ->
                    val widthPercentage = status.amount / total
                    val color = statusColors[status.status] ?: MaterialTheme.colorScheme.secondary

                    Column(
                        modifier = Modifier
                            .weight(widthPercentage.coerceAtLeast(0.1f)) // Ensure a minimum width
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Chart Section
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Legend Item
                        Text(
                            text = status.status.lowercase()
                                .replaceFirstChar { it.uppercase() }
                                .replace("_", " "),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = textSize * 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${status.amount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = textSize * 0.8f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreDistributionChart(
    distribution: List<AnilistTypes.ScoreDistribution>,
    modifier: Modifier = Modifier
) {
    val maxAmount = distribution.maxOfOrNull { it.amount }?.toFloat() ?: 1f
    val colorScheme = MaterialTheme.colorScheme

    val sortedDistribution = remember(distribution) {
        distribution.sortedBy { it.score }
    }

    // Dynamic color based on score value
    val getBarColor: (Int) -> Brush = remember {
        { score ->
            val t = (score - 10).coerceIn(0, 90) / 90f

            val color = when {
                t < 0.5f -> lerp(Color.Red, Color.Green, t * 2)
                else -> lerp(Color.Green, Color.Blue, (t - 0.5f) * 2)
            }

            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.8f), color)
            )
        }
    }

    var showAllLabels by remember { mutableStateOf(false) }
    var hoveredBarIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total votes: ${sortedDistribution.sumOf { it.amount }}",
                style = MaterialTheme.typography.bodyMedium
            )

            TextButton(onClick = { showAllLabels = !showAllLabels }) {
                Text(if (showAllLabels) "Hide values" else "Show all values")
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxSize()
                ) {
                    sortedDistribution.forEachIndexed { index, score ->
                        val adjustedAmount = sqrt(score.amount.toFloat())
                        val adjustedMax = sqrt(maxAmount)
                        val normalizedHeight = if (adjustedMax > 0) {
                            (adjustedAmount / adjustedMax * 1.2f).coerceIn(0.05f, 1f)
                        } else {
                            0.05f
                        }

                        val isHighlighted = hoveredBarIndex == index
                        val shouldShowLabel = showAllLabels || isHighlighted

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                        ) {

                            Text(
                                text = score.score.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHighlighted)
                                    colorScheme.primary
                                else
                                    colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Spacer(modifier = Modifier.weight((1f - normalizedHeight).coerceAtLeast(0.01f))) // Space above the bar

                            Box(
                                modifier = Modifier
                                    .width(if (isHighlighted) 36.dp else 28.dp)
                                    .weight(normalizedHeight.coerceAtLeast(0.01f)) // Use normalized height
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)) // Rounded corners at the top
                                    .background(
                                        brush = getBarColor(score.score),
                                        alpha = if (isHighlighted) 1f else 0.9f
                                    )
                                    .border(
                                        width = if (isHighlighted) 1.dp else 0.dp,
                                        color = colorScheme.onSurface.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        hoveredBarIndex = if (hoveredBarIndex == index) null else index
                                    }
                            )

                            Box(
                                modifier = Modifier
                                    .height(18.dp)
                            ) {
                                if (shouldShowLabel) {
                                    Text(
                                        text = score.amount.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Tap bars to highlight individual scores",
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = FontStyle.Italic
            ),
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
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
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
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

    val statusMap = mapOf(
        "FINISHED" to "Finished",
        "RELEASING" to "Releasing",
        "NOT_YET_RELEASED" to "Not Yet Released",
        "CANCELLED" to "Cancelled",
        "HIATUS" to "Hiatus",
        "UNRELEASED" to "Unreleased",
        "TBA" to "TBA"
    )
    anime.status?.let { statusMap[it] } ?: "Unknown"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
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
                    InfoRow("Status", statusMap[it] ?: it)
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

            TextButton(
                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (isDescriptionExpanded) "Read Less" else "Read More")
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
                .height(200.dp)
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
                .height(320.dp)
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
                val trailerUrl = when (trailer.site) {
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
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Trailer",
                            modifier = Modifier.padding(end = 8.dp)
                        )
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

    val characterRoles = mapOf(
        "MAIN" to "Main",
        "SUPPORTING" to "Supporting",
        "BACKGROUND" to "Background"
    )

    val role = characterRoles[characterEdge.role] ?: characterEdge.role

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.showCharacterDetails(characterEdge) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Character image (left)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(character.image?.large)
                        .crossfade(true)
                        .build(),
                    contentDescription = character.name?.full,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp, 140.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                )

                // Character info and details (middle)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = character.name?.full ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = role ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (voiceActor != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.7f),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = voiceActor.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = voiceActor.languageV2 ?: voiceActor.homeTown ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Voice actor image (right)
                if (voiceActor != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(voiceActor.image?.large)
                            .crossfade(true)
                            .build(),
                        contentDescription = voiceActor.name?.full,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp, 140.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodesSection(
    anime: AnimeDetailed,
    navController: NavController,
    viewModel: AnimeDetailedViewModel
) {
    val episodesList by viewModel.episodesViewModel.episodesList.collectAsState(initial = emptyList())
    val episodesLoading by viewModel.episodesViewModel.episodesLoading.collectAsState(initial = false)
    val episodesError by viewModel.episodesViewModel.episodesError.collectAsState(initial = null)
    val currentEpisode by viewModel.episodesViewModel.currentEpisode.collectAsState(initial = null)

    when {
        episodesLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        episodesError != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Failed to load episodes: $episodesError",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        episodesList.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "No episodes available yet."
                )
            }
        }
        else -> {
            // Sort episodes by number for better navigation
            val sortedEpisodes = remember(episodesList) {
                episodesList.sortedBy { it.number }
            }

            // Display episodes with pagination
            val episodesPerPage = 12
            val episodePages = remember(sortedEpisodes) {
                sortedEpisodes.chunked(episodesPerPage)
            }

            val coroutineScope = rememberCoroutineScope()
            val pagerState = rememberPagerState(pageCount = { episodePages.size })

            // Calculate initial page based on current episode if provided
            val initialPage = remember(currentEpisode, episodePages) {
                if (currentEpisode != null) {
                    val episodeIndex = sortedEpisodes.indexOfFirst {
                        it.number == currentEpisode
                    }.takeIf { it >= 0 } ?: 0
                    episodeIndex / episodesPerPage
                } else 0
            }

            // Set initial page
            LaunchedEffect(initialPage) {
                if (pagerState.currentPage != initialPage) {
                    pagerState.scrollToPage(initialPage)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Episodes grid
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
                        if (page < episodePages.size) {
                            items(episodePages[page].size) { index ->
                                val episode = episodePages[page][index]
                                EnhancedEpisodeCard(
                                    animeDetailed = anime,
                                    episode = episode,
                                    navController = navController,
                                    isCurrentEpisode = currentEpisode != null && episode.number == currentEpisode
                                )
                            }
                        }
                    }
                }

                // Pagination controls
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
fun EnhancedEpisodeCard(
    animeDetailed: AnimeDetailed,
    episode: UiEpisodeModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    isCurrentEpisode: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val provider = remember { AllAnimeAPI() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                scope.launch {
                    try {
                        val title = animeDetailed.title?.english
                            ?: animeDetailed.title?.romaji
                            ?: animeDetailed.title?.native
                            ?: ""

                        val result = provider.searchForAnime(
                            anilistId = animeDetailed.id,
                            query = title,
                            translationType = "sub"
                        )

                        result.onSuccess { anime ->
                            val showId = anime.alternativeId ?: return@onSuccess

                            withContext(Dispatchers.Main) {
                                navController.navigate(
                                    Screen.WatchAnime.createRoute(
                                        showId = showId,
                                        episodeNumber = episode.number
                                    )
                                )
                            }
                        }.onFailure { error ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Failed to load episode: ${error.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Log.e("EpisodeCard", "Error: ${error.message}", error)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error loading episode",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Log.e("EpisodeCard", "Exception", e)
                    }
                }
            }
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
                        text = episode.number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrentEpisode) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Episode info
                Column(modifier = Modifier.weight(1f)) {
                    // Title and episode number
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Episode ${episode.number}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = if (episode.thumbnail != null) Color.White else MaterialTheme.colorScheme.onSurface
                        )

                        // Add episode title if available
                        episode.title?.let {
                            if (it.isNotBlank()) {
                                Text(
                                    text = "• $it",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(2f),
                                    color = if (episode.thumbnail != null)
                                        Color.White.copy(alpha = 0.9f)
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Watch Episode",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
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
                .padding(end = 8.dp),
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
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
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
                    val formattedStatus = when (status) {
                        "RELEASING" -> "Releasing"
                        "FINISHED" -> "Finished"
                        "NOT_YET_RELEASED" -> "Not Yet Released"
                        "CANCELLED" -> "Cancelled"
                        "HIATUS" -> "Hiatus"
                        else -> status.split("_").joinToString(" ") { it.lowercase().capitalize() }
                    }

                    val statusColor = when (status) {
                        "RELEASING" -> Color(0xFF4CAF50) // Green
                        "FINISHED" -> Color(0xFF2196F3) // Blue
                        "NOT_YET_RELEASED" -> Color(0xFFFFA000) // Amber
                        "CANCELLED" -> Color(0xFFF44336) // Red
                        "HIATUS" -> Color(0xFF9C27B0) // Purple
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formattedStatus,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
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

suspend fun saveImage(context: Context, imageUrl: String?, fileName: String) {
    try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val result = (loader.execute(request) as SuccessResult).drawable
        val bitmap = (result as BitmapDrawable).bitmap

        // Save the image to MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            // Switch to main thread for Toast
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } ?: throw Exception("Failed to create MediaStore URI")
    } catch (e: Exception) {
        e.printStackTrace()
        // Switch to main thread for Toast
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun LongPressWrapper(
    imageUrl: String?,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }

    // Add the permission launcher here
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val fileName = "anime_image_${System.currentTimeMillis()}"
            GlobalScope.launch(Dispatchers.IO) {
                saveImage(context, imageUrl, fileName)
            }
        } else {
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Box {
        Box(modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { showSaveDialog = true }
            )
        }) {
            content()
        }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Image") },
                text = { Text("Do you want to save this image to your gallery?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val fileName = "anime_image_${System.currentTimeMillis()}"
                                GlobalScope.launch(Dispatchers.IO) {
                                    saveImage(context, imageUrl, fileName)
                                }
                            } else {
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED -> {
                                        val fileName = "anime_image_${System.currentTimeMillis()}"
                                        GlobalScope.launch(Dispatchers.IO) {
                                            saveImage(context, imageUrl, fileName)
                                        }
                                    }
                                    else -> {
                                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                }
                            }
                            showSaveDialog = false
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ZoomableImage(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f

    // Create a transformable state
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        // Update scale with zoom limitations
        scale = (scale * zoomChange).coerceIn(minScale, maxScale)

        // Only apply offset changes if we're zoomed in
        if (scale > 1f) {
            offset = Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y
            )
        } else {
            // Reset offset when scale returns to 1
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LongPressWrapper(imageUrl = imageUrl) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(state = transformableState)
                    // Double tap to reset zoom
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                scale = if (scale > 1f) 1f else 2f
                                if (scale == 1f) offset = Offset.Zero
                            }
                        )
                    }
            )
        }
    }
}