package com.example.movildilo.data.model.dto.inventario

data class AlertaCaducidadDto(
    val productoId: Long?,
    val productoNombre: String?,
    val fechaCaducidad: String?,
    val diasParaCaducar: Int?,
    val cantidadActual: Int?
)