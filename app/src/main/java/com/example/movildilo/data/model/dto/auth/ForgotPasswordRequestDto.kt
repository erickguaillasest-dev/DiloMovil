package com.example.movildilo.data.model.dto.auth

import com.google.gson.annotations.SerializedName

data class ForgotPasswordRequestDto(
    @SerializedName("email")
    val email: String
)

data class ResetPasswordRequestDto(
    @SerializedName("email")
    val email: String,

    @SerializedName("codigo")
    val codigo: String,

    @SerializedName("nuevaPassword")
    val nuevaPassword: String
)