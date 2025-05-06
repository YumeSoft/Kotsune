package me.thuanc177.kotsune.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.R
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.viewmodel.MangaDexTrackingViewModel
import java.util.Locale
import androidx.compose.foundation.layout.FlowRow

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
    mangaList: List<MangaDexTrackingViewModel.MangaWithStatus>,
    onMangaClick: (String) -> Unit,
    onStatusChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(mangaList) { mangaWithStatus ->
            MangaGridItem(
                mangaWithStatus = mangaWithStatus,
                onClick = { onMangaClick(mangaWithStatus.manga.id) },
                onStatusChange = onStatusChange
            )
        }
    }
}

@Composable
fun MangaGridItem(
    mangaWithStatus: MangaDexTrackingViewModel.MangaWithStatus,
    onClick: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val manga = mangaWithStatus.manga
    val status = mangaWithStatus.status

    var showDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
                contentAlignment = Alignment.TopEnd
            ) {
                // Manga cover image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.poster)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_kotsune_orange)
                        .error(R.drawable.ic_kotsune_white)
                        .build(),
                    contentDescription = "Cover for ${manga.title.firstOrNull() ?: "Unknown manga"}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                )

                // Status dropdown menu
                IconButton(
                    onClick = { showDropdown = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Change status",
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        ReadingStatusMenuItem("reading", "Reading", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }

                        ReadingStatusMenuItem("plan_to_read", "Plan to Read", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }

                        ReadingStatusMenuItem("completed", "Completed", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }

                        ReadingStatusMenuItem("on_hold", "On Hold", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }

                        ReadingStatusMenuItem("dropped", "Dropped", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }

                        ReadingStatusMenuItem("re_reading", "Re-reading", status,
                            { newStatus -> onStatusChange(manga.id, newStatus) })
                        { showDropdown = false }
                    }
                }
            }

            // Reading status indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                val statusLabel = when(status) {
                    "reading" -> "Reading"
                    "plan_to_read" -> "Plan to Read"
                    "completed" -> "Completed"
                    "on_hold" -> "On Hold"
                    "re_reading" -> "Re-reading"
                    "dropped" -> "Dropped"
                    else -> "Unknown"
                }

                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Manga title
            Text(
                text = manga.title.firstOrNull() ?: "Unknown title",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MangaCard(
    manga: Manga,
    status: String,
    onMangaClick: () -> Unit,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    val title = manga.title.firstOrNull() ?: "Unknown"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable { onMangaClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Cover image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Manga cover
                AsyncImage(
                    model = manga.poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(id = R.drawable.mangadex_icon),
                    error = painterResource(id = R.drawable.mangadex_icon)
                )

                // Reading status indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        .clickable { showStatusMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Reading Status",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        ReadingStatusMenuItem("reading", "Reading", status, onStatusChange) { showStatusMenu = false }
                        ReadingStatusMenuItem("plan_to_read", "Plan to Read", status, onStatusChange) { showStatusMenu = false }
                        ReadingStatusMenuItem("completed", "Completed", status, onStatusChange) { showStatusMenu = false }
                        ReadingStatusMenuItem("on_hold", "On Hold", status, onStatusChange) { showStatusMenu = false }
                        ReadingStatusMenuItem("dropped", "Dropped", status, onStatusChange) { showStatusMenu = false }
                        ReadingStatusMenuItem("re_reading", "Re-reading", status, onStatusChange) { showStatusMenu = false }
                    }
                }
            }

            // Title and status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(status)

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = formatReadingStatus(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedMangaCard(
    manga: Manga,
    status: String,
    statistics: MangaStatistics?,
    onMangaClick: () -> Unit,
    onStatusChange: (String) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var showFullDescription by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }

    val title = manga.title.firstOrNull() ?: "Unknown"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clickable { onMangaClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Cover image (left side)
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = manga.poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(id = R.drawable.mangadex_icon),
                    error = painterResource(id = R.drawable.mangadex_icon)
                )

                // Status badge overlay at the bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusChip(
                        status = status,
                        onClick = { showStatusMenu = true },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            isNotificationEnabled = !isNotificationEnabled
                            onNotificationToggle(isNotificationEnabled)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isNotificationEnabled)
                                Icons.Default.Notifications
                            else
                                Icons.Default.NotificationsOff,
                            contentDescription = "Toggle notifications",
                            tint = if (isNotificationEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Status dropdown menu
                DropdownMenu(
                    expanded = showStatusMenu,
                    onDismissRequest = { showStatusMenu = false }
                ) {
                    ReadingStatusMenuItem("reading", "Reading", status, onStatusChange) { showStatusMenu = false }
                    ReadingStatusMenuItem("plan_to_read", "Plan to Read", status, onStatusChange) { showStatusMenu = false }
                    ReadingStatusMenuItem("completed", "Completed", status, onStatusChange) { showStatusMenu = false }
                    ReadingStatusMenuItem("on_hold", "On Hold", status, onStatusChange) { showStatusMenu = false }
                    ReadingStatusMenuItem("dropped", "Dropped", status, onStatusChange) { showStatusMenu = false }
                    ReadingStatusMenuItem("re_reading", "Re-reading", status, onStatusChange) { showStatusMenu = false }
                }
            }

            // Content area (right side)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // Language and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Fix for originalLanguage property
                    Text(
                        text = manga.status?.let { "[$it]" } ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Stats row (rating, follows, etc)
                statistics?.let { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rating
                        if (stats.rating != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "%.1f".format(stats.rating),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Follows
                        if (stats.follows > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Follows",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = formatNumber(stats.follows),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Comments
                        if (stats.comments > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Comment,
                                    contentDescription = "Comments",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = formatNumber(stats.comments),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Status chip
                        Text(
                            text = manga.status?.capitalizeWords() ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = getStatusColor(manga.status ?: ""),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Content rating and tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContentRatingChip(contentRating = manga.contentRating)

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tags with "show more" option
                    val displayTags = if (showAllTags) manga.tags else manga.tags.take(2)
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayTags.forEach { tag ->
                            TagChip(tag = tag.tagName)
                        }

                        if (manga.tags.size > 2 && !showAllTags) {
                            TagChip(tag = "...") {
                                showAllTags = true
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description with "show more" option
                if (manga.description.isNotEmpty()) {
                    Column {
                        Text(
                            text = manga.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = if (showFullDescription) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (manga.description.length > 100 && !showFullDescription) {
                            TextButton(
                                onClick = { showFullDescription = true },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Show more",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        "reading" -> MaterialTheme.colorScheme.primary
        "plan_to_read" -> MaterialTheme.colorScheme.tertiary
        "completed" -> Color.Green.copy(alpha = 0.8f)
        "on_hold" -> Color.Yellow.copy(alpha = 0.8f)
        "dropped" -> Color.Red.copy(alpha = 0.8f)
        "re_reading" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        color = statusColor.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, statusColor),
        modifier = modifier
            .height(24.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatReadingStatus(status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Change status",
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ContentRatingChip(contentRating: String) {
    val (color, text) = when (contentRating) {
        "safe" -> Pair(Color.Green, "Safe")
        "suggestive" -> Pair(Color.Yellow, "Suggestive")
        "erotica" -> Pair(Color.Red.copy(alpha = 0.7f), "Erotica")
        "pornographic" -> Pair(Color.Red, "Adult")
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, "Unknown")
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color),
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun TagChip(
    tag: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .height(20.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

// Helper function to format numbers (e.g., 1500 -> 1.5K)
private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> "%.1fM".format(number / 1_000_000f)
        number >= 1_000 -> "%.1fK".format(number / 1_000f)
        else -> number.toString()
    }
}

@Composable
private fun getStatusColor(status: String): Color {
    return when (status) {
        "ongoing" -> MaterialTheme.colorScheme.primary
        "completed" -> Color.Green.copy(alpha = 0.8f)
        "hiatus" -> Color.Yellow.copy(alpha = 0.8f)
        "cancelled" -> Color.Red.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun String.capitalizeWords(): String {
    return split("_").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

// Data class for manga statistics
data class MangaStatistics(
    val rating: Float? = null,
    val follows: Int = 0,
    val comments: Int = 0
)

@Composable
private fun ReadingStatusMenuItem(
    statusValue: String,
    displayName: String,
    currentStatus: String,
    onStatusChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusValue == currentStatus) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(displayName)
            }
        },
        onClick = {
            if (statusValue != currentStatus) {
                onStatusChange(statusValue)
            }
            onDismiss()
        }
    )
}

@Composable
private fun StatusBadge(status: String) {
    val (color, icon) = when (status) {
        "reading" -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.Book)
        "plan_to_read" -> Pair(MaterialTheme.colorScheme.tertiary, Icons.Default.AccessTime)
        "completed" -> Pair(Color.Green.copy(alpha = 0.8f), Icons.Default.CheckCircle)
        "on_hold" -> Pair(Color.Yellow.copy(alpha = 0.8f), Icons.Default.Pause)
        "dropped" -> Pair(Color.Red.copy(alpha = 0.8f), Icons.Default.Close)
        "re_reading" -> Pair(MaterialTheme.colorScheme.secondary, Icons.Default.Refresh)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, Icons.Default.Help)
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}

private fun formatReadingStatus(status: String): String {
    return when (status) {
        "reading" -> "Reading"
        "plan_to_read" -> "Plan to Read"
        "completed" -> "Completed"
        "on_hold" -> "On Hold"
        "dropped" -> "Dropped"
        "re_reading" -> "Re-reading"
        else -> "Unknown"
    }
}