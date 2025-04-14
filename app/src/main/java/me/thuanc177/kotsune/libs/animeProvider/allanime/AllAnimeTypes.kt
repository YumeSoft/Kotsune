package me.thuanc177.kotsune.libs.animeProvider.allanime

data class AllAnimeEpisodesInfo(
    val dub: Int? = null,
    val sub: Int? = null,
    val raw: Int? = null
)

data class AllAnimePageInfo(
    val total: Int? = null
)

data class AllAnimeShow(
    val _id: String? = null,
    val name: String? = null,
    val availableEpisodesDetail: AllAnimeEpisodesInfo? = null,
    val __typename: String? = null
)

data class AllAnimeSearchResult(
    val _id: String? = null,
    val name: String? = null,
    val availableEpisodes: List<String>? = null,
    val __typename: String? = null
)

data class AllAnimeShows(
    val pageInfo: AllAnimePageInfo? = null,
    val edges: List<AllAnimeSearchResult>? = null
)

data class AllAnimeSearchResults(
    val shows: AllAnimeShows? = null
)

data class AllAnimeSourcesDownloads(
    val sourceName: String? = null,
    val dowloadUrl: String? = null
)

data class AllAnimeSources(
    val sourceUrl: String? = null,
    val priority: Float? = null,
    val sandbox: String? = null,
    val sourceName: String? = null,
    val type: String? = null,
    val className: String? = null,
    val streamerId: String? = null,
    val downloads: AllAnimeSourcesDownloads? = null
)

// Converting the Literal type to an enum
enum class Server(val value: String) {
    GOGOANIME("gogoanime"),
    DROPBOX("dropbox"),
    WETRANSFER("wetransfer"),
    SHAREPOINT("sharepoint")
}

data class AllAnimeEpisode(
    val episodeString: String? = null,
    val sourceUrls: List<AllAnimeSources>? = null,
    val notes: String? = null
)

data class AllAnimeStream(
    val link: String? = null,
    val mp4: Boolean? = null,
    val hls: Boolean? = null,
    val resolutionStr: String? = null,
    val fromCache: String? = null,
    val priority: Int? = null,
    val headers: Map<String, String>? = null
)

data class AllAnimeStreams(
    val links: List<AllAnimeStream>? = null
)