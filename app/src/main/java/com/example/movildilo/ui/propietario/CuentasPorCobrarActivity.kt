package com.example.movildilo.ui.propietario

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.facturacion.CuentaPorCobrarResponseDto
import com.example.movildilo.data.model.dto.facturacion.CuotaDto
import com.example.movildilo.data.model.dto.facturacion.PagoRequestDto
import com.example.movildilo.data.model.dto.usuarios.ClienteAgrupado
import com.example.movildilo.data.model.dto.usuarios.CreditoClienteResumenDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.utils.Constants
import com.example.movildilo.ui.adapters.ClientesAgrupadosAdapter
import com.example.movildilo.ui.adapters.CuentasPorCobrarAdapter
import com.example.movildilo.ui.adapters.FacturasClienteModalAdapter
import com.example.movildilo.ui.adapters.HistorialAbonosAdapter
import com.example.movildilo.ui.adapters.ProductosFacturaAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pantalla "Cuentas por Cobrar".
 * Muestra dos vistas (pestañas):
 *  - Tab 0: lista general de cuentas/facturas con saldo.
 *  - Tab 1: directorio de clientes agrupados con su deuda total.
 *
 * También permite abrir un panel modal ("panel de cobranza") por cada cuenta,
 * donde se puede: registrar un abono, ver el historial de pagos, y ver los
 * productos comprados en esa factura.
 */
class CuentasPorCobrarActivity : AppCompatActivity() {

    // ---------- Dependencias / estado de sesión ----------
    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = 0

    // ---------- Vistas del header (KPIs) ----------
    private lateinit var btnRegresar: ImageButton
    private lateinit var tvTotalPorCobrar: TextView
    private lateinit var tvTotalAbonado: TextView
    private lateinit var tvCuentasVencidas: TextView

    // ---------- Vistas de filtros y tabs principales ----------
    private lateinit var tabLayoutPrincipal: TabLayout
    private lateinit var etBuscar: TextInputEditText
    private lateinit var containerFiltrosFacturas: LinearLayout
    private lateinit var chipTodas: TextView
    private lateinit var chipPendiente: TextView
    private lateinit var chipVencida: TextView
    private lateinit var chipPagada: TextView

    private lateinit var etRangoFechas: TextInputEditText
    private lateinit var btnLimpiarFechas: ImageButton
    private lateinit var actvTipoFiltroFecha: MaterialAutoCompleteTextView

    // ---------- RecyclerViews y estados vacíos/carga ----------
    private lateinit var rvCuentasGeneral: RecyclerView
    private lateinit var rvClientesDirectorio: RecyclerView
    private lateinit var layoutSinResultados: LinearLayout
    private lateinit var layoutVacio: LinearLayout
    private lateinit var layoutLoading: FrameLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var cuentasBase: List<CuentaPorCobrarResponseDto> = emptyList()
    private var clientesAgrupados: List<ClienteAgrupado> = emptyList()

    private lateinit var adapterCuentas: CuentasPorCobrarAdapter
    private lateinit var adapterClientes: ClientesAgrupadosAdapter

    private var estadoFiltro: String = "TODAS"
    private var fechaDesdeSel: String = ""
    private var fechaHastaSel: String = ""

    private var tipoFiltroFecha: String = "vencimiento"
    private val opcionesFiltroFecha = listOf("Fecha de Vencimiento", "Fecha de Creación (Emisión)")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private data class ResumenProductos(
        val detalles: List<com.example.movildilo.data.model.dto.facturacion.DetalleFacturaResponseDto>,
        val descuento: Double,
        val subtotal: Double,
        val iva: Double,
        val total: Double
    )

    private var facturasCache: List<com.example.movildilo.data.model.dto.facturacion.FacturaResponseDto>? = null

    private val cacheProductosPorFactura = mutableMapOf<Long, ResumenProductos>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Si el token expiró, se redirige a login y se corta la ejecución
        if (sessionManager.checkAndRedirectIfExpired(this)) {
            return
        }

        setContentView(R.layout.activity_cuentas_por_cobrar)

        negocioId = sessionManager.getNegocioId()

        initViews()
        setupListeners()
        setupTabs()
        cargarCuentas()

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
            contextoNegocioTexto = "Estás visualizando las cuentas por cobrar.",
            alertasTexto = "Sin alertas recientes.",
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        tvTotalPorCobrar = findViewById(R.id.tvTotalPorCobrar)
        tvTotalAbonado = findViewById(R.id.tvTotalAbonado)
        tvCuentasVencidas = findViewById(R.id.tvCuentasVencidas)

