package me.thuanc177.kotsune.navigation

// Import your screen composable here
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import me.thuanc177.kotsune.ui.screens.AnimeScreen
import me.thuanc177.kotsune.ui.screens.MangaScreen
import me.thuanc177.kotsune.ui.screens.SearchScreen
import me.thuanc177.kotsune.ui.screens.AnimeDetailedScreen
import me.thuanc177.kotsune.ui.screens.WatchAnimeScreen

// Import ViewModels if needed directly (or use Hilt)
// import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(UnstableApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Anime.route,
        modifier = modifier
    ) {
        // Bottom Nav Screens
        composable(Screen.Anime.route) {
            AnimeScreen(navController = navController /* viewModel = hiltViewModel() */)
        }
        composable(Screen.Manga.route) {
            MangaScreen(navController = navController /* viewModel = hiltViewModel() */)
        }
        composable(Screen.Search.route) {
            SearchScreen(navController = navController /* viewModel = hiltViewModel() */)
        }
        // Detail Screens
        composable(
            route = Screen.AnimeDetail.route,
            arguments = listOf(navArgument("anilistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val anilistId = backStackEntry.arguments?.getString("anilistId") ?: "INVALID_ID"
            AnimeDetailedScreen(
                navController = navController,
                anilistId = anilistId.toIntOrNull() ?: -1
            )
        }
        // Add this to the NavHost in AppNavigation.kt
        composable(
            route = Screen.WatchAnime.route,
            arguments = listOf(
                navArgument("anilistId") { type = NavType.IntType },
                navArgument("animeTitle") { type = NavType.StringType },
                navArgument("episodeNumber") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val anilistId = backStackEntry.arguments?.getInt("anilistId") ?: -1
            val animeTitle = backStackEntry.arguments?.getString("animeTitle") ?: "Unknown"
            val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber") ?: 1
            WatchAnimeScreen(
                navController = navController,
                anilistId = anilistId,
                animeTitle = animeTitle,
                episodeNumber = episodeNumber
            )
        }
////        composable(Screen.Tracking.route) {
////            TrackingScreen(navController = navController /* viewModel = hiltViewModel() */)
////        }
//
//         Detail Screens
//        composable(
//            route = Screen.AnimeDetail.route,
//            arguments = listOf(navArgument("animeId") { type = NavType.StringType })
//        ) { backStackEntry ->
//            val animeId = backStackEntry.arguments?.getString("animeId") ?: "INVALID_ID"
//            AnimeDetailScreen(
//                animeId = animeId,
//                navController = navController
//                /* viewModel = hiltViewModel() */
//            )
//        }
//        composable(
//            route = Screen.MangaDetail.route,
//            arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
//        ) { backStackEntry ->
//            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: "INVALID_ID"
//            MangaDetailScreen(
//                mangaId = mangaId,
//                navController = navController
//                /* viewModel = hiltViewModel() */
//            )
//        }
//
//        // Placeholder Player/Reader Screens
//        composable(
//            route = Screen.AnimePlayer.route,
//            arguments = listOf(navArgument("episodeId") { type = NavType.StringType })
//        ) { backStackEntry ->
//            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: "INVALID_ID"
//            // TODO: Create AnimePlayerScreen
//            PlaceholderScreen(title = "Anime Player for Ep $episodeId", navController = navController)
//        }
//
//        composable(
//            route = Screen.MangaReader.route,
//            arguments = listOf(
//                navArgument("chapterId") { type = NavType.StringType },
//                navArgument("languageCode") { type = NavType.StringType }
//            )
//        ) { backStackEntry ->
//            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: "INVALID_ID"
//            val langCode = backStackEntry.arguments?.getString("languageCode") ?: "N/A"
//            // TODO: Create MangaReaderScreen
//            PlaceholderScreen(title = "Manga Reader for Ch $chapterId ($langCode)", navController = navController)
//        }
    }
}