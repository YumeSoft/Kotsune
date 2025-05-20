package me.thuanc177.kotsune.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import me.thuanc177.kotsune.R
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaMoreDetails
import java.util.Locale

enum class LibraryTab {
    ALL,
    READING,
    PLAN_TO_READ,
    COMPLETED,
    ON_HOLD,
    RE_READING,
    DROPPED
}

@Composable
fun MangaLibraryGrid(
    mangaList: List<MangaMoreDetails>,
    onMangaClick: (String) -> Unit,
    onStatusChange: (String, String) -> Unit,
    onThreadClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columns = if (screenWidth >= 480.dp) 2 else 1
    LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(mangaList) { mangaWithDetails ->
            EnhancedMangaCard(
                manga = mangaWithDetails,
                onMangaClick = { onMangaClick(mangaWithDetails.manga.id) },
                onStatusChange = onStatusChange,
                onNotificationToggle = { isEnabled ->
                    // Implement notification toggle if needed
                    Log.d("MangaLibraryGrid", "Notification ${if (isEnabled) "enabled" else "disabled"} for manga ${mangaWithDetails.manga.id}")
                },
                onThreadClick = { threadId ->
                    onThreadClick(threadId)
                }
            )
        }
    }
}

internal fun String.capitalizeWords(): String {
    return split("_").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedMangaCard(
    manga: MangaMoreDetails,
    onMangaClick: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    onThreadClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mangaData = manga.manga
    val readingStatus = manga.readingStatus
    val statistics = manga.statistics

    var showStatusMenu by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }
    var showFullDescription by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = 240.dp, max = 320.dp)
            .clickable { onMangaClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // LEFT COLUMN - Cover image and buttons
            Column {
                // Cover image with proper aspect ratio (2:3 standard for manga)
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(mangaData.poster)
                            .crossfade(true)
                            .placeholder(R.drawable.mangadex_icon)
                            .error(R.drawable.mangadex_icon)
                            .scale(if (mangaData.poster?.isNotEmpty() == true) Scale.FILL else Scale.FIT)
                            .build(),
                        contentDescription = "Manga cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Status and notification buttons below cover image
                Row(
                    modifier = Modifier.width(120.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Reading status button
                    Surface(
                        modifier = Modifier
                            .weight(3f)
                            .height(40.dp)
                            .clickable { showStatusMenu = true },
                        color = getStatusColor(readingStatus),
                        shape = MaterialTheme.shapes.small.copy(
                            topStart = CornerSize(0.dp),
                            topEnd = CornerSize(0.dp),
                            bottomStart = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = getStatusIcon(readingStatus),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatReadingStatus(readingStatus),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Notification toggle
                    IconButton(
                        onClick = {
                            isNotificationEnabled = !isNotificationEnabled
                            onNotificationToggle(isNotificationEnabled)
                        },
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = getStatusColor(readingStatus),
                                shape = MaterialTheme.shapes.small.copy(
                                    topStart = CornerSize(0.dp),
                                    topEnd = CornerSize(0.dp),
                                    bottomStart = CornerSize(0.dp),
                                    bottomEnd = CornerSize(8.dp)
                                )
                            )
                            .background(
                                color = Color.Transparent,
                                shape = MaterialTheme.shapes.small.copy(
                                    topStart = CornerSize(0.dp),
                                    topEnd = CornerSize(0.dp),
                                    bottomStart = CornerSize(0.dp),
                                    bottomEnd = CornerSize(8.dp)
                                )
                            )
                            .weight(1f)
                            .height(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isNotificationEnabled)
                                Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = "Toggle notifications",
                            tint = if (isNotificationEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Status menu dropdown
                Box {
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        listOf("reading", "plan_to_read", "completed", "on_hold", "dropped", "re_reading").forEach { status ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = getStatusIcon(status),
                                            contentDescription = null,
                                            tint = getStatusColor(status),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = formatReadingStatus(status))
                                    }
                                },
                                onClick = {
                                    onStatusChange(mangaData.id, status)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // RIGHT COLUMN - Manga information section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // Title with language flag prepended
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Combined language flag and title
                    Text(
                        text = buildString {
                            // Add language flag
                            append(when (mangaData.originalLanguage) {
                                "ja" -> "🇯🇵 "
                                "ko" -> "🇰🇷 "
                                "zh" -> "🇨🇳 "
                                "en" -> "🇺🇸 "
                                "fr" -> "🇫🇷 "
                                "es" -> "🇪🇸 "
                                "de" -> "🇩🇪 "
                                "it" -> "🇮🇹 "
                                "ru" -> "🇷🇺 "
                                "pt" -> "🇵🇹 "
                                else -> "🌐 "
                            })
                            // Add title
                            append(mangaData.title.firstOrNull() ?: "Unknown")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Publication status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PublicationStatusPill(mangaData.status)

                    Spacer(modifier = Modifier.width(8.dp))

                    // Statistics row with ratings, follows, comments
                    StatisticsRow(
                        statistics = statistics,
                        onThreadClick = onThreadClick
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Combined content rating and tags in a single flow row
                if (mangaData.tags.isNotEmpty() || mangaData.contentRating.isNotEmpty()) {
                    val displayTags = if (showAllTags) mangaData.tags else mangaData.tags.take(3)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Content rating as a tag-like chip
                        mangaData.contentRating.let { rating ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = getContentRatingColor(rating).copy(alpha = 0.1f),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = rating.capitalizeWords(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = getContentRatingColor(rating),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Tags
                        displayTags.forEach { tag ->
                            TagChip(tag)
                        }

                        // Show more/less button for tags
                        if (mangaData.tags.size > 3 || showAllTags) {
                            Box(
                                modifier = Modifier
                                    .clickable(onClick = {
                                        showAllTags = !showAllTags
                                    })
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = if (showAllTags) "Show less" else "Show more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                if (mangaData.description.isNotEmpty()) {
                    val displayText = if (showFullDescription) {
                        mangaData.description
                    } else {
                        if (mangaData.description.length > 160) {
                            mangaData.description.take(160) + "..."
                        } else {
                            mangaData.description
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = if (showFullDescription) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showFullDescription = !showFullDescription }
                        )
                    }
                }
            }
        }
    }
}