        tabLayoutPrincipal = findViewById(R.id.tabLayoutPrincipal)
        etBuscar = findViewById(R.id.etBuscar)
        containerFiltrosFacturas = findViewById(R.id.containerFiltrosFacturas)

        chipTodas = findViewById(R.id.chipTodas)
        chipPendiente = findViewById(R.id.chipPendiente)
        chipVencida = findViewById(R.id.chipVencida)
        chipPagada = findViewById(R.id.chipPagada)

        etRangoFechas = findViewById(R.id.etRangoFechas)
        btnLimpiarFechas = findViewById(R.id.btnLimpiarFechas)
        actvTipoFiltroFecha = findViewById(R.id.actvTipoFiltroFecha)

        // Dropdown con las 2 opciones de "tipo de fecha" a filtrar
        val adapterTipoFecha = ArrayAdapter(this, android.R.layout.simple_list_item_1, opcionesFiltroFecha)
        actvTipoFiltroFecha.setAdapter(adapterTipoFecha)
        actvTipoFiltroFecha.setText(opcionesFiltroFecha[0], false)

        rvCuentasGeneral = findViewById(R.id.rvCuentasGeneral)
        rvClientesDirectorio = findViewById(R.id.rvClientesDirectorio)
        layoutSinResultados = findViewById(R.id.layoutSinResultados)
        layoutVacio = findViewById(R.id.layoutVacio)
        layoutLoading = findViewById(R.id.layoutLoading)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        rvCuentasGeneral.layoutManager = LinearLayoutManager(this)
        rvClientesDirectorio.layoutManager = LinearLayoutManager(this)

        adapterCuentas = CuentasPorCobrarAdapter(
            listaCuentas = emptyList(),
            onAbonarClick = { cuenta -> abrirPanelCobranza(cuenta) },
            onAbonarCuotaClick = { cuenta, cuota -> abrirPanelCobranza(cuenta, cuota) },
            onRecordatorioClick = { cuenta -> confirmarYEnviarRecordatorio(cuenta) }
        )
        rvCuentasGeneral.adapter = adapterCuentas

