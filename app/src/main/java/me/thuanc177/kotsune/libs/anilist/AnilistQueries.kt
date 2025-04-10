package me.thuanc177.kotsune.libs.anilist

object AniListQueries {
    const val mediaListMutation = """
        mutation (${'$'}userId: Int, ${'$'}mediaId: Int, ${'$'}status: MediaListStatus) {
            SaveMediaListEntry (userId: ${'$'}userId, mediaId: ${'$'}mediaId, status: ${'$'}status) {
                id
                status
            }
        }
    """

    const val mediaListQuery = """
        query (${'$'}userId: Int, ${'$'}status: MediaListStatus, ${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
            MediaListCollection (userId: ${'$'}userId, status: ${'$'}status, type: ${'$'}type, page: ${'$'}page, perPage: ${'$'}perPage) {
                lists {
                    name
                    entries {
                        media {
                            id
                            title {
                                english
                                romaji
                                native
                            }
                            coverImage {
                              large
                            }
                            countryOfOrigin
                            status
                            seasonYear
                            averageScore
                        }
                    }
                }
            }
        }
    """

    const val getMedialistItemQuery = """
        query (${'$'}mediaId: Int) {
            MediaList (mediaId: ${'$'}mediaId) {
                id
                status
            }
        }
    """

    const val deleteListEntryQuery = """
        mutation (${'$'}id: Int) {
            DeleteMediaListEntry (id: ${'$'}id) {
                deleted
            }
        }
    """

    const val SEARCH_QUERY = """
        query(\${'$'}query: String, \${'$'}page: Int, \${'$'}genre_in: [String], \${'$'}genre_not_in: [String], \${'$'}tag_in: [String], \${'$'}tag_not_in: [String], \${'$'}status_in: [MediaStatus], \${'$'}status: MediaStatus, \${'$'}startDate: FuzzyDateInt, \${'$'}status_not_in: [MediaStatus], \${'$'}popularity_greater: Int, \${'$'}popularity_lesser: Int, \${'$'}averageScore_greater: Int, \${'$'}averageScore_lesser: Int, \${'$'}startDate_greater: FuzzyDateInt) {
          Page(perPage: \${'$'}max_results, page: \${'$'}page) {
            pageInfo {
              total
              currentPage
              hasNextPage
            }
            media(
              search: \${'$'}query
              genre_in: \${'$'}genre_in
              genre_not_in: \${'$'}genre_not_in
              tag_in: \${'$'}tag_in
              tag_not_in: \${'$'}tag_not_in
              status_in: \${'$'}status_in
              status: \${'$'}status
              startDate: \${'$'}startDate
              status_not_in: \${'$'}status_not_in
              popularity_greater: \${'$'}popularity_greater
              popularity_lesser: \${'$'}popularity_lesser
              averageScore_greater: \${'$'}averageScore_greater
              averageScore_lesser: \${'$'}averageScore_lesser
              startDate_greater: \${'$'}startDate_greater
            ) {
              id
              idMal
              title {
                english
                romaji
                native
              }
              coverImage {
                large
              }
              trailer {
                site
                id
              }
              mediaListEntry {
                status
                id
                progress
              }
              popularity
              streamingEpisodes {
                title
                thumbnail
              }
              favourites
              averageScore
              episodes
              genres
              synonyms
              studios {
                nodes {
                  name
                  isAnimationStudio
                }
              }
              tags {
                name
              }
              startDate {
                year
                month
                day
              }
              endDate {
                year
                month
                day
              }
              status
              description
              nextAiringEpisode {
                timeUntilAiring
                airingAt
                episode
              }
            }
          }
        }
    """

    const val TRENDING_QUERY = """
        query (${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            media(
              sort: TRENDING_DESC
              type: ${'$'}type
              genre_not_in: ["hentai"]
            ) {
              id
              idMal
              title {
                romaji
                english
                native
              }
              coverImage {
                medium
                large
              }
              bannerImage
              episodes
              averageScore
              status
            }
          }
        }
    """

    const val MARK_AS_READ_MUTATION = """
        mutation{
          UpdateUser{
            unreadNotificationCount
          }
        }
    """

    const val REVIEWS_QUERY = """
        query(${'$'}id: Int){
          Page{
            pageInfo{
              total
            }
            reviews(mediaId: ${'$'}id){
              summary
              user{
                name
                avatar {
                  large
                  medium
                }
              }
              body
            }
          }
        }
    """

    const val NOTIFICATION_QUERY = """
        query {
          Page(perPage: 5) {
            pageInfo {
              total
            }
            notifications(resetNotificationCount: true, type: AIRING) {
              ... on AiringNotification {
                id
                type
                episode
                contexts
                createdAt
                media {
                  id
                  idMal
                  title {
                    romaji
                    english
                    native
                  }
                  coverImage {
                    medium
                  }
                }
              }
            }
          }
        }
    """

//    const val GET_MEDIALIST_ITEM_QUERY = """
//        query (${'$'}mediaId: Int) {
//          MediaList(mediaId: ${'$'}mediaId) {
//            id
//          }
//        }
////    """
//
//    const val DELETE_LIST_ENTRY_QUERY = """
//        mutation (${'$'}id: Int) {
//          DeleteMediaListEntry(id: ${'$'}id) {
//            deleted
//          }
//        }
//    """

