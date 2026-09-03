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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.propietario.ClientesActivity
import com.example.movildilo.ui.propietario.CuentasPorCobrarActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import com.example.movildilo.ui.propietario.Perfil
import com.example.movildilo.ui.propietario.RendimientoComercialActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class VendedorActivity : AppCompatActivity() {

    private lateinit var btnLogout: MaterialButton
    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvWelcome: TextView
    private lateinit var tvBusinessName: TextView

    private lateinit var cardAlert: MaterialCardView
    private lateinit var btnVerCxC: MaterialButton

    private lateinit var tvMensajeCxC: TextView
    private lateinit var tvVentasContado: TextView
    private lateinit var tvVentasCredito: TextView
    private lateinit var tvTotalFacturas: TextView

    private lateinit var cardFacturas: LinearLayout
    private lateinit var cardClientes: LinearLayout
    private lateinit var cardCuentasPorCobrar: LinearLayout
    private lateinit var cardRendimiento: LinearLayout

    private lateinit var sessionManager: SessionManager
    private lateinit var fabZoe: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val baseServerUrl = "https://dilo-backend-mxlu.onrender.com"

    private var negocioId: Long = -1L
    private var usuarioNombre: String = "Vendedor"
    private var negocioNombreReal: String = "Mi Empresa"

    private var fotoUsuarioUrl: String? = null
    private var logoNegocioUrl: String? = null

    private var contextoNegocioTexto: String = "Aún no se ha cargado la información del negocio."
    private var alertasTexto: String = "No hay cuentas por cobrar pendientes por el momento."

    private val ROLES_AUTORIZADOS = listOf("PROPIETARIO", "ADMINISTRADOR", "VENDEDOR")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_vendedor)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupListeners()
        cargarDatosHeader()
        verificarEstadoSuspension()

        if (negocioId != -1L) {
            cargarResumenVentas()
        } else {
            Toast.makeText(this, "Selecciona una empresa válida para continuar", Toast.LENGTH_SHORT).show()
        }

        if (intent.getBooleanExtra(ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun initViews() {
        btnLogout = findViewById(R.id.btnLogout)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvBusinessName = findViewById(R.id.tvBusinessName)

        cardAlert = findViewById(R.id.cardAlert)
        btnVerCxC = findViewById(R.id.btnVerCxC)
        tvMensajeCxC = findViewById(R.id.tvMensajeCxC)
        tvVentasContado = findViewById(R.id.tvVentasContado)
        tvVentasCredito = findViewById(R.id.tvVentasCredito)
        tvTotalFacturas = findViewById(R.id.tvTotalFacturas)

        cardFacturas = findViewById(R.id.cardFacturas)
        cardClientes = findViewById(R.id.cardClientes)
        cardCuentasPorCobrar = findViewById(R.id.cardCuentasPorCobrar)
        cardRendimiento = findViewById(R.id.cardRendimiento)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        fabZoe = findViewById(R.id.fabZoe)
        fabZoe.bringToFront()
    }

    private fun construirUrlFoto(rutaFoto: String?): String? {
        if (rutaFoto.isNullOrBlank()) return null
        return if (rutaFoto.startsWith("http")) rutaFoto else "$baseServerUrl$rutaFoto"
    }

    private fun setupListeners() {
        btnLogout.setOnClickListener {
            confirmarCerrarSesion()
        }

        findViewById<LinearLayout>(R.id.headerProfileClick).setOnClickListener {
            startActivity(Intent(this, Perfil::class.java))
        }

        btnVerCxC.setOnClickListener {
            abrirModulo(CuentasPorCobrarActivity::class.java)
        }

        cardFacturas.setOnClickListener { abrirModulo(HistorialFacturasActivity::class.java) }
        cardClientes.setOnClickListener { abrirModulo(ClientesActivity::class.java) }
        cardCuentasPorCobrar.setOnClickListener { abrirModulo(CuentasPorCobrarActivity::class.java) }
        cardRendimiento.setOnClickListener { abrirModulo(RendimientoComercialActivity::class.java) }

        fabZoe.setOnClickListener { abrirChatZoe() }

        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId != -1L) {
                cargarDatosHeader()
                cargarResumenVentas()
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun verificarEstadoSuspension() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful) {
                    val usuario = response.body()
                    if (usuario?.suspendido == true) {
                        MaterialAlertDialogBuilder(this@VendedorActivity)
                            .setTitle("Cuenta o Negocio Suspendido")
                            .setMessage("El acceso a este negocio se encuentra suspendido. ¿Qué deseas hacer?")
                            .setCancelable(false)
                            .setPositiveButton("Salir del negocio") { dialog, _ ->
                                dialog.dismiss()
                                sessionManager.clearSession()
                                val intent = Intent(this@VendedorActivity, LoginActivity::class.java)
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

    private fun abrirChatZoe() {
        val userMap = sessionManager.getUserMap()
        val nombreUsuario = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString() ?: "Usuario"
        val rolUsuario = sessionManager.getUserRole() ?: "PROPIETARIO"
        val negocioNombre = userMap?.get("negocioNombre")?.toString() ?: userMap?.get("nombreNegocio")?.toString() ?: "Tu Negocio"

        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = nombreUsuario,
            negocioNombre = negocioNombre,
            negocioId = negocioId.toString(),
            groqApiKey = Constants.GROQ_API_KEY_CHAT,
            rolUsuario = rolUsuario
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
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

    private fun cargarDatosHeader() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val responsePerfil = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (responsePerfil.isSuccessful && responsePerfil.body() != null) {
                    val usuario = responsePerfil.body()!!
                    usuarioNombre = usuario.primerNombre?.takeIf { it.isNotBlank() } ?: "Vendedor"
                    tvWelcome.text = "Hola, $usuarioNombre 👋"

                    fotoUsuarioUrl = construirUrlFoto(usuario.fotoPerfil)
                    actualizarAvatarHeader()
                }
            } catch (e: Exception) {}
        }
    }

    private fun actualizarAvatarHeader() {
        val urlAMostrar = logoNegocioUrl?.takeIf { it.isNotBlank() } ?: fotoUsuarioUrl
        if (urlAMostrar.isNullOrBlank()) return

        Glide.with(this)
            .load(urlAMostrar)
            .placeholder(R.drawable.bg_avatar_circulo)
            .error(R.drawable.ic_mic)
            .circleCrop()
            .into(ivAvatar)
    }

    private fun cargarResumenVentas() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val responseNegocio = RetrofitClient.apiService.getNegocio(authHeader, negocioId)
                if (responseNegocio.isSuccessful) {
                    val negocio = responseNegocio.body()
                    tvBusinessName.text = negocio?.nombreComercial ?: negocio?.razonSocial ?: "Mi Negocio"
                    negocioNombreReal = tvBusinessName.text.toString()

                    logoNegocioUrl = construirUrlFoto(negocio?.rutaImagen)
                    actualizarAvatarHeader()
                } else {
                    tvBusinessName.text = "Mi Negocio"
                }
            } catch (e: Exception) {
                tvBusinessName.text = "Mi Negocio"
            }

            try {
                val responseFacturas = RetrofitClient.apiService.getFacturas(authHeader, negocioId)
                if (responseFacturas.isSuccessful) {
                    val facturas = responseFacturas.body() ?: emptyList()

                    val ventasContado = facturas
                        .filter { it.metodoPago != "TARJETA_CREDITO" }
                        .sumOf { it.totalCalculado }

                    val ventasCredito = facturas
                        .filter { it.metodoPago == "TARJETA_CREDITO" }
                        .sumOf { it.totalCalculado }

                    tvVentasContado.text = String.format(java.util.Locale.US, "$%.2f", ventasContado)
                    tvVentasCredito.text = String.format(java.util.Locale.US, "$%.2f", ventasCredito)
                    tvTotalFacturas.text = "${facturas.size} facturas"
                } else {
                    tvVentasContado.text = "$0.00"
                    tvVentasCredito.text = "$0.00"
                    tvTotalFacturas.text = "0 facturas"
                }

                val responseCxC = RetrofitClient.apiService.getCuentasPorCobrar(authHeader, negocioId)
                if (responseCxC.isSuccessful) {
                    val cuentas = responseCxC.body() ?: emptyList()
                    val totalPendiente = cuentas.sumOf { it.saldoPendiente ?: 0.0 }

                    if (cuentas.isNotEmpty()) {
                        tvMensajeCxC.text = "Tienes $${String.format(java.util.Locale.US, "%.2f", totalPendiente)} pendientes en ${cuentas.size} cuentas."
                    } else {
                        tvMensajeCxC.text = "No hay cuentas pendientes por cobrar."
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@VendedorActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
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