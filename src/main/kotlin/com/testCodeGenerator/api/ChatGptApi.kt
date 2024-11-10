package com.testCodeGenerator.api

import com.testCodeGenerator.dto.request.ChatGptRequest
import com.testCodeGenerator.dto.response.ChatGptResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

fun interface ChatGptApi {

    @POST("v1/chat/completions")
    fun generateTestCode(@Body request: ChatGptRequest): Call<ChatGptResponse>

    companion object {

        fun create(): ChatGptApi =
            ChatGptApiClient.getClient()
                .create(ChatGptApi::class.java)
    }
}
