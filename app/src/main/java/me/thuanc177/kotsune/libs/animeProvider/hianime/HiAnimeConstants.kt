package me.thuanc177.kotsune.libs.animeProvider.hianime

  object HiAnimeConstants {
      // Available server names shown to users
      val SERVERS_AVAILABLE = listOf("HD1", "HD2", "StreamSB", "StreamTape")

      // Server ID mappings used in API requests
      object ServerIds {
          const val HD1 = "hd-1"
          const val HD2 = "hd-2"
          const val MEGACLOUD = "megacloud"
          const val STREAMSB = "streamsb"
          const val STREAMTAPE = "streamtape"
          const val ASIANLOAD = "asianload"
          const val GOGOCDN = "gogocdn"
          const val MIXDROP = "mixdrop"
          const val UPCLOUD = "upcloud"
          const val VIZCLOUD = "vizcloud"
          const val MYCLOUD = "mycloud"
          const val FILEMOON = "filemoon"
      }

      // Mapping between display names and server IDs
      val SERVER_MAP = mapOf(
          "HD1" to ServerIds.HD1,
          "HD2" to ServerIds.HD2,
          "StreamSB" to ServerIds.STREAMSB,
          "StreamTape" to ServerIds.STREAMTAPE
      )
  }