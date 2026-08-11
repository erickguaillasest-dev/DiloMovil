package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class CuentaPorCobrarResponseDto(
    @SerializedName("id") val id: Long,
    @SerializedName("montoTotal") val montoTotal: Double?,
    @SerializedName("saldoPendiente") val saldoPendiente: Double?,
    @SerializedName("fechaVencimiento") val fechaVencimiento: String?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("numeroFactura") val numeroFactura: String?,
    @SerializedName("factura") val factura: FacturaDto?,
    @SerializedName("clienteNombre") val clienteNombre: String?,
    @SerializedName("cuotas") val cuotas: List<CuotaDto>?,
    var isExpanded: Boolean = false
)

data class FacturaDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("numeroFactura") val numeroFactura: String?,
    @SerializedName("cliente") val cliente: ClienteDto?
)

data class ClienteDto(
    @SerializedName("primerNombre") val primerNombre: String?,
    @SerializedName("apellidoPaterno") val apellidoPaterno: String?
)

data class CuotaDto(
    @SerializedName("id") val id: Long,
    @SerializedName("numeroCuota") val numeroCuota: Int?,
    @SerializedName("fechaVencimiento") val fechaVencimiento: String?,
    @SerializedName("montoCuota") val montoCuota: Double?,
    @SerializedName("saldoPendienteCuota") val saldoPendienteCuota: Double?,
    @SerializedName("estado") val estado: String?
)

data class PagoRequestDto(
    @SerializedName("montoPago") val montoPago: Double
)