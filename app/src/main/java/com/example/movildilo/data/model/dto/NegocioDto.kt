package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class NegocioResponseDto(
    @SerializedName(value = "id", alternate = ["idNegocio"])
    val id: Long? = null,

    @SerializedName("ruc")
    val ruc: String? = null,

    @SerializedName("razonSocial")
    val razonSocial: String? = null,

    @SerializedName("nombreComercial")
    val nombreComercial: String? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("direccion")
    val direccion: String? = null,

    @SerializedName("obligadoContabilidad")
    val obligadoContabilidad: Boolean? = false,

    @SerializedName("metodoCosteo")
    val metodoCosteo: String? = null,

    @SerializedName("rutaImagen")
    val rutaImagen: String? = null,

    @SerializedName("codigoInvitacion")
    val codigoInvitacion: String? = null,

    @SerializedName("fechaCreacion")
    val fechaCreacion: com.google.gson.JsonElement? = null
)