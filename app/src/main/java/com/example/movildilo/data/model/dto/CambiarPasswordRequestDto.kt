package com.example.movildilo.data.model.dto

data class CambiarPasswordRequestDto(
    val newPassword: String,
    val confirmPassword: String
)