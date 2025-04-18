package me.thuanc177.kotsune.libs.anilist

object AniListQueries {
    const val GET_MEDIA_ITEM_QUERY = """
        query (${'$'}mediaId: Int) {
            MediaList (mediaId: ${'$'}mediaId) {
                id
                status
            }
        }
    """

    const val DELETE_LIST_ENTRY_QUERY = """
        mutation (${'$'}id: Int) {
            DeleteMediaListEntry (id: ${'$'}id) {
                deleted
            }
        }
    """

    const val SEARCH_QUERY = """
        query(${'$'}query: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}genre_in: [String], ${'$'}genre_not_in: [String], ${'$'}tag_in: [String], ${'$'}tag_not_in: [String], ${'$'}status_in: [MediaStatus], ${'$'}status: MediaStatus, ${'$'}status_not_in: [MediaStatus], ${'$'}sort: [MediaSort], ${'$'}type: MediaType) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo {
              total
              currentPage
              hasNextPage
            }
            media(
              search: ${'$'}query
              genre_in: ${'$'}genre_in
              genre_not_in: ${'$'}genre_not_in
              tag_in: ${'$'}tag_in
              tag_not_in: ${'$'}tag_not_in
              status_in: ${'$'}status_in
              status: ${'$'}status
              status_not_in: ${'$'}status_not_in
              sort: ${'$'}sort
              type: ${'$'}type
            ) {
              id
              idMal
              title {
                english
                romaji
                native
              }
              coverImage {
                extraLarge
                large
                medium
              }
              bannerImage
              description
              genres
              seasonYear
              episodes
              averageScore
              status
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
              description
              genres
              seasonYear
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
              description
              genres
              seasonYear
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
              extraLarge
            }
            bannerImage
            averageScore
            duration
            favourites
            isFavourite
            rankings {
              year
              season
              context
              rank
            }
            format
            genres
            isAdult
            startDate {
              day
              month
              year
            }
            tags {
              name
              description
              rank
            }
            countryOfOrigin
            status
            stats {
              statusDistribution {
                status
                amount
              }
              scoreDistribution {
                score
                amount
              }
            }
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
                  name {
                    full
                    native
                  }
                  age
                  image {
                    large
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
                    native
                  }
                  image {
                    large
                  }
                  languageV2
                  homeTown
                  bloodType
                  description
                  characters {
                    nodes {
                      id
                      age
                      name {
                        full
                        native
                      }
                      image {
                        large
                      }
                      description
                    }
                  }
                } 
              }
            }
            streamingEpisodes {
              title
              url
              thumbnail
            }
            nextAiringEpisode {
              episode
              timeUntilAiring
            }
            recommendations (perPage: 15) {
              edges {
                node {
                  mediaRecommendation {
                    id
                    idMal
                    title {
                      romaji
                      english
                      native
                    }
                    coverImage {
                      large
                    }
                    description
                    genres
                    seasonYear
                    episodes
                    averageScore
                    status
                  }
                }
              }
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
              description
              genres
              seasonYear
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