package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class InventarioResponseDto(
    @SerializedName("id")
    val id: Long?,

    @SerializedName("productoId")
    val productoId: Long?,

    @SerializedName("productoNombre")
    val productoNombre: String?,

    @SerializedName("productoCodigo")
    val productoCodigo: String?,

    @SerializedName("codigoPrincipal")
    val codigoPrincipal: String?,

    @SerializedName("bodegaId")
    val bodegaId: Long?,

    @SerializedName("bodegaNombre")
    val bodegaNombre: String?,

    @SerializedName("costoPromedio")
    val costoPromedio: Double?,

    @SerializedName("valorInventario")
    val valorInventario: Double?,

    @SerializedName("cantidadActual")
    var cantidadActual: Int?,

    @SerializedName("stockMinimo")
    var stockMinimo: Int?
)