        // Adapter del directorio de clientes (Tab 1)
        adapterClientes = ClientesAgrupadosAdapter(emptyList()) { cliente ->
            mostrarModalFacturasCliente(cliente)
        }
        rvClientesDirectorio.adapter = adapterClientes
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }


        swipeRefreshLayout.setOnRefreshListener {
            facturasCache = null
            cacheProductosPorFactura.clear()

            cargarCuentas {
                swipeRefreshLayout.isRefreshing = false
            }
        }


        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { aplicarFiltros() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        chipTodas.setOnClickListener { cambiarFiltroEstado("TODAS", chipTodas) }
        chipPendiente.setOnClickListener { cambiarFiltroEstado("PENDIENTE", chipPendiente) }
        chipVencida.setOnClickListener { cambiarFiltroEstado("VENCIDA", chipVencida) }
        chipPagada.setOnClickListener { cambiarFiltroEstado("PAGADA", chipPagada) }

        etRangoFechas.setOnClickListener {
            abrirSelectorFechas()
        }

        btnLimpiarFechas.setOnClickListener {
            fechaDesdeSel = ""
            fechaHastaSel = ""
            etRangoFechas.setText("Rango")
            btnLimpiarFechas.visibility = View.GONE
            aplicarFiltros()
        }

        // Cambia si se filtra por fecha de vencimiento o de emisión
        actvTipoFiltroFecha.setOnItemClickListener { _, _, position, _ ->
            tipoFiltroFecha = if (position == 1) "emision" else "vencimiento"
            etRangoFechas.hint = if (tipoFiltroFecha == "emision") "Emisión" else "Vencimiento"
            aplicarFiltros()
        }
    }

    private fun abrirSelectorFechas() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        val tituloCampo = if (tipoFiltroFecha == "emision") "de emisión" else "de vencimiento"
        builder.setTitleText("Seleccionar rango $tituloCampo")
        val picker = builder.build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDate = Date(selection.second)

            val sdfISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // El picker trabaja en UTC, por eso forzamos ese timezone al formatear
            sdfISO.timeZone = TimeZone.getTimeZone("UTC")
            sdfDisplay.timeZone = TimeZone.getTimeZone("UTC")

            fechaDesdeSel = sdfISO.format(startDate)
            fechaHastaSel = sdfISO.format(endDate)

            etRangoFechas.setText("${sdfDisplay.format(startDate)} - ${sdfDisplay.format(endDate)}")
            btnLimpiarFechas.visibility = View.VISIBLE

            aplicarFiltros()
        }
        picker.show(supportFragmentManager, "DATE_PICKER")
    }


    private fun setupTabs() {
        tabLayoutPrincipal.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    containerFiltrosFacturas.visibility = View.VISIBLE
                    rvCuentasGeneral.visibility = View.VISIBLE
                    rvClientesDirectorio.visibility = View.GONE
                } else {
                    containerFiltrosFacturas.visibility = View.GONE
                    rvCuentasGeneral.visibility = View.GONE
                    rvClientesDirectorio.visibility = View.VISIBLE
                }
                aplicarFiltros()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun cambiarFiltroEstado(nuevoEstado: String, chipSeleccionado: TextView) {
        estadoFiltro = nuevoEstado
        val chips = listOf(chipTodas, chipPendiente, chipVencida, chipPagada)
        chips.forEach {
            it.setBackgroundResource(R.drawable.bg_input_field)
            it.setTextColor(Color.parseColor("#64748B"))
        }
        chipSeleccionado.setBackgroundColor(Color.parseColor("#EA580C"))
        chipSeleccionado.setTextColor(Color.WHITE)
        aplicarFiltros()
    }

    private fun cargarCuentas(onComplete: (() -> Unit)? = null) {
        val token = sessionManager.getAuthHeader() ?: run {
            onComplete?.invoke()
            return
        }

        if (!swipeRefreshLayout.isRefreshing) {
            layoutLoading.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCuentasPorCobrar(token, negocioId)
                layoutLoading.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    cuentasBase = response.body()!!.filter { cuenta ->
                        val nombre = obtenerNombreCliente(cuenta)
                        !nombre.lowercase().contains("consumidor final")
                    }

                    if (cuentasBase.isEmpty()) {
                        layoutVacio.visibility = View.VISIBLE
                    } else {
                        layoutVacio.visibility = View.GONE
                        calcularKPIs()
                        agruparClientes()
                        aplicarFiltros()
                    }
                } else {
                    layoutVacio.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@CuentasPorCobrarActivity, "Error de red al cargar datos", Toast.LENGTH_SHORT).show()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun cargarCuentasYResaltarUltima(idReciente: Long) {
        val token = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCuentasPorCobrar(token, negocioId)
                if (response.isSuccessful && response.body() != null) {
                    val filtradas = response.body()!!.filter { cuenta ->
                        val nombre = obtenerNombreCliente(cuenta)
                        !nombre.lowercase().contains("consumidor final")
                    }

                    cuentasBase = filtradas.sortedByDescending { it.id == idReciente }

                    calcularKPIs()
                    agruparClientes()
                    aplicarFiltros()

                    rvCuentasGeneral.scrollToPosition(0)
                }
            } catch (_: Exception) {}
        }
    }

    private fun calcularKPIs() {
        var totalPendiente = 0.0
        var totalRecuperado = 0.0
        var vencidasContador = 0
        val hoyStr = dateFormat.format(Date())

        cuentasBase.forEach { item ->
            val saldo = item.saldoPendiente ?: 0.0
            val total = item.montoTotal ?: 0.0
            totalPendiente += saldo
            totalRecuperado += (total - saldo) // lo que ya se pagó = total - lo que falta

            val vencimiento = item.fechaVencimiento ?: ""
            // Comparación de Strings en formato yyyy-MM-dd funciona igual que comparar fechas
            if (saldo > 0 && vencimiento.isNotEmpty() && vencimiento < hoyStr) {
                vencidasContador++
            }
        }

        tvTotalPorCobrar.text = String.format(Locale.US, "$%.2f", totalPendiente)
        tvTotalAbonado.text = String.format(Locale.US, "$%.2f", totalRecuperado)
        tvCuentasVencidas.text = vencidasContador.toString()
    }

    private fun agruparClientes() {
        val mapa = mutableMapOf<String, ClienteAgrupado>()

        cuentasBase.forEach { cuenta ->
            val nombre = obtenerNombreCliente(cuenta)
            val ident = cuenta.factura?.cliente?.razonSocial ?: ""
            val key = "$nombre-$ident" // clave única por cliente

            if (!mapa.containsKey(key)) {
                mapa[key] = ClienteAgrupado(
                    nombre = nombre,
                    identificacion = ident,
                    totalDeuda = 0.0,
                    cuentasPendientes = 0,
                    cuentas = mutableListOf<CreditoClienteResumenDto>()
                )
            }

            val itemGroup = mapa[key]!!
            val saldo = cuenta.saldoPendiente ?: 0.0


            val creditoItem = CreditoClienteResumenDto(
                id = cuenta.id,
                factura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "N/A",
                montoTotal = cuenta.montoTotal ?: 0.0,
                saldoPendiente = saldo,
                fechaVencimiento = cuenta.fechaVencimiento ?: "",
                estado = cuenta.estado ?: if (saldo > 0) "PENDIENTE" else "PAGADA"
            )

            (itemGroup.cuentas as MutableList<CreditoClienteResumenDto>).add(creditoItem)

            if (saldo > 0) {
                itemGroup.totalDeuda += saldo
                itemGroup.cuentasPendientes++
            }
        }

        // Dentro de cada cliente: las facturas pagadas van al final, y entre
        // las pendientes se ordenan por fecha de vencimiento ascendente
        mapa.values.forEach { cliente ->
            @Suppress("UNCHECKED_CAST")
            val lista = (cliente.cuentas as MutableList<CreditoClienteResumenDto>)
            lista.sortWith(Comparator { a, b ->
                val aPagada = if (a.estado.equals("PAGADA", ignoreCase = true) || a.saldoPendiente <= 0) 1 else 0
                val bPagada = if (b.estado.equals("PAGADA", ignoreCase = true) || b.saldoPendiente <= 0) 1 else 0
                if (aPagada != bPagada) {
                    return@Comparator aPagada - bPagada
                }
                return@Comparator a.fechaVencimiento.compareTo(b.fechaVencimiento)
            })
        }

        // Orden general de clientes: primero los que deben (deuda mayor primero), luego alfabético
        clientesAgrupados = mapa.values.sortedWith(Comparator { c1, c2 ->
            val c1Pagado = if (c1.totalDeuda <= 0) 1 else 0
            val c2Pagado = if (c2.totalDeuda <= 0) 1 else 0
            if (c1Pagado != c2Pagado) {
                return@Comparator c1Pagado - c2Pagado
            }
            if (c1.totalDeuda != c2.totalDeuda) {
                return@Comparator c2.totalDeuda.compareTo(c1.totalDeuda)
            }
            return@Comparator c1.nombre.compareTo(c2.nombre, ignoreCase = true)
        })
        adapterClientes.actualizarLista(clientesAgrupados)
    }

    private fun aplicarFiltros() {
        val query = etBuscar.text.toString().lowercase().trim()
        val hoyStr = dateFormat.format(Date())

        if (tabLayoutPrincipal.selectedTabPosition == 0) {
            // ---- Filtrado para la lista general de cuentas ----
            val filtradas = cuentasBase.filter { item ->
                val cliente = obtenerNombreCliente(item).lowercase()
                val numFactura = (item.numeroFactura ?: item.factura?.numeroFactura ?: "").lowercase()
                val coincideTexto = cliente.contains(query) || numFactura.contains(query)

                val saldo = item.saldoPendiente ?: 0.0
                val vencimiento = item.fechaVencimiento ?: ""

                val coincideEstado = when (estadoFiltro) {
                    "PENDIENTE" -> saldo > 0 && (vencimiento.isEmpty() || vencimiento >= hoyStr)
                    "VENCIDA" -> saldo > 0 && vencimiento.isNotEmpty() && vencimiento < hoyStr
                    "PAGADA" -> saldo <= 0 || item.estado.equals("PAGADA", true)
                    else -> true // "TODAS"
                }

                // Según el tipo de filtro de fecha elegido, comparamos vencimiento o emisión
                val fechaEvaluar = if (tipoFiltroFecha == "emision") obtenerFechaEmision(item) else vencimiento
                val fechaSoloDia = if (fechaEvaluar.length >= 10) fechaEvaluar.substring(0, 10) else fechaEvaluar

                val coincideFecha = if (fechaDesdeSel.isNotEmpty() && fechaHastaSel.isNotEmpty()) {
                    fechaSoloDia.isNotEmpty() && fechaSoloDia in fechaDesdeSel..fechaHastaSel
                } else {
                    true // no hay rango seleccionado -> no filtra por fecha
                }

                coincideTexto && coincideEstado && coincideFecha
            }.sortedWith(Comparator { a, b ->
                // Pagadas al final; entre las no pagadas, la más próxima a vencer primero
                val aPagada = if (a.estado.equals("PAGADA", ignoreCase = true) || (a.saldoPendiente ?: 0.0) <= 0.0) 1 else 0
                val bPagada = if (b.estado.equals("PAGADA", ignoreCase = true) || (b.saldoPendiente ?: 0.0) <= 0.0) 1 else 0
                if (aPagada != bPagada) {
                    return@Comparator aPagada - bPagada
                }

                val fechaA = a.fechaVencimiento ?: ""
                val fechaB = b.fechaVencimiento ?: ""
                return@Comparator fechaA.compareTo(fechaB)
            })

            adapterCuentas.actualizarLista(filtradas)
            layoutSinResultados.visibility = if (filtradas.isEmpty() && cuentasBase.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            // ---- Filtrado para el directorio de clientes (Tab 1) ----
            val clientesFiltrados = clientesAgrupados.filter {
                it.nombre.lowercase().contains(query) || it.identificacion.lowercase().contains(query)
            }
            adapterClientes.actualizarLista(clientesFiltrados)
            layoutSinResultados.visibility = if (clientesFiltrados.isEmpty() && clientesAgrupados.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun obtenerFechaEmision(item: CuentaPorCobrarResponseDto): String {
        return item.fechaEmision ?: item.factura?.fechaEmision ?: ""
    }

    private fun obtenerNombreCliente(item: CuentaPorCobrarResponseDto): String {
        return when {
            !item.clienteNombre.isNullOrEmpty() -> item.clienteNombre
            !item.factura?.cliente?.nombreCompleto.isNullOrEmpty() -> item.factura?.cliente?.nombreCompleto!!
            !item.factura?.cliente?.nombre.isNullOrEmpty() -> item.factura?.cliente?.nombre!!
            else -> "${item.factura?.cliente?.primerNombre ?: ""} ${item.factura?.cliente?.apellidoPaterno ?: ""}".trim()
        }
    }

    private fun mostrarModalFacturasCliente(cliente: ClienteAgrupado) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_facturas_cliente, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo = view.findViewById<TextView>(R.id.tvTituloFacturasCliente)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarModalFacturas)
        val rvFacturas = view.findViewById<RecyclerView>(R.id.rvFacturasDelCliente)

        // Armamos el título coloreando el nombre del cliente en naranja
        val idTexto = if (cliente.identificacion.isNotEmpty()) " (${cliente.identificacion})" else ""
        val tituloSpannable = SpannableStringBuilder("Facturas de: ${cliente.nombre}$idTexto")

        val startIndex = "Facturas de: ".length
        val endIndex = startIndex + cliente.nombre.length + idTexto.length
        tituloSpannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#EA580C")),
            startIndex,
            endIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvTitulo.text = tituloSpannable
        btnCerrar.setOnClickListener { dialog.dismiss() }

        @Suppress("UNCHECKED_CAST")
        val listaCreditos = cliente.cuentas as List<CreditoClienteResumenDto>

        rvFacturas.layoutManager = LinearLayoutManager(this)
        rvFacturas.adapter = FacturasClienteModalAdapter(
            lista = listaCreditos,
            onAbonarClick = { credito ->
                // Buscamos la cuenta "completa" original a partir del resumen, y abrimos el panel
                dialog.dismiss()
                val cuentaOriginal = cuentasBase.find { it.id == credito.id }
                cuentaOriginal?.let { abrirPanelCobranza(it) }
            },
            onEmailClick = { credito ->
                val cuentaOriginal = cuentasBase.find { it.id == credito.id }
                cuentaOriginal?.let { confirmarYEnviarRecordatorio(it) }
            }
        )

        dialog.show()
    }

    private fun formatearFecha(fechaRaw: String?): String {
        if (fechaRaw.isNullOrBlank()) return "--"

        val formatosEntrada = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        val formatoSalida = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        for (formato in formatosEntrada) {
            try {
                val date = SimpleDateFormat(formato, Locale.getDefault()).parse(fechaRaw)
                if (date != null) {
                    return formatoSalida.format(date)
                }
            } catch (_: Exception) {
                // Si falla este formato, probamos el siguiente
            }
        }
        return fechaRaw
    }

    private fun confirmarYEnviarRecordatorio(cuenta: CuentaPorCobrarResponseDto) {
        val clienteNombre = obtenerNombreCliente(cuenta)
        val saldo = String.format(Locale.US, "$%.2f", cuenta.saldoPendiente ?: 0.0)
        val numFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "N/A"

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("¿Enviar Recordatorio?")
            .setMessage("Se enviará un correo a $clienteNombre recordándole su saldo pendiente de $saldo por la factura #$numFactura.")
            .setPositiveButton("Sí, enviar") { _, _ ->
                enviarCorreoRecordatorio(cuenta)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarCorreoRecordatorio(cuenta: CuentaPorCobrarResponseDto) {
        val token = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.enviarRecordatorioEmail(token, cuenta.id)
                layoutLoading.visibility = View.VISIBLE

                if (response.isSuccessful) {
                    Toast.makeText(this@CuentasPorCobrarActivity, "El recordatorio ha sido enviado exitosamente al correo del cliente.", Toast.LENGTH_SHORT).show()
                } else {
                    val errorStr = response.errorBody()?.string()?.replace("\"", "") ?: "Ocurrió un error al intentar enviar el correo."
                    Toast.makeText(this@CuentasPorCobrarActivity, errorStr, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@CuentasPorCobrarActivity, "Error de red al intentar enviar el correo", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun abrirPanelCobranza(cuenta: CuentaPorCobrarResponseDto, cuotaSeleccionada: CuotaDto? = null) {
        val dialog = Dialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_panel_cobranza, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvNumFactura = view.findViewById<TextView>(R.id.tvNumeroFacturaModal)
        val tvCliente = view.findViewById<TextView>(R.id.tvClienteModal)
        val tvSaldo = view.findViewById<TextView>(R.id.tvSaldoPendienteModal)
        val tabLayoutModal = view.findViewById<TabLayout>(R.id.tabLayoutModal)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrarPanel)
        val btnCancelar = view.findViewById<Button>(R.id.btnCancelarPago)


        val containerAbono = view.findViewById<LinearLayout>(R.id.containerRegistrarAbono)
        val containerHistorial = view.findViewById<LinearLayout>(R.id.containerHistorialPagos)
        val containerProductos = view.findViewById<LinearLayout>(R.id.containerProductosComprados)

        val layoutContenedorCuotas = view.findViewById<LinearLayout>(R.id.layoutContenedorCuotas)
        val rvHistorial = view.findViewById<RecyclerView>(R.id.rvHistorialAbonos)
        val layoutHistorialVacio = view.findViewById<LinearLayout>(R.id.layoutHistorialVacio)
        val rvProductos = view.findViewById<RecyclerView>(R.id.rvProductosComprados)
        val pbProductos = view.findViewById<ProgressBar>(R.id.pbProductos)
        val layoutResumenProductos = view.findViewById<LinearLayout>(R.id.layoutResumenProductos)
        val tvDescuentoFactura = view.findViewById<TextView>(R.id.tvDescuentoFactura)
        val tvSubtotalFacturaProductos = view.findViewById<TextView>(R.id.tvSubtotalFacturaProductos)
        val tvIvaFactura = view.findViewById<TextView>(R.id.tvIvaFactura)
        val tvTotalFacturaProductos = view.findViewById<TextView>(R.id.tvTotalFacturaProductos)

        rvHistorial.layoutManager = LinearLayoutManager(this)
        rvProductos.layoutManager = LinearLayoutManager(this)

        val todasLasCuotas = cuenta.cuotas ?: emptyList()

        // ---- Tab "Historial de pagos" ----
        val historialAbonosOrdenado = (cuenta.historialAbonos ?: emptyList())
            .sortedByDescending { it.fechaAbono ?: "" } // más reciente primero

        rvHistorial.adapter = HistorialAbonosAdapter(historialAbonosOrdenado)

        if (historialAbonosOrdenado.isEmpty()) {
            rvHistorial.visibility = View.GONE
            layoutHistorialVacio.visibility = View.VISIBLE
        } else {
            rvHistorial.visibility = View.VISIBLE
            layoutHistorialVacio.visibility = View.GONE
        }

        // ---- Tab "Registrar abono" ----
        val etMonto = view.findViewById<EditText>(R.id.etMontoAbono)
        val spMetodo = view.findViewById<Spinner>(R.id.spMetodoPago)
        val etRef = view.findViewById<EditText>(R.id.etReferencia)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        val saldoPendienteTotal = cuenta.saldoPendiente ?: 0.0
        val numFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "N/A"

        tvNumFactura.text = "#$numFactura"
        tvCliente.text = obtenerNombreCliente(cuenta)
        tvSaldo.text = String.format(Locale.US, "$%.2f", saldoPendienteTotal)

        // Solo mostramos como "seleccionables" las cuotas que aún no están pagadas
        val cuotasPendientes = todasLasCuotas.filter { cuota ->
            val saldoCuota = cuota.saldoPendienteCuota ?: 0.0
            val esPagada = cuota.estado?.equals("PAGADA", ignoreCase = true) == true
            !esPagada && saldoCuota > 0
        }.sortedBy { it.fechaVencimiento ?: "" }

        // Armamos dinámicamente las "tarjetas" seleccionables: Saldo Total + cada cuota pendiente
        layoutContenedorCuotas.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val listaTarjetas = mutableListOf<MaterialCardView>()

        // Tarjeta especial "Saldo Total" (liquidar toda la deuda de una vez)
        val cardTotalView = inflater.inflate(R.layout.item_tarjeta_cuota, layoutContenedorCuotas, false)
        val cardTotal = cardTotalView.findViewById<MaterialCardView>(R.id.cardCuotaItem)
        cardTotalView.findViewById<TextView>(R.id.tvTituloCuotaCard).text = "Saldo Total"
        cardTotalView.findViewById<TextView>(R.id.tvSubtituloCuotaCard).text = "Liquidar deuda"
        cardTotalView.findViewById<TextView>(R.id.tvMontoCuotaCard).text = String.format(Locale.US, "$%.2f", saldoPendienteTotal)

        listaTarjetas.add(cardTotal)
        layoutContenedorCuotas.addView(cardTotalView)

        cardTotal.setOnClickListener {
            seleccionarTarjetaCuotaConAnimacion(cardTotal, listaTarjetas, saldoPendienteTotal, etMonto)
        }

        // Una tarjeta por cada cuota pendiente
        cuotasPendientes.forEachIndexed { index, cuota ->
            val cardCuotaView = inflater.inflate(R.layout.item_tarjeta_cuota, layoutContenedorCuotas, false)
            val cardItem = cardCuotaView.findViewById<MaterialCardView>(R.id.cardCuotaItem)

            val numeroSecuencial = index + 1
            cardCuotaView.findViewById<TextView>(R.id.tvTituloCuotaCard).text = "Cuota #$numeroSecuencial"
            cardCuotaView.findViewById<TextView>(R.id.tvSubtituloCuotaCard).text = "Vence: ${formatearFecha(cuota.fechaVencimiento)}"

            val montoCuotaActual = cuota.saldoPendienteCuota ?: cuota.montoCuota ?: 0.0
            cardCuotaView.findViewById<TextView>(R.id.tvMontoCuotaCard).text = String.format(Locale.US, "$%.2f", montoCuotaActual)

            listaTarjetas.add(cardItem)
            layoutContenedorCuotas.addView(cardCuotaView)

            cardItem.setOnClickListener {
                seleccionarTarjetaCuotaConAnimacion(cardItem, listaTarjetas, montoCuotaActual, etMonto)
            }

            // Si el usuario venía de tocar "abonar esta cuota" en la lista general,
            // preseleccionamos automáticamente esa tarjeta
            if (cuotaSeleccionada != null && cuotaSeleccionada.id == cuota.id) {
                seleccionarTarjetaCuotaConAnimacion(cardItem, listaTarjetas, montoCuotaActual, etMonto)
            }
        }

        // Si no venía ninguna cuota preseleccionada, por defecto se selecciona "Saldo Total"
        if (cuotaSeleccionada == null) {
            seleccionarTarjetaCuotaConAnimacion(cardTotal, listaTarjetas, saldoPendienteTotal, etMonto)
        }

        // Spinner de método de pago
        val metodos = arrayOf("Efectivo", "Transferencia")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, metodos)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMetodo.adapter = adapterSpinner

        // El campo "Referencia" solo aplica (y se muestra) si el método es Transferencia
        spMetodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                etRef.visibility = if (metodos[pos] == "Transferencia") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnCerrar.setOnClickListener { dialog.dismiss() }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        // ---- Cambio entre las 3 sub-pestañas del modal ----
        tabLayoutModal.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                containerAbono.visibility = View.GONE
                containerHistorial.visibility = View.GONE
                containerProductos.visibility = View.GONE

                when (tab?.position) {
                    0 -> containerAbono.visibility = View.VISIBLE
                    1 -> containerHistorial.visibility = View.VISIBLE
                    2 -> {
                        containerProductos.visibility = View.VISIBLE
                        // Gracias a la caché (ver más abajo), si ya se precargó
                        // esto se pinta al instante sin volver a pedir datos.
                        cargarProductosFactura(
                            cuenta, rvProductos, pbProductos, layoutResumenProductos,
                            tvDescuentoFactura, tvSubtotalFacturaProductos, tvIvaFactura, tvTotalFacturaProductos
                        )
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // ---- Confirmar el registro del abono ----
        btnConfirmar.setOnClickListener {
            val monto = etMonto.text.toString().toDoubleOrNull()
            if (monto == null || monto <= 0 || monto > saldoPendienteTotal) {
                Toast.makeText(this, "Monto inválido o excede el saldo pendiente", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val token = sessionManager.getAuthHeader() ?: return@setOnClickListener
            val request = PagoRequestDto(montoPago = monto)
            layoutLoading.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.registrarPagoCuenta(token, cuenta.id, request)
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@CuentasPorCobrarActivity, "Abono registrado con éxito", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()

                        // Recargamos la lista completa para reflejar el nuevo saldo,
                        // resaltando/subiendo la cuenta recién modificada
                        adapterCuentas.idUltimaCuentaModificada = cuenta.id
                        cargarCuentasYResaltarUltima(cuenta.id)
                    } else {
                        Toast.makeText(this@CuentasPorCobrarActivity, "Error al procesar el pago", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@CuentasPorCobrarActivity, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()

        cargarProductosFactura(
            cuenta, rvProductos, pbProductos, layoutResumenProductos,
            tvDescuentoFactura, tvSubtotalFacturaProductos, tvIvaFactura, tvTotalFacturaProductos
        )
    }


    private fun seleccionarTarjetaCuotaConAnimacion(
        seleccionada: MaterialCardView,
        todas: List<MaterialCardView>,
        monto: Double,
        etMonto: EditText
    ) {
        val density = resources.displayMetrics.density

        // Reseteamos el estilo de todas las tarjetas
        todas.forEach { card ->
            card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            card.setCardBackgroundColor(Color.WHITE)
            card.strokeColor = Color.parseColor("#E2E8F0")
            card.strokeWidth = (1 * density).toInt()
            card.cardElevation = 0f
        }

        // Resaltamos solo la tarjeta seleccionada
        seleccionada.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start()
        seleccionada.setCardBackgroundColor(Color.parseColor("#FFF7ED"))
        seleccionada.strokeColor = Color.parseColor("#EA580C")
        seleccionada.strokeWidth = (2 * density).toInt()
        seleccionada.cardElevation = (3 * density)

        // Autocompletamos el campo de monto con el valor de la tarjeta elegida
        val montoFormateado = String.format(Locale.US, "%.2f", monto)
        etMonto.setText(montoFormateado)
        etMonto.setSelection(montoFormateado.length) // cursor al final
        etMonto.requestFocus()
    }


    private fun cargarProductosFactura(
        cuenta: CuentaPorCobrarResponseDto,
        rvProductos: RecyclerView,
        pb: ProgressBar,
        layoutResumen: LinearLayout,
        tvDescuento: TextView,
        tvSubtotal: TextView,
        tvIva: TextView,
        tvTotal: TextView
    ) {
        // 1) ¿Ya lo teníamos calculado? -> pintar directo, sin tocar la red
        cacheProductosPorFactura[cuenta.id]?.let { resumen ->
            pintarProductos(resumen, rvProductos, layoutResumen, tvDescuento, tvSubtotal, tvIva, tvTotal)
            return
        }

        val numFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: return
        val token = sessionManager.getAuthHeader() ?: return

        pb.visibility = View.VISIBLE
        layoutResumen.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 2) Traemos la lista de facturas (usa caché si ya se pidió antes)
                val facturas = obtenerFacturasConCache(token)
                val facturaEncontrada = facturas.find {
                    it.numeroFactura?.equals(numFactura, ignoreCase = true) == true || it.id == cuenta.factura?.id
                }
                val detalles = facturaEncontrada?.detalles ?: emptyList()

                // 3) Calculamos los totales una sola vez
                val subtotalBruto = detalles.sumOf { (it.precioUnitario ?: 0.0) * (it.cantidad ?: 0) }
                val descuento = facturaEncontrada?.totalDescuento ?: 0.0
                val subtotalNeto = subtotalBruto - descuento
                val iva = subtotalNeto * 0.15
                val total = facturaEncontrada?.totalFactura ?: (subtotalNeto + iva)

                val resumen = ResumenProductos(detalles, descuento, subtotalNeto, iva, total)

                // 4) Guardamos en caché para que la próxima vez sea instantáneo
                cacheProductosPorFactura[cuenta.id] = resumen

                pb.visibility = View.GONE
                pintarProductos(resumen, rvProductos, layoutResumen, tvDescuento, tvSubtotal, tvIva, tvTotal)
            } catch (e: Exception) {
                pb.visibility = View.GONE

            }
        }
    }

    private fun pintarProductos(
        resumen: ResumenProductos,
        rvProductos: RecyclerView,
        layoutResumen: LinearLayout,
        tvDescuento: TextView,
        tvSubtotal: TextView,
        tvIva: TextView,
        tvTotal: TextView
    ) {
        rvProductos.adapter = ProductosFacturaAdapter(resumen.detalles)
        tvDescuento.text = String.format(Locale.US, "-$%.2f", resumen.descuento)
        tvSubtotal.text = String.format(Locale.US, "$%.2f", resumen.subtotal)
        tvIva.text = String.format(Locale.US, "$%.2f", resumen.iva)
        tvTotal.text = String.format(Locale.US, "$%.2f", resumen.total)
        layoutResumen.visibility = View.VISIBLE
    }

    private suspend fun obtenerFacturasConCache(
        token: String
    ): List<com.example.movildilo.data.model.dto.facturacion.FacturaResponseDto> {
        facturasCache?.let { return it }

        val res = RetrofitClient.apiService.getFacturas(token, negocioId)
        val lista = if (res.isSuccessful) res.body() ?: emptyList() else emptyList()
        facturasCache = lista
        return lista
    }
}