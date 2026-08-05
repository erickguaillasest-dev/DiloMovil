package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class UnirseNegocioRequestDto(
    @SerializedName("codigoInvitacion")
    val codigoInvitacion: String,

    @SerializedName("idRol")
    val idRol: Int = 3
)