    const val GET_LOGGED_IN_USER_QUERY = """
        query{
          Viewer{
            id
            name
            bannerImage
            avatar {
              large
              medium
            }
          }
        }
    """

    const val GET_USER_INFO = """
        query (${'$'}userId: Int) {
          User(id: ${'$'}userId) {
            name
            about
            avatar {
              large
              medium
            }
            bannerImage
            statistics {
              anime {
                count
                minutesWatched
                episodesWatched
                genres {
                  count
                  meanScore
                  genre
                }
                tags {
                  tag {
                    id
                  }
                  count
                  meanScore
                }
              }
              manga {
                count
                meanScore
                chaptersRead
                volumesRead
                tags {
                  count
                  meanScore
                }
                genres {
                  count
                  meanScore
                }
              }
            }
            favourites {
              anime {
                nodes {
                  title {
                    english
                    romaji
                    native
                  }
                }
              }
              manga {
                nodes {
                  title {
                    english
                    romaji
                    native
                  }
                }
              }
            }
          }
        }
    """

    const val MEDIA_LIST_MUTATION = """
        mutation (
          ${'$'}mediaId: Int
          ${'$'}scoreRaw: Int
          ${'$'}repeat: Int
          ${'$'}progress: Int
          ${'$'}status: MediaListStatus
        ) {
          SaveMediaListEntry(
            mediaId: ${'$'}mediaId
            scoreRaw: ${'$'}scoreRaw
            progress: ${'$'}progress
            repeat: ${'$'}repeat
            status: ${'$'}status
          ) {
            id
            status
            mediaId
            score
            progress
            repeat
            startedAt {
              year
              month
              day
            }
            completedAt {
              year
              month
              day
            }
          }
        }
    """

    const val MEDIA_LIST_QUERY = """
        query (${'$'}userId: Int, ${'$'}status: MediaListStatus, ${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            pageInfo {
              currentPage
              total
            }
            mediaList(userId: ${'$'}userId, status: ${'$'}status, type: ${'$'}type) {
              mediaId
              media {
                id
                idMal
                title {
                  english
                  romaji
                  native
                }
                coverImage {
                  medium
                  large
                }
                trailer {
                  site
                  id
                }
                popularity
                streamingEpisodes {
                  title
                  thumbnail
                }
                favourites
                averageScore
                episodes
                genres
                synonyms
                studios {
                  nodes {
                    name
                    isAnimationStudio
                  }
                }
                tags {
                  name
                }
                startDate {
                  year
                  month
                  day
                }
                endDate {
                  year
                  month
                  day
                }
                status
                description
                mediaListEntry {
                  status
                  id
                  progress
                }
                nextAiringEpisode {
                  timeUntilAiring
                  airingAt
                  episode
                }
              }
              status
              progress
              score
              repeat
              notes
              startedAt {
                year
                month
                day
              }
              completedAt {
                year
                month
                day
              }
              createdAt
            }
          }
        }
    """

    const val MOST_FAVOURITE_QUERY = """
        query (${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            media(sort: FAVOURITES_DESC, type: ${'$'}type, genre_not_in: ["hentai"]) {
              id
              idMal
              title {
                romaji
                english
                native
              }
              coverImage {
                medium
                large
              }
              trailer {
                site
                id
              }
              mediaListEntry {
                status
                id
                progress
              }
              popularity
              streamingEpisodes {
                title
                thumbnail
              }
              favourites
              averageScore
              episodes
              description
              genres
              synonyms
              studios {
                nodes {
                  name
                  isAnimationStudio
                }
              }
              tags {
                name
              }
              startDate {
                year
                month
                day
              }
              endDate {
                year
                month
                day
              }
              status
              nextAiringEpisode {
                timeUntilAiring
                airingAt
                episode
              }
            }
          }
        }
    """

    const val MOST_SCORED_QUERY = """
        query (${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            media(sort: SCORE_DESC, type: ${'$'}type, genre_not_in: ["hentai"]) {
              id
              idMal
              title {
                romaji
                english
                native
              }
              coverImage {
                medium
                large
              }
              episodes
              averageScore
              status
            }
          }
        }
    """

