package me.thuanc177.kotsune.libs.mangaProvider.mangadex.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Token response from MangaDex authentication
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    
    @SerialName("expires_in")
    val expiresIn: Int? = null
)

/**
 * User profile response
 */
@Serializable
data class UserResponse(
    val result: String,
    val data: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val type: String,
    val attributes: UserAttributes
)

@Serializable
data class UserAttributes(
    val username: String,
    val avatar: String? = null
)

/**
 * Manga status response
 */
@Serializable
data class MangaStatusResponse(
    val result: String,
    val statuses: Map<String, String> = mapOf()
)

/**
 * Request to update manga status
 */
@Serializable
data class UpdateStatusRequest(
    val status: String
)

/**
 * Follow response
 */
@Serializable
data class FollowResponse(
    val isFollowing: Boolean
)

/**
 * Manga detail response
 */
@Serializable
data class MangaDetailResponse(
    val result: String,
    val data: MangaDto
)

/**
 * Manga list response
 */
@Serializable
data class MangaListResponse(
    val result: String,
    val data: List<MangaDto> = emptyList()
)

/**
 * Manga DTO
 */
@Serializable
data class MangaDto(
    val id: String,
    val type: String,
    val attributes: MangaAttributes,
    val relationships: List<RelationshipDto> = emptyList()
)

@Serializable
data class MangaAttributes(
    val title: Map<String, String> = mapOf(),
    val altTitles: List<Map<String, String>> = emptyList(),
    val description: Map<String, String> = mapOf(),
    val originalLanguage: String,
    val status: String? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val tags: List<TagDto> = emptyList(),
    val updatedAt: String? = null,
    val version: Int? = null,
    val createdAt: String? = null,
    val isLocked: Boolean? = null,
    val latestUploadedChapter: String? = null
)

/**
 * Tag DTO
 */
@Serializable
data class TagDto(
    val id: String,
    val type: String,
    val attributes: TagAttributes? = null
)

@Serializable
data class TagAttributes(
    val name: Map<String, String> = mapOf(),
    val group: String? = null
)

/**
 * Relationship DTO
 */
@Serializable
data class RelationshipDto(
    val id: String,
    val type: String,
    val attributes: RelationshipAttributes? = null
)

@Serializable
data class RelationshipAttributes(
    val name: String? = null,
    val fileName: String? = null
)

/**
 * Chapter list response
 */
@Serializable
data class ChapterListResponse(
    val result: String,
    val data: List<ChapterDto> = emptyList()
)

/**
 * Chapter DTO
 */
@Serializable
data class ChapterDto(
    val id: String,
    val type: String,
    val attributes: ChapterAttributes,
    val relationships: List<RelationshipDto> = emptyList()
)

@Serializable
data class ChapterAttributes(
    val title: String? = null,
    val volume: String? = null,
    val chapter: String? = null,
    val pages: Int? = 0,
    val translatedLanguage: String? = null,
    val externalUrl: String? = null,
    val publishAt: String? = null,
    val readableAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Manga statistics response
 */
@Serializable
data class StatisticsResponse(
    val result: String,
    val statistics: Map<String, MangaStatisticsDto> = mapOf()
)

@Serializable
data class MangaStatisticsDto(
    val comments: CommentsDto? = null,
    val rating: RatingDto? = null,
    val follows: Int? = null
)

@Serializable
data class CommentsDto(
    val threadId: Int? = null,
    val repliesCount: Int = 0
)

@Serializable
data class RatingDto(
    val average: Double? = null,
    val bayesian: Double? = null,
    val distribution: Map<String, Int> = mapOf()
)

/**
 * User manga status response
 */
@Serializable
data class UserMangaStatusResponse(
    val result: String,
    val status: String? = null
)

/**
 * Rating request
 */
@Serializable
data class RatingRequest(
    val rating: Int
)

/**
 * User ratings response
 */
@Serializable
data class UserRatingsResponse(
    val result: String,
    val ratings: Map<String, Int> = mapOf()
)

/**
 * Read markers response
 */
@Serializable
data class ReadMarkersResponse(
    val result: String,
    val data: List<String> = emptyList()
)

/**
 * Update read markers request
 */
@Serializable
data class UpdateReadMarkersRequest(
    @SerialName("chapterIdsRead")
    val chapterIdsRead: List<String> = emptyList(),
    
    @SerialName("chapterIdsUnread")
    val chapterIdsUnread: List<String> = emptyList()
)

/**
 * All read markers response
 */
@Serializable
data class AllReadMarkersResponse(
    val result: String,
    val data: Map<String, List<String>> = mapOf()
)

@Serializable
data class ReadingHistoryResponse(
    val result: String,
    val data: List<ReadingHistoryDto> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0
)

@Serializable
data class ReadingHistoryDto(
    val chapterId: String,
    val mangaId: String? = null,
    val readDate: String
)