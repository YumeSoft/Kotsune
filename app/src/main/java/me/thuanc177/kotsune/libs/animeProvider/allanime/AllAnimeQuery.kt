package me.thuanc177.kotsune.libs.animeProvider.allanime

/**
 * GraphQL queries for AllAnime API
 */
object GqlQueries {

    /**
     * Query to search for anime shows
     */
    const val SEARCH_GQL = """
    query (
      ${'$'}search: SearchInput
      ${'$'}limit: Int
      ${'$'}page: Int
      ${'$'}translationType: VaildTranslationTypeEnumType
      ${'$'}countryOrigin: VaildCountryOriginEnumType
    ) {
      shows(
        search: ${'$'}search
        limit: ${'$'}limit
        page: ${'$'}page
        translationType: ${'$'}translationType
        countryOrigin: ${'$'}countryOrigin
      ) {
        pageInfo {
          total
        }
        edges {
          _id
          aniListId
          name
          availableEpisodes
          episodeCount
          __typename
        }
      }
    }
    """

    /**
     * Query to get episode information
     */
    const val EPISODES_GQL = """
    query (
      ${'$'}showId: String!
      ${'$'}translationType: VaildTranslationTypeEnumType!
      ${'$'}episodeString: String!
    ) {
      episode(
        showId: ${'$'}showId
        translationType: ${'$'}translationType
        episodeString: ${'$'}episodeString
      ) {
        episodeString
        sourceUrls
        notes
      }
    }
    """

    /**
     * Query to get show details
     */
    const val SHOW_GQL = """
    query (${'$'}showId: String!) {
      show(_id: ${'$'}showId) {
        _id
        aniListId
        name
        availableEpisodesDetail
      }
    }
    """
}