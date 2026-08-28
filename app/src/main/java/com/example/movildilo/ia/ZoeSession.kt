package com.example.movildilo.ia

import com.example.movildilo.data.model.dto.ia.ChatItem
import com.example.movildilo.data.model.dto.ia.GroqMessage

object ZoeSession {

    val historialDto = mutableListOf<GroqMessage>()
    val historialUi = mutableListOf<ChatItem>()

    fun limpiar() {
        historialDto.clear()
        historialUi.clear()
    }
}