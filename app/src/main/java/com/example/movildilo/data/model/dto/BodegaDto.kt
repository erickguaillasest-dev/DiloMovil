package com.example.movildilo.data.model.dto


data class BodegaDto(
    val id: Long,
    val nombre: String,
    val direccion: String?
)

data class BodegaRequest(
    val nombre: String,
    val direccion: String?
)