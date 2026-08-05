package com.example.movildilo.data.model.dto

data class GroqRequest(
    val model: String = "llama-3.1-8b-instant",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.5,
    val max_tokens: Int = 500
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