package com.testCodeGenerator.dto.request

data class ChatGptRequest(
    val model: String = "gpt-3.5-turbo",
    val prompt: String,
    val maxTokens: Int = 1600,
    val temperature: Int = 0
) {

    companion object {
        fun of(fileExtension: String, code: String): ChatGptRequest =
            ChatGptRequest(prompt = makePrompt(fileExtension, code))

        private fun makePrompt(fileExtension: String, code: String): String =
            """
                Your role is to generate unit tests for the provided code.
                Create comprehensive unit tests that cover the primary functionality and edge cases.
                Return only the generated test code without any explanation.
                The file extension of the code: $fileExtension
                Here is the code to generate tests for:
                ```
                $code
                ```
                Respond starting with the line 'Test Code:'
            """.trimIndent()
    }
}