    const val MOST_POPULAR_QUERY = """
        query (${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            media(sort: POPULARITY_DESC, type: ${'$'}type, genre_not_in: ["hentai"]) {
              id
              idMal
              title {
                romaji
                english
                native
              }
              coverImage {
                medium
                large
              }
              episodes
              averageScore
              status
            }
          }
        }
    """
    const val ANIME_INFO_QUERY = """
    query (${'$'}id: Int) {
      Media(type: ANIME, id: ${'$'}id) {
        id
        title {
          english
          romaji
          native
        }
        coverImage {
          large
        }
        bannerImage
        averageScore
        genres
        isAdult
        countryOfOrigin
        status
        seasonYear
        description
        trailer {
          id
          site
        }
        characters {
          edges {
            node {
              id
              age
              name {
                full
                native
              }
              image {
                medium
              }
              dateOfBirth {
                day
                month
                year
              }
              description
            }
            role
            voiceActors {
              age
              name {
                full
              }
              image {
                medium
              }
              homeTown
              bloodType
            }
          }
        }
        episodes
        streamingEpisodes {
          title
          url
          site
          thumbnail
        }
        nextAiringEpisode {
          episode
          timeUntilAiring
        }
      }
    }
"""

    const val MOST_RECENTLY_UPDATED_QUERY = """
        query (${'$'}type: MediaType, ${'$'}page: Int, ${'$'}perPage: Int) {
          Page(perPage: ${'$'}perPage, page: ${'$'}page) {
            media(
              sort: UPDATED_AT_DESC
              type: ${'$'}type
              genre_not_in: ["hentai"]
              status: RELEASING
            ) {
              id
              idMal
              title {
                romaji
                english
                native
              }
              coverImage {
                medium
                large
              }
              episodes
              averageScore
              status
            }
          }
        }
    """

    const val RECOMMENDED_QUERY = """
        query (${'$'}mediaRecommendationId: Int, ${'$'}page: Int) {
          Page(perPage: 50, page: ${'$'}page) {
            recommendations(mediaRecommendationId: ${'$'}mediaRecommendationId) {
              media {
                id
                idMal
                mediaListEntry {
                  status
                  id
                  progress
                }
                title {
                  english
                  romaji
                  native
                }
                coverImage {
                  medium
                  large
                }
                description
                episodes
                trailer {
                  site
                  id
                }
                genres
                synonyms
                averageScore
                popularity
                streamingEpisodes {
                  title
                  thumbnail
                }
                favourites
                tags {
                  name
                }
                startDate {
                  year
                  month
                  day
                }
                endDate {
                  year
                  month
                  day
                }
                status
                nextAiringEpisode {
                  timeUntilAiring
                  airingAt
                  episode
                }
              }
            }
          }
        }
    """

    const val ANIME_CHARACTERS_QUERY = """
        query (${'$'}id: Int, ${'$'}type: MediaType) {
          Page {
            media(id: ${'$'}id, type: ${'$'}type) {
              characters {
                nodes {
                  name {
                    first
                    middle
                    last
                    full
                    native
                  }
                  image {
                    medium
                    large
                  }
                  description
                  gender
                  dateOfBirth {
                    year
                    month
                    day
                  }
                  age
                  bloodType
                  favourites
                }
              }
            }
          }
        }
    """
    const val ANIME_RELATIONS_QUERY = """
    query (${'$'}id: Int) {
      Media(id: ${'$'}id) {
        relations {
          nodes {
            id
            idMal
            title {
              english
              romaji
              native
            }
            coverImage {
              medium
              large
            }
            mediaListEntry {
              status
              id
              progress
            }
            description
            episodes
            trailer {
              site
              id
            }
            genres
            synonyms
            averageScore
            popularity
            streamingEpisodes {
              title
              thumbnail
            }
            favourites
            tags {
              name
            }
            startDate {
              year
              month
              day
            }
            endDate {
              year
              month
              day
            }
            status
            nextAiringEpisode {
              timeUntilAiring
              airingAt
              episode
            }
          }
        }
      }
    }
    """

//        const val UPCOMING_ANIME_QUERY = """
//    query (${'$'}page: Int, ${'$'}type: MediaType, ${'$'}perPage: Int) {
//      Page(perPage: ${'$'}perPage, page: ${'$'}page) {
//        pageInfo {
//          total
//          perPage
//          currentPage
//          hasNextPage
//        }
//        media(
//          type: ${'$'}type
//          status: NOT_YET_RELEASED
//          sort: POPULARITY_DESC
//          genre_not_in: ["hentai"]
//        ) {
//          id
//          idMal
//          title {
//            romaji
//            english
//            native
//          }
//          coverImage {
//            medium
//            large
//          }
//          trailer {
//            site
//            id
//          }
//          mediaListEntry {
//            status
//            id
//            progress
//          }
//          popularity
//          streamingEpisodes {
//            title
//            thumbnail
//          }
//          favourites
//          averageScore
//          genres
//          synonyms
//          episodes
//          description
//          studios {
//            nodes {
//              name
//              isAnimationStudio
//            }
//          }
//          tags {
//            name
//          }
//          startDate {
//            year
//            month
//            day
//          }
//          endDate {
//            year
//            month
//            day
//          }
//          status
//          nextAiringEpisode {
//            timeUntilAiring
//            airingAt
//            episode
//          }
//        }
//      }
//    }
//    """
}