package me.thuanc177.kotsune.libs.anilist

import retrofit2.http.Streaming


class AnilistTypes {
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
        val seasonYear: Int?,
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

    data class Anime(
        val id: Int,
        val title: List<String> = listOf(),
        val description: String? = null,
        val coverImage: String? = null,
        val bannerImage: String? = null,
        val genres: List<String> = listOf(),
        val episodes: Int? = null,
        val seasonYear: Int? = null,
        val status: String? = null,
        val score: Float? = null,
    )

    data class AnimeDetailed(
        val id: Int,
        val title: List<String> = listOf(),
        val description: String? = null,
        val coverImage: String? = null,
        val bannerImage: String? = null,
        val averageScore: Int? = null,
        val genres: List<String> = listOf(),
        val isAdult: Boolean? = false,
        val countryOfOrigin: String? = null,
        val status: String? = null,
        val seasonYear: Int? = null,
        val episodes: Int? = null,
        val characters: CharactersConnection? = null,
        val streamingEpisodes: List<StreamingEpisode> = listOf(),
        val nextAiringEpisode: NextAiringEpisode? = null
    )

    data class AnimeTitle(
        val english: String? = null,
        val romaji: String? = null,
        val native: String? = null
    )

    data class CoverImage(
        val large: String? = null
    )

    data class CharactersConnection(
        val edges: List<CharacterEdge> = listOf()
    )

    data class CharacterEdge(
        val node: Character? = null,
        val role: String? = null,
        val voiceActors: List<VoiceActor> = listOf()
    )

    data class Character(
        val id: Int,
        val age: String? = null,
        val name: CharacterName? = null,
        val image: CharacterImage? = null,
        val dateOfBirth: DateOfBirth? = null,
        val description: String? = null
    )

    data class CharacterName(
        val full: String? = null,
        val native: String? = null
    )

    data class CharacterImage(
        val medium: String? = null
    )

    data class DateOfBirth(
        val day: Int? = null,
        val month: Int? = null,
        val year: Int? = null
    )

    data class VoiceActor(
        val age: Int? = null,
        val name: VoiceActorName? = null,
        val image: VoiceActorImage? = null,
        val homeTown: String? = null,
        val bloodType: String? = null
    )

    data class VoiceActorName(
        val full: String? = null
    )

    data class VoiceActorImage(
        val medium: String? = null
    )

    data class NextAiringEpisode(
        val episode: Int,
        val timeUntilAiring: Int,
        val airingAt: Long
    )
}