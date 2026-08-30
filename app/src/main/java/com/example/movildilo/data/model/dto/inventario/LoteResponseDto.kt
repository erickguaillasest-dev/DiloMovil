package com.example.movildilo.data.model.dto.inventario

import com.google.gson.annotations.SerializedName

data class LoteResponseDto(
    @SerializedName("id")
    val id: Long?,

    @SerializedName("codigoLote", alternate = ["numeroLote", "codigo"])
    val codigoLote: String?,

    @SerializedName("fechaCaducidad", alternate = ["fechaVencimiento", "vencimiento", "caducidad"])
    val fechaCaducidad: String?,

    @SerializedName("fechaCreacion", alternate = ["fechaIngreso", "createdAt", "fechaRegistro"])
    val fechaCreacion: String?,

    @SerializedName("cantidadDisponible", alternate = ["cantidad", "stockDisponible", "cantidadActual"])
    val cantidadDisponible: Int?,

    @SerializedName("cantidadInicial", alternate = ["stockInicial", "cantidadOriginal"])
    val cantidadInicial: Int?,

    @SerializedName("costoUnitario", alternate = ["costo", "precioUnitario", "precioCosto"])
    val costoUnitario: Double?,

    @SerializedName("estado", alternate = ["estadoLote"])
    val estado: String?
)