package com.example.movildilo.data.api

import com.example.movildilo.data.model.dto.IvaRequestDto
import com.example.movildilo.data.model.dto.AlertaCaducidadDto
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.data.model.dto.BodegaRequest
import com.example.movildilo.data.model.dto.CambiarPasswordRequestDto
import com.example.movildilo.data.model.dto.CategoriaDto
import com.example.movildilo.data.model.dto.ClienteResponseDto
import com.example.movildilo.data.model.dto.CodigoInvitacionResponseDto
import com.example.movildilo.data.model.dto.CompraRequestDto
import com.example.movildilo.data.model.dto.CompraResponseDto
import com.example.movildilo.data.model.dto.CuentaPorCobrarResponseDto
import com.example.movildilo.data.model.dto.FacturaRequestDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.data.model.dto.KardexMovimientoDto
import com.example.movildilo.data.model.dto.LoginRequestDto
import com.example.movildilo.data.model.dto.LoginResponseDto
import com.example.movildilo.data.model.dto.LoteResponseDto
import com.example.movildilo.data.model.dto.MiembroResponseDto
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.example.movildilo.data.model.dto.NuevoAjusteRequestDto
import com.example.movildilo.data.model.dto.PagoRequestDto
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.example.movildilo.data.model.dto.ProductoResponseDto
import com.example.movildilo.data.model.dto.ProveedorRequestDto
import com.example.movildilo.data.model.dto.ProveedorResponseDto
import com.example.movildilo.data.model.dto.ForgotPasswordRequestDto
import com.example.movildilo.data.model.dto.ResetPasswordRequestDto
import com.example.movildilo.data.model.dto.UnirseNegocioRequestDto
import com.example.movildilo.data.model.dto.UsuarioMeDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {


    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @Multipart
    @POST("api/v1/auth/registro")
    suspend fun register(
        @Part("datos") datosUsuario: RequestBody,
        @Part foto: MultipartBody.Part? = null
    ): Response<ResponseBody>

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): Response<ResponseBody>

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): Response<ResponseBody>

    @GET("api/v1/usuarios/verificar-estado")
    suspend fun verificarEstado(
        @Header("Authorization") token: String
    ): Response<ResponseBody>

    @GET("api/v1/usuarios/me")
    suspend fun getMiPerfil(
        @Header("Authorization") token: String
    ): Response<UsuarioMeDto>

    @Multipart
    @PUT("api/v1/usuarios/me")
    suspend fun actualizarMiPerfil(
        @Header("Authorization") token: String,
        @Part("datos") datos: RequestBody,
        @Part foto: MultipartBody.Part? = null
    ): Response<UsuarioMeDto>

    @PUT("api/v1/usuarios/me/password")
    suspend fun cambiarContrasena(
        @Header("Authorization") token: String,
        @Body request: CambiarPasswordRequestDto
    ): Response<ResponseBody>

    @GET("api/v1/usuarios")
    suspend fun getAllUsuarios(
        @Header("Authorization") token: String
    ): Response<List<UsuarioMeDto>>

    @Multipart
    @PUT("api/v1/usuarios/{id}")
    suspend fun actualizarUsuarioAdmin(
        @Header("Authorization") token: String,
        @Path("id") id: Long,
        @Part("datos") datos: RequestBody,
        @Part foto: MultipartBody.Part? = null
    ): Response<ResponseBody>

    // PARROQUIAS / UBICACIÓN

    @GET("api/v1/parroquias")
    suspend fun getParroquias(
        @Header("Authorization") token: String? = null
    ): Response<List<ParroquiaResponseDto>>

    @POST("api/v1/parroquias")
    suspend fun crearParroquia(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<ParroquiaResponseDto>

    @PUT("api/v1/parroquias/{id}")
    suspend fun actualizarParroquia(
        @Header("Authorization") token: String,
        @Path("id") id: Long,
        @Body body: Map<String, String>
    ): Response<ParroquiaResponseDto>

    @DELETE("api/v1/parroquias/{id}")
    suspend fun eliminarParroquia(
        @Header("Authorization") token: String,
        @Path("id") id: Long
    ): Response<ResponseBody>

    // NEGOCIOS Y EQUIPO

    @GET("api/v1/negocios")
    suspend fun getAllNegocios(
        @Header("Authorization") token: String
    ): Response<List<NegocioResponseDto>>

    @POST("api/v1/negocios/unirse")
    suspend fun unirseANegocio(
        @Header("Authorization") token: String,
        @Body request: UnirseNegocioRequestDto
    ): Response<ResponseBody>

    @Multipart
    @POST("api/v1/negocios")
    suspend fun registrarNegocio(
        @Header("Authorization") token: String,
        @Part("datos") datos: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<Map<String, Any>>

    @Headers("Cache-Control: no-cache")
    @GET("api/v1/negocios/{id}")
    suspend fun getNegocio(
        @Header("Authorization") token: String,
        @Path("id") id: Long
    ): Response<NegocioResponseDto>

    @Multipart
    @PUT("api/v1/negocios/{id}")
    suspend fun actualizarNegocio(
        @Header("Authorization") token: String,
        @Path("id") id: Long,
        @Part("datos") datos: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<ResponseBody>

    @DELETE("api/v1/negocios/{id}")
    suspend fun eliminarNegocio(
        @Header("Authorization") token: String,
        @Path("id") id: Long
    ): Response<ResponseBody>

    @GET("api/v1/negocios/{id}/dashboard/alertas-caducidad")
    suspend fun getAlertasCaducidad(
        @Header("Authorization") token: String,
        @Path("id") id: Long,
        @Query("dias") dias: Int = 30
    ): Response<List<AlertaCaducidadDto>>


    @GET("api/v1/negocios/{negocioId}/kardex/producto/{productoId}")
    suspend fun getKardexPorProducto(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("productoId") productoId: Long
    ): Response<List<KardexMovimientoDto>>

    // MIEMBROS DE EQUIPO
    @Headers("Cache-Control: no-cache")
    @GET("api/v1/negocios/{negocioId}/miembros")
    suspend fun getEquipo(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<MiembroResponseDto>>

    @PUT("api/v1/negocios/{negocioId}/miembros/{miembroId}/responder")
    suspend fun responderInvitacion(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("miembroId") miembroId: Long,
        @Query("aceptar") aceptar: Boolean
    ): Response<ResponseBody>

    @PUT("api/v1/negocios/{negocioId}/miembros/{miembroId}/rol")
    suspend fun cambiarRolMiembro(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("miembroId") miembroId: Long,
        @Query("rol") rol: String
    ): Response<MiembroResponseDto>

    @PUT("api/v1/negocios/{negocioId}/miembros/{miembroId}/desactivar")
    suspend fun desactivarMiembro(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("miembroId") miembroId: Long
    ): Response<MiembroResponseDto>

    @PUT("api/v1/negocios/{negocioId}/miembros/{miembroId}/activar")
    suspend fun activarMiembro(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("miembroId") miembroId: Long
    ): Response<MiembroResponseDto>

    // CATEGORÍAS

    @GET("api/v1/negocios/{negocioId}/categorias")
    suspend fun getCategorias(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<CategoriaDto>>

    @POST("api/v1/negocios/{negocioId}/categorias")
    suspend fun crearCategoria(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Body categoria: CategoriaDto
    ): Response<CategoriaDto>

    @PUT("api/v1/negocios/{negocioId}/categorias/{id}")
    suspend fun actualizarCategoria(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long,
        @Body categoria: CategoriaDto
    ): Response<CategoriaDto>

    @DELETE("api/v1/negocios/{negocioId}/categorias/{id}")
    suspend fun eliminarCategoria(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long
    ): Response<ResponseBody>


    // CLIENTES

    @GET("api/v1/negocios/{id}/clientes")
    suspend fun getClientes(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long
    ): Response<List<ClienteResponseDto>>

    @POST("api/v1/negocios/{id}/clientes")
    suspend fun crearCliente(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long,
        @Body cliente: ClienteResponseDto
    ): Response<ClienteResponseDto>

    @PUT("api/v1/negocios/{id}/clientes/{clienteId}")
    suspend fun actualizarCliente(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long,
        @Path("clienteId") clienteId: Long,
        @Body cliente: ClienteResponseDto
    ): Response<ClienteResponseDto>

    @DELETE("api/v1/negocios/{id}/clientes/{clienteId}")
    suspend fun eliminarCliente(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long,
        @Path("clienteId") clienteId: Long
    ): Response<ResponseBody>

    // CATÁLOGO Y PRODUCTOS

    @GET("api/v1/negocios/{negocioId}/productos")
    suspend fun getCatalogo(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<ProductoResponseDto>>

    @Multipart
    @POST("api/v1/negocios/{negocioId}/productos")
    suspend fun crearProducto(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Part("datos") datos: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<ProductoResponseDto>

    @Multipart
    @PUT("api/v1/negocios/{negocioId}/productos/{id}")
    suspend fun actualizarProducto(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long,
        @Part("datos") datos: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<ProductoResponseDto>

    @DELETE("api/v1/negocios/{negocioId}/productos/{id}")
    suspend fun eliminarProducto(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long
    ): Response<ResponseBody>

    // FACTURACIÓN

    @POST("api/v1/negocios/{negocioId}/facturas")
    suspend fun crearFactura(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Body request: FacturaRequestDto
    ): Response<FacturaResponseDto>

    @GET("api/v1/negocios/{negocioId}/facturas")
    suspend fun getFacturas(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<FacturaResponseDto>>


    // CUENTAS POR COBRAR Y ABONOS

    @GET("api/v1/cuentas-por-cobrar/negocio/{id}")
    suspend fun getCuentasPorCobrar(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long
    ): Response<List<CuentaPorCobrarResponseDto>>

    @POST("api/v1/cuentas-por-cobrar/{id}/pagar")
    suspend fun registrarPagoCuenta(
        @Header("Authorization") token: String,
        @Path("id") cuentaId: Long,
        @Body payload: PagoRequestDto
    ): Response<ResponseBody>


    // INVENTARIO EN BODEGAS

    @GET("api/v1/negocios/{negocioId}/inventario")
    suspend fun getInventario(
        @Header("Authorization") authHeader: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<InventarioResponseDto>>


    @PATCH("api/v1/negocios/{negocioId}/inventario/{inventarioId}/stock-minimo")
    suspend fun actualizarStockMinimo(
        @Header("Authorization") authHeader: String,
        @Path("negocioId") negocioId: Long,
        @Path("inventarioId") inventarioId: Long,
        @Query("valor") valor: Int
    ): Response<ResponseBody>

    @GET("api/v1/negocios/{negocioId}/inventario/bodegas/{bodegaId}/productos/{productoId}/lotes")
    suspend fun getLotesPorProducto(
        @Header("Authorization") authHeader: String,
        @Path("negocioId") negocioId: Long,
        @Path("bodegaId") bodegaId: Long,
        @Path("productoId") productoId: Long
    ): Response<List<LoteResponseDto>>


    // KARDEX / MOVIMIENTOS

    @GET("api/v1/negocios/{negocioId}/kardex")
    suspend fun getKardexMovimientos(
        @Header("Authorization") authHeader: String,
        @Path("negocioId") negocioId: Long,
        @Query("tipo") tipo: String? = null,
        @Query("bodegaId") bodegaId: Long? = null,
        @Query("fechaInicio") fechaInicio: String? = null,
        @Query("fechaFin") fechaFin: String? = null
    ): Response<List<KardexMovimientoDto>>

    @POST("api/v1/negocios/{negocioId}/kardex")
    suspend fun registrarAjusteManual(
        @Header("Authorization") authHeader: String,
        @Path("negocioId") negocioId: Long,
        @Query("emailUsuario") emailUsuario: String? = null,
        @Body dto: NuevoAjusteRequestDto
    ): Response<ResponseBody>


    // BODEGAS

    @GET("api/v1/negocios/{negocioId}/bodegas")
    suspend fun getBodegas(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<BodegaDto>>

    @GET("api/v1/negocios/{negocioId}/bodegas/search")
    suspend fun buscarBodegas(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Query("term") termino: String
    ): Response<List<BodegaDto>>

    @POST("api/v1/negocios/{negocioId}/bodegas")
    suspend fun crearBodega(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Body request: BodegaRequest
    ): Response<BodegaDto>

    @PUT("api/v1/negocios/{negocioId}/bodegas/{bodegaId}")
    suspend fun editarBodega(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("bodegaId") bodegaId: Long,
        @Body request: BodegaRequest
    ): Response<BodegaDto>

    @DELETE("api/v1/negocios/{negocioId}/bodegas/{bodegaId}")
    suspend fun eliminarBodega(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("bodegaId") bodegaId: Long
    ): Response<ResponseBody>

    // PROVEEDORES

    @GET("api/v1/negocios/{negocioId}/proveedores")
    suspend fun getProveedores(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<ProveedorResponseDto>>

    @POST("api/v1/negocios/{negocioId}/proveedores")
    suspend fun crearProveedor(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Body request: ProveedorRequestDto
    ): Response<ProveedorResponseDto>

    @PUT("api/v1/negocios/{negocioId}/proveedores/{id}")
    suspend fun actualizarProveedor(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long,
        @Body request: ProveedorRequestDto
    ): Response<ProveedorResponseDto>

    @DELETE("api/v1/negocios/{negocioId}/proveedores/{id}")
    suspend fun eliminarProveedor(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Path("id") id: Long
    ): Response<ResponseBody>


    // COMPRAS

    @GET("api/v1/negocios/{negocioId}/compras")
    suspend fun getCompras(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long
    ): Response<List<CompraResponseDto>>

    @POST("api/v1/negocios/{negocioId}/compras")
    suspend fun registrarCompra(
        @Header("Authorization") token: String,
        @Path("negocioId") negocioId: Long,
        @Body request: CompraRequestDto
    ): Response<CompraResponseDto>


    // MÓDULO IVA / PARÁMETROS GLOBALES

    @GET("api/v1/parametros/iva")
    suspend fun getIva(
        @Header("Authorization") token: String
    ): Response<Map<String, String>>

    @PUT("api/v1/parametros/iva")
    suspend fun actualizarIva(
        @Header("Authorization") token: String,
        @Body request: IvaRequestDto
    ): Response<Map<String, String>>

    @PUT("api/v1/negocios/{id}/codigo/regenerar")
    suspend fun regenerarCodigoInvitacion(
        @Header("Authorization") token: String,
        @Path("id") negocioId: Long
    ): Response<CodigoInvitacionResponseDto>
}