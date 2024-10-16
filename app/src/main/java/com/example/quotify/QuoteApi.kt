package com.example.quotify

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

interface QuoteApi {

    @GET("random")
    suspend fun getRandomQuote() : Response<List<QuoteModel>>
}