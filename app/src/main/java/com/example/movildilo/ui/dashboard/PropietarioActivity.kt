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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.data.model.dto.usuarios.ClienteResponseDto
import com.example.movildilo.data.model.dto.facturacion.FacturaResponseDto
import com.example.movildilo.data.model.dto.inventario.InventarioResponseDto
import com.example.movildilo.data.model.dto.inventario.ProductoResponseDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.Kardex.KardexActivity
import com.example.movildilo.ui.adapters.MiembrosAdapter
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.bodega.BodegasActivity
import com.example.movildilo.ui.abastecimiento.ComprasActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
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
import java.util.Calendar
import java.util.Locale

class PropietarioActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvWelcome: TextView
    private lateinit var tvBusinessName: TextView
    private lateinit var btnLogout: MaterialButton

    private lateinit var cardAlert: MaterialCardView
    private lateinit var btnVerStock: MaterialButton

    private lateinit var tvVentasMes: TextView
    private lateinit var tvTotalFacturas: TextView
    private lateinit var tvClientesActivos: TextView

    private lateinit var cardFacturas: LinearLayout
    private lateinit var cardProductos: LinearLayout
    private lateinit var cardInventario: LinearLayout
    private lateinit var cardClientes: LinearLayout

    private lateinit var cardProveedores: LinearLayout
    private lateinit var cardBodegas: LinearLayout
    private lateinit var cardCategorias: LinearLayout
    private lateinit var cardCuentasPorCobrar: LinearLayout
    private lateinit var cardMovimientos: LinearLayout
    private lateinit var cardRendimiento: LinearLayout

    private lateinit var btnAdminEquipo: LinearLayout
    private lateinit var btnAdminConfig: LinearLayout
    private lateinit var btnAdminPerfil: LinearLayout
    private lateinit var cardAbastecimiento: LinearLayout

    private lateinit var rvEquipo: RecyclerView
    private lateinit var miembrosAdapter: MiembrosAdapter
    private lateinit var fabZoe: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private var usuarioNombre: String = "Administrador"
    private var negocioNombreReal: String = "Mi Empresa"

    private var contextoNegocioTexto: String = "Aún no se ha cargado la información del negocio."
    private var alertasTexto: String = "No hay productos próximos a caducar en los siguientes 30 días."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_propietario)

        sessionManager = SessionManager(this)

        initViews()
        setupRecyclerView()
        cargarDatosUsuarioLocal()
        setupListeners()
        verificarEstadoSuspension()
        cargarContextoCompletoDashboard()

        if (intent.getBooleanExtra(ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun initViews() {
        ivAvatar = findViewById(R.id.ivAvatar)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvBusinessName = findViewById(R.id.tvBusinessName)
        btnLogout = findViewById(R.id.btnLogout)

        cardAbastecimiento = findViewById(R.id.cardAbastecimiento)
        cardAlert = findViewById(R.id.cardAlert)
        btnVerStock = findViewById(R.id.btnVerStock)

        tvVentasMes = findViewById(R.id.tvVentasMes)
        tvTotalFacturas = findViewById(R.id.tvTotalFacturas)
        tvClientesActivos = findViewById(R.id.tvClientesActivos)

        cardFacturas = findViewById(R.id.cardFacturas)
        cardProductos = findViewById(R.id.cardProductos)
        cardInventario = findViewById(R.id.cardInventario)
        cardClientes = findViewById(R.id.cardClientes)
        cardBodegas = findViewById(R.id.cardBodegas)
        cardProveedores = findViewById(R.id.cardCategorias)
        cardCategorias = findViewById(R.id.cardCategorias)
        cardProveedores = findViewById(R.id.cardProveedores)
        cardCuentasPorCobrar = findViewById(R.id.cardCuentasPorCobrar)
        cardMovimientos = findViewById(R.id.cardMovimientos)
        cardRendimiento = findViewById(R.id.cardRendimiento)

        btnAdminEquipo = findViewById(R.id.btnAdminEquipo)
        btnAdminConfig = findViewById(R.id.btnAdminConfig)
        btnAdminPerfil = findViewById(R.id.btnAdminPerfil)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        rvEquipo = findViewById(R.id.rvEquipo)
        fabZoe = findViewById(R.id.fabZoe)
        fabZoe.bringToFront()
    }

    private fun setupRecyclerView() {
        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId != -1L) {
                cargarContextoCompletoDashboard()
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }

        miembrosAdapter = MiembrosAdapter(
            listaMiembros = emptyList(),
            onCambiarRolClick = {
                Toast.makeText(this, "Para cambiar roles ingresa al apartado 'Equipo de Trabajo'", Toast.LENGTH_SHORT).show()
            },
            onDesactivarClick = {
                Toast.makeText(this, "Para gestionar miembros ingresa a 'Equipo de Trabajo'", Toast.LENGTH_SHORT).show()
            },
            soloLectura = true
        )
        rvEquipo.layoutManager = LinearLayoutManager(this)
        rvEquipo.adapter = miembrosAdapter
        rvEquipo.isNestedScrollingEnabled = false
    }

    private fun cargarDatosUsuarioLocal() {
        negocioId = sessionManager.getNegocioId()
        val userMap = sessionManager.getUserMap()
        val nombre = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString()
        val email = userMap?.get("email")?.toString()

        val fotoUsuarioLocal = userMap?.get("foto")?.toString()
            ?: userMap?.get("fotoUrl")?.toString()
            ?: userMap?.get("imagen")?.toString()

        usuarioNombre = when {
            !nombre.isNullOrBlank() -> nombre
            !email.isNullOrBlank() -> email
            else -> "Propietario"
        }
        tvWelcome.text = "¡Hola, $usuarioNombre!"

        if (!fotoUsuarioLocal.isNullOrBlank()) {
            cargarFotoPerfilUsuario(fotoUsuarioLocal)
        }
    }

    private fun setupListeners() {
        btnLogout.setOnClickListener { confirmarCerrarSesion() }
        btnVerStock.setOnClickListener { startActivity(Intent(this, InventarioBodegasActivity::class.java)) }

        cardAbastecimiento.setOnClickListener { startActivity(Intent(this, ComprasActivity::class.java)) }
        cardFacturas.setOnClickListener { startActivity(Intent(this, HistorialFacturasActivity::class.java)) }
        cardProductos.setOnClickListener { startActivity(Intent(this, CatalogoProductosActivity::class.java)) }
        cardInventario.setOnClickListener { startActivity(Intent(this, InventarioBodegasActivity::class.java)) }
        cardClientes.setOnClickListener { startActivity(Intent(this, ClientesActivity::class.java)) }
        cardBodegas.setOnClickListener { startActivity(Intent(this, BodegasActivity::class.java)) }
        cardProveedores.setOnClickListener { startActivity(Intent(this, ProveedoresActivity::class.java)) }
        cardCategorias.setOnClickListener { startActivity(Intent(this, CategoriasActivity::class.java)) }
        cardCuentasPorCobrar.setOnClickListener { startActivity(Intent(this, CuentasPorCobrarActivity::class.java)) }
        cardMovimientos.setOnClickListener { startActivity(Intent(this, KardexActivity::class.java)) }
        cardRendimiento.setOnClickListener { startActivity(Intent(this, RendimientoComercialActivity::class.java)) }

        btnAdminEquipo.setOnClickListener { startActivity(Intent(this, Mi_equipo::class.java)) }
        btnAdminConfig.setOnClickListener { startActivity(Intent(this, ConfiguracionNegocioActivity::class.java)) }
        btnAdminPerfil.setOnClickListener { startActivity(Intent(this, Perfil::class.java)) }

        findViewById<View>(R.id.headerProfileClick).setOnClickListener {
            startActivity(Intent(this, Perfil::class.java))
        }

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
                        MaterialAlertDialogBuilder(this@PropietarioActivity)
                            .setTitle("Cuenta o Negocio Suspendido")
                            .setMessage("El acceso a este negocio se encuentra suspendido. ¿Qué deseas hacer?")
                            .setCancelable(false)
                            .setPositiveButton("Salir del negocio") { dialog, _ ->
                                dialog.dismiss()
                                sessionManager.clearSession()
                                val intent = Intent(this@PropietarioActivity, LoginActivity::class.java)
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

    // Refactorización: Carga progresiva y paralela del Dashboard
    private fun cargarContextoCompletoDashboard() {
        if (negocioId == -1L) {
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val authHeader = sessionManager.getAuthHeader() ?: return
        val api = RetrofitClient.apiService

        // Activamos indicador visual de refresco
        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            // Variables temporales para no duplicar peticiones de Zoe en segundo plano
            var facturasShared: List<FacturaResponseDto> = emptyList()
            var clientesShared: List<ClienteResponseDto> = emptyList()
            var inventarioShared: List<InventarioResponseDto> = emptyList()

            supervisorScope {
                // Bloque 1: Cargar Perfil y Negocio (Header)
                launch(Dispatchers.IO) {
                    val perfil = runCatching { api.getMiPerfil(authHeader) }.getOrNull()?.body()
                    val negocio = runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull()?.body()

                    withContext(Dispatchers.Main) {
                        negocioNombreReal = negocio?.nombreComercial ?: negocio?.razonSocial ?: "Mi Empresa"
                        tvBusinessName.text = "$negocioNombreReal • Panel de Control"
                        val imagenAMostrarEnHeader = negocio?.rutaImagen?.takeIf { it.isNotBlank() } ?: perfil?.fotoPerfil
                        if (!imagenAMostrarEnHeader.isNullOrBlank()) {
                            cargarFotoPerfilUsuario(imagenAMostrarEnHeader)
                        }
                    }
                }

                // Bloque 2: Cargar Resumen del Mes (Facturas y Clientes)
                launch(Dispatchers.IO) {
                    val facturas = runCatching { api.getFacturas(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
                    val clientes = runCatching { api.getClientes(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()

                    facturasShared = facturas
                    clientesShared = clientes

                    val calendar = Calendar.getInstance()
                    val currentYear = calendar.get(Calendar.YEAR)
                    val currentMonth = calendar.get(Calendar.MONTH) + 1
                    val mesActualStr = String.format(Locale.US, "%04d-%02d", currentYear, currentMonth)

                    val facturasMesActual = facturas.filter { it.fechaEmision?.startsWith(mesActualStr) == true }
                    val totalVentasMes = facturasMesActual.sumOf { it.totalCalculado }

                    withContext(Dispatchers.Main) {
                        tvTotalFacturas.text = "${facturasMesActual.size} emitidas"
                        tvVentasMes.text = formatearMonto(totalVentasMes)
                        tvClientesActivos.text = "${clientes.size} activos"
                    }
                }

                // Bloque 3: Cargar Alertas de Inventario
                launch(Dispatchers.IO) {
                    val inventario = runCatching { api.getInventario(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
                    inventarioShared = inventario

                    val itemsBajoStock = inventario.filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 5) }

                    withContext(Dispatchers.Main) {
                        cardAlert.visibility = if (itemsBajoStock.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Bloque 4: Cargar Equipo de Trabajo
                launch(Dispatchers.IO) {
                    val equipo = runCatching { api.getEquipo(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
                    val equipoSinPendientes = equipo.filter { miembro ->
                        val estado = miembro.estadoInvitacion?.uppercase(Locale.ROOT) ?: ""
                        val estadoSolicitud = miembro.estadoLaboral?.uppercase(Locale.ROOT) ?: ""
                        estado != "PENDIENTE" && estadoSolicitud != "PENDIENTE"
                    }

                    withContext(Dispatchers.Main) {
                        miembrosAdapter.actualizarLista(equipoSinPendientes)
                    }
                }
            }

            // Una vez terminadas las 4 tareas críticas de interfaz (visibles), escondemos el spinner.
            swipeRefreshLayout.isRefreshing = false

            // Bloque 5: Cargar Contexto para Zoe IA (Silencioso, en segundo plano desvinculado de la interfaz)
            launch(Dispatchers.IO) {
                val productos = runCatching { api.getCatalogo(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
                val categorias = runCatching { api.getCategorias(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
                val alertas = runCatching { api.getAlertasCaducidad(authHeader, negocioId, 30) }.getOrNull()?.body() ?: emptyList()

                contextoNegocioTexto = construirResumenDelNegocio(
                    productos, categorias, clientesShared, inventarioShared, facturasShared
                )

                alertasTexto = if (alertas.isNotEmpty()) {
                    alertas.take(15).joinToString("; ") { a ->
                        "${a.productoNombre ?: "Producto"} caduca el ${a.fechaCaducidad ?: "N/D"}"
                    }
                } else {
                    "No hay productos próximos a caducar en los siguientes 30 días."
                }
            }
        }
    }

    private fun cargarFotoPerfilUsuario(urlOBase64: String) {
        Glide.with(this)
            .load(urlOBase64)
            .circleCrop()
            .placeholder(R.drawable.bg_avatar_circulo)
            .error(R.drawable.ic_mic)
            .into(ivAvatar)
    }

    private fun construirResumenDelNegocio(
        productos: List<ProductoResponseDto>,
        categorias: List<CategoriaDto>,
        clientes: List<ClienteResponseDto>,
        inventario: List<InventarioResponseDto>,
        facturas: List<FacturaResponseDto>
    ): String {
        val nombresCategorias = categorias.mapNotNull { it.nombre }.filter { it.isNotBlank() }

        val listaProductos = productos.take(20).joinToString("; ") { p ->
            val pvp = String.format(Locale.US, "%.2f", p.precioUnitario ?: 0.0)
            "${p.nombre ?: "S/N"} (cod: ${p.codigoPrincipal ?: "S/C"}, marca: ${p.marca ?: "-"}, PVP: $$pvp)"
        }.ifEmpty { "Aún no hay productos registrados." }

        val stockBajo = inventario
            .filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 0) }
            .take(15)
            .joinToString("; ") { i ->
                "${i.productoNombre ?: "Producto"} en ${i.bodegaNombre ?: "bodega"} (quedan ${i.cantidadActual ?: 0})"
            }.ifEmpty { "Ningún producto en stock bajo por el momento." }

        val valorTotalInventario = inventario.sumOf { it.valorInventario ?: 0.0 }

        val nombresClientes = clientes.take(10).mapNotNull { c ->
            c.nombreCompleto ?: listOfNotNull(c.primerNombre, c.apellidoPaterno).joinToString(" ").ifBlank { null }
        }

        val totalVentas = facturas.sumOf { it.totalCalculado }
        val ultimasFacturas = facturas.takeLast(5).joinToString("; ") { f ->
            val tot = String.format(Locale.US, "%.2f", f.totalCalculado)
            "#${f.numeroFactura ?: "S/N"} - ${f.nombreClienteFormateado} - $$tot"
        }.ifEmpty { "Aún no hay facturas emitidas." }

        return """
            DATOS REALES Y ACTUALES DEL NEGOCIO "$negocioNombreReal":
            - Categorías de productos registradas (${categorias.size}): ${nombresCategorias.joinToString(", ").ifEmpty { "ninguna aún" }}.
            - Total de productos en catálogo: ${productos.size}. Ejemplos: $listaProductos.
            - Valor total actual del inventario: $${String.format(Locale.US, "%.2f", valorTotalInventario)}.
            - Productos con stock bajo o crítico: $stockBajo.
            - Total de clientes registrados: ${clientes.size}. Algunos: ${nombresClientes.joinToString(", ").ifEmpty { "ninguno aún" }}.
            - Total de facturas emitidas: ${facturas.size}, con ventas acumuladas por $${String.format(Locale.US, "%.2f", totalVentas)}.
            - Últimas facturas emitidas: $ultimasFacturas.
        """.trimIndent()
    }

    private fun abrirChatZoe() {
        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = usuarioNombre,
            negocioNombre = negocioNombreReal,
            contextoNegocioTexto = contextoNegocioTexto,
            alertasTexto = alertasTexto,
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Cerrar Sesión") { d, _ -> d.dismiss(); cerrarSesionEfectiva() }
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

    private fun formatearMonto(monto: Double): String {
        val formato = java.text.NumberFormat.getNumberInstance(Locale.US) as java.text.DecimalFormat
        formato.applyPattern("#,##0.00")
        return "$${formato.format(monto)}"
    }
}