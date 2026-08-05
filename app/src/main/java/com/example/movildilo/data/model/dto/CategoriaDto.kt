package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class CategoriaDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("descripcion")
    val descripcion: String? = null,

    @SerializedName("negocioId")
    val negocioId: Long? = null
) {
    override fun toString(): String = nombre
}