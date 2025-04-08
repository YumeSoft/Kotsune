package me.thuanc177.kotsune.libs.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AniListAPI
import me.thuanc177.kotsune.libs.anilist.AnilistMedia
import me.thuanc177.kotsune.libs.anilist.AnilistResponse

interface AnimeProvider {
    suspend fun searchForAnime(query: String, page: Int = 1): Result<List<Anime>>
    suspend fun getAnime(animeId: Int): Result<Anime?>
    suspend fun getTrendingAnime(page: Int = 1): Result<List<Anime>>
}

data class Anime(
    val id: Int,
    val title: String,
    val alternateTitles: List<String> = listOf(),
    val description: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val genres: List<String> = listOf(),
    val episodes: Int? = null,
    val seasonYear: Int? = null,
    val status: String? = null,
    val score: Float? = null,
    val nextAiringEpisode: NextAiringEpisode? = null,
)

data class NextAiringEpisode(
    val episode: Int,
    val timeUntilAiring: Int,
    val airingAt: Long
)

class AniListAnimeProvider : AnimeProvider {
    private val api = AniListAPI()

    override suspend fun searchForAnime(query: String, page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            // Use search method with type ANIME instead of searchAnime
            val response = api.search(query = query, page = page, type = "ANIME")

            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.map { media: AnilistMedia ->
                    mapToAnime(media)
                } ?: emptyList<Anime>()
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

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

    override suspend fun getTrendingAnime(page: Int): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrendingAnime(page)

            return@withContext response.map { anilistResponse: AnilistResponse ->
                anilistResponse.data?.Page?.media?.map { media: AnilistMedia ->
                    mapToAnime(media)
                } ?: emptyList<Anime>()
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun mapToAnime(media: AnilistMedia): Anime {
        return Anime(
            id = media.id,
            title = media.title?.english ?: media.title?.romaji ?: "Unknown title",
            alternateTitles = listOfNotNull(
                media.title?.romaji,
                media.title?.english
            ).distinct() + (media.synonyms ?: emptyList()),
            description = media.description,
            coverImage = media.coverImage?.large ?: media.coverImage?.medium,
            bannerImage = media.bannerImage,
            genres = media.genres ?: emptyList(),
            episodes = media.episodes,
            status = media.status,
            score = media.averageScore?.toFloat()?.div(10),
            nextAiringEpisode = media.nextAiringEpisode?.let {
                NextAiringEpisode(
                    episode = it.episode,
                    timeUntilAiring = it.timeUntilAiring,
                    airingAt = it.airingAt.toLong()
                )
            },
        )
    }
}