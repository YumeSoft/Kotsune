package me.thuanc177.kotsune.navigation

// Import your screen composable here
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexAPI
import me.thuanc177.kotsune.repository.ChaptersRepository
import me.thuanc177.kotsune.repository.FavoritesRepository
import me.thuanc177.kotsune.ui.screens.AnimeScreen
import me.thuanc177.kotsune.ui.screens.MangaScreen
import me.thuanc177.kotsune.ui.screens.SearchScreen
import me.thuanc177.kotsune.ui.screens.AnimeDetailedScreen
import me.thuanc177.kotsune.ui.screens.MangaDetailedScreen
import me.thuanc177.kotsune.ui.screens.ReadMangaScreen
import me.thuanc177.kotsune.ui.screens.WatchAnimeScreen
import me.thuanc177.kotsune.viewmodel.ChapterModel
import me.thuanc177.kotsune.viewmodel.MangaDetailedViewModel
import me.thuanc177.kotsune.viewmodel.ViewModelContextProvider.context

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
                navArgument("showId") { type = NavType.StringType },
                navArgument("episodeNumber") { type = NavType.FloatType }
            )
        ) { backStackEntry ->
            val showId = backStackEntry.arguments?.getString("showId") ?: ""
            val episodeNumber = backStackEntry.arguments?.getFloat("episodeNumber") ?: 1f

            WatchAnimeScreen(
                showId = showId,
                episodeNumber = episodeNumber,
                navController = navController
            )
        }

        composable(
            route = Screen.MangaDetail.route,
            arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: "INVALID_ID"

            // Create ViewModel
            val mangaDexAPI = MangaDexAPI(AppConfig.getInstance(context))
            val favoritesRepository = FavoritesRepository(LocalContext.current)
            val viewModel = remember {
                MangaDetailedViewModel(
                    mangaDexAPI = mangaDexAPI,
                    mangaId = mangaId,
                    favoritesRepository = favoritesRepository
                )
            }

            MangaDetailedScreen(
                navController = navController,
                mangaId = mangaId,
                viewModel = viewModel,
                onBackPressed = { navController.popBackStack() },
                onChapterClick = { chapter, chaptersList ->
                    // Store chapters for use in reader
                    ChaptersRepository.setChaptersForManga(mangaId, chaptersList)
                    navController.navigate("read_manga/${chapter.id}/en")
                }
            )
        }

        composable(
            route = Screen.ReadManga.route,
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: "INVALID_ID"

            // Get the manga ID from the previous screen's back stack entry to fetch correct chapters
            val mangaId = navController.previousBackStackEntry?.arguments?.getString("mangaId") ?: ""
            val mangaDexAPI = MangaDexAPI(AppConfig.getInstance(context))
            val chaptersList = ChaptersRepository.getChaptersForManga(mangaId)

            ReadMangaScreen(
                navController = navController,
                chapterId = chapterId,
                mangaDexAPI = mangaDexAPI,
                chaptersList = chaptersList,
            )
        }
    }
}