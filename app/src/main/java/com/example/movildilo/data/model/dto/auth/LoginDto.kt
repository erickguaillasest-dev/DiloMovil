package com.example.movildilo.data.model.dto.auth

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String
)

data class LoginResponseDto(
    @SerializedName("token")
    val token: String?,

    @SerializedName("tokenType")
    val tokenType: String? = "Bearer",

    @SerializedName("idUsuario")
    val idUsuario: Long? = null,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("nombreCompleto")
    val nombreCompleto: String? = null,

    @SerializedName("primerNombre")
    val primerNombre: String? = null,

    @SerializedName("apellidoPaterno")
    val apellidoPaterno: String? = null,

    @SerializedName("fotoPerfil")
    val fotoPerfil: String? = null,

    @SerializedName("rol")
    val rol: String? = null,

    @SerializedName("roles")
    val roles: List<String>? = null,

    @SerializedName("negocioId")
    val negocioId: Long? = null,

    @SerializedName("selectedBusinessId")
    val selectedBusinessId: Long? = null,

    @SerializedName("businesses")
    val businesses: List<Any>? = null,

    @SerializedName("needsBusinessSelection")
    val needsBusinessSelection: Boolean? = false,

    @SerializedName("needsRoleSelection")
    val needsRoleSelection: Boolean? = false,

    @SerializedName("suspendido")
    val suspendido: Boolean? = null,

    @SerializedName("superAdmin")
    val superAdmin: Boolean? = false
)