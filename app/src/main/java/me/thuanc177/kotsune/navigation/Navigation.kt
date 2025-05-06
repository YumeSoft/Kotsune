package me.thuanc177.kotsune.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv

import androidx.compose.ui.graphics.vector.ImageVector

// Define Screens/Routes
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Anime : Screen("anime_home", "Anime", Icons.Filled.Tv, Icons.Outlined.Tv)
    object Manga : Screen("manga_home", "Manga", Icons.Filled.Book, Icons.Outlined.Book)
    object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    object Tracking : Screen("tracking", "Tracking", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)

    // Detail Screens (don't need icons for bottom nav)
    object AnimeDetail : Screen("anime_detailed/{anilistId}", "Anime Details", Icons.Filled.Tv, Icons.Outlined.Tv) {
        fun createRoute(anilistId: String) = "anime_detailed/$anilistId"
    }
    object MangaDetail : Screen("manga_detail/{mangaId}", "Manga Details", Icons.Filled.Book, Icons.Outlined.Book) {
        fun createRoute(mangaId: String) = "manga_detail/$mangaId"
    }
    object WatchAnime : Screen("watch_anime/{showId}/{episodeNumber}", "Watch", Icons.Filled.Tv, Icons.Outlined.Tv) {
        fun createRoute(
            showId: String,
            episodeNumber: Float
        ) = "watch_anime/$showId/$episodeNumber"
    }
    object ReadManga : Screen("read_manga/{chapterId}/{languageCode}", "Read", Icons.Filled.Book, Icons.Outlined.Book) {
        fun createRoute(chapterId: String, languageCode: String) = "manga_reader/$chapterId/$languageCode"
    }

    object TrackingSelection : Screen("tracking_selection", "Tracking", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
    object AnilistTracking : Screen("anilist_tracking", "Anilist", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
    object MangadexTracking : Screen("mangadex_tracking", "MangaDex", Icons.Filled.Book, Icons.Outlined.Book)
}
// Update the bottomNavItems to use the selection screen instead
val bottomNavItems = listOf(
    Screen.Anime,
    Screen.Manga,
    Screen.Search,
    Screen.TrackingSelection  // Changed from Screen.Tracking to Screen.TrackingSelection
)