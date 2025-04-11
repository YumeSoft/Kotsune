package me.thuanc177.kotsune.libs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AniListAPI
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistMedia
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistResponse
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnimeDetailed
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.NextAiringEpisode
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.StreamingEpisode

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

    private fun mapToAnime(media: AnilistMedia): Anime {
        // Create a list of titles
        val titleList = mutableListOf<String>()

        // Add the primary title first (prioritize english, then romaji)
        media.title?.english?.takeIf { it != "null" }?.let { titleList.add(it) }
        media.title?.romaji?.takeIf { it != "null" && !titleList.contains(it) }?.let { titleList.add(it) }
        media.title?.native?.takeIf { it != "null" && !titleList.contains(it) }?.let { titleList.add(it) }

        // Add synonyms if available
        media.synonyms?.forEach { synonym ->
            if (!titleList.contains(synonym)) titleList.add(synonym)
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

    private fun mapToAnimeDetailed(media: AnilistMedia): AnimeDetailed {
        // Get primary title string
        val primaryTitle = media.title?.english
            ?: media.title?.romaji
            ?: media.title?.native
            ?: "Unknown title"

        return AnimeDetailed(
            id = media.id,
            title = listOf(primaryTitle),
            description = media.description,
            coverImage = media.coverImage?.large ?: media.coverImage?.medium,
            bannerImage = media.bannerImage,
            averageScore = media.averageScore,
            genres = media.genres ?: emptyList(),
            isAdult = false,
            countryOfOrigin = null,
            status = media.status,
            seasonYear = media.seasonYear,
            episodes = media.episodes,
            characters = null,
            streamingEpisodes = media.streamingEpisodes?.map { episode ->
                StreamingEpisode(
                    title = episode.title,
                    thumbnail = episode.thumbnail
                )
            } ?: emptyList(),
            nextAiringEpisode = media.nextAiringEpisode?.let {
                NextAiringEpisode(
                    episode = it.episode,
                    timeUntilAiring = it.timeUntilAiring,
                    airingAt = it.airingAt.toLong()
                )
            }
        )
    }
}