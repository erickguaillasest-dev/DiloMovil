package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class KardexMovimientoDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("tipo", alternate = ["tipoMovimiento"]) val tipo: String? = null,
    @SerializedName("productoNombre") val productoNombre: String? = null,
    @SerializedName("numeroLote", alternate = ["loteCodigo"]) val numeroLote: String? = null,
    @SerializedName("documentoReferencia", alternate = ["docReferencia"]) val documentoReferencia: String? = null,
    @SerializedName("fechaTransaccion", alternate = ["fechaHora"]) val fechaTransaccion: String? = null,
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("costoUnitario") val costoUnitario: Double? = null,
    @SerializedName("costoTotal", alternate = ["totalMovimiento"]) val totalMovimiento: Double? = null,
    @SerializedName("bodegaOrigenNombre") val bodegaOrigenNombre: String? = null,
    @SerializedName("bodegaDestinoNombre") val bodegaDestinoNombre: String? = null,
    @SerializedName("motivo") val motivo: String? = null,
    @SerializedName("usuarioResponsableNombre", alternate = ["usuarioResponsable"]) val usuarioResponsableNombre: String? = null
)

data class NuevoAjusteRequestDto(
    @SerializedName("tipo") val tipo: String,
    @SerializedName("productoId") val productoId: Long,
    @SerializedName("bodegaOrigenId") val bodegaOrigenId: Long? = null,
    @SerializedName("bodegaDestinoId") val bodegaDestinoId: Long? = null,
    @SerializedName("cantidad") val cantidad: Int,
    @SerializedName("costoUnitario") val costoUnitario: Double? = null,
    @SerializedName("motivo") val motivo: String,
    @SerializedName("documentoReferencia") val documentoReferencia: String? = null
)

data class ActualizarStockMinimoRequestDto(
    @SerializedName("productoId") val productoId: Long,
    @SerializedName("bodegaId") val bodegaId: Long,
    @SerializedName("nuevoStockMinimo") val nuevoStockMinimo: Int
)