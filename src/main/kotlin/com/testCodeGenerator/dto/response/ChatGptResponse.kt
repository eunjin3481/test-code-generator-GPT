package com.testCodeGenerator.dto.response

import com.testCodeGenerator.dto.GeneratedTestCode

data class ChatGptResponse(
    val id: String,
    var choices: List<Choice>
) {

    data class Choice(
        val index: Int,
        val text: String,
        val finishReason: String
    )

    fun toGeneratedTestCode(): GeneratedTestCode {
        return GeneratedTestCode(
            code = choices.first().text.substringAfter("Code:").trim('`', '\n', ' ')
        )
    }

    fun getFinishReason(): String {
        return choices.first().finishReason
    }
}
