package me.thuanc177.kotsune.libs.animeProvider.hianime

sealed class HiAnimeTypes {

    data class HiAnimeSkipTime(
        val start: Int,
        val end: Int
    )

    data class HiAnimeSource(
        val file: String,
        val type: String
    )

    data class HiAnimeTrack(
        val file: String,
        val label: String,
        val kind: String  // One of: "captions", "thumbnails", "audio"
    )

    data class HiAnimeStream(
        val sources: List<HiAnimeSource>,
        val tracks: List<HiAnimeTrack>,
        val encrypted: Boolean,
        val intro: HiAnimeSkipTime,
        val outro: HiAnimeSkipTime,
        val server: Int
    )
}