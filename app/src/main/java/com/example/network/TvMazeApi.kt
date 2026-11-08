package com.example.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class TvMazeSearchResult(
    val score: Double,
    val show: TvMazeShow
)

data class TvMazeShow(
    val id: Int,
    val url: String?,
    val name: String?,
    val type: String?,
    val language: String?,
    val summary: String?,
    val rating: TvMazeRating?,
    val image: TvMazeImage?
)

data class TvMazeRating(
    val average: Double?
)

data class TvMazeImage(
    val medium: String?,
    val original: String?
)

data class TvMazeCastResult(
    val person: TvMazePerson,
    val character: TvMazeCharacter
)

data class TvMazePerson(
    val id: Int,
    val name: String,
    val image: TvMazeImage?
)

data class TvMazeCharacter(
    val name: String
)

interface TvMazeApi {
    @GET("search/shows")
    suspend fun searchShows(
        @Query("q") query: String
    ): List<TvMazeSearchResult>

    @GET("shows/{id}/cast")
    suspend fun getShowCast(
        @Path("id") id: Int
    ): List<TvMazeCastResult>

    companion object {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        fun create(): TvMazeApi {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.tvmaze.com/")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            return retrofit.create(TvMazeApi::class.java)
        }
    }
}
