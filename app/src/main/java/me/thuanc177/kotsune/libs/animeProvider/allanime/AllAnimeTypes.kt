package me.thuanc177.kotsune.libs.animeProvider.allanime

data class AllAnimeEpisodesInfo(
    val dub: Int? = null,
    val sub: Int? = null,
    val raw: Int? = null
)

data class AllAnimePageInfo(
    val total: Int? = null
)

data class EpisodeInfo(
    val episodeIdNum: Float,
    val notes: String? = null,
    val description: String? = null,
    val thumbnails: List<String> = emptyList(),
    val uploadDates: Map<String, String>? = null
) {
    fun getThumbnailUrl(): String? {
        return thumbnails.firstOrNull { it.startsWith("https://") }
    }

    fun getFormattedDate(type: String = "sub"): String? {
        val dateStr = uploadDates?.get(type) ?: return null
        return try {
            // Parse ISO 8601 date format (e.g., 2023-09-29T13:11:27.000Z)
            val parts = dateStr.split("T", ".", "Z")
            val datePart = parts[0].split("-")
            val timePart = parts[1].split(":")

            val year = datePart[0]
            val month = getMonthName(datePart[1].toInt())
            val day = datePart[2]
            val hour = timePart[0].toInt()
            val minute = timePart[1]

            // Format: "September 29, 2023 at 1:11 PM"
            "$month $day, $year at ${formatHour(hour)}:$minute ${if (hour < 12) "AM" else "PM"}"
        } catch (e: Exception) {
            dateStr // Return original if parsing fails
        }
    }

    private fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> month.toString()
        }
    }

    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12"
            hour > 12 -> (hour - 12).toString()
            else -> hour.toString()
        }
    }
}

data class EpisodeInfoResponse(
    val data: EpisodeInfosData
)

data class EpisodeInfosData(
    val episodeInfos: List<EpisodeInfo>
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