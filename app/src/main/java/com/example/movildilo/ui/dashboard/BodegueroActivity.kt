// BodegueroActivity.kt
package com.example.movildilo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.data.model.dto.inventario.InventarioResponseDto
import com.example.movildilo.data.model.dto.inventario.ProductoResponseDto
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.Kardex.KardexActivity
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.Bodegas.BodegasActivity
import com.example.movildilo.ui.abastecimiento.ComprasActivity
import com.example.movildilo.ui.productos.CatalogoProductosActivity
import com.example.movildilo.ui.propietario.*
import com.example.movildilo.ui.proveedores.ProveedoresActivity
import com.example.movildilo.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.Locale

class BodegueroActivity : AppCompatActivity() {

    private lateinit var btnLogout: MaterialButton
    private lateinit var btnNotifications: MaterialButton
    private lateinit var headerProfileClick: LinearLayout
    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvWelcome: TextView
    private lateinit var tvBusinessName: TextView

    private lateinit var cardAlert: MaterialCardView
    private lateinit var tvTotalProductos: TextView
    private lateinit var tvStockCritico: TextView
    private lateinit var tvTotalBodegas: TextView

    private lateinit var cardProductos: LinearLayout
    private lateinit var cardInventario: LinearLayout
    private lateinit var cardBodegas: LinearLayout
    private lateinit var cardProveedores: LinearLayout
    private lateinit var cardAbastecimiento: LinearLayout
    private lateinit var cardMovimientos: LinearLayout
    private lateinit var cardCategorias: LinearLayout

    private lateinit var btnAdminPerfil: LinearLayout
    private lateinit var fabZoe: View

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private var usuarioNombre: String = "Bodeguero"
    private var negocioNombreReal: String = "Mi Empresa"
    private val baseServerUrl = "https://dilo-backend-mxlu.onrender.com"

    private var contextoNegocioTexto: String = "Cargando información del almacén..."
    private var alertasTexto: String = "No hay alertas de caducidad o stock registradas."

