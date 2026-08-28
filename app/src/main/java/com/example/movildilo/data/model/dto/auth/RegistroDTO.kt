package com.example.movildilo.data.model.dto.auth

import com.google.gson.annotations.SerializedName

data class RegistroDto(
    @SerializedName("dni")
    val dni: String,

    @SerializedName("fotoPerfil")
    val fotoPerfil: String? = null,

    @SerializedName("primerNombre")
    val primerNombre: String,

    @SerializedName("segundoNombre")
    val segundoNombre: String? = null,

    @SerializedName("apellidoPaterno")
    val apellidoPaterno: String,

    @SerializedName("apellidoMaterno")
    val apellidoMaterno: String? = null,

    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("fechaNacimiento")
    val fechaNacimiento: String,

    @SerializedName("telefono")
    val telefono: String? = null,

    @SerializedName("direccion")
    val direccion: String? = null,

    @SerializedName("id_parroquia")
    val id_parroquia: Long? = null
)