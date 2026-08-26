package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class UsuarioDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("apellido")
    val apellido: String? = null,

    @SerializedName("nombreCompleto")
    val nombreCompleto: String? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("rol")
    val rol: String? = null,

    @SerializedName("estado")
    val estado: String? = null,

    @SerializedName("suspendido")
    val suspendido: Boolean? = null,

    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,

    @SerializedName("negocioId")
    val negocioId: Long? = null
)