    private val ROLES_AUTORIZADOS = listOf("PROPIETARIO", "ADMINISTRADOR", "BODEGUERO", "INVENTARIO")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bodeguero)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        cargarDatosUsuarioLocal()
        setupListeners()
        verificarEstadoSuspension()

        if (negocioId != -1L) {
            cargarContextoAlmacenYDashboard()
        } else {
            Toast.makeText(this, "Selecciona una empresa válida para continuar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        btnLogout = findViewById(R.id.btnLogout)
        btnNotifications = findViewById(R.id.btnNotifications)
        headerProfileClick = findViewById(R.id.headerProfileClick)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvBusinessName = findViewById(R.id.tvBusinessName)

        cardAlert = findViewById(R.id.cardAlert)
        tvTotalProductos = findViewById(R.id.tvTotalProductos)
        tvStockCritico = findViewById(R.id.tvStockCritico)
        tvTotalBodegas = findViewById(R.id.tvTotalBodegas)

        cardProductos = findViewById(R.id.cardProductos)
        cardInventario = findViewById(R.id.cardInventario)
        cardBodegas = findViewById(R.id.cardBodegas)
        cardProveedores = findViewById(R.id.cardProveedores)
        cardAbastecimiento = findViewById(R.id.cardAbastecimiento)
        cardMovimientos = findViewById(R.id.cardMovimientos)
        cardCategorias = findViewById(R.id.cardCategorias)

        btnAdminPerfil = findViewById(R.id.btnAdminPerfil)
        fabZoe = findViewById(R.id.fabZoe)
        fabZoe.bringToFront()
    }

    private fun cargarDatosUsuarioLocal() {
        val userMap = sessionManager.getUserMap()
        val nombre = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString()
        val email = userMap?.get("email")?.toString()

        usuarioNombre = when {
            !nombre.isNullOrBlank() -> nombre
            !email.isNullOrBlank() -> email
            else -> "Bodeguero"
        }
        tvWelcome.text = "¡Hola, $usuarioNombre!"
    }

    private fun setupListeners() {
        btnLogout.setOnClickListener { confirmarCerrarSesion() }

        btnNotifications.setOnClickListener {
            val alertasActuales = alertasTexto
            MaterialAlertDialogBuilder(this)
                .setTitle("Notificaciones y Alertas")
                .setMessage(alertasActuales)
                .setPositiveButton("Entendido", null)
                .show()
        }

        val abrirPerfil = View.OnClickListener {
            startActivity(Intent(this, Perfil::class.java))
        }
        headerProfileClick.setOnClickListener(abrirPerfil)
        btnAdminPerfil.setOnClickListener(abrirPerfil)

        cardAlert.setOnClickListener { abrirModulo(InventarioBodegasActivity::class.java) }
        cardProductos.setOnClickListener { abrirModulo(CatalogoProductosActivity::class.java) }
        cardInventario.setOnClickListener { abrirModulo(InventarioBodegasActivity::class.java) }
        cardBodegas.setOnClickListener { abrirModulo(BodegasActivity::class.java) }
        cardProveedores.setOnClickListener { abrirModulo(ProveedoresActivity::class.java) }
        cardAbastecimiento.setOnClickListener { abrirModulo(ComprasActivity::class.java) }
        cardMovimientos.setOnClickListener { abrirModulo(KardexActivity::class.java) }
        cardCategorias.setOnClickListener { abrirModulo(CategoriasActivity::class.java) }

        fabZoe.setOnClickListener { abrirChatZoe() }
    }

    private fun verificarEstadoSuspension() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful) {
                    val usuario = response.body()
                    if (usuario?.suspendido == true) {
                        MaterialAlertDialogBuilder(this@BodegueroActivity)
                            .setTitle("Cuenta o Negocio Suspendido")
                            .setMessage("El acceso a este negocio se encuentra suspendido. ¿Qué deseas hacer?")
                            .setCancelable(false)
                            .setPositiveButton("Salir del negocio") { dialog, _ ->
                                dialog.dismiss()
                                sessionManager.clearSession()
                                val intent = Intent(this@BodegueroActivity, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                            .setNegativeButton("Esperar", null)
                            .show()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun abrirModulo(actividadDestino: Class<*>) {
        val idEmpresaActual = sessionManager.getNegocioId()
        val rolUsuario = sessionManager.getUserRole()?.uppercase() ?: ""

        if (idEmpresaActual == -1L || idEmpresaActual == 0L) {
            Toast.makeText(this, "No hay una empresa/negocio seleccionado", Toast.LENGTH_SHORT).show()
            return
        }

        if (ROLES_AUTORIZADOS.contains(rolUsuario)) {
            startActivity(Intent(this, actividadDestino))
        } else {
            Toast.makeText(this, "No tienes permiso para acceder a este módulo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarContextoAlmacenYDashboard() {
        lifecycleScope.launch(Dispatchers.IO) {
            supervisorScope {
                val api = RetrofitClient.apiService
                val authHeader = sessionManager.getAuthHeader() ?: ""

                val reqNegocio = async { runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull() }
                val reqProductos = async { runCatching { api.getCatalogo(authHeader, negocioId) }.getOrNull() }
                val reqInventario = async { runCatching { api.getInventario(authHeader, negocioId) }.getOrNull() }
                val reqBodegas = async { runCatching { api.getBodegas(authHeader, negocioId) }.getOrNull() }
                val reqCategorias = async { runCatching { api.getCategorias(authHeader, negocioId) }.getOrNull() }
                val reqAlertas = async { runCatching { api.getAlertasCaducidad(authHeader, negocioId, 30) }.getOrNull() }

                val resNegocio = reqNegocio.await()
                val productos = reqProductos.await()?.body() ?: emptyList()
                val inventario = reqInventario.await()?.body() ?: emptyList()
                val bodegas = reqBodegas.await()?.body() ?: emptyList()
                val categorias = reqCategorias.await()?.body() ?: emptyList()
                val alertas = reqAlertas.await()?.body() ?: emptyList()

                var logoNegocioUrl: String? = null
                if (resNegocio?.isSuccessful == true && resNegocio.body() != null) {
                    val n = resNegocio.body()!!
                    negocioNombreReal = n.nombreComercial ?: n.razonSocial ?: "Mi Empresa"
                    logoNegocioUrl = n.rutaImagen
                }

                contextoNegocioTexto = construirResumenAlmacen(productos, categorias, inventario, bodegas.size)

                alertasTexto = if (alertas.isNotEmpty()) {
                    alertas.take(15).joinToString("\n• ") { a ->
                        "${a.productoNombre ?: "Producto"} caduca el ${a.fechaCaducidad ?: "N/D"}"
                    }.let { "• $it" }
                } else {
                    "No hay productos próximos a caducar en los siguientes 30 días."
                }

                val itemsBajoStock = inventario.filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 5) }

                withContext(Dispatchers.Main) {
                    tvBusinessName.text = negocioNombreReal
                    tvTotalProductos.text = "${productos.size} prod."
                    tvStockCritico.text = "${itemsBajoStock.size} alertas"
                    tvTotalBodegas.text = "${bodegas.size} activa${if (bodegas.size != 1) "s" else ""}"

                    cardAlert.visibility = if (itemsBajoStock.isNotEmpty()) View.VISIBLE else View.GONE

                    if (!logoNegocioUrl.isNullOrBlank()) {
                        cargarImagenNegocio(logoNegocioUrl)
                    }
                }
            }
        }
    }

    private fun cargarImagenNegocio(rutaOUrl: String) {
        val urlFinal = if (rutaOUrl.startsWith("http")) {
            rutaOUrl
        } else {
            val prefijoBarra = if (rutaOUrl.startsWith("/")) "" else "/"
            "$baseServerUrl$prefijoBarra$rutaOUrl"
        }

        Glide.with(this)
            .load(urlFinal)
            .circleCrop()
            .placeholder(R.drawable.bg_avatar_circulo)
            .error(R.drawable.bg_avatar_circulo)
            .into(ivAvatar)
    }

    private fun construirResumenAlmacen(
        productos: List<ProductoResponseDto>,
        categorias: List<CategoriaDto>,
        inventario: List<InventarioResponseDto>,
        totalBodegas: Int
    ): String {
        val nombresCategorias = categorias.mapNotNull { it.nombre }.filter { it.isNotBlank() }
        val stockBajo = inventario
            .filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 0) }
            .take(15)
            .joinToString("; ") { i ->
                "${i.productoNombre ?: "Producto"} en ${i.bodegaNombre ?: "bodega"} (quedan ${i.cantidadActual ?: 0})"
            }.ifEmpty { "Ningún producto en stock crítico." }

        val valorInventario = inventario.sumOf { it.valorInventario ?: 0.0 }

        return """
            ESTADO ACTUAL DEL ALMACÉN EN "$negocioNombreReal":
            - Categorías registradas (${categorias.size}): ${nombresCategorias.joinToString(", ").ifEmpty { "ninguna" }}.
            - Total de productos en el catálogo: ${productos.size}.
            - Bodegas activas: $totalBodegas.
            - Valor del inventario almacenado: $${String.format(Locale.US, "%.2f", valorInventario)}.
            - Alertas de Stock Bajo / Crítico: $stockBajo.
        """.trimIndent()
    }

    private fun abrirChatZoe() {
        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = usuarioNombre,
            negocioNombre = negocioNombreReal,
            contextoNegocioTexto = contextoNegocioTexto,
            alertasTexto = alertasTexto,
            groqApiKey = Constants.GROQ_API_KEY_CHAT,
            rolUsuario = "BODEGUERO"
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Cerrar Sesión") { d, _ ->
                d.dismiss()
                cerrarSesionEfectiva()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun cerrarSesionEfectiva() {
        sessionManager.logout()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}