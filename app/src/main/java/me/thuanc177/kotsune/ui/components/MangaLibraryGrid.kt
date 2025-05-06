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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.ModeComment
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.R
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaWithStatus
import me.thuanc177.kotsune.viewmodel.MangaDexTrackingViewModel
import java.util.Locale
import kotlin.math.absoluteValue

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
    mangaList: List<MangaWithStatus>,
    onMangaClick: (String) -> Unit,
    onStatusChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columns = if (screenWidth >= 600.dp) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(mangaList) { mangaWithStatus ->
            EnhancedMangaCard(
                manga = mangaWithStatus,
                onMangaClick = { onMangaClick(mangaWithStatus.manga.id) },
                onStatusChange = onStatusChange,
                onNotificationToggle = { isEnabled ->
                    // Implement notification toggle if needed
                    // For now, just log the action
                    Log.d("MangaLibraryGrid", "Notification ${if (isEnabled) "enabled" else "disabled"} for manga ${mangaWithStatus.manga.id}")
                }
            )
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedMangaCard(
    manga: MangaWithStatus,
    onMangaClick: () -> Unit,
    onStatusChange: (String, String) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val mangaData = manga.manga
    val readingStatus = manga.status

    var showStatusMenu by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var showAllTags by remember { mutableStateOf(false) }
    var showFullDescription by remember { mutableStateOf(false) }

    // Generate realistic statistics based on manga ID hash
    val statistics = remember {
        MangaStatistics(
            rating = (mangaData.id.hashCode() % 50 + 50) / 10f, // Generate rating 5.0-9.9
            follows = mangaData.id.hashCode().absoluteValue % 10000 + 1000, // 1000-10999
            comments = mangaData.id.hashCode().absoluteValue % 500 // 0-499
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(min = 280.dp, max = 420.dp)
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
                            .build(),
                        contentDescription = "Manga cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Status and notification buttons below cover image
                Row(
                    modifier = Modifier
                        .width(120.dp),
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

                    // Notification toggle (without background as requested)
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
                                    // Only round the bottom-right corner
                                    topStart = CornerSize(0.dp),
                                    topEnd = CornerSize(0.dp),
                                    bottomStart = CornerSize(0.dp),
                                    bottomEnd = CornerSize(8.dp)
                                )
                            )
                            // Custom border that only shows on bottom and right
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
                    // Language flag + Title
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Publication status, ratings, follows, comments
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Publication status
                    PublicationStatusPill(mangaData.status)

                    Spacer(modifier = Modifier.width(8.dp))

                    // Rating
                    statistics.rating?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "%.1f".format(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Follows with bookmark icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "Follows",
                            tint = Color(0xFF78B6F3),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatNumber(statistics.follows),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Comments with comment icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ModeComment,
                            contentDescription = "Comments",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatNumber(statistics.comments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
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
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(),
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

// Helper functions for the enhanced card
private fun getStatusIcon(status: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        "reading" -> Icons.Default.Book
        "plan_to_read" -> Icons.Default.AccessTime
        "completed" -> Icons.Default.Check
        "on_hold" -> Icons.Default.Pause
        "dropped" -> Icons.Default.Close
        "re_reading" -> Icons.Default.Refresh
        else -> Icons.AutoMirrored.Filled.Help
    }
}

@Composable
private fun getStatusColor(status: String): Color {
    return when (status) {
        "reading" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        "plan_to_read" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        "completed" -> Color.Green.copy(alpha = 0.6f)
        "on_hold" -> Color(0xFFFFC107).copy(alpha = 0.6f) // Amber
        "dropped" -> Color.Red.copy(alpha = 0.6f)
        "re_reading" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
}

private fun formatReadingStatus(status: String): String {
    return when (status) {
        "reading" -> "Reading"
        "plan_to_read" -> "Plan"
        "completed" -> "Done"
        "on_hold" -> "Hold"
        "dropped" -> "Drop"
        "re_reading" -> "ReRead"
        else -> status.capitalizeWords()
    }
}

@Composable
private fun PublicationStatusPill(status: String) {
    val (backgroundColor, textColor) = when (status.lowercase()) {
        "ongoing" -> Color(0xFF4CAF50) to Color.White
        "completed" -> Color(0xFF2196F3) to Color.White
        "hiatus" -> Color(0xFFFFC107) to Color.Black
        "cancelled" -> Color(0xFFE91E63) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor
    ) {
        Text(
            text = status.capitalizeWords(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TagChip(tag: MangaTag) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.height(24.dp)
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun getContentRatingColor(contentRating: String): Color {
    return when (contentRating.lowercase()) {
        "safe" -> Color(0xFF4CAF50)
        "suggestive" -> Color(0xFFFF9800)
        "erotica" -> Color(0xFFF44336)
        "pornographic" -> Color(0xFF9C27B0)
        else -> Color.Gray
    }
}

// Helper function to format numbers with K/M suffix
private fun formatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000f)
        number >= 1_000 -> String.format("%.1fK", number / 1_000f)
        else -> number.toString()
    }
}

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (statusValue == currentStatus)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            Color.Transparent
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Icon based on reading status
                    val icon = when (statusValue) {
                        "reading" -> Icons.Default.Book
                        "plan_to_read" -> Icons.Default.AccessTime
                        "completed" -> Icons.Default.CheckCircle
                        "on_hold" -> Icons.Default.Pause
                        "dropped" -> Icons.Default.Close
                        "re_reading" -> Icons.Default.Refresh
                        else -> Icons.Default.Help
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (statusValue == currentStatus)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = displayName,
                        color = if (statusValue == currentStatus)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )

                    if (statusValue == currentStatus) {
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        onClick = {
            if (statusValue != currentStatus) {
                onStatusChange(statusValue)
            }
            onDismiss()
        },
        contentPadding = PaddingValues(0.dp)
    )
}