package me.thuanc177.kotsune.libs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AniListAPI
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistMedia
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistResponse
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeTitle

interface AnimeProvider {
    suspend fun searchForAnime(query: String, page: Int = 1): Result<List<Anime>>
    suspend fun getAnime(animeId: Int): Result<Anime?>
    suspend fun getTrendingAnime(page: Int = 1): Result<List<Anime>>
    suspend fun getAnimeDetailed(animeId: Int): Result<AnimeDetailed?>
}

class AniListAnimeProvider : AnimeProvider {
    private val api = AniListAPI()

    override suspend fun searchForAnime(query: String, page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(query = query, page = page, type = "ANIME")

            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.map { media: AnilistMedia ->
                    mapToAnime(media)
                } ?: emptyList()
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    // Fix: Keep original function signature matching the interface
    override suspend fun getAnime(animeId: Int): Result<Anime?> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAnime(animeId)

            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.firstOrNull()?.let { media: AnilistMedia ->
                    mapToAnime(media)
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    // New method to get detailed anime information
    override suspend fun getAnimeDetailed(animeId: Int): Result<AnimeDetailed?> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAnime(animeId)

            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.firstOrNull()?.let { media: AnilistMedia ->
                    mapToAnimeDetailed(media)
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    override suspend fun getTrendingAnime(page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrendingAnime(page)
            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.map { media: AnilistMedia ->
                    mapToAnime(media)
                } ?: emptyList()
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    fun mapToAnime(media: AnilistMedia): Anime {
        // Create a list of titles
        val titleList = mutableListOf<String>()

        // Add the primary title first (prioritize English, then romaji)
        media.title?.english?.takeIf { it.isNotBlank() }?.let { titleList.add(it) }
        media.title?.romaji?.takeIf { it.isNotBlank() && !titleList.contains(it) }?.let { titleList.add(it) }
        media.title?.native?.takeIf { it.isNotBlank() && !titleList.contains(it) }?.let { titleList.add(it) }

        // Add synonyms if available
        media.synonyms?.forEach { synonym ->
            if (synonym.isNotBlank() && !titleList.contains(synonym)) titleList.add(synonym)
        }

        // If no titles were found, add a default
        if (titleList.isEmpty()) {
            titleList.add("Unknown title")
        }

        return Anime(
            id = media.id,
            title = titleList,
            description = media.description,
            coverImage = media.coverImage?.large ?: media.coverImage?.medium,
            bannerImage = media.bannerImage,
            genres = media.genres ?: emptyList(),
            episodes = media.episodes,
            seasonYear = media.seasonYear,
            status = media.status,
            score = media.averageScore?.toFloat()?.div(10)
        )
    }

    fun mapToAnimeDetailed(media: AnilistMedia): AnimeDetailed {
        return AnimeDetailed(
            id = media.id,
            title = media.title?.let { anilistTitle ->
                AnimeTitle(
                    english = anilistTitle.english,
                    romaji = anilistTitle.romaji,
                    native = anilistTitle.native
                )
            },
            description = media.description,
            coverImage = media.coverImage,
            bannerImage = media.bannerImage,
            averageScore = media.averageScore,
            duration = media.duration,
            favourites = media.favourites,
            isFavourite = media.isFavourite ?: false,
            rankings = media.rankings,
            format = media.format,
            genres = media.genres ?: emptyList(),
            isAdult = media.isAdult ?: false,
            startDate = media.startDate,
            tags = media.tags,
            countryOfOrigin = media.countryOfOrigin,
            status = media.status,
            stats = media.stats,
            seasonYear = media.seasonYear,
            trailer = media.trailer,
            characters = media.characters,
            episodes = media.episodes,
            streamingEpisodes = media.streamingEpisodes,
            nextAiringEpisode = media.nextAiringEpisode,
            recommendations = media.recommendations
        )
    }
}