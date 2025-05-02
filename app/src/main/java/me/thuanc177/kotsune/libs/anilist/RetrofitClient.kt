package me.thuanc177.kotsune.libs.anilist

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class TokenRequest(
    val grantType: String,
    val clientId: String,
    val clientSecret: String?,
    val redirectUri: String,
    val code: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

interface AnilistTokenService {
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String?,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String
    ): Response<TokenResponse>
}
object RetrofitClient {
    private const val BASE_URL = "https://graphql.anilist.co/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    internal val gson = GsonBuilder()
        .create()

    val service: AnilistService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AnilistService::class.java)
    }

    val tokenService: AnilistTokenService by lazy {
        Retrofit.Builder()
            .baseUrl("https://anilist.co/api/v2/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
            .create(AnilistTokenService::class.java)
    }
}