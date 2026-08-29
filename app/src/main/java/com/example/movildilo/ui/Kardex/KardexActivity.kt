package com.example.movildilo.ui.Kardex

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.BodegaDto
import com.example.movildilo.data.model.dto.inventario.InventarioResponseDto
import com.example.movildilo.data.model.dto.inventario.KardexMovimientoDto
import com.example.movildilo.data.model.dto.inventario.NuevoAjusteRequestDto
import com.example.movildilo.data.model.dto.inventario.ProductoDto
import com.example.movildilo.data.model.dto.inventario.toProductoDtoList
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.adapters.KardexAdapter
import com.example.movildilo.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker // <-- IMPORTA PARA RANGO DE FECHAS
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat // <-- IMPORTA
import java.util.Date // <-- IMPORTA
import java.util.Locale
import java.util.TimeZone // <-- IMPORTA

class KardexActivity : AppCompatActivity() {

    private lateinit var btnRegresarKardex: View
    private lateinit var btnAbrirNuevoAjuste: MaterialButton
    private lateinit var etBuscarKardex: EditText
    private lateinit var spinnerFiltroTipo: MaterialAutoCompleteTextView
    private lateinit var spinnerFiltroBodegaKardex: MaterialAutoCompleteTextView
    private lateinit var rvKardex: RecyclerView
    private lateinit var layoutLoadingKardex: View
    private lateinit var swipeRefreshLayoutKardex: SwipeRefreshLayout

    private lateinit var etRangoFechas: EditText
    private lateinit var btnLimpiarFechas: View

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: KardexAdapter

    private var negocioId: Long = -1L
    private val listaOriginal = mutableListOf<KardexMovimientoDto>()

    private val listaProductosBD = mutableListOf<ProductoDto>()
    private val listaBodegasBD = mutableListOf<BodegaDto>()
    private val inventarioTotal = mutableListOf<InventarioResponseDto>()

