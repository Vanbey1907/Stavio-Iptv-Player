package com.example.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class TmdbSearchResponse(
    val results: List<TmdbResult>? = emptyList()
)

data class TmdbResult(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null
)

data class TmdbCreditsResponse(
    val cast: List<TmdbCast>? = emptyList()
)

data class TmdbCast(
    val id: Int = 0,
    val name: String? = "",
    val character: String? = "",
    val profile_path: String? = null
)

interface TmdbApi {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String = "26685f67fcab81ee85fdac74066060c4",
        @Query("language") language: String = "tr-TR"
    ): TmdbSearchResponse

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @retrofit2.http.Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String = "26685f67fcab81ee85fdac74066060c4",
        @Query("language") language: String = "tr-TR"
    ): TmdbCreditsResponse

    @GET("tv/{tv_id}/credits")
    suspend fun getTvCredits(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String = "26685f67fcab81ee85fdac74066060c4",
        @Query("language") language: String = "tr-TR"
    ): TmdbCreditsResponse

    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        private val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        private val instance: TmdbApi by lazy {
            Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TmdbApi::class.java)
        }

        fun create(): TmdbApi = instance
    }
}
