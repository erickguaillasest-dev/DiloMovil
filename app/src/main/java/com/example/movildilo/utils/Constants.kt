package com.example.movildilo.utils

import com.example.movildilo.BuildConfig

object Constants {

    private val llavesChat = BuildConfig.GROQ_API_KEY_CHAT
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private val llavesFacturas = BuildConfig.GROQ_API_KEY_FACTURAS
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private var indiceChat = 0
    private var indiceFacturas = 0

    val GROQ_API_KEY_CHAT: String
        get() = obtenerClaveChatActual()

    fun obtenerClaveChatActual(): String {
        if (llavesChat.isEmpty()) return ""
        return llavesChat[indiceChat]
    }

    fun rotarClaveChat(): String {
        if (llavesChat.isEmpty()) return ""
        indiceChat = (indiceChat + 1) % llavesChat.size
        return llavesChat[indiceChat]
    }

    fun totalClavesChat(): Int = llavesChat.size


    val GROQ_API_KEY_FACTURAS: String
        get() = obtenerClaveFacturasActual()

    fun obtenerClaveFacturasActual(): String {
        if (llavesFacturas.isEmpty()) return ""
        return llavesFacturas[indiceFacturas]
    }

    fun rotarClaveFacturas(): String {
        if (llavesFacturas.isEmpty()) return ""
        indiceFacturas = (indiceFacturas + 1) % llavesFacturas.size
        return llavesFacturas[indiceFacturas]
    }

    fun totalClavesFacturas(): Int = llavesFacturas.size
}