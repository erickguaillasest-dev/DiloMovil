package com.example.movildilo.data.model.dto.facturacion

import com.google.gson.annotations.SerializedName

data class CuentaPorCobrarResponseDto(
    @SerializedName("id") val id: Long,
    @SerializedName("montoTotal") val montoTotal: Double?,
    @SerializedName("saldoPendiente") val saldoPendiente: Double?,
    @SerializedName("fechaVencimiento") val fechaVencimiento: String?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("numeroFactura") val numeroFactura: String?,
    @SerializedName("factura") val factura: FacturaDto?,
    @SerializedName("nombreCliente", alternate = ["clienteNombre"]) val clienteNombre: String?,
    @SerializedName("dniCliente", alternate = ["dni", "identificacion"]) val dniCliente: String?,
    @SerializedName("cuotas") val cuotas: List<CuotaDto>?,
    @SerializedName("historialAbonos") val historialAbonos: List<HistorialAbonoDto>?,
    var isExpanded: Boolean = false
)

data class FacturaDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("numeroFactura") val numeroFactura: String?,
    @SerializedName("cliente") val cliente: ClienteDto?
)

data class ClienteDto(
    @SerializedName("primerNombre") val primerNombre: String?,
    @SerializedName("apellidoPaterno") val apellidoPaterno: String?,
    @SerializedName("nombreCompleto") val nombreCompleto: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("razonSocial", alternate = ["razon_social"]) val razonSocial: String? = null,
    @SerializedName("dni", alternate = ["identificacion", "ruc", "cedula"]) val dni: String? = null
)

data class CuotaDto(
    @SerializedName("id") val id: Long,
    @SerializedName("numeroCuota") val numeroCuota: Int?,
    @SerializedName("fechaVencimiento") val fechaVencimiento: String?,
    @SerializedName("montoCuota") val montoCuota: Double?,
    @SerializedName("saldoPendienteCuota") val saldoPendienteCuota: Double?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("metodoPago", alternate = ["metodo_pago"]) val metodoPago: String?,
    @SerializedName("fechaActualizacion", alternate = ["fecha_actualizacion", "fechaAbono", "fecha_abono"]) val fechaActualizacion: String?
)

data class HistorialAbonoDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("metodoPago", alternate = ["metodo_pago"]) val metodoPago: String?,
    @SerializedName("fechaAbono", alternate = ["fecha_abono"]) val fechaAbono: String?,
    @SerializedName("usuarioRecibio", alternate = ["usuario_recibio"]) val usuarioRecibio: String?,
    @SerializedName("referencia") val referencia: String?,
    @SerializedName("montoAbonado", alternate = ["monto_abonado", "montoPago", "monto_pago"]) val montoAbonado: Double?
)

data class PagoRequestDto(
    @SerializedName("montoPago") val montoPago: Double
)