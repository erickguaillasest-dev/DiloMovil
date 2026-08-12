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
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.CategoriaDto
import com.example.movildilo.data.model.dto.ClienteResponseDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.data.model.dto.ProductoResponseDto
import com.example.movildilo.ui.Kardex.KardexActivity
import com.example.movildilo.ui.adapters.MiembrosAdapter
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.bodegas.BodegasActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ui.propietario.*
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
        cargarContextoCompletoDashboard()
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
        cardProveedores = findViewById(R.id.cardProveedores)
        cardCategorias = findViewById(R.id.cardCategorias)
        cardCuentasPorCobrar = findViewById(R.id.cardCuentasPorCobrar)
        cardMovimientos = findViewById(R.id.cardMovimientos)
        cardRendimiento = findViewById(R.id.cardRendimiento)

        btnAdminEquipo = findViewById(R.id.btnAdminEquipo)
        btnAdminConfig = findViewById(R.id.btnAdminConfig)
        btnAdminPerfil = findViewById(R.id.btnAdminPerfil)

        rvEquipo = findViewById(R.id.rvEquipo)
        fabZoe = findViewById(R.id.fabZoe)
        fabZoe.bringToFront()
    }

    private fun setupRecyclerView() {
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
        cardCuentasPorCobrar.setOnClickListener { startActivity(Intent(this,CuentasPorCobrarActivity::class.java)) }
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

    private fun cargarContextoCompletoDashboard() {
        if (negocioId == -1L) return

        lifecycleScope.launch(Dispatchers.IO) {
            supervisorScope {
                val api = RetrofitClient.apiService
                val authHeader = sessionManager.getAuthHeader() ?: ""

                val reqMiPerfil = async { runCatching { api.getMiPerfil(authHeader) }.getOrNull() }
                val reqNegocio = async { runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull() }
                val reqProductos = async { runCatching { api.getCatalogo(authHeader, negocioId) }.getOrNull() }
                val reqCategorias = async { runCatching { api.getCategorias(authHeader, negocioId) }.getOrNull() }
                val reqClientes = async { runCatching { api.getClientes(authHeader, negocioId) }.getOrNull() }
                val reqInventario = async { runCatching { api.getInventario(authHeader, negocioId) }.getOrNull() }
                val reqFacturas = async { runCatching { api.getFacturas(authHeader, negocioId) }.getOrNull() }
                val reqEquipo = async { runCatching { api.getEquipo(authHeader, negocioId) }.getOrNull() }
                val reqAlertas = async { runCatching { api.getAlertasCaducidad(authHeader, negocioId, 30) }.getOrNull() }

                val resMiPerfil = reqMiPerfil.await()
                val resNegocio = reqNegocio.await()
                val productos = reqProductos.await()?.body() ?: emptyList()
                val categorias = reqCategorias.await()?.body() ?: emptyList()
                val clientes = reqClientes.await()?.body() ?: emptyList()
                val inventario = reqInventario.await()?.body() ?: emptyList()
                val facturas = reqFacturas.await()?.body() ?: emptyList()
                val equipo = reqEquipo.await()?.body() ?: emptyList()
                val alertas = reqAlertas.await()?.body() ?: emptyList()

                // 1. Obtener la foto de perfil del USUARIO
                var fotoUsuarioApi: String? = null
                if (resMiPerfil?.isSuccessful == true && resMiPerfil.body() != null) {
                    val perfil = resMiPerfil.body()!!
                    fotoUsuarioApi = perfil.fotoPerfil
                }

                // 2. Nombre del Negocio y su logo
                var logoNegocioUrl: String? = null
                if (resNegocio?.isSuccessful == true && resNegocio.body() != null) {
                    val n = resNegocio.body()!!
                    negocioNombreReal = n.nombreComercial ?: n.razonSocial ?: "Mi Empresa"
                    logoNegocioUrl = n.rutaImagen
                }

                // 3. Resumen para Zoe
                contextoNegocioTexto = construirResumenDelNegocio(
                    productos, categorias, clientes, inventario, facturas
                )

                // 4. Alertas de caducidad
                alertasTexto = if (alertas.isNotEmpty()) {
                    alertas.take(15).joinToString("; ") { a ->
                        "${a.productoNombre ?: "Producto"} caduca el ${a.fechaCaducidad ?: "N/D"}"
                    }
                } else {
                    "No hay productos próximos a caducar en los siguientes 30 días."
                }

                val totalVentas = facturas.sumOf { it.totalCalculado }
                val itemsBajoStock = inventario.filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 5) }

                // 5. Filtrar equipo: OMITIR solicitudes pendientes únicamente para esta vista
                val equipoSinPendientes = equipo.filter { miembro ->
                    val estado = miembro.estadoInvitacion?.uppercase(Locale.ROOT) ?: ""
                    val estadoSolicitud = miembro.estadoLaboral?.uppercase(Locale.ROOT) ?: ""
                    estado != "PENDIENTE" && estadoSolicitud != "PENDIENTE"
                }

                withContext(Dispatchers.Main) {
                    tvBusinessName.text = "$negocioNombreReal • Panel de Control"
                    tvTotalFacturas.text = "${facturas.size} emitidas"
                    tvVentasMes.text = String.format(Locale.US, "$%.2f", totalVentas)
                    tvClientesActivos.text = "${clientes.size} activos"
                    cardAlert.visibility = if (itemsBajoStock.isNotEmpty()) View.VISIBLE else View.GONE

                    // Se asigna la lista filtrada
                    miembrosAdapter.actualizarLista(equipoSinPendientes)

                    val imagenAMostrarEnHeader = logoNegocioUrl?.takeIf { it.isNotBlank() }
                        ?: fotoUsuarioApi

                    if (!imagenAMostrarEnHeader.isNullOrBlank()) {
                        cargarFotoPerfilUsuario(imagenAMostrarEnHeader)
                    }
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
            groqApiKey = Constants.GROQ_API_KEY
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
}