package me.thuanc177.kotsune.libs.anilist

/**
 * Data classes for AniList API responses
 */
data class AnilistMediaTitle(
    val english: String? = null,
    val native: String? = null,
    val romaji: String? = null
)

data class AnilistImage(
    val medium: String? = null,
    val large: String? = null,
    val extraLarge: String? = null,
    val small: String? = null
)

data class AnilistUser(
    val id: Int,
    val name: String,
    val bannerImage: String? = null,
    val avatar: String? = null
)

data class AnilistMediaTrailer(
    val id: String? = null,
    val site: String? = null
)

data class AnilistStudio(
    val name: String,
    val isAnimationStudio: Boolean
)

data class AnilistMediaTag(
    val name: String,
    val rank: Int? = null
)

data class AnilistDateObject(
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null
)

data class AnilistNextAiringEpisode(
    val timeUntilAiring: Int,
    val airingAt: Int,
    val episode: Int
)

data class StreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null
)

data class AnilistMediaListEntry(
    val id: Int? = null,
    val status: String? = null,
    val progress: Int? = null
)

data class AnilistMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: AnilistMediaTitle? = null,
    val coverImage: AnilistImage? = null,
    val bannerImage: String? = null,
    val trailer: AnilistMediaTrailer? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val genres: List<String>? = null,
    val synonyms: List<String>? = null,
    val description: String? = null,
    val status: String? = null,
    val startDate: AnilistDateObject? = null,
    val endDate: AnilistDateObject? = null,
    val mediaListEntry: AnilistMediaListEntry? = null,
    val nextAiringEpisode: AnilistNextAiringEpisode? = null,
    val streamingEpisodes: List<StreamingEpisode>? = null
)

data class PageInfo(
    val total: Int,
    val currentPage: Int,
    val hasNextPage: Boolean
)

data class Page(
    val pageInfo: PageInfo? = null,
    val media: List<AnilistMedia>? = null
)

data class AnilistResponse(
    val data: Data? = null,
    val errors: List<Error>? = null
)

data class Data(
    val Page: Page? = null,
    val Viewer: AnilistUser? = null,
    val User: AnilistUser? = null
)

data class Error(
    val message: String
)