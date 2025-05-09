package me.thuanc177.kotsune.libs.mangaProvider.mangadex

object MangaDexTypes {
    data class Manga(
        val id: String,
        val title: List<String>,
        val originalLanguage: String?,
        val poster: String?,
        val status: String,
        val description: String,
        val lastUpdated: String?,
        val lastChapter: Int?,
        val latestUploadedChapterId: String?,
        val year: Int?,
        val contentRating: String,
        val tags: MutableList<MangaTag> = mutableListOf(),
    )

    data class MangaTag(
        val id: String,
        val name: String
    )

    data class MangaTagGrouped(
        val group: String,
        val id: String,
        val name: String
    )

    data class ReadingHistoryItem(
        val chapterId: String,
        val readDate: String
    )

    data class RatingStatistics(
        val average: Float,
        val bayesian: Float,
        val distribution: Map<Int, Int>
    )

    data class MangaStatistics(
        val rating: RatingStatistics?,
        val follows: Int,
        val comments: Comments
    )

    data class Comments(
        val repliesCount: Int,
        val threadId: String?
    )

    data class ChapterModel(
        val id: String,
        val number: String,
        val title: String,
        val publishedAt: String,
        val pages: Int = 0,
        val thumbnail: String? = null,
        var isRead: Boolean = false,
        val volume: String? = null,
        val language: String = "en",
        val translatorGroup: String? = null,
        val languageFlag: String? = null,
        val isOfficial: Boolean = false,
        val externalUrl: String? = null
    )

    data class MangaDetailedState(
        val manga: Manga? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val isFavorite: Boolean = false,
        val chapters: List<ChapterModel> = emptyList(),
        val chaptersLoading: Boolean = false,
        val chaptersError: String? = null,
        val chapterSortAscending: Boolean = false,
        val selectedChapterIndex: Int? = null,
        val selectedTranslationGroup: String? = null
    )

    data class AnimeSearchResult(
        val id: Int,
        val title: String,
        val coverImage: String,
        val seasonYear: String? = null,
        val rating: Float? = null,
        val status: String? = null,
        val genres: List<String>? = null
    )

    sealed class SearchState {
        data object Initial : SearchState()
        data object Loading : SearchState()
        data object Success : SearchState()
        data object Empty : SearchState()
        data class Error(val message: String) : SearchState()
    }

    // MangaDex user profile data class
    data class MangaDexUserProfile(
        val id: String,
        val username: String,
        val avatarUrl: String?
    )

    // MangaDex user reading status data class
    data class MangaMoreDetails(
        val statistics: MangaStatistics,
        val status: String,
        val manga: Manga
    )

    data class MangaForDetailedScreen(
        val id: String,
        val title: List<String>,
        val originalLanguage: String?,
        val poster: String?,
        val status: String,
        val description: String,
        val lastUpdated: String?,
        val lastChapter: Int?,
        val latestUploadedChapterId: String?,
        val year: Int?,
        val contentRating: String,
        val tags: MutableList<MangaTag> = mutableListOf(),
        val demographic: String? = null,
        val links: List<MangaLink>? = null
    )

    data class MangaLink(
        val url: String,
        val type: String,
        val name: String
    )
}