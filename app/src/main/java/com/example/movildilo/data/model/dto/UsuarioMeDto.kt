package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class UsuarioMeDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("dni") val dni: String? = null,
    @SerializedName("primerNombre") val primerNombre: String? = null,
    @SerializedName("segundoNombre") val segundoNombre: String? = null,
    @SerializedName("apellidoPaterno") val apellidoPaterno: String? = null,
    @SerializedName("apellidoMaterno") val apellidoMaterno: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("fechaNacimiento") val fechaNacimiento: String? = null,
    @SerializedName("fotoPerfil") val fotoPerfil: String? = null,
    @SerializedName("nameParroquia") val nameParroquia: String? = null
)


data class EditarPerfilRequestDto(
    val primerNombre: String? = null,
    val segundoNombre: String? = null,
    val apellidoPaterno: String? = null,
    val apellidoMaterno: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val fechaNacimiento: String? = null,
    val password: String? = null
)