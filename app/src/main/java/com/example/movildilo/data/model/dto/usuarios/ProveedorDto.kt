package com.example.movildilo.data.model.dto.usuarios

import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.google.gson.annotations.SerializedName

data class ProveedorResponseDto(
    @SerializedName("id")
    val id: Long?,

    @SerializedName(value = "nombreComercial", alternate = ["nombre_comercial", "nombre"])
    val nombreComercial: String?,

    @SerializedName(value = "dni", alternate = ["ruc", "cedula", "identificacion"])
    val dni: String?,

    @SerializedName(value = "telefono", alternate = ["celular", "contacto"])
    val telefono: String?,

    @SerializedName("estado")
    val estado: Boolean?,

    @SerializedName(value = "fechaCreacion", alternate = ["fecha_creacion"])
    val fechaCreacion: String?,

    @SerializedName("categorias")
    val categorias: List<CategoriaDto>? = emptyList()
)

data class ProveedorRequestDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("dni")
    val dni: String,

    @SerializedName(value = "nombre", alternate = ["nombreComercial"])
    val nombreComercial: String,

    @SerializedName("telefono")
    val telefono: String? = null,

    @SerializedName("estado")
    val estado: Boolean,

    @SerializedName(value = "categoriasIds", alternate = ["categoriaIds"])
    val categoriasIds: List<Long> = emptyList()
)