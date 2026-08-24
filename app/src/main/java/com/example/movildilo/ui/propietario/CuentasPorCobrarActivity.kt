package com.example.movildilo.ui.propietario

import android.app.DatePickerDialog
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
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.*
import com.example.movildilo.ui.adapters.ClientesAgrupadosAdapter
import com.example.movildilo.ui.adapters.CuentasPorCobrarAdapter
import com.example.movildilo.ui.adapters.FacturasClienteModalAdapter
import com.example.movildilo.ui.adapters.HistorialAbonosAdapter
import com.example.movildilo.ui.adapters.ProductosFacturaAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CuentasPorCobrarActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = 0

    private lateinit var btnRegresar: ImageButton
    private lateinit var tvTotalPorCobrar: TextView
    private lateinit var tvTotalAbonado: TextView
    private lateinit var tvCuentasVencidas: TextView

    private lateinit var tabLayoutPrincipal: TabLayout
    private lateinit var etBuscar: TextInputEditText
    private lateinit var containerFiltrosFacturas: LinearLayout
    private lateinit var chipTodas: TextView
    private lateinit var chipPendiente: TextView
    private lateinit var chipVencida: TextView
    private lateinit var chipPagada: TextView
    private lateinit var etFechaDesde: TextInputEditText
    private lateinit var etFechaHasta: TextInputEditText
    private lateinit var btnLimpiarFechas: ImageButton

    private lateinit var rvCuentasGeneral: RecyclerView
    private lateinit var rvClientesDirectorio: RecyclerView
    private lateinit var layoutSinResultados: LinearLayout
    private lateinit var layoutVacio: LinearLayout
    private lateinit var layoutLoading: FrameLayout

    private var cuentasBase: List<CuentaPorCobrarResponseDto> = emptyList()
    private var clientesAgrupados: List<ClienteAgrupado> = emptyList()
    private lateinit var adapterCuentas: CuentasPorCobrarAdapter
    private lateinit var adapterClientes: ClientesAgrupadosAdapter

    private var estadoFiltro: String = "TODAS"
    private var fechaDesdeSel: String = ""
    private var fechaHastaSel: String = ""

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        if (sessionManager.checkAndRedirectIfExpired(this)) {
            return
        }

        setContentView(R.layout.activity_cuentas_por_cobrar)

        negocioId = sessionManager.getNegocioId()

        initViews()
        setupListeners()
        setupTabs()
        cargarCuentas()
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

        etFechaDesde = findViewById(R.id.etFechaDesde)
        etFechaHasta = findViewById(R.id.etFechaHasta)
        btnLimpiarFechas = findViewById(R.id.btnLimpiarFechas)

        rvCuentasGeneral = findViewById(R.id.rvCuentasGeneral)
        rvClientesDirectorio = findViewById(R.id.rvClientesDirectorio)
        layoutSinResultados = findViewById(R.id.layoutSinResultados)
        layoutVacio = findViewById(R.id.layoutVacio)
        layoutLoading = findViewById(R.id.layoutLoading)

        rvCuentasGeneral.layoutManager = LinearLayoutManager(this)
        rvClientesDirectorio.layoutManager = LinearLayoutManager(this)

        adapterCuentas = CuentasPorCobrarAdapter(
            listaCuentas = emptyList(),
            onAbonarClick = { cuenta -> abrirPanelCobranza(cuenta) },
            onAbonarCuotaClick = { cuenta, cuota -> abrirPanelCobranza(cuenta, cuota) }
        )
        rvCuentasGeneral.adapter = adapterCuentas

        adapterClientes = ClientesAgrupadosAdapter(emptyList()) { cliente ->
            mostrarModalFacturasCliente(cliente)
        }
        rvClientesDirectorio.adapter = adapterClientes
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { aplicarFiltros() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        chipTodas.setOnClickListener { cambiarFiltroEstado("TODAS", chipTodas) }
        chipPendiente.setOnClickListener { cambiarFiltroEstado("PENDIENTE", chipPendiente) }
        chipVencida.setOnClickListener { cambiarFiltroEstado("VENCIDA", chipVencida) }
        chipPagada.setOnClickListener { cambiarFiltroEstado("PAGADA", chipPagada) }

        etFechaDesde.setOnClickListener { abrirDatePicker { fecha ->
            fechaDesdeSel = fecha
            etFechaDesde.setText(fecha)
            aplicarFiltros()
        }}

        etFechaHasta.setOnClickListener { abrirDatePicker { fecha ->
            fechaHastaSel = fecha
            etFechaHasta.setText(fecha)
            aplicarFiltros()
        }}

        btnLimpiarFechas.setOnClickListener {
            fechaDesdeSel = ""
            fechaHastaSel = ""
            etFechaDesde.setText("")
            etFechaHasta.setText("")
            aplicarFiltros()
        }
    }

    private fun setupTabs() {
        val headerDirectorio = findViewById<LinearLayout>(R.id.headerDirectorioClientes)

        tabLayoutPrincipal.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    containerFiltrosFacturas.visibility = View.VISIBLE
                    rvCuentasGeneral.visibility = View.VISIBLE
                    rvClientesDirectorio.visibility = View.GONE
                    headerDirectorio?.visibility = View.GONE
                } else {
                    containerFiltrosFacturas.visibility = View.GONE
                    rvCuentasGeneral.visibility = View.GONE
                    rvClientesDirectorio.visibility = View.VISIBLE
                    headerDirectorio?.visibility = View.VISIBLE
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

    private fun cargarCuentas() {
        val token = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

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

                    // Colocar la cuenta recién modificada/pagada AL INICIO de la lista (Posición 0)
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
            totalRecuperado += (total - saldo)

            val vencimiento = item.fechaVencimiento ?: ""
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
            val key = "$nombre-$ident"

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

        clientesAgrupados = mapa.values.sortedByDescending { it.totalDeuda }
        adapterClientes.actualizarLista(clientesAgrupados)
    }

    private fun aplicarFiltros() {
        val query = etBuscar.text.toString().lowercase().trim()
        val hoyStr = dateFormat.format(Date())

        if (tabLayoutPrincipal.selectedTabPosition == 0) {
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
                    else -> true
                }

                val fechaItem = item.fechaVencimiento ?: ""
                val coincideFechaDesde = fechaDesdeSel.isEmpty() || fechaItem >= fechaDesdeSel
                val coincideFechaHasta = fechaHastaSel.isEmpty() || fechaItem <= fechaHastaSel

                coincideTexto && coincideEstado && coincideFechaDesde && coincideFechaHasta
            }

            adapterCuentas.actualizarLista(filtradas)
            layoutSinResultados.visibility = if (filtradas.isEmpty() && cuentasBase.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            val clientesFiltrados = clientesAgrupados.filter {
                it.nombre.lowercase().contains(query) || it.identificacion.lowercase().contains(query)
            }
            adapterClientes.actualizarLista(clientesFiltrados)
            layoutSinResultados.visibility = if (clientesFiltrados.isEmpty() && clientesAgrupados.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun obtenerNombreCliente(item: CuentaPorCobrarResponseDto): String {
        return when {
            !item.clienteNombre.isNullOrEmpty() -> item.clienteNombre
            !item.factura?.cliente?.nombreCompleto.isNullOrEmpty() -> item.factura?.cliente?.nombreCompleto!!
            !item.factura?.cliente?.nombre.isNullOrEmpty() -> item.factura?.cliente?.nombre!!
            else -> "${item.factura?.cliente?.primerNombre ?: ""} ${item.factura?.cliente?.apellidoPaterno ?: ""}".trim()
        }
    }

    private fun abrirDatePicker(onDateSelected: (String) -> Unit) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            onDateSelected(dateFormat.format(cal.time))
        }, year, month, day).show()
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
        rvFacturas.adapter = FacturasClienteModalAdapter(listaCreditos) { credito ->
            dialog.dismiss()
            val cuentaOriginal = cuentasBase.find { it.id == credito.id }
            cuentaOriginal?.let { abrirPanelCobranza(it) }
        }

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
            }
        }
        return fechaRaw
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
        val rvProductos = view.findViewById<RecyclerView>(R.id.rvProductosComprados)
        val pbProductos = view.findViewById<ProgressBar>(R.id.pbProductos)

        rvHistorial.layoutManager = LinearLayoutManager(this)
        rvProductos.layoutManager = LinearLayoutManager(this)

        val todasLasCuotas = cuenta.cuotas ?: emptyList()
        val emailUsuario = sessionManager.getUserEmail() ?: ""
        rvHistorial.adapter = HistorialAbonosAdapter(todasLasCuotas, emailUsuario)

        val etMonto = view.findViewById<EditText>(R.id.etMontoAbono)
        val spMetodo = view.findViewById<Spinner>(R.id.spMetodoPago)
        val etRef = view.findViewById<EditText>(R.id.etReferencia)
        val btnConfirmar = view.findViewById<Button>(R.id.btnConfirmarPago)

        val saldoPendienteTotal = cuenta.saldoPendiente ?: 0.0
        val numFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "N/A"

        tvNumFactura.text = "#$numFactura"
        tvCliente.text = obtenerNombreCliente(cuenta)
        tvSaldo.text = String.format(Locale.US, "$%.2f", saldoPendienteTotal)

        // 1. Filtrar únicamente las cuotas PENDIENTES
        val cuotasPendientes = todasLasCuotas.filter { cuota ->
            val saldoCuota = cuota.saldoPendienteCuota ?: 0.0
            val esPagada = cuota.estado?.equals("PAGADA", ignoreCase = true) == true
            !esPagada && saldoCuota > 0
        }.sortedBy { it.fechaVencimiento ?: "" }

        // 2. Renderizado de Tarjetas con Renumeración Dinámica
        layoutContenedorCuotas.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val listaTarjetas = mutableListOf<MaterialCardView>()

        // Tarjeta 1: Saldo Total
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

        // Tarjetas de cuotas activas recorridas secuencialmente
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

            if (cuotaSeleccionada != null && cuotaSeleccionada.id == cuota.id) {
                seleccionarTarjetaCuotaConAnimacion(cardItem, listaTarjetas, montoCuotaActual, etMonto)
            }
        }

        if (cuotaSeleccionada == null) {
            seleccionarTarjetaCuotaConAnimacion(cardTotal, listaTarjetas, saldoPendienteTotal, etMonto)
        }

        // 3. Métodos de Pago
        val metodos = arrayOf("Efectivo", "Transferencia")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_item, metodos)
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spMetodo.adapter = adapterSpinner

        spMetodo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                etRef.visibility = if (metodos[pos] == "Transferencia") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnCerrar.setOnClickListener { dialog.dismiss() }
        btnCancelar.setOnClickListener { dialog.dismiss() }

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
                        cargarProductosFactura(cuenta, rvProductos, pbProductos)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

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

                        // Activar el Badge Verde "💸 PAGADO" y mover la cuenta al inicio
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
    }

    private fun seleccionarTarjetaCuotaConAnimacion(
        seleccionada: MaterialCardView,
        todas: List<MaterialCardView>,
        monto: Double,
        etMonto: EditText
    ) {
        val density = resources.displayMetrics.density

        // Resetear tarjetas inactivas
        todas.forEach { card ->
            card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            card.setCardBackgroundColor(Color.WHITE)
            card.strokeColor = Color.parseColor("#E2E8F0")
            card.strokeWidth = (1 * density).toInt()
            card.cardElevation = 0f
        }

        // Animación de la tarjeta activa estilo Web (#FFF7ED, #EA580C)
        seleccionada.animate().scaleX(1.03f).scaleY(1.03f).setDuration(150).start()
        seleccionada.setCardBackgroundColor(Color.parseColor("#FFF7ED"))
        seleccionada.strokeColor = Color.parseColor("#EA580C")
        seleccionada.strokeWidth = (2 * density).toInt()
        seleccionada.cardElevation = (3 * density)

        val montoFormateado = String.format(Locale.US, "%.2f", monto)
        etMonto.setText(montoFormateado)
        etMonto.setSelection(montoFormateado.length)
        etMonto.requestFocus()
    }

    private fun cargarProductosFactura(cuenta: CuentaPorCobrarResponseDto, rvProductos: RecyclerView, pb: ProgressBar) {
        val numFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: return
        val token = sessionManager.getAuthHeader() ?: return
        pb.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val res = RetrofitClient.apiService.getFacturas(token, negocioId)
                pb.visibility = View.GONE
                if (res.isSuccessful && res.body() != null) {
                    val facturaEncontrada = res.body()!!.find {
                        it.numeroFactura?.equals(numFactura, ignoreCase = true) == true || it.id == cuenta.factura?.id
                    }
                    val detalles = facturaEncontrada?.detalles ?: emptyList()
                    rvProductos.adapter = ProductosFacturaAdapter(detalles)
                }
            } catch (e: Exception) {
                pb.visibility = View.GONE
            }
        }
    }
}