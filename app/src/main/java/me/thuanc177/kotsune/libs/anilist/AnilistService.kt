package me.thuanc177.kotsune.libs.anilist

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AnilistService {
    @POST(".")
    suspend fun executeQuery(
        @Body requestBody: GraphqlRequest,
        @Header("Authorization") authHeader: String? = null
    ): Response<Map<String, Any>>
}

data class GraphqlRequest(
    val query: String,
    val variables: Map<String, Any?>
)