    private var fechaInicioFiltro: String? = null
    private var fechaFinFiltro: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kardex_movimientos)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        setupFiltros()

        btnRegresarKardex.setOnClickListener { finish() }

        swipeRefreshLayoutKardex.setOnRefreshListener {
            if (negocioId > 0L) {
                cargarCatalogosAuxiliares {
                    cargarKardexDesdeApi {
                        swipeRefreshLayoutKardex.isRefreshing = false
                    }
                }
            } else {
                swipeRefreshLayoutKardex.isRefreshing = false
                Toast.makeText(this, "No se encontró un negocio activo.", Toast.LENGTH_SHORT).show()
            }
        }

        btnAbrirNuevoAjuste.setOnClickListener {
            if (negocioId <= 0L) {
                Toast.makeText(this, "No se puede realizar un ajuste sin un negocio activo.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (listaBodegasBD.isEmpty()) {
                Toast.makeText(this, "No hay bodegas registradas para realizar ajustes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (listaProductosBD.isEmpty()) {
                Toast.makeText(this, "No hay productos registrados para realizar ajustes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            abrirModalAjusteManual()
        }

        if (negocioId > 0L) {
            cargarCatalogosAuxiliares()
            cargarKardexDesdeApi()
        } else {
            Toast.makeText(this, "No se encontró un negocio activo.", Toast.LENGTH_SHORT).show()
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
            contextoNegocioTexto = "Estás visualizando ...",
            alertasTexto = "Sin alertas recientes.",
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun initViews() {
        btnRegresarKardex = findViewById(R.id.btnRegresarKardex)
        btnAbrirNuevoAjuste = findViewById(R.id.btnAbrirNuevoAjuste)
        etBuscarKardex = findViewById(R.id.etBuscarKardex)
        spinnerFiltroTipo = findViewById(R.id.spinnerFiltroTipo)
        spinnerFiltroBodegaKardex = findViewById(R.id.spinnerFiltroBodegaKardex)
        rvKardex = findViewById(R.id.rvKardex)
        layoutLoadingKardex = findViewById(R.id.layoutLoadingKardex)
        swipeRefreshLayoutKardex = findViewById(R.id.swipeRefreshLayoutKardex)

        etRangoFechas = findViewById(R.id.etRangoFechas)
        btnLimpiarFechas = findViewById(R.id.btnLimpiarFechas)
    }

    private fun setupRecyclerView() {
        adapter = KardexAdapter(mutableListOf())
        rvKardex.layoutManager = LinearLayoutManager(this)
        rvKardex.adapter = adapter
    }

    private fun setupFiltros() {
        poblarSpinnerTipos()
        poblarSpinnerBodegas()

        spinnerFiltroTipo.setOnClickListener {
            (spinnerFiltroTipo.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }
        spinnerFiltroBodegaKardex.setOnClickListener {
            (spinnerFiltroBodegaKardex.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

        spinnerFiltroTipo.setOnItemClickListener { _, _, _, _ -> aplicarFiltros() }
        spinnerFiltroBodegaKardex.setOnItemClickListener { _, _, _, _ -> aplicarFiltros() }

        etBuscarKardex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etRangoFechas.setOnClickListener {
            abrirSelectorFechas()
        }

        btnLimpiarFechas.setOnClickListener {
            fechaInicioFiltro = null
            fechaFinFiltro = null
            etRangoFechas.setText("")
            btnLimpiarFechas.visibility = View.GONE
            aplicarFiltros()
        }
    }

    private fun abrirSelectorFechas() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        builder.setTitleText("Seleccionar rango de fechas")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDate = Date(selection.second)

            val sdfISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            sdfISO.timeZone = TimeZone.getTimeZone("UTC")
            sdfDisplay.timeZone = TimeZone.getTimeZone("UTC")

            fechaInicioFiltro = sdfISO.format(startDate)
            fechaFinFiltro = sdfISO.format(endDate)

            etRangoFechas.setText("${sdfDisplay.format(startDate)} - ${sdfDisplay.format(endDate)}")
            btnLimpiarFechas.visibility = View.VISIBLE

            aplicarFiltros()
        }
        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun poblarSpinnerTipos() {
        val tiposCategorias = mutableListOf("Todos", "INGRESO", "EGRESO", "TRANSFERENCIA")

        val tiposRealesBD = listaOriginal
            .mapNotNull { it.tipo?.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        tiposRealesBD.forEach { tipoBD ->
            if (tiposCategorias.none { it.equals(tipoBD, ignoreCase = true) }) {
                tiposCategorias.add(tipoBD)
            }
        }

        val adapterTipos = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            tiposCategorias
        )
        spinnerFiltroTipo.setAdapter(adapterTipos)

        val textoActual = spinnerFiltroTipo.text.toString().trim()
        if (textoActual.isEmpty() || textoActual !in tiposCategorias) {
            spinnerFiltroTipo.setText("Todos", false)
        }
    }

    private fun poblarSpinnerBodegas() {
        val bodegas = mutableListOf("Todas")
        val nombresBodegas = listaBodegasBD
            .mapNotNull { it.nombre?.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        bodegas.addAll(nombresBodegas)

        val adapterBodegas = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            bodegas
        )
        spinnerFiltroBodegaKardex.setAdapter(adapterBodegas)

        val textoActual = spinnerFiltroBodegaKardex.text.toString().trim()
        if (textoActual.isEmpty() || textoActual !in bodegas) {
            spinnerFiltroBodegaKardex.setText("Todas", false)
        }
    }

    private fun cargarCatalogosAuxiliares(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            onComplete?.invoke()
            return
        }

        lifecycleScope.launch {
            try {
                val (respProductos, respBodegas, respInventario) = withContext(Dispatchers.IO) {
                    val p = RetrofitClient.apiService.getCatalogo(authHeader, negocioId)
                    val b = RetrofitClient.apiService.getBodegas(authHeader, negocioId)
                    val i = RetrofitClient.apiService.getInventario(authHeader, negocioId)
                    Triple(p, b, i)
                }

                if (respProductos.isSuccessful) {
                    listaProductosBD.clear()
                    listaProductosBD.addAll(respProductos.body()?.toProductoDtoList() ?: emptyList())
                }

                if (respBodegas.isSuccessful) {
                    listaBodegasBD.clear()
                    listaBodegasBD.addAll(respBodegas.body() ?: emptyList())
                    poblarSpinnerBodegas()
                }

                if (respInventario.isSuccessful) {
                    inventarioTotal.clear()
                    inventarioTotal.addAll(respInventario.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@KardexActivity,
                    "Error al cargar catálogos auxiliares",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun cargarKardexDesdeApi(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrEmpty()) {
            onComplete?.invoke()
            mostrarAlertaSesionExpirada()
            return
        }

        if (!swipeRefreshLayoutKardex.isRefreshing) {
            mostrarCargando(true)
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getKardexMovimientos(authHeader, negocioId)
                }

                mostrarCargando(false)

                if (response.isSuccessful) {
                    listaOriginal.clear()
                    val datos = response.body()

                    if (!datos.isNullOrEmpty()) {
                        listaOriginal.addAll(datos)
                    } else {
                        Toast.makeText(
                            this@KardexActivity,
                            "No se encontraron movimientos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    poblarSpinnerTipos()
                    aplicarFiltros()
                } else if (response.code() == 401) {
                    mostrarAlertaSesionExpirada()
                } else {
                    Toast.makeText(
                        this@KardexActivity,
                        "Error del servidor (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: JsonSyntaxException) {
                mostrarCargando(false)
                Toast.makeText(
                    this@KardexActivity,
                    "Error de formato: El servidor envió una respuesta inesperada",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                mostrarCargando(false)
                Toast.makeText(
                    this@KardexActivity,
                    "Error de conexión: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun aplicarFiltros() {
        val textoBusqueda = etBuscarKardex.text.toString().trim().lowercase(Locale.ROOT)
        val tipoSeleccionado = spinnerFiltroTipo.text.toString().trim()
        val bodegaSeleccionada = spinnerFiltroBodegaKardex.text.toString().trim()

        val resultado = listaOriginal.filter { k ->
            val tipoRaw = (k.tipo ?: "").trim().uppercase(Locale.ROOT)
            val cant = k.cantidad ?: 0

            val coincideTipo = when {
                tipoSeleccionado.isEmpty() ||
                        tipoSeleccionado.isBlank() ||
                        tipoSeleccionado.equals("Todos", ignoreCase = true) -> true

                tipoSeleccionado.equals("INGRESO", ignoreCase = true) -> {
                    tipoRaw.contains("INGRESO") ||
                            tipoRaw.contains("ENTRADA") ||
                            tipoRaw.contains("COMPRA") ||
                            (tipoRaw.contains("AJUSTE") && cant > 0)
                }

                tipoSeleccionado.equals("EGRESO", ignoreCase = true) -> {
                    tipoRaw.contains("EGRESO") ||
                            tipoRaw.contains("SALIDA") ||
                            tipoRaw.contains("VENTA") ||
                            (tipoRaw.contains("AJUSTE") && cant < 0)
                }

                tipoSeleccionado.equals("TRANSFERENCIA", ignoreCase = true) -> {
                    tipoRaw.contains("TRANSFER") ||
                            tipoRaw.contains("TRASP") ||
                            tipoRaw.contains("TRASLADO")
                }

                else -> tipoRaw.isEmpty() ||
                        tipoRaw.equals(tipoSeleccionado, ignoreCase = true) ||
                        tipoRaw.contains(tipoSeleccionado, ignoreCase = true)
            }

            val origen = k.bodegaOrigenNombre?.trim() ?: ""
            val destino = k.bodegaDestinoNombre?.trim() ?: ""

            val coincideBodega = bodegaSeleccionada.isEmpty() ||
                    bodegaSeleccionada.isBlank() ||
                    bodegaSeleccionada.equals("Todas", ignoreCase = true) ||
                    origen.equals(bodegaSeleccionada, ignoreCase = true) ||
                    destino.equals(bodegaSeleccionada, ignoreCase = true) ||
                    origen.contains(bodegaSeleccionada, ignoreCase = true) ||
                    destino.contains(bodegaSeleccionada, ignoreCase = true)

            val prod = (k.productoNombre ?: "").lowercase(Locale.ROOT)
            val lote = (k.numeroLote ?: "").lowercase(Locale.ROOT)
            val doc = (k.documentoReferencia ?: "").lowercase(Locale.ROOT)
            val mot = (k.motivo ?: "").lowercase(Locale.ROOT)
            val user = (k.usuarioResponsableNombre ?: "").lowercase(Locale.ROOT)

            val coincideTexto = textoBusqueda.isEmpty() ||
                    prod.contains(textoBusqueda) ||
                    lote.contains(textoBusqueda) ||
                    doc.contains(textoBusqueda) ||
                    mot.contains(textoBusqueda) ||
                    user.contains(textoBusqueda)

            val fechaTxRaw = k.fechaTransaccion ?: ""
            val coincideFecha = if (fechaInicioFiltro != null && fechaFinFiltro != null) {
                val fechaSoloDia = fechaTxRaw.take(10)
                if (fechaSoloDia.length == 10) {
                    fechaSoloDia in fechaInicioFiltro!!..fechaFinFiltro!!
                } else {
                    true
                }
            } else {
                true
            }

            coincideTipo && coincideBodega && coincideTexto && coincideFecha
        }

        adapter.actualizarLista(resultado)
    }

    private fun abrirModalAjusteManual() {
        val dialog = AjusteManualDialog(
            listaProductosBD = listaProductosBD,
            listaBodegasBD = listaBodegasBD,
            inventarioTotal = inventarioTotal,
            onAjusteRegistradoListener = { dto ->
                registrarAjusteEnBackend(dto)
            }
        )
        dialog.show(supportFragmentManager, "AjusteManualDialog")
    }

    private fun validarAjusteManual(dto: NuevoAjusteRequestDto): Boolean {
        if (negocioId <= 0L) {
            Toast.makeText(this, "No se detectó un negocio válido para registrar el ajuste.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (dto.productoId == null || dto.productoId <= 0L) {
            Toast.makeText(this, "Debes seleccionar un producto válido.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (dto.tipo == "TRANSFERENCIA") {
            if (dto.bodegaOrigenId == null || dto.bodegaDestinoId == null) {
                Toast.makeText(this, "La transferencia requiere bodega de origen y de destino.", Toast.LENGTH_SHORT).show()
                return false
            }
            if (dto.bodegaOrigenId == dto.bodegaDestinoId) {
                Toast.makeText(this, "No puedes transferir a la misma bodega.", Toast.LENGTH_SHORT).show()
                return false
            }
        } else {
            val bodegaId = dto.bodegaOrigenId ?: dto.bodegaDestinoId
            if (bodegaId == null || bodegaId <= 0L) {
                Toast.makeText(this, "Debes seleccionar una bodega válida.", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        if (dto.cantidad == null || dto.cantidad == 0) {
            Toast.makeText(this, "La cantidad del ajuste no puede ser cero.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (dto.motivo.isNullOrBlank()) {
            Toast.makeText(this, "Debes ingresar un motivo para el ajuste.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (dto.motivo.trim().length < 3) {
            Toast.makeText(this, "El motivo debe tener al menos 3 caracteres.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registrarAjusteEnBackend(dto: NuevoAjusteRequestDto) {
        if (!validarAjusteManual(dto)) {
            return
        }

        val authHeader = sessionManager.getAuthHeader()
        val emailUsuario = sessionManager.getUserEmail() ?: ""

        if (authHeader.isNullOrEmpty()) {
            mostrarAlertaSesionExpirada()
            return
        }

        mostrarCargando(true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.registrarAjusteManual(
                        authHeader = authHeader,
                        negocioId = negocioId,
                        emailUsuario = emailUsuario,
                        dto = dto
                    )
                }

                mostrarCargando(false)

                if (response.isSuccessful) {
                    Toast.makeText(
                        this@KardexActivity,
                        "Ajuste registrado con éxito",
                        Toast.LENGTH_SHORT
                    ).show()
                    cargarKardexDesdeApi()
                    cargarCatalogosAuxiliares()
                } else if (response.code() == 401) {
                    mostrarAlertaSesionExpirada()
                } else {
                    Toast.makeText(
                        this@KardexActivity,
                        "Error al registrar ajuste (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                mostrarCargando(false)
                Toast.makeText(
                    this@KardexActivity,
                    "Error de red: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        layoutLoadingKardex.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarAlertaSesionExpirada() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sesión Expirada")
            .setMessage("Tu sesión ha caducado. Vuelve a iniciar sesión.")
            .setPositiveButton("Aceptar") { _, _ ->
                sessionManager.clearSession()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}