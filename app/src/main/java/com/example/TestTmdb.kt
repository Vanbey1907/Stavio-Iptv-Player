package com.example

import kotlinx.coroutines.runBlocking
import com.example.network.TmdbApi

fun main() = runBlocking {
    try {
        val api = TmdbApi.create()
        val res = api.searchMulti("Game of Thrones")
        println(res.results.firstOrNull()?.name)
    } catch (e: Exception) {
        println(e.message)
    }
}
