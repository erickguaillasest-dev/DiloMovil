package com.example.movildilo.data.model.dto

import com.google.gson.annotations.SerializedName

data class ParroquiaResponseDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("nombre")
    val nombre: String
)