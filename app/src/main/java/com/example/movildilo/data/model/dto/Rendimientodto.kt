package com.example.movildilo.data.model.dto

data class DiaCalorDto(
    val fecha: String,
    val label: String,
    val diaSemana: String,
    val total: Double,
    val cantidad: Int,
    val intensidad: Double
)

data class ProductoDemandaDto(
    val nombre: String,
    val unidades: Int,
    val ingresos: Double,
    val porcentaje: Int
)

data class ComparativaItemDto(
    val label: String,
    val actual: Double,
    val anterior: Double,
    val variacion: Double
)

data class ClienteTopDto(
    val nombre: String,
    val total: Double,
    val facturas: Int,
    val porcentaje: Int
)

data class FormaPagoItemDto(
    val nombre: String,
    val total: Double,
    val porcentaje: Int
)

data class DiaSemanaItemDto(
    val nombre: String,
    val total: Double,
    val intensidad: Double
)

data class HoraItemDto(
    val hora: String,
    val total: Double,
    val intensidad: Double
)

data class SerieDiariaItemDto(
    val label: String,
    val total: Double,
    val altura: Int
)