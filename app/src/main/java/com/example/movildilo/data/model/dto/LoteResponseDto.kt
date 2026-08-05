package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class LoteResponseDto(
    @SerializedName("id")
    val id: Long?,

    @SerializedName("codigoLote")
    val codigoLote: String?,

    @SerializedName("fechaVencimiento")
    val fechaVencimiento: String?,

    @SerializedName("cantidad")
    val cantidad: Int?
)