package com.example.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class TvMazeSearchResult(
    val score: Double? = 0.0,
    val show: TvMazeShow? = null
)

data class TvMazeShow(
    val id: Int = 0,
    val url: String? = null,
    val name: String? = null,
    val type: String? = null,
    val language: String? = null,
    val summary: String? = null,
    val rating: TvMazeRating? = null,
    val image: TvMazeImage? = null
)

data class TvMazeRating(
    val average: Double? = null
)

data class TvMazeImage(
    val medium: String? = null,
    val original: String? = null
)

data class TvMazeCastResult(
    val person: TvMazePerson? = null,
    val character: TvMazeCharacter? = null
)

data class TvMazePerson(
    val id: Int = 0,
    val name: String? = "",
    val image: TvMazeImage? = null
)

data class TvMazeCharacter(
    val name: String? = ""
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
