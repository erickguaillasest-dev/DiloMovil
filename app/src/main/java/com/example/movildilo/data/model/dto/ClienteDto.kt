package com.example.movildilo.data.model.dto

data class ClienteResponseDto(
    val id: Long? = null,
    val dni: String?,
    val primerNombre: String?,
    val segundoNombre: String? = null,
    val apellidoPaterno: String?,
    val apellidoMaterno: String? = null,
    val email: String? = null,
    val contrasena: String? = null,
    val fechaNacimiento: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val nombreCompleto: String? = null,
    val fotoPerfil: String? = null,
    val rutaImagen: String? = null,
    val fotoUrl: String? = null
)

data class FacturaClienteResumenDto(
    val id: Long?,
    val numero: String,
    val fecha: String,
    val tipo: String,
    val monto: Double,
    val estado: String,
    val detalles: List<DetalleFacturaResumenDto>,
    val descuentoGlobal: Double = 0.0,
    var showDetalles: Boolean = false
)

data class CreditoClienteResumenDto(
    val id: Long?,
    val factura: String,
    val montoTotal: Double,
    val saldoPendiente: Double,
    val fechaVencimiento: String,
    val estado: String,
    var showDetalles: Boolean = false
)

data class ClienteReporteDto(
    var key: String = "",
    var clienteId: Long? = null,
    var identificacion: String? = null,
    var nombre: String = "",
    var totalFacturado: Double = 0.0,
    var numFacturas: Int = 0,
    var totalCredito: Double = 0.0,
    var saldoPendiente: Double = 0.0,
    var numCuentasCredito: Int = 0,
    var facturas: MutableList<FacturaClienteResumenDto> = mutableListOf(),
    var creditos: MutableList<CreditoClienteResumenDto> = mutableListOf()
)

data class DocumentoUiModel(
    val numero: String,
    val fecha: String,
    val tipo: String,
    val estado: String,
    val monto: Double,
    val isCredito: Boolean,
    val detalles: List<DetalleFacturaResumenDto> = emptyList(),
    val descuentoGlobal: Double = 0.0,
    val saldoPendiente: Double = 0.0,
    var expandido: Boolean = false
)