package com.example.movildilo.data.model.dto

data class GroqRequest(
    val model: String = "openai/gpt-oss-120b",
    val messages: List<GroqMessage>,
    val temperature: Int = 1,
    val max_tokens: Int = 2048
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<GroqChoice>?
)

data class GroqChoice(
    val message: GroqMessage?
)

data class ChatItem(
    val role: String,
    val text: String
)