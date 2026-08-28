package com.example.movildilo.data.model.dto.negocio

import com.google.gson.annotations.SerializedName

data class UnirseNegocioRequestDto(
    @SerializedName("codigoInvitacion")
    val codigoInvitacion: String,

    @SerializedName("idRol")
    val idRol: Int = 3
)