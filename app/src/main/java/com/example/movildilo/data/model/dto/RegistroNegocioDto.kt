package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class RegistroNegocioRequestDto(
    @SerializedName("nombreComercial") val nombreComercial: String,
    @SerializedName("razonSocial") val razonSocial: String,
    @SerializedName("ruc") val ruc: String,
    @SerializedName("direccion") val direccion: String
)