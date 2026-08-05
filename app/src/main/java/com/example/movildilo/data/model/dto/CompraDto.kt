package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class DetalleCompraRequestDto(
    @SerializedName("productoId") val productoId: Long,
    @SerializedName("cantidad") val cantidad: Int,
    @SerializedName("costoUnitario") val costoUnitario: Double,
    @SerializedName("fechaCaducidad") val fechaCaducidad: String? = null // "yyyy-MM-dd"
)

data class CompraRequestDto(
    @SerializedName("proveedorId") val proveedorId: Long,
    @SerializedName("bodegaIngresoId") val bodegaIngresoId: Long,
    @SerializedName("numeroComprobante") val numeroComprobante: String,
    @SerializedName("detalles") val detalles: List<DetalleCompraRequestDto>
)

data class DetalleCompraResponseDto(
    @SerializedName("productoId") val productoId: Long? = null,
    @SerializedName("productoNombre") val productoNombre: String? = null,
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("costoUnitario") val costoUnitario: Double? = null,
    @SerializedName("costoTotal") val costoTotal: Double? = null,
    @SerializedName("fechaCaducidad") val fechaCaducidad: String? = null
)

data class CompraResponseDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("proveedorNombre") val proveedorNombre: String? = null,
    @SerializedName("bodegaIngresoNombre") val bodegaIngresoNombre: String? = null,
    @SerializedName("numeroComprobante") val numeroComprobante: String? = null,
    @SerializedName("fechaCompra") val fechaCompra: String? = null,
    @SerializedName("totalCompra") val totalCompra: Double? = null,
    @SerializedName("detalles") val detalles: List<DetalleCompraResponseDto>? = null
)