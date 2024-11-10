package com.testCodeGenerator.service

import com.testCodeGenerator.api.ChatGptApi
import com.testCodeGenerator.dto.GeneratedTestCode
import com.testCodeGenerator.dto.request.ChatGptRequest
import com.testCodeGenerator.dto.response.ChatGptResponse
import com.testCodeGenerator.exception.ChatGptAuthenticationException
import com.testCodeGenerator.exception.ChatGptFetchFailureException
import retrofit2.Response
import java.net.SocketTimeoutException

class ChatGptService(
    private val chatGptApi: ChatGptApi = ChatGptApi.create()
) {

    fun generateTestCode(fileExtension: String, code: String): GeneratedTestCode =
        runCatching {
            ChatGptRequest.of(fileExtension, code)
                .let { chatGptApi.generateTestCode(it).execute() }
        }.fold(
            onSuccess = { response -> onGenerateTestCodeSuccess(response) },
            onFailure = { exception ->
                if (exception is SocketTimeoutException) {
                    throw ChatGptFetchFailureException(
                        """
                        timeout error.
                        Please check your network or set longer timeout in settings.
                        """
                    )
                }
                throw ChatGptFetchFailureException(exception.message)
            }
        )

    private fun onGenerateTestCodeSuccess(response: Response<ChatGptResponse>): GeneratedTestCode {
        if (response.code() == 401) {
            throw ChatGptAuthenticationException()
        }

        val body = response.body()
            ?: throw ChatGptFetchFailureException("OpenAI's response body is null.")

        if (response.isSuccessful.not()) {
            throw ChatGptFetchFailureException("${response.errorBody()?.string()}")
        }

        if (body.getFinishReason() == "length") {
            throw ChatGptFetchFailureException(
                """
                The response exceeds the maximum token limit.
                Please try again with a shorter code.
                """
            )
        }

        return body.toGeneratedTestCode()
    }
}
