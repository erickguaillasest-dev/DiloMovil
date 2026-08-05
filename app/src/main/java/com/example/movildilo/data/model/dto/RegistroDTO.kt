package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class RegistroDto(
    @SerializedName("dni")
    val dni: String,

    @SerializedName("fotoPerfil")
    val fotoPerfil: String? = null, // Puede ser null si no sube foto al inicio

    @SerializedName("primerNombre")
    val primerNombre: String,

    @SerializedName("segundoNombre")
    val segundoNombre: String? = null, // Opcional

    @SerializedName("apellidoPaterno")
    val apellidoPaterno: String,

    @SerializedName("apellidoMaterno")
    val apellidoMaterno: String? = null, // Opcional

    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String, // Coincide con tu backend

    @SerializedName("fechaNacimiento")
    val fechaNacimiento: String, // En formato "yyyy-MM-dd"

    @SerializedName("telefono")
    val telefono: String? = null,

    @SerializedName("direccion")
    val direccion: String? = null,

    @SerializedName("id_parroquia")
    val id_parroquia: Long? = null // Coincide exactamente con el backend y con el Activity
)