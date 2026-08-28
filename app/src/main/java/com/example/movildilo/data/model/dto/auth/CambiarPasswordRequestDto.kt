package com.example.movildilo.data.model.dto.auth

data class CambiarPasswordRequestDto(
    val newPassword: String,
    val confirmPassword: String
)