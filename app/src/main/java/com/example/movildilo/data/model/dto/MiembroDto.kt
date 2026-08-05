package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class MiembroResponseDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("usuarioId")
    val usuarioId: Long? = null,

    @SerializedName("nombreUsuario")
    val nombreUsuario: String? = null,

    @SerializedName("emailUsuario")
    val emailUsuario: String? = null,

    @SerializedName("rol")
    val rol: String? = null,

    @SerializedName("fotoPerfil")
    val fotoPerfil: String? = null,

    @SerializedName("estadoLaboral")
    val estadoLaboral: String? = null,

    @SerializedName("estadoInvitacion")
    val estadoInvitacion: String? = null,

    @SerializedName("negocioId")
    val negocioId: Long? = null,

    @SerializedName("fechaVinculacion")
    val fechaVinculacion: String? = null,

    var esCreador: Boolean = false
)