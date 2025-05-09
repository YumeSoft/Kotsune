package me.thuanc177.kotsune.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaForDetailedScreen
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaStatistics
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag

@Composable
fun StatisticsRow(
    statistics: MangaStatistics,
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Rating with star icon
        statistics.rating?.let { rating ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Rating",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "%.1f".format(rating.bayesian), // Using bayesian rating as it's more accurate
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

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

        // Comments with comment icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                statistics.comments.threadId?.let { onThreadClick(it) }
            }
        ) {
            Icon(
                imageVector = Icons.Default.ModeComment,
                contentDescription = "Comments",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = formatNumber(statistics.comments.repliesCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

fun openForumThread(context: Context, threadId: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://forums.mangadex.org/threads/$threadId"))
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

// Helper functions for the enhanced card
internal fun getStatusIcon(status: String): androidx.compose.ui.graphics.vector.ImageVector {
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
internal fun getStatusColor(status: String): Color {
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

internal fun formatReadingStatus(status: String): String {
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
internal fun PublicationStatusPill(status: String) {
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
internal fun TagChip(tag: MangaTag) {
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

internal fun getContentRatingColor(contentRating: String): Color {
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
fun MangaBannerSection(
    manga: Manga,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        // Banner Image with gradient overlay
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(manga.poster)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(500f, 300f)
                    )
                )
        )

        // Cover Image
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 80.dp)
                .width(120.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCoverClick)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(manga.poster)
                    .crossfade(true)
                    .build(),
                contentDescription = "Manga Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Text elements
        Column(
            modifier = Modifier
                .padding(start = 148.dp, top = 80.dp, end = 16.dp, bottom = 16.dp)
                .align(Alignment.TopStart)
        ) {
            // Title
            Text(
                text = manga.title.firstOrNull() ?: "Unknown Title",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // Alternative Title (only if main title is short)
            if (manga.title.size > 1) {
                Text(
                    text = manga.title.getOrNull(1) ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Author Name (positioned at bottom)
            Text(
                text = "By Author", // Replace with actual author from relationships
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun ChaptersSection(
    chapters: List<ChapterModel>,
    isLoading: Boolean,
    error: String?,
    sortAscending: Boolean,
    onSortToggle: () -> Unit,
    onChapterClick: (ChapterModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section header with sort button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Chapters (${chapters.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Sort button
            IconButton(onClick = onSortToggle) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = "Sort ${if (sortAscending) "Ascending" else "Descending"}"
                )
            }
        }

        // Error state
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Loading state
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                CircularProgressIndicator()
            }
        }

        // Chapters list
        if (chapters.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chapters) { chapter ->
                    ChapterItem(
                        chapter = chapter,
                        onClick = { onChapterClick(chapter) }
                    )
                }
            }
        } else if (!isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Text(
                    text = "No chapters available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: ChapterModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Chapter info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title row with chapter number and language flag
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ch ${chapter.number}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Language flag
                Text(
                    text = chapter.languageFlag?: "",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Official badge if applicable
                if (chapter.isOfficial) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Official",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Chapter title (if different from number)
            if (chapter.title.isNotEmpty() && chapter.title != "Chapter ${chapter.number}") {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Translation group
            chapter.translatorGroup?.let {
                Text(
                    text = "Scanlator: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Read indicator
        if (chapter.isRead) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Read",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun AlternativeTitlesSection(
    manga: Manga,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = "Alternative Titles",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Alternative titles list
        // Note: In actual implementation, would extract altTitles from the manga object
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1 until manga.title.size) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Language flag (would need to be derived from the title language)
                    Text(
                        text = "🇺🇸", // Example flag
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Text(
                        text = manga.title[i],
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ExtendedInfoSection(
    manga: MangaForDetailedScreen,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section header
        Text(
            text = "Additional Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // Extract relevant information from manga
        InfoRow(label = "Author", value = "Author Name") // Replace with actual author
        InfoRow(label = "Artist", value = "Artist Name") // Replace with actual artist

        val groupedTags = groupMangaTags(manga.tags)

        // Display tags by groups
        groupedTags.forEach { (group, tagList) ->
            if (tagList.isNotEmpty()) {
                TagsRow(label = group.capitalizeWords(), tags = tagList)
            }
        }

        // Demographic
        manga.demographic?.let {
            InfoRow(label = "Demographic", value = it.capitalizeWords())
        }

        // Links section
        manga.links?.let { links ->
            if (links.isNotEmpty()) {
                // Read or buy links
                val readBuyLinks = links.filter { it.type == "buy" || it.type == "read" }
                if (readBuyLinks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Read or Buy",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Implement links display here
                    }
                }

                // Tracking links
                val trackLinks = links.filter { it.type == "track" }
                if (trackLinks.isNotEmpty()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Track",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Implement links display here
                    }
                }
            }
        }
    }
}

internal fun groupMangaTags(tags: List<MangaTag>): Map<String, List<MangaTag>> {
    val groupedTags = mutableMapOf<String, MutableList<MangaTag>>()

    // Predefined tag groups based on the MangaDex taxonomy
    val genreTags = listOf(
        "action", "adventure", "boys' love", "comedy", "crime", "drama", "fantasy",
        "girls' love", "historical", "horror", "isekai", "magical girls", "mecha",
        "medical", "mystery", "philosophical", "psychological", "romance", "sci-fi",
        "slice of life", "sports", "superhero", "thriller", "tragedy", "wuxia"
    )

    val themeTags = listOf(
        "aliens", "animals", "cooking", "crossdressing", "delinquents", "demons",
        "genderswap", "ghosts", "gyaru", "harem", "incest", "loli", "mafia", "magic",
        "martial arts", "military", "monster girls", "monsters", "music", "ninja",
        "office workers", "police", "post-apocalyptic", "reincarnation", "reverse harem",
        "samurai", "school life", "shota", "supernatural", "survival", "time travel",
        "traditional games", "vampires", "video games", "villainess", "virtual reality", "zombies"
    )

    val formatTags = listOf(
        "4-koma", "adaptation", "anthology", "award winning", "doujinshi",
        "fan colored", "full color", "long strip", "official colored",
        "oneshot", "promotional", "user created", "web comic"
    )

    val contentWarningTags = listOf("gore", "sexual violence")

    // Group tags by their category
    for (tag in tags) {
        val tagName = tag.name.lowercase()

        val group = when {
            genreTags.contains(tagName) -> "genre"
            themeTags.contains(tagName) -> "theme"
            formatTags.contains(tagName) -> "format"
            contentWarningTags.contains(tagName) -> "content warning"
            else -> "other"
        }

        if (!groupedTags.containsKey(group)) {
            groupedTags[group] = mutableListOf()
        }
        groupedTags[group]?.add(tag)
    }

    return groupedTags
}
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TagsRow(label: String, tags: List<MangaTag>) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tags) { tag ->
                TagChip(tag = tag)
            }
        }
    }
}

@Composable
fun DescriptionSection(
    manga: Manga,
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Description text with fade effect if not expanded
        Box {
            Text(
                text = manga.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = if (isExpanded) 0.dp else 24.dp)
            )

            // Fade-out effect and show more button
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }

        // Horizontal separator with Show More/Less button
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )

        TextButton(
            onClick = { onToggleExpand(!isExpanded) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = if (isExpanded) "Show Less" else "Show More",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun PublicationInfoSection(
    manga: Manga,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Publication Year
        manga.year?.let { year ->
            Text(
                text = "Year: $year",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }

        // Publication Status
        PublicationStatusPill(status = manga.status)
    }
}

@Composable
fun ContentInfoSection(
    manga: Manga,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Content Rating
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = getContentRatingColor(manga.contentRating)
            ) {
                Text(
                    text = manga.contentRating.capitalizeWords(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Tags Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(manga.tags) { tag ->
                TagChip(tag = tag)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatusDialog(
    manga: MangaForDetailedScreen?,
    currentStatus: String?,
    onStatusSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    if (manga == null) return

    var selectedStatus by remember { mutableStateOf(currentStatus) }
    var enableNotifications by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val statusOptions = listOf(
        null to "None",
        "reading" to "Reading",
        "on_hold" to "On Hold",
        "plan_to_read" to "Plan to Read",
        "completed" to "Completed",
        "re_reading" to "Re-reading",
        "dropped" to "Dropped"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (currentStatus.isNullOrEmpty()) "Add to Library" else "Update Status",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Manga info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Small cover image
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(manga.poster)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = manga.title.firstOrNull() ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = statusOptions.firstOrNull { it.first == selectedStatus }?.second ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        statusOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedStatus = value
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notification toggle (only if status is not None)
                if (selectedStatus != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Notify on new chapters",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = enableNotifications,
                            onCheckedChange = { enableNotifications = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onStatusSelected(selectedStatus)
                            onDismiss()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaRatingDialog(
    manga: Manga?,
    currentRating: Int?,
    onRatingSubmitted: (Int) -> Unit,
    onRatingDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    if (manga == null) return

    var selectedRating by remember { mutableStateOf(currentRating ?: 0) }
    val hasExistingRating = currentRating != null && currentRating > 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (hasExistingRating) "Update Rating" else "Rate Manga",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Manga info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Small cover image
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(manga.poster)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = manga.title.firstOrNull() ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Rating stars
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..10) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating $i",
                            tint = if (i <= selectedRating)
                                Color(0xFFFFD700) // Gold color for selected stars
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { selectedRating = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Rating label
                Text(
                    text = when (selectedRating) {
                        0 -> "No rating"
                        in 1..2 -> "Poor"
                        in 3..4 -> "Below Average"
                        in 5..6 -> "Average"
                        in 7..8 -> "Good"
                        else -> "Excellent"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (hasExistingRating) {
                        TextButton(
                            onClick = {
                                onRatingDeleted()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete Rating")
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (selectedRating > 0) {
                                onRatingSubmitted(selectedRating)
                            }
                            onDismiss()
                        },
                        enabled = selectedRating > 0
                    ) {
                        Text(if (hasExistingRating) "Update" else "Submit")
                    }
                }
            }
        }
    }
}