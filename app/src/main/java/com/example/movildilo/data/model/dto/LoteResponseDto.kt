package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class LoteResponseDto(
    @SerializedName("id")
    val id: Long?,

    @SerializedName("codigoLote", alternate = ["numeroLote", "codigo"])
    val codigoLote: String?,

    @SerializedName("fechaCaducidad", alternate = ["fechaVencimiento"])
    val fechaCaducidad: String?,

    @SerializedName("cantidadDisponible", alternate = ["cantidad"])
    val cantidadDisponible: Int?,

    @SerializedName("cantidadInicial")
    val cantidadInicial: Int?,

    @SerializedName("costoUnitario")
    val costoUnitario: Double?,

    @SerializedName("estado")
    val estado: String?
)