package com.example.movildilo.data.model.dto

data class ClienteResponseDto(
    val id: Long? = null,
    val dni: String?,
    val primerNombre: String?,
    val segundoNombre: String? = null,
    val apellidoPaterno: String?,
    val apellidoMaterno: String? = null,
    val email: String? = null,
    val contrasena: String? = null,
    val fechaNacimiento: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val nombreCompleto: String? = null,
    val fotoPerfil: String? = null,
    val rutaImagen: String? = null,
    val fotoUrl: String? = null
)