package com.example.movildilo.data.repository

import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ClienteResponseDto
import com.example.movildilo.data.model.dto.FacturaRequestDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.LoginRequestDto
import com.example.movildilo.data.model.dto.LoginResponseDto
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.example.movildilo.data.model.dto.ProductoDto
import com.example.movildilo.data.model.dto.ProductoResponseDto
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

class VendedorRepository(private val sessionManager: SessionManager) {

    private val api = RetrofitClient.apiService
    private val gson = Gson()

    // ==========================================
    // ENDPOINTS PÚBLICOS
    // ==========================================

    suspend fun login(request: LoginRequestDto): Response<LoginResponseDto> {
        return api.login(request)
    }

    suspend fun register(datos: RequestBody, foto: MultipartBody.Part?): Response<ResponseBody> {
        return api.register(datos, foto)
    }

    suspend fun getParroquias(): Response<List<ParroquiaResponseDto>> {
        return api.getParroquias()
    }

    // ==========================================
    // HELPERS DE SESIÓN
    // ==========================================

    private fun negocioIdOrNull(): Long? {
        val id = sessionManager.getNegocioId()
        return if (id > 0) id else null
    }

    private fun tokenOrNull(): String? {
        return sessionManager.getAuthHeader()
    }

    // ==========================================
    // CLIENTES
    // ==========================================

    suspend fun getClientes(): Response<List<ClienteResponseDto>>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null
        return api.getClientes(token, negocioId)
    }

    // ==========================================
    // CATÁLOGO Y PRODUCTOS
    // ==========================================

    suspend fun getCatalogo(): Response<List<ProductoResponseDto>>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null
        return api.getCatalogo(token, negocioId)
    }

    suspend fun crearProducto(
        producto: ProductoDto,
        imagen: MultipartBody.Part? = null
    ): Response<ProductoResponseDto>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null

        val jsonProducto = gson.toJson(producto)
        val mediaType = MediaType.parse("application/json")
        val datosPart = RequestBody.create(mediaType, jsonProducto)

        return api.crearProducto(
            token = token,
            negocioId = negocioId,
            datos = datosPart,
            imagen = imagen
        )
    }

    suspend fun actualizarProducto(
        id: Long,
        producto: ProductoDto,
        imagen: MultipartBody.Part? = null
    ): Response<ProductoResponseDto>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null

        val jsonProducto = gson.toJson(producto)
        val mediaType = MediaType.parse("application/json")
        val datosPart = RequestBody.create(mediaType, jsonProducto)

        return api.actualizarProducto(
            token = token,
            negocioId = negocioId,
            id = id,
            datos = datosPart,
            imagen = imagen
        )
    }

    suspend fun eliminarProducto(id: Long): Response<ResponseBody>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null
        return api.eliminarProducto(token, negocioId, id)
    }


    suspend fun crearFactura(request: FacturaRequestDto): Response<FacturaResponseDto>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null
        return api.crearFactura(token, negocioId, request)
    }

    suspend fun getFacturas(): Response<List<FacturaResponseDto>>? {
        val negocioId = negocioIdOrNull() ?: return null
        val token = tokenOrNull() ?: return null
        return api.getFacturas(token, negocioId)
    }
}