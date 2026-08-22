package com.example.movildilo.data.model.dto

import com.example.movildilo.ui.facturas.DetalleFacturaDialogHelper.ItemLinea
import com.google.gson.annotations.SerializedName

data class FacturaRequestDto(
    @SerializedName("clienteId")
    val clienteId: Long? = null,

    @SerializedName("metodoPago")
    val metodoPago: String = "EFECTIVO",

    @SerializedName("tarjeta")
    val tarjeta: String? = null,

    @SerializedName("numeroCuotas")
    val numeroCuotas: Int? = null,

    @SerializedName("descuentoGlobal")
    val descuentoGlobal: Double = 0.0,

    @SerializedName("detalles")
    val detalles: List<DetalleFacturaRequestDto> = emptyList()
)

data class DetalleFacturaRequestDto(
    @SerializedName("productoId") val productoId: Long,
    @SerializedName("bodegaId") val bodegaId: Long,
    @SerializedName("cantidad") val cantidad: Int,
    @SerializedName("descuento") val descuento: Double = 0.0,
    @SerializedName("tarjeta")val tarjeta: String? = null,
)

data class FacturaResponseDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("numeroFactura", alternate = ["numero"]) val numeroFactura: String? = null,
    @SerializedName("claveAccesoSri") val claveAccesoSri: String? = null,
    @SerializedName("estadoSri", alternate = ["estado"]) val estadoSri: String? = null,
    @SerializedName("fechaEmision", alternate = ["fecha"]) val fechaEmision: String? = null,
    @SerializedName("metodoPago", alternate = ["formaPago", "tipo"]) val metodoPago: String? = null,
    @SerializedName("totalFactura", alternate = ["total", "monto"]) val totalFactura: Double? = null,
    @SerializedName("totalDescuento", alternate = ["descuentoGlobal", "total_descuento"]) val totalDescuento: Double? = 0.0,
    @SerializedName("clienteNombre") val clienteNombre: String? = null,
    @SerializedName("cliente") val cliente: ClienteResponseDto? = null,
    @SerializedName("detalles") val detalles: List<DetalleFacturaResponseDto>? = emptyList()
){
    val fechaFormateada: String
        get() {
            if (fechaEmision.isNullOrBlank()) return "S/F"
            return try {
                val partes = fechaEmision.split("T", " ")
                val fechaPart = partes[0]
                val horaPart = partes.getOrNull(1)?.take(5) ?: ""

                val componentesFecha = fechaPart.split("-")
                val fechaLimpia = if (componentesFecha.size == 3) {
                    "${componentesFecha[2]}/${componentesFecha[1]}/${componentesFecha[0]}"
                } else {
                    fechaPart
                }

                if (horaPart.isNotEmpty()) "$fechaLimpia $horaPart" else fechaLimpia
            } catch (e: Exception) {
                fechaEmision
            }
        }

    val totalCalculado: Double
        get() = totalFactura ?: 0.0

    val estadoFormateado: String
        get() = estadoSri ?: "AUTORIZADO"

    val nombreClienteFormateado: String
        get() = clienteNombre ?: cliente?.nombreCompleto ?: "Consumidor Final"
}

data class DetalleFacturaResponseDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("productoId", alternate = ["producto_id", "idProducto", "id_producto"])
    val productoId: Long? = null,
    @SerializedName("nombreProducto", alternate = ["nombre", "productoNombre", "descripcion", "itemNombre"])
    val nombreProducto: String? = null,
    @SerializedName("producto")
    val producto: ProductoResponseDto? = null,
    @SerializedName("cantidad") val cantidad: Int? = 0,
    @SerializedName("precioUnitario", alternate = ["precio", "precio_unitario"]) val precioUnitario: Double? = 0.0,
    @SerializedName("descuento", alternate = ["descuentoMonto", "descuento_monto"]) val descuento: Double? = 0.0,
    @SerializedName("subtotalItem", alternate = ["subtotal", "subtotal_item"]) val subtotalItem: Double? = 0.0
)


data class ItemCarritoFactura(
    val productoId: Long,
    val bodegaId: Long,
    val cantidad: Int,
    val nombreProducto: String,
    val precioUnitario: Double,
    val descuentoPorcentaje: Double = 0.0
) {
    val subtotalBruto: Double
        get() = cantidad * precioUnitario

    val descuentoMonto: Double
        get() = subtotalBruto * (descuentoPorcentaje.coerceIn(0.0, 100.0) / 100.0)

    val subtotalConDescuento: Double
        get() = subtotalBruto - descuentoMonto
}

data class DetalleFacturaResumenDto(
    val productoNombre: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val descuento: Double,
    val subtotalItem: Double
)