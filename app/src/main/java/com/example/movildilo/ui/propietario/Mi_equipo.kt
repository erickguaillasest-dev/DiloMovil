package com.example.movildilo.ui.propietario

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
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
import com.example.movildilo.data.model.dto.usuarios.MiembroResponseDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.adapters.MiembroEquipoAdapter
import com.example.movildilo.utils.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class Mi_equipo : AppCompatActivity() {

    private lateinit var btnRegresar: ImageView
    private lateinit var btnNotificaciones: ImageView
    private lateinit var btnCopiarCodigo: ImageView
    private lateinit var btnRegenerarCodigo: ImageView
    private lateinit var tvCodigoAcceso: TextView
    private lateinit var tvBadgeCantidadSolicitudes: TextView
    private lateinit var etBuscarMiembro: TextInputEditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var tvHeaderIniciales: TextView
    private lateinit var ivHeaderAvatar: ImageView

    private lateinit var rvSolicitudesPendientes: RecyclerView
    private lateinit var rvColaboradoresActivos: RecyclerView

    private lateinit var adapterPendientes: MiembroEquipoAdapter
    private lateinit var adapterActivos: MiembroEquipoAdapter

    private lateinit var tvBreadcrumbNegocio: TextView
    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L

    private var solicitudes = mutableListOf<MiembroResponseDto>()
    private var miembrosActivos = mutableListOf<MiembroResponseDto>()

    private var cantidadAlertasVencimiento: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mi_equipo)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupListeners()
        setupRecyclerViews()
        cargarAvatarUsuarioLogueado()

        if (negocioId == -1L) {
            tvCodigoAcceso.text = "ERROR: Sin Negocio"
            MaterialAlertDialogBuilder(this)
                .setTitle("Sesión desactualizada")
                .setMessage("No podemos encontrar el ID de tu negocio. Por favor, cierra sesión y vuelve a ingresar para sincronizar tus datos.")
                .setPositiveButton("Entendido", null)
                .show()
        }

        if (intent.getStringExtra(ZoeActionRouter.EXTRA_ACCION) ==
            ZoeActionRouter.Accion.VER_EQUIPO
        ) {
            tvCodigoAcceso.postDelayed({
                Toast.makeText(
                    this,
                    "Comparte tu código de invitación para que se unan nuevos miembros, o toca a alguien de la lista para cambiar su rol.",
                    Toast.LENGTH_LONG
                ).show()
            }, 600)
        }

        if (intent.getBooleanExtra(ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun abrirChatZoe() {
        val userMap = sessionManager.getUserMap()
        val nombreUsuario = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString() ?: "Usuario"
        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = nombreUsuario,
            negocioNombre = "Mi Empresa",
            contextoNegocioTexto = "Estás visualizando tu equipo de trabajo.",
            alertasTexto = "Sin alertas recientes.",
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    override fun onResume() {
        super.onResume()
        if (negocioId != -1L) {
            cargarEquipo(negocioId)
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        btnNotificaciones = findViewById(R.id.btnNotificaciones)
        btnCopiarCodigo = findViewById(R.id.btnCopiarCodigo)
        btnRegenerarCodigo = findViewById(R.id.btnRegenerarCodigo)
        tvCodigoAcceso = findViewById(R.id.tvCodigoAcceso)
        tvBadgeCantidadSolicitudes = findViewById(R.id.tvCantidadSolicitudes)
        etBuscarMiembro = findViewById(R.id.etBuscarMiembro)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        tvHeaderIniciales = findViewById(R.id.tvHeaderIniciales)
        ivHeaderAvatar = findViewById(R.id.ivHeaderAvatar)
        rvSolicitudesPendientes = findViewById(R.id.rvSolicitudesPendientes)
        rvColaboradoresActivos = findViewById(R.id.rvColaboradoresActivos)
        tvBreadcrumbNegocio = findViewById(R.id.tvBreadcrumbNegocio)
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }
        btnNotificaciones.setOnClickListener { view -> mostrarPopupAlertas(view) }
        btnCopiarCodigo.setOnClickListener { copiarCodigo() }
        btnRegenerarCodigo.setOnClickListener { regenerarCodigo() }

        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId != -1L) {
                cargarEquipo(negocioId)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }

        etBuscarMiembro.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarEquipo(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerViews() {
        adapterPendientes = MiembroEquipoAdapter(
            lista = solicitudes,
            onAprobar = { miembro -> responderSolicitud(miembro, aceptar = true) },
            onRechazar = { miembro -> responderSolicitud(miembro, aceptar = false) }
        )
        rvSolicitudesPendientes.layoutManager = LinearLayoutManager(this)
        rvSolicitudesPendientes.adapter = adapterPendientes

        adapterActivos = MiembroEquipoAdapter(
            lista = miembrosActivos,
            onDesactivar = { miembro -> desactivarMiembro(miembro) },
            onEditarRol = { miembro -> cambiarRol(miembro) }
        )
        rvColaboradoresActivos.layoutManager = LinearLayoutManager(this)
        rvColaboradoresActivos.adapter = adapterActivos
    }

    private fun cargarEquipo(id: Long) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrEmpty()) {
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Sesión no válida. Por favor vuelve a iniciar sesión.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val reqMiembros = async { runCatching { RetrofitClient.apiService.getEquipo(authHeader, id) }.getOrNull() }
                val reqNegocio = async { runCatching { RetrofitClient.apiService.getNegocio(authHeader, id) }.getOrNull() }

                val respMiembros = reqMiembros.await()
                val respNegocio = reqNegocio.await()

                if (respMiembros?.isSuccessful == true) {
                    val equipoCompleto = respMiembros.body() ?: emptyList()

                    solicitudes = equipoCompleto.filter {
                        it.estadoInvitacion?.trim()?.uppercase() == "PENDIENTE"
                    }.toMutableList()

                    miembrosActivos = equipoCompleto.filter {
                        it.estadoInvitacion?.trim()?.uppercase() != "PENDIENTE"
                    }.toMutableList()

                    if (miembrosActivos.isNotEmpty()) {
                        miembrosActivos.sortBy { miembro ->
                            obtenerTimestampFecha(miembro.fechaVinculacion)
                        }
                        miembrosActivos[0].esCreador = true
                    }

                    filtrarEquipo(etBuscarMiembro.text?.toString() ?: "")
                    tvBadgeCantidadSolicitudes.text = solicitudes.size.toString()
                } else {
                    Log.e("Equipo", "Error API miembros: ${respMiembros?.code()}")
                    Toast.makeText(this@Mi_equipo, "Error al obtener miembros del equipo.", Toast.LENGTH_SHORT).show()
                }

                if (respNegocio?.isSuccessful == true && respNegocio.body() != null) {
                    val negocio = respNegocio.body()!!
                    tvCodigoAcceso.text = negocio.codigoInvitacion ?: "NO-DISPONIBLE"
                    val nombreNegocio = negocio.nombreComercial ?: negocio.razonSocial ?: "Mi Negocio"
                    tvBreadcrumbNegocio.text = "🏢 $nombreNegocio / Mi Equipo"
                } else {
                    tvCodigoAcceso.text = "NO-DISPONIBLE"
                    tvBreadcrumbNegocio.text = "🏢 Mi Negocio / Mi Equipo"
                }

            } catch (e: Exception) {
                Log.e("Equipo", "Excepción al cargar equipo", e)
                Toast.makeText(this@Mi_equipo, "Error al cargar la información.", Toast.LENGTH_LONG).show()
            } finally {
                // Detener siempre la animación de carga (SwipeRefresh)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun filtrarEquipo(query: String) {
        val texto = query.trim().lowercase(Locale.ROOT)

        val solicitudesFiltradas = if (texto.isEmpty()) {
            solicitudes
        } else {
            solicitudes.filter { miembro -> coincideFiltro(miembro, texto) }
        }

        val activosFiltrados = if (texto.isEmpty()) {
            miembrosActivos
        } else {
            miembrosActivos.filter { miembro -> coincideFiltro(miembro, texto) }
        }

        adapterPendientes.actualizarLista(solicitudesFiltradas)
        adapterActivos.actualizarLista(activosFiltrados)
    }

    private fun coincideFiltro(miembro: MiembroResponseDto, texto: String): Boolean {
        val nombreUsuario = miembro.nombreUsuario?.lowercase(Locale.ROOT) ?: ""
        val email = miembro.emailUsuario?.lowercase(Locale.ROOT) ?: ""
        val rol = miembro.rol?.lowercase(Locale.ROOT) ?: ""
        val idStr = miembro.id?.toString() ?: ""
        val usuarioIdStr = miembro.usuarioId?.toString() ?: ""

        return nombreUsuario.contains(texto) ||
                email.contains(texto) ||
                rol.contains(texto) ||
                idStr.contains(texto) ||
                usuarioIdStr.contains(texto)
    }

    private fun responderSolicitud(miembro: MiembroResponseDto, aceptar: Boolean) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        val miembroId = miembro.id ?: miembro.usuarioId

        if (miembroId == null) {
            Toast.makeText(this, "Error: ID de miembro no encontrado.", Toast.LENGTH_SHORT).show()
            return
        }

        val accion = if (aceptar) "aceptar" else "rechazar"
        val mensaje = if (aceptar) "El usuario tendrá acceso al sistema." else "La solicitud será eliminada."

        MaterialAlertDialogBuilder(this)
            .setTitle("¿${if (aceptar) "Aceptar" else "Rechazar"} solicitud?")
            .setMessage(mensaje)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, $accion") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.responderInvitacion(authHeader, negocioId, miembroId, aceptar)
                        if (response.isSuccessful) {
                            Toast.makeText(this@Mi_equipo, "¡Listo! La solicitud fue ${if (aceptar) "aceptada" else "rechazada"}.", Toast.LENGTH_SHORT).show()
                            cargarEquipo(negocioId)
                        } else {
                            Log.e("Equipo", "Error responderInvitacion (${response.code()})")
                            Toast.makeText(this@Mi_equipo, "Oops... Error al procesar la solicitud.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        if (e is java.io.EOFException || e.cause is java.io.EOFException || e.message?.contains("End of input") == true) {
                            Toast.makeText(this@Mi_equipo, "¡Listo! La solicitud fue ${if (aceptar) "aceptada" else "rechazada"}.", Toast.LENGTH_SHORT).show()
                            cargarEquipo(negocioId)
                        } else {
                            Log.e("Equipo", "Excepción responderSolicitud", e)
                            Toast.makeText(this@Mi_equipo, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun desactivarMiembro(miembro: MiembroResponseDto) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        val miembroId = miembro.id ?: miembro.usuarioId

        if (miembroId == null) {
            Toast.makeText(this, "Error: ID de miembro no encontrado.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Expulsar miembro?")
            .setMessage("El usuario perderá acceso al sistema y será eliminado del negocio permanentemente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, expulsar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.desactivarMiembro(authHeader, negocioId, miembroId)
                        if (response.isSuccessful) {
                            Toast.makeText(this@Mi_equipo, "¡Expulsado! El miembro ha sido eliminado del negocio.", Toast.LENGTH_SHORT).show()
                            cargarEquipo(negocioId)
                        } else {
                            Log.e("Equipo", "Error desactivarMiembro (${response.code()})")
                            Toast.makeText(this@Mi_equipo, "Oops... Error al expulsar al miembro.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        if (e is java.io.EOFException || e.cause is java.io.EOFException || e.message?.contains("End of input") == true) {
                            Toast.makeText(this@Mi_equipo, "¡Expulsado! El miembro ha sido eliminado del negocio.", Toast.LENGTH_SHORT).show()
                            cargarEquipo(negocioId)
                        } else {
                            Log.e("Equipo", "Excepción desactivarMiembro", e)
                            Toast.makeText(this@Mi_equipo, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun cambiarRol(miembro: MiembroResponseDto) {
        val miembroId = miembro.id ?: miembro.usuarioId

        if (miembroId == null) {
            Toast.makeText(this, "Error: No se pudo obtener el ID del usuario.", Toast.LENGTH_LONG).show()
            return
        }

        val rolesClaves = arrayOf("PROPIETARIO", "VENDEDOR", "BODEGUERO")
        val rolesEtiquetas = arrayOf(
            "Propietario / Administrador (Control total)",
            "Vendedor (Solo facturación)",
            "Bodeguero (Solo inventario)"
        )

        val rolActual = miembro.rol?.trim()?.uppercase() ?: ""

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setTitle("Modificar Rol")
            .setSingleChoiceItems(rolesEtiquetas, rolesClaves.indexOf(rolActual)) { dialog, which ->
                dialog.dismiss()
                val nuevoRol = rolesClaves[which]

                if (nuevoRol.equals(rolActual, ignoreCase = true)) {
                    Toast.makeText(this, "El usuario ya tiene este rol asignado.", Toast.LENGTH_SHORT).show()
                } else {
                    ejecutarCambioRol(miembroId, nuevoRol)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarCambioRol(miembroId: Long, nuevoRol: String) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrEmpty()) {
            Toast.makeText(this, "Sesión no válida.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.cambiarRolMiembro(authHeader, negocioId, miembroId, nuevoRol)
                if (response.isSuccessful) {
                    Toast.makeText(this@Mi_equipo, "¡Actualizado! El rol del colaborador ha sido modificado.", Toast.LENGTH_SHORT).show()
                    cargarEquipo(negocioId)
                } else {
                    Log.e("Equipo", "Error cambiarRolMiembro (${response.code()})")
                    Toast.makeText(this@Mi_equipo, "Oops... Hubo un error al cambiar el rol.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is java.io.EOFException || e.cause is java.io.EOFException || e.message?.contains("End of input") == true) {
                    Toast.makeText(this@Mi_equipo, "¡Actualizado! El rol del colaborador ha sido modificado.", Toast.LENGTH_SHORT).show()
                    cargarEquipo(negocioId)
                } else {
                    Log.e("Equipo", "Excepción cambiarRolMiembro", e)
                    Toast.makeText(this@Mi_equipo, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun regenerarCodigo() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        if (negocioId == -1L) return

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Generar nuevo código?")
            .setMessage("El código actual dejará de funcionar inmediatamente.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, generar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.regenerarCodigoInvitacion(authHeader, negocioId)
                        if (response.isSuccessful && response.body() != null) {
                            val res = response.body()!!
                            val nuevoCodigo = res.codigoInvitacion ?: res.codigo ?: "NO-DISPONIBLE"
                            tvCodigoAcceso.text = nuevoCodigo
                            Toast.makeText(this@Mi_equipo, "¡Actualizado! Se ha generado un nuevo código de acceso.", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("Equipo", "Error regenerarCodigo (${response.code()})")
                            Toast.makeText(this@Mi_equipo, "Error: No se pudo generar el nuevo código.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("Equipo", "Excepción regenerarCodigo", e)
                        Toast.makeText(this@Mi_equipo, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun obtenerTimestampFecha(fechaStr: String?): Long {
        if (fechaStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val fechaIso = fechaStr.replace(" ", "T")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(fechaIso)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun copiarCodigo() {
        val codigo = tvCodigoAcceso.text.toString()
        if (codigo.isNotBlank() && !codigo.contains("ERROR") && codigo != "NO-DISPONIBLE" && codigo != "Cargando...") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Código de Acceso", codigo)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "¡Código copiado al portapapeles!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error: No hay un código válido para copiar.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarAvatarUsuarioLogueado() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful && response.body() != null) {
                    val usuario = response.body()!!
                    val nombre = "${usuario.primerNombre.orEmpty()} ${usuario.apellidoPaterno.orEmpty()}".trim()
                    tvHeaderIniciales.text = obtenerInicialesUsuario(nombre)

                    if (!usuario.fotoPerfil.isNullOrBlank()) {
                        ivHeaderAvatar.visibility = View.VISIBLE
                        Glide.with(this@Mi_equipo)
                            .load(usuario.fotoPerfil)
                            .circleCrop()
                            .into(ivHeaderAvatar)
                    }
                }
            } catch (e: Exception) {
                Log.e("Equipo", "Error al cargar avatar", e)
            }
        }
    }

    private fun obtenerInicialesUsuario(nombreCompleto: String): String {
        val partes = nombreCompleto.trim().split(" ").filter { it.isNotBlank() }
        return when {
            partes.isEmpty() -> "US"
            partes.size == 1 -> partes[0].take(2).uppercase()
            else -> (partes[0].take(1) + partes[1].take(1)).uppercase()
        }
    }

    private fun mostrarPopupAlertas(anchorView: View) {
        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.dialog_alertas_vencimiento, null)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 20f

        val tvBadgeAlertas = popupView.findViewById<TextView>(R.id.tvBadgeAlertas)
        val tvMensajeAlerta = popupView.findViewById<TextView>(R.id.tvMensajeAlerta)
        val btnIrInventario = popupView.findViewById<TextView>(R.id.btnIrInventario)

        tvBadgeAlertas.text = cantidadAlertasVencimiento.toString()
        tvMensajeAlerta.text = if (cantidadAlertasVencimiento > 0) {
            "Tienes $cantidadAlertasVencimiento producto(s) próximos a vencer o vencidos."
        } else {
            "Todo tu inventario está en óptimas condiciones."
        }

        btnIrInventario.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent().setClassName(this, "com.example.movildilo.ui.propietario.InventarioBodegasActivity")
            startActivity(intent)
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val offsetX = -popupView.measuredWidth + anchorView.width
        val offsetY = 12
        popupWindow.showAsDropDown(anchorView, offsetX, offsetY)
    }
}