package com.example.movildilo.ui.propietario

import android.app.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.usuarios.ClienteReporteDto
import com.example.movildilo.data.model.dto.usuarios.ClienteResponseDto
import com.example.movildilo.data.model.dto.facturacion.ClienteTopDto
import com.example.movildilo.data.model.dto.facturacion.ComparativaItemDto
import com.example.movildilo.data.model.dto.usuarios.CreditoClienteResumenDto
import com.example.movildilo.data.model.dto.facturacion.CuentaPorCobrarResponseDto
import com.example.movildilo.data.model.dto.facturacion.DetalleFacturaResumenDto
import com.example.movildilo.data.model.dto.facturacion.DiaCalorDto
import com.example.movildilo.data.model.dto.facturacion.DiaSemanaItemDto
import com.example.movildilo.data.model.dto.usuarios.DocumentoUiModel
import com.example.movildilo.data.model.dto.usuarios.FacturaClienteResumenDto
import com.example.movildilo.data.model.dto.facturacion.FacturaResponseDto
import com.example.movildilo.data.model.dto.facturacion.FormaPagoItemDto
import com.example.movildilo.data.model.dto.facturacion.HoraItemDto
import com.example.movildilo.data.model.dto.facturacion.ProductoDemandaDto
import com.example.movildilo.data.model.dto.facturacion.SerieDiariaItemDto
import com.example.movildilo.ui.adapters.DetalleDocumentoAdapter
import com.example.movildilo.ui.adapters.ReporteClientesAdapter
import com.example.movildilo.ui.facturas.DetalleFacturaDialogHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

class RendimientoComercialActivity : AppCompatActivity() {

    private lateinit var tabLayoutRendimiento: TabLayout
    private lateinit var layoutResumenGeneral: LinearLayout
    private lateinit var layoutReporteClientes: LinearLayout

    private lateinit var chip7: TextView
    private lateinit var chip30: TextView
    private lateinit var chip90: TextView
    private lateinit var btnExportarPdf: MaterialButton
    private lateinit var loadingContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var kpiContainer: LinearLayout
    private lateinit var cardRacha: MaterialCardView
    private lateinit var rachaFlameCard: MaterialCardView
    private lateinit var tvRachaTitulo: TextView
    private lateinit var tvRachaDesc: TextView
    private lateinit var tvComparativaSubtitulo: TextView
    private lateinit var comparativasContainer: LinearLayout
    private lateinit var barChartContainer: LinearLayout
    private lateinit var tvBarChartVacio: TextView
    private lateinit var heatmapGrid: GridLayout
    private lateinit var weekdayContainer: LinearLayout
    private lateinit var hourGrid: GridLayout
    private lateinit var productosContainer: LinearLayout
    private lateinit var clientesContainer: LinearLayout
    private lateinit var pagosContainer: LinearLayout

    private lateinit var tvTotalClientesDir: TextView
    private lateinit var tvClientesConDeudaDir: TextView
    private lateinit var tvTotalPorCobrarDir: TextView
    private lateinit var etBuscarClienteRendimiento: EditText
    private lateinit var cbSoloDeuda: CheckBox
    private lateinit var rvClientesReporte: RecyclerView
    private lateinit var layoutClientesVacio: View
    private lateinit var reporteAdapter: ReporteClientesAdapter

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private var negocioNombre: String = "Mi Negocio"
    private var isLoading = true
    private var exportandoPdf = false
    private var periodoDias = 30

    private var facturasRaw: List<FacturaResponseDto> = emptyList()
    private var cuentasRaw: List<CuentaPorCobrarResponseDto> = emptyList()
    private var clientesRaw: List<ClienteResponseDto> = emptyList()
    private var reporteClientesCompleto: List<ClienteReporteDto> = emptyList()

    private var ventasPeriodo = 0.0
    private var facturasPeriodoCount = 0
    private var diasConVenta = 0
    private var rachaActual = 0
    private var mejorRacha = 0
    private var rachaActivaHoy = false

    private var comparativas: List<ComparativaItemDto> = emptyList()
    private var heatmapDias: List<DiaCalorDto> = emptyList()
    private var calorPorDiaSemana: List<DiaSemanaItemDto> = emptyList()
    private var calorPorHora: List<HoraItemDto> = emptyList()
    private var topProductos: List<ProductoDemandaDto> = emptyList()
    private var topClientes: List<ClienteTopDto> = emptyList()
    private var porFormaPago: List<FormaPagoItemDto> = emptyList()
    private var serieDiaria: List<SerieDiariaItemDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rendimiento_comercial)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupListeners()
        setupTabs()
        actualizarChipsActivos()

        if (negocioId != -1L) {
            cargarDatos()
        } else {
            mostrarLoading(false)
            Toast.makeText(this, "No se encontró un negocio activo en la sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        findViewById<View>(R.id.btnRegresar).setOnClickListener { finish() }

        tabLayoutRendimiento = findViewById(R.id.tabLayoutRendimiento)
        layoutResumenGeneral = findViewById(R.id.layoutResumenGeneral)
        layoutReporteClientes = findViewById(R.id.layoutReporteClientes)

        chip7 = findViewById(R.id.chip7)
        chip30 = findViewById(R.id.chip30)
        chip90 = findViewById(R.id.chip90)
        btnExportarPdf = findViewById(R.id.btnExportarPdf)
        loadingContainer = findViewById(R.id.loadingContainer)
        contentContainer = findViewById(R.id.contentContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        kpiContainer = findViewById(R.id.kpiContainer)
        cardRacha = findViewById(R.id.cardRacha)
        rachaFlameCard = findViewById(R.id.rachaFlameCard)
        tvRachaTitulo = findViewById(R.id.tvRachaTitulo)
        tvRachaDesc = findViewById(R.id.tvRachaDesc)

        tvComparativaSubtitulo = findViewById(R.id.tvComparativaSubtitulo)
        comparativasContainer = findViewById(R.id.comparativasContainer)

        barChartContainer = findViewById(R.id.barChartContainer)
        tvBarChartVacio = findViewById(R.id.tvBarChartVacio)

        heatmapGrid = findViewById(R.id.heatmapGrid)
        weekdayContainer = findViewById(R.id.weekdayContainer)
        hourGrid = findViewById(R.id.hourGrid)
        productosContainer = findViewById(R.id.productosContainer)
        clientesContainer = findViewById(R.id.clientesContainer)
        pagosContainer = findViewById(R.id.pagosContainer)

        tvTotalClientesDir = findViewById(R.id.tvTotalClientesDir)
        tvClientesConDeudaDir = findViewById(R.id.tvClientesConDeudaDir)
        tvTotalPorCobrarDir = findViewById(R.id.tvTotalPorCobrarDir)
        etBuscarClienteRendimiento = findViewById(R.id.etBuscarClienteRendimiento)
        cbSoloDeuda = findViewById(R.id.cbSoloDeuda)

        rvClientesReporte = findViewById(R.id.rvClientesReporte)
        layoutClientesVacio = findViewById(R.id.layoutClientesVacio)
        rvClientesReporte.layoutManager = LinearLayoutManager(this)

        reporteAdapter = ReporteClientesAdapter(emptyList()) { cliente ->
            abrirModalDetalleCliente(cliente)
        }
        rvClientesReporte.adapter = reporteAdapter
    }

    private fun setupListeners() {
        chip7.setOnClickListener { cambiarPeriodo(7) }
        chip30.setOnClickListener { cambiarPeriodo(30) }
        chip90.setOnClickListener { cambiarPeriodo(90) }

        btnExportarPdf.setOnClickListener { exportarPdf() }

        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId != -1L) {
                cargarDatos {
                    swipeRefreshLayout.isRefreshing = false
                }
            } else {
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Negocio no válido", Toast.LENGTH_SHORT).show()
            }
        }

        etBuscarClienteRendimiento.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { aplicarFiltroClientes() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        cbSoloDeuda.setOnCheckedChangeListener { _, _ -> aplicarFiltroClientes() }
    }

    private fun setupTabs() {
        tabLayoutRendimiento.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                btnExportarPdf.visibility = View.VISIBLE
                when (tab?.position) {
                    0 -> {
                        layoutResumenGeneral.visibility = View.VISIBLE
                        layoutReporteClientes.visibility = View.GONE
                        btnExportarPdf.text = "Exportar PDF"
                        btnExportarPdf.setOnClickListener { exportarPdf() }
                    }
                    1 -> {
                        layoutResumenGeneral.visibility = View.GONE
                        layoutReporteClientes.visibility = View.VISIBLE
                        btnExportarPdf.text = "Exportar PDF Clientes"
                        btnExportarPdf.setOnClickListener { exportarPdfClientesGeneral() }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun cambiarPeriodo(dias: Int) {
        if (isLoading) return
        periodoDias = dias
        actualizarChipsActivos()
        procesarMetricas()
        renderTodo()
    }

    private fun actualizarChipsActivos() {
        val activo = Color.parseColor("#EA580C")
        val inactivo = Color.parseColor("#64748B")
        listOf(chip7 to 7, chip30 to 30, chip90 to 90).forEach { (chip, valor) ->
            val esActivo = periodoDias == valor
            chip.setTextColor(if (esActivo) activo else inactivo)
            if (esActivo) {
                val bg = GradientDrawable()
                bg.cornerRadius = dp(8f)
                bg.setColor(Color.WHITE)
                chip.background = bg
            } else {
                chip.background = null
            }
        }
    }

    private fun cargarDatos(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrBlank() || negocioId <= 0) {
            onComplete?.invoke()
            mostrarLoading(false)
            Toast.makeText(this, "Sesión no válida o negocio no seleccionado", Toast.LENGTH_SHORT).show()
            return
        }

        if (!swipeRefreshLayout.isRefreshing) {
            mostrarLoading(true)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val api = RetrofitClient.apiService

            val facturasReq = async { runCatching { api.getFacturas(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
            val negocioReq = async { runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull()?.body() }
            val cuentasReq = async { runCatching { api.getCuentasPorCobrar(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
            val clientesReq = async { runCatching { api.getClientes(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }

            facturasRaw = facturasReq.await()
            cuentasRaw = cuentasReq.await()
            clientesRaw = clientesReq.await()
            val negocio = negocioReq.await()

            if (negocio != null) {
                negocioNombre = negocio.nombreComercial ?: negocio.razonSocial ?: "Mi Negocio"
            }

            procesarMetricas()

            withContext(Dispatchers.Main) {
                renderTodo()
                mostrarLoading(false)
                onComplete?.invoke()
            }
        }
    }

    private fun mostrarLoading(loading: Boolean) {
        isLoading = loading
        if (!swipeRefreshLayout.isRefreshing) {
            loadingContainer.visibility = if (loading) View.VISIBLE else View.GONE
        }
        contentContainer.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun normalizarNombreCliente(nombre: String?): String {
        val str = nombre ?: "Consumidor Final"
        val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return normalized
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.getDefault())
            .trim()
    }

    private fun procesarReporteClientes() {
        val dniIndex = mutableMapOf<String, ClienteReporteDto>()
        val nombreIndex = mutableMapOf<String, ClienteReporteDto>()
        val clientesUnicos = mutableListOf<ClienteReporteDto>()

        val facturaIdToCliente = mutableMapOf<Long, ClienteReporteDto>()
        val facturaNumeroToCliente = mutableMapOf<String, ClienteReporteDto>()

        fun limpiarDni(identificacionCruda: String?): String? =
            identificacionCruda?.trim()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotBlank() }

        val clientesPorId = clientesRaw.mapNotNull { c -> c.id?.let { it to c } }.toMap()
        val clientesPorNombre = mutableMapOf<String, ClienteResponseDto>()
        clientesRaw.forEach { c ->
            val nombreCompleto = c.nombreCompleto?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(c.primerNombre, c.apellidoPaterno).joinToString(" ").takeIf { it.isNotBlank() }
            if (nombreCompleto != null) {
                clientesPorNombre[normalizarNombreCliente(nombreCompleto)] = c
            }
        }

        fun identificacionAutoritativa(clienteId: Long?, nombreCrudo: String?, identificacionEmbebida: String?): String? {
            val porId = clienteId?.let { clientesPorId[it] }?.dni?.let { limpiarDni(it) }
            if (porId != null) return porId
            val porNombre = nombreCrudo
                ?.let { clientesPorNombre[normalizarNombreCliente(it)] }
                ?.dni?.let { limpiarDni(it) }
            if (porNombre != null) return porNombre
            return limpiarDni(identificacionEmbebida)
        }

        fun resolverCliente(nombreCrudo: String?, identificacionCruda: String?): ClienteReporteDto? {
            val nombreLimpio = nombreCrudo?.trim()?.takeIf { it.isNotBlank() } ?: "Consumidor Final"
            val nombreNorm = normalizarNombreCliente(nombreLimpio)
            if (nombreNorm.contains("consumidor final") || nombreNorm.contains("consumidorfinal")) return null

            val dni = limpiarDni(identificacionCruda)

            var cliente = dni?.let { dniIndex[it] }
            if (cliente == null) cliente = nombreIndex[nombreNorm]
            if (cliente == null) {
                cliente = ClienteReporteDto().apply {
                    this.key = dni?.let { "dni_$it" } ?: "nom_$nombreNorm"
                    this.nombre = nombreLimpio
                    this.identificacion = dni
                }
                clientesUnicos.add(cliente)
            }

            if (dni != null) dniIndex[dni] = cliente
            nombreIndex[nombreNorm] = cliente
            if (cliente.identificacion.isNullOrBlank() && !dni.isNullOrBlank()) {
                cliente.identificacion = dni
            }
            if (cliente.nombre.isBlank() || cliente.nombre == "Cliente") {
                cliente.nombre = nombreLimpio
            }

            return cliente
        }

        fun registrarAlias(cliente: ClienteReporteDto, nombreCrudo: String?, identificacionCruda: String?) {
            val dni = limpiarDni(identificacionCruda)
            if (dni != null) dniIndex[dni] = cliente
            val nombreLimpio = nombreCrudo?.trim()?.takeIf { it.isNotBlank() }
            if (nombreLimpio != null) nombreIndex[normalizarNombreCliente(nombreLimpio)] = cliente
            if (cliente.identificacion.isNullOrBlank() && !dni.isNullOrBlank()) {
                cliente.identificacion = dni
            }
        }

        facturasRaw.forEach { f ->
            val cliObj = f.cliente
            val nombreCrudo = f.nombreClienteFormateado ?: cliObj?.nombreCompleto ?:
            (listOfNotNull(cliObj?.primerNombre, cliObj?.apellidoPaterno).joinToString(" ").takeIf { it.isNotBlank() }) ?: "Consumidor Final"
            val identificacion = identificacionAutoritativa(cliObj?.id, nombreCrudo, f.cliente?.dni ?: cliObj?.dni)

            val cliente = resolverCliente(nombreCrudo, identificacion) ?: return@forEach

            f.id?.let { facturaIdToCliente[it] = cliente }
            f.numeroFactura?.takeIf { it.isNotBlank() }?.let { facturaNumeroToCliente[it] = cliente }

            cliente.numFacturas += 1
            cliente.totalFacturado += f.totalCalculado

            val dets = (f.detalles ?: emptyList()).map { det ->
                DetalleFacturaResumenDto(
                    productoNombre = det.nombreProducto ?: "Producto",
                    cantidad = det.cantidad ?: 0,
                    precioUnitario = det.precioUnitario ?: 0.0,
                    descuento = det.descuento ?: 0.0,
                    subtotalItem = det.subtotalItem ?: 0.0
                )
            }

            val cal = parseFecha(f.fechaEmision)
            val fechaStr = if (cal != null) String.format(Locale.US, "%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)) else "—"

            if (cliente.facturas.none { it.id == f.id || (it.numero == f.numeroFactura && f.numeroFactura != null) }) {
                cliente.facturas.add(
                    FacturaClienteResumenDto(
                        id = f.id,
                        numero = f.numeroFactura ?: "S/N",
                        fecha = fechaStr,
                        tipo = f.metodoPago ?: "OTRO",
                        monto = f.totalCalculado,
                        estado = f.estadoFormateado,
                        detalles = dets,
                        descuentoGlobal = f.totalDescuento ?: 0.0,
                        showDetalles = false
                    )
                )
            }
        }

        cuentasRaw.forEach { c ->
            val cliObj = c.factura?.cliente
            val nombreCrudo = c.clienteNombre ?: cliObj?.nombreCompleto ?: cliObj?.nombre ?:
            (listOfNotNull(cliObj?.primerNombre, cliObj?.apellidoPaterno).joinToString(" ").takeIf { it.isNotBlank() }) ?: "Cliente"
            val identificacion = identificacionAutoritativa(null, nombreCrudo, c.dniCliente ?: cliObj?.dni)

            val numeroFacturaCredito = c.numeroFactura ?: c.factura?.numeroFactura ?: "S/N"
            val facturaIdCredito = c.factura?.id

            val clienteVíaFactura = facturaIdCredito?.let { facturaIdToCliente[it] }
                ?: numeroFacturaCredito.takeIf { it != "S/N" }?.let { facturaNumeroToCliente[it] }

            val cliente = clienteVíaFactura ?: resolverCliente(nombreCrudo, identificacion) ?: return@forEach

            if (clienteVíaFactura != null) {
                registrarAlias(cliente, nombreCrudo, identificacion)
            }

            val montoTotal = c.montoTotal ?: 0.0
            val saldoPendiente = c.saldoPendiente ?: 0.0

            cliente.saldoPendiente += saldoPendiente
            if (saldoPendiente > 0) {
                cliente.numCuentasCredito += 1
            }

            val cal = parseFecha(c.fechaVencimiento)
            val fechaVencStr = if (cal != null) String.format(Locale.US, "%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)) else "—"

            val facturaRelacionada = cliente.facturas.find { facturaIdCredito != null && it.id == facturaIdCredito }
                ?: cliente.facturas.find { it.numero == numeroFacturaCredito }

            if (cliente.creditos.none { it.id == c.id || it.factura == numeroFacturaCredito }) {
                cliente.creditos.add(
                    CreditoClienteResumenDto(
                        id = c.id,
                        factura = numeroFacturaCredito,
                        montoTotal = montoTotal,
                        saldoPendiente = saldoPendiente,
                        fechaVencimiento = fechaVencStr,
                        estado = c.estado ?: "PENDIENTE",
                        detalles = facturaRelacionada?.detalles ?: emptyList(),
                        descuentoGlobal = facturaRelacionada?.descuentoGlobal ?: 0.0,
                        metodoPago = facturaRelacionada?.tipo,
                        showDetalles = false
                    )
                )
            }
        }

        reporteClientesCompleto = clientesUnicos.sortedWith(
            compareByDescending<ClienteReporteDto> { it.saldoPendiente }
                .thenByDescending { it.totalFacturado }
        )

        val totalCobrar = reporteClientesCompleto.sumOf { it.saldoPendiente }
        val conDeuda = reporteClientesCompleto.count { it.saldoPendiente > 0 }

        tvTotalClientesDir.text = reporteClientesCompleto.size.toString()
        tvClientesConDeudaDir.text = conDeuda.toString()
        tvTotalPorCobrarDir.text = String.format(Locale.US, "$%,.2f", totalCobrar)

        aplicarFiltroClientes()
    }

    private fun abrirModalDetalleCliente(cliente: ClienteReporteDto) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_detalle_cliente)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.95).toInt(), (resources.displayMetrics.heightPixels * 0.85).toInt())

        val tvModalInicial: TextView = dialog.findViewById(R.id.tvModalInicial)
        val tvModalNombre: TextView = dialog.findViewById(R.id.tvModalNombre)
        val tvModalRuc: TextView = dialog.findViewById(R.id.tvModalRuc)
        val tvModalDeudaBadge: TextView = dialog.findViewById(R.id.tvModalDeudaBadge)

        val tvModalCountFacturas: TextView = dialog.findViewById(R.id.tvModalCountFacturas)
        val tvModalTotalFacturado: TextView = dialog.findViewById(R.id.tvModalTotalFacturado)
        val tvModalTotalDeuda: TextView = dialog.findViewById(R.id.tvModalTotalDeuda)

        val tabFacturas: LinearLayout = dialog.findViewById(R.id.tabFacturas)
        val tvTabFacturasText: TextView = dialog.findViewById(R.id.tvTabFacturasText)
        val tvTabFacturasBadge: TextView = dialog.findViewById(R.id.tvTabFacturasBadge)
        val indicatorFacturas: View = dialog.findViewById(R.id.indicatorFacturas)

        val tabCreditos: LinearLayout = dialog.findViewById(R.id.tabCreditos)
        val tvTabCreditosText: TextView = dialog.findViewById(R.id.tvTabCreditosText)
        val tvTabCreditosBadge: TextView = dialog.findViewById(R.id.tvTabCreditosBadge)
        val indicatorCreditos: View = dialog.findViewById(R.id.indicatorCreditos)

        val rvModalDocumentos: RecyclerView = dialog.findViewById(R.id.rvModalDocumentos)
        rvModalDocumentos.layoutManager = LinearLayoutManager(this)

        tvModalNombre.text = cliente.nombre
        tvModalInicial.text = if (cliente.nombre.isNotEmpty()) cliente.nombre.substring(0, 1).uppercase() else "C"
        tvModalRuc.text = if (!cliente.identificacion.isNullOrEmpty()) "CI/RUC: ${cliente.identificacion}" else "Sin identificación"

        if (cliente.saldoPendiente > 0) {
            tvModalDeudaBadge.text = String.format(Locale.US, "Debe $%,.2f", cliente.saldoPendiente)
            tvModalDeudaBadge.visibility = View.VISIBLE
        } else {
            tvModalDeudaBadge.visibility = View.GONE
        }

        tvModalCountFacturas.text = cliente.numFacturas.toString()
        tvModalTotalFacturado.text = String.format(Locale.US, "$%,.2f", cliente.totalFacturado)
        tvModalTotalDeuda.text = String.format(Locale.US, "$%,.2f", cliente.saldoPendiente)

        tvTabFacturasBadge.text = cliente.numFacturas.toString()
        tvTabCreditosBadge.text = cliente.numCuentasCredito.toString()

        val adapterDoc = DetalleDocumentoAdapter(emptyList()) { doc ->
            val facturaEncontrada = cliente.facturas.find { it.numero == doc.numero }
            val creditoEncontrado = cliente.creditos.find { it.factura == doc.numero }

            val detallesOrigen = when {
                doc.detalles.isNotEmpty() -> doc.detalles
                facturaEncontrada != null && facturaEncontrada.detalles.isNotEmpty() -> facturaEncontrada.detalles
                creditoEncontrado != null && creditoEncontrado.detalles.isNotEmpty() -> creditoEncontrado.detalles
                else -> {
                    val facturaPorId = facturasRaw.find { f -> f.id == creditoEncontrado?.id || f.numeroFactura == doc.numero }
                    facturaPorId?.detalles?.map { det ->
                        DetalleFacturaResumenDto(
                            productoNombre = det.nombreProducto ?: "Producto",
                            cantidad = det.cantidad ?: 0,
                            precioUnitario = det.precioUnitario ?: 0.0,
                            descuento = det.descuento ?: 0.0,
                            subtotalItem = det.subtotalItem ?: 0.0
                        )
                    } ?: emptyList()
                }
            }

            val metodoPagoOrigen = when {
                !doc.metodoPago.isNullOrBlank() && doc.metodoPago != "N/D" && doc.metodoPago != "CRÉDITO" -> doc.metodoPago
                facturaEncontrada != null && !facturaEncontrada.tipo.isNullOrBlank() -> facturaEncontrada.tipo
                else -> "CRÉDITO"
            }

            val items = detallesOrigen.map {
                DetalleFacturaDialogHelper.ItemLinea(
                    nombre = it.productoNombre,
                    cantidad = it.cantidad,
                    precioUnitario = it.precioUnitario,
                    descuento = it.descuento,
                    subtotal = it.subtotalItem
                )
            }
            val datos = DetalleFacturaDialogHelper.DatosFactura(
                numero = doc.numero,
                fecha = doc.fecha,
                clienteNombre = cliente.nombre,
                metodoPago = metodoPagoOrigen,
                estado = doc.estado,
                total = doc.monto,
                descuentoGlobal = doc.descuentoGlobal,
                items = items,
                clienteIdentificacion = cliente.identificacion
            )
            DetalleFacturaDialogHelper.mostrar(this, datos)
        }
        rvModalDocumentos.adapter = adapterDoc

        fun renderTabList(isCredito: Boolean) {
            if (isCredito) {
                tvTabCreditosText.setTextColor(Color.parseColor("#3B82F6"))
                indicatorCreditos.setBackgroundColor(Color.parseColor("#3B82F6"))
                tvTabFacturasText.setTextColor(Color.parseColor("#64748B"))
                indicatorFacturas.setBackgroundColor(Color.TRANSPARENT)

                val uiList = cliente.creditos.map { c ->
                    val facturaEncontrada = cliente.facturas.find { f -> f.id == c.id || f.numero == c.factura }
                    val facturaCruda = facturasRaw.find { f -> f.id == c.id || f.numeroFactura == c.factura }

                    val detallesFinales = when {
                        c.detalles.isNotEmpty() -> c.detalles
                        facturaEncontrada != null && facturaEncontrada.detalles.isNotEmpty() -> facturaEncontrada.detalles
                        facturaCruda != null -> facturaCruda.detalles?.map { det ->
                            DetalleFacturaResumenDto(
                                productoNombre = det.nombreProducto ?: "Producto",
                                cantidad = det.cantidad ?: 0,
                                precioUnitario = det.precioUnitario ?: 0.0,
                                descuento = det.descuento ?: 0.0,
                                subtotalItem = det.subtotalItem ?: 0.0
                            )
                        } ?: emptyList()
                        else -> emptyList()
                    }

                    DocumentoUiModel(
                        numero = c.factura,
                        fecha = c.fechaVencimiento,
                        tipo = "CRÉDITO",
                        estado = c.estado,
                        monto = c.montoTotal,
                        isCredito = true,
                        detalles = detallesFinales,
                        descuentoGlobal = facturaEncontrada?.descuentoGlobal ?: facturaCruda?.totalDescuento ?: 0.0,
                        metodoPago = c.metodoPago ?: facturaEncontrada?.tipo ?: facturaCruda?.metodoPago ?: "CRÉDITO",
                        saldoPendiente = c.saldoPendiente
                    )
                }
                adapterDoc.actualizarLista(uiList)
            } else {
                tvTabFacturasText.setTextColor(Color.parseColor("#3B82F6"))
                indicatorFacturas.setBackgroundColor(Color.parseColor("#3B82F6"))
                tvTabCreditosText.setTextColor(Color.parseColor("#64748B"))
                indicatorCreditos.setBackgroundColor(Color.TRANSPARENT)

                val uiList = cliente.facturas.map { f ->
                    DocumentoUiModel(
                        numero = f.numero,
                        fecha = f.fecha,
                        tipo = f.tipo,
                        estado = f.estado,
                        monto = f.monto,
                        isCredito = false,
                        detalles = f.detalles,
                        descuentoGlobal = f.descuentoGlobal,
                        metodoPago = f.tipo
                    )
                }.sortedByDescending { it.fecha }
                adapterDoc.actualizarLista(uiList)
            }
        }

        tabFacturas.setOnClickListener { renderTabList(false) }
        tabCreditos.setOnClickListener { renderTabList(true) }

        val defaultTabIsCredito = (cliente.saldoPendiente > 0 && cliente.facturas.isEmpty())
        renderTabList(defaultTabIsCredito)

        val btnExportarPdfCliente: ImageButton = dialog.findViewById(R.id.btnExportarPdfCliente)
        btnExportarPdfCliente.setOnClickListener { exportarPdfCliente(cliente) }

        dialog.findViewById<ImageView>(R.id.btnModalCloseTop).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<MaterialButton>(R.id.btnModalCerrar).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun aplicarFiltroClientes() {
        val term = etBuscarClienteRendimiento.text.toString().lowercase().trim()
        val soloDeuda = cbSoloDeuda.isChecked

        val listaFiltrada = reporteClientesCompleto.filter { c ->
            if (soloDeuda && c.saldoPendiente <= 0) return@filter false
            if (term.isEmpty()) return@filter true

            c.nombre.lowercase().contains(term) || (c.identificacion?.lowercase()?.contains(term) == true)
        }

        reporteAdapter.actualizarLista(listaFiltrada)
        val vacio = listaFiltrada.isEmpty()
        layoutClientesVacio.visibility = if (vacio) View.VISIBLE else View.GONE
        rvClientesReporte.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun procesarMetricas() {
        val ahora = Calendar.getInstance()
        val inicioPeriodo = inicioDia(restarDias(ahora, periodoDias - 1))
        val inicioAnterior = inicioDia(restarDias(inicioPeriodo, periodoDias))
        val finAnterior = inicioDia(restarDias(inicioPeriodo, 1))

        val facturasPeriodo = facturasRaw.filter { f ->
            val d = parseFecha(f.fechaEmision) ?: return@filter false
            d.timeInMillis >= inicioPeriodo.timeInMillis && d.timeInMillis <= ahora.timeInMillis
        }
        val facturasAnterior = facturasRaw.filter { f ->
            val d = parseFecha(f.fechaEmision) ?: return@filter false
            d.timeInMillis >= inicioAnterior.timeInMillis && d.timeInMillis <= finAnterior.timeInMillis
        }

        ventasPeriodo = facturasPeriodo.sumOf { it.totalCalculado }
        facturasPeriodoCount = facturasPeriodo.size

        data class Acum(var total: Double = 0.0, var cantidad: Int = 0)
        val mapaDia = LinkedHashMap<String, Acum>()
        for (i in 0 until periodoDias) {
            val d = restarDias(ahora, periodoDias - 1 - i)
            mapaDia[keyFecha(d)] = Acum()
        }
        facturasPeriodo.forEach { f ->
            val d = parseFecha(f.fechaEmision) ?: return@forEach
            val key = keyFecha(d)
            val entry = mapaDia.getOrPut(key) { Acum() }
            entry.total += f.totalCalculado
            entry.cantidad += 1
        }

        diasConVenta = mapaDia.values.count { it.cantidad > 0 }

        val maxTotal = max(mapaDia.values.maxOfOrNull { it.total } ?: 1.0, 1.0)
        heatmapDias = mapaDia.entries.map { (fecha, v) ->
            val d = parseFechaSimple(fecha)
            DiaCalorDto(
                fecha = fecha,
                label = fmtDiaMes(d),
                diaSemana = nombreDiaCorto(d),
                total = v.total,
                cantidad = v.cantidad,
                intensidad = v.total / maxTotal
            )
        }

        val ultimos = heatmapDias.takeLast(minOf(14, periodoDias))
        val maxBarra = max(ultimos.maxOfOrNull { it.total } ?: 1.0, 1.0)
        serieDiaria = ultimos.map {
            SerieDiariaItemDto(it.label, it.total, max(4, round((it.total / maxBarra) * 100).toInt()))
        }

        calcularRachas(mapaDia.mapValues { it.value.cantidad }, ahora)

        val diasSem = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        val acumDia = DoubleArray(7)
        facturasPeriodo.forEach { f ->
            val d = parseFecha(f.fechaEmision) ?: return@forEach
            acumDia[d.get(Calendar.DAY_OF_WEEK) - 1] += f.totalCalculado
        }
        val maxDiaSem = max(acumDia.maxOrNull() ?: 1.0, 1.0)
        calorPorDiaSemana = diasSem.mapIndexed { i, nombre -> DiaSemanaItemDto(nombre, acumDia[i], acumDia[i] / maxDiaSem) }

        val acumHora = DoubleArray(24)
        facturasPeriodo.forEach { f ->
            val d = parseFecha(f.fechaEmision) ?: return@forEach
            acumHora[d.get(Calendar.HOUR_OF_DAY)] += f.totalCalculado
        }
        val maxHora = max(acumHora.maxOrNull() ?: 1.0, 1.0)
        calorPorHora = acumHora.mapIndexed { h, total -> HoraItemDto(String.format(Locale.US, "%02d:00", h), total, total / maxHora) }

        val ventasAnt = facturasAnterior.sumOf { it.totalCalculado }
        val facturasAnt = facturasAnterior.size
        val diasAntSet = facturasAnterior.mapNotNull { parseFecha(it.fechaEmision) }.map { keyFecha(it) }.toSet()

        comparativas = listOf(
            ComparativaItemDto("Ventas totales", ventasPeriodo, ventasAnt, variacionPct(ventasPeriodo, ventasAnt)),
            ComparativaItemDto("Facturas emitidas", facturasPeriodoCount.toDouble(), facturasAnt.toDouble(), variacionPct(facturasPeriodoCount.toDouble(), facturasAnt.toDouble())),
            ComparativaItemDto("Días con venta", diasConVenta.toDouble(), diasAntSet.size.toDouble(), variacionPct(diasConVenta.toDouble(), diasAntSet.size.toDouble()))
        )

        data class AcumProd(var unidades: Int = 0, var ingresos: Double = 0.0)
        val mapProd = LinkedHashMap<String, AcumProd>()
        facturasPeriodo.forEach { f ->
            (f.detalles ?: emptyList()).forEach { d ->
                val nombre = d.nombreProducto ?: "Producto"
                val entry = mapProd.getOrPut(nombre) { AcumProd() }
                entry.unidades += (d.cantidad ?: 0)
                entry.ingresos += d.subtotalItem?.takeIf { it != 0.0 } ?: ((d.cantidad ?: 0) * (d.precioUnitario ?: 0.0))
            }
        }
        val listaProd = mapProd.entries.map { (nombre, v) -> Triple(nombre, v.unidades, v.ingresos) }
            .sortedByDescending { it.third }
            .take(8)
        val maxIng = listaProd.firstOrNull()?.third ?: 1.0
        topProductos = listaProd.map { (nombre, unidades, ingresos) ->
            ProductoDemandaDto(nombre, unidades, ingresos, round((ingresos / max(maxIng, 1.0)) * 100).toInt())
        }

        data class AcumCli(var total: Double = 0.0, var facturas: Int = 0)
        val mapCli = LinkedHashMap<String, AcumCli>()
        facturasPeriodo.forEach { f ->
            val nombre = f.nombreClienteFormateado
            val entry = mapCli.getOrPut(nombre) { AcumCli() }
            entry.total += f.totalCalculado
            entry.facturas += 1
        }
        val listaCli = mapCli.entries.map { (nombre, v) -> Triple(nombre, v.total, v.facturas) }
            .sortedByDescending { it.second }
            .take(6)
        val maxCli = listaCli.firstOrNull()?.second ?: 1.0
        topClientes = listaCli.map { (nombre, total, facturasCount) ->
            ClienteTopDto(nombre, total, facturasCount, round((total / max(maxCli, 1.0)) * 100).toInt())
        }

        val mapPago = LinkedHashMap<String, Double>()
        facturasPeriodo.forEach { f ->
            val fp = f.metodoPago ?: "Otro"
            mapPago[fp] = (mapPago[fp] ?: 0.0) + f.totalCalculado
        }
        val totalPago = max(mapPago.values.sum(), 1.0)
        porFormaPago = mapPago.entries.map { (nombre, total) ->
            FormaPagoItemDto(nombre, total, round((total / totalPago) * 100).toInt())
        }.sortedByDescending { it.total }

        procesarReporteClientes()
    }

    private fun calcularRachas(cantidadPorDia: Map<String, Int>, ahora: Calendar) {
        var mejor = 0
        var actual = 0
        cantidadPorDia.keys.sorted().forEach { k ->
            if ((cantidadPorDia[k] ?: 0) > 0) {
                actual++
                mejor = max(mejor, actual)
            } else {
                actual = 0
            }
        }
        mejorRacha = mejor

        val hoyKey = keyFecha(ahora)
        val hoyTiene = (cantidadPorDia[hoyKey] ?: 0) > 0
        rachaActivaHoy = hoyTiene

        var cursor = if (hoyTiene) ahora else restarDias(ahora, 1)
        var racha = 0
        for (i in 0 until 365) {
            val key = keyFecha(cursor)
            val cantidad = cantidadPorDia[key] ?: facturasRaw.count { f ->
                val d = parseFecha(f.fechaEmision)
                d != null && keyFecha(d) == key
            }
            if (cantidad > 0) {
                racha++
                cursor = restarDias(cursor, 1)
            } else {
                break
            }
        }
        rachaActual = racha
    }

    private fun parseFecha(raw: String?): Calendar? {
        if (raw.isNullOrBlank()) return null
        return try {
            val datePart = raw.substringBefore('T').substringBefore(' ')
            val restante = if (raw.contains('T')) raw.substringAfter('T') else raw.substringAfter(' ', "")
            val dateComp = datePart.split("-")
            if (dateComp.size < 3) return null
            val y = dateComp[0].toInt()
            val m = dateComp[1].toInt()
            val d = dateComp[2].toInt()
            var hh = 0
            var mm = 0
            var ss = 0
            if (restante.isNotBlank()) {
                val limpio = restante.substringBefore('Z').substringBefore('+').substringBefore('.')
                val timeComp = limpio.split(":")
                hh = timeComp.getOrNull(0)?.toIntOrNull() ?: 0
                mm = timeComp.getOrNull(1)?.toIntOrNull() ?: 0
                ss = timeComp.getOrNull(2)?.toIntOrNull() ?: 0
            }
            Calendar.getInstance().apply {
                clear()
                set(y, m - 1, d, hh, mm, ss)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFechaSimple(fecha: String): Calendar {
        val comp = fecha.split("-")
        return Calendar.getInstance().apply {
            clear()
            set(comp[0].toInt(), comp[1].toInt() - 1, comp[2].toInt(), 12, 0, 0)
        }
    }

    private fun keyFecha(cal: Calendar): String {
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun inicioDia(cal: Calendar): Calendar {
        return (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }

    private fun restarDias(cal: Calendar, n: Int): Calendar {
        return (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -n) }
    }

    private fun fmtDiaMes(d: Calendar): String {
        return String.format(Locale.US, "%02d/%02d", d.get(Calendar.DAY_OF_MONTH), d.get(Calendar.MONTH) + 1)
    }

    private fun nombreDiaCorto(d: Calendar): String {
        return arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")[d.get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun variacionPct(actual: Double, anterior: Double): Double {
        if (anterior == 0.0) return if (actual > 0) 100.0 else 0.0
        return round(((actual - anterior) / anterior) * 1000) / 10.0
    }

    private fun colorCalor(intensidad: Double): Int = when {
        intensidad <= 0 -> Color.parseColor("#F1F5F9")
        intensidad < 0.25 -> Color.parseColor("#FFEDD5")
        intensidad < 0.5 -> Color.parseColor("#FED7AA")
        intensidad < 0.75 -> Color.parseColor("#FB923C")
        else -> Color.parseColor("#EA580C")
    }

    private fun colorTextoCalor(intensidad: Double): Int =
        if (intensidad >= 0.5) Color.WHITE else Color.parseColor("#475569")

    private fun fmtMoney(n: Double): String = String.format(Locale.US, "%,.2f", n)

    private fun renderTodo() {
        renderKpis()
        renderRacha()
        renderComparativas()
        renderBarChart()
        renderHeatmap()
        renderWeekday()
        renderHourHeat()
        renderProductos()
        renderClientes()
        renderPagos()
    }

    private fun renderKpis() {
        kpiContainer.removeAllViews()
        kpiContainer.addView(crearTarjetaKpi("$${fmtMoney(ventasPeriodo)}", "Ventas del periodo", "#F97316", "#FFF7ED", "#9A3412", R.drawable.ic_kpi_money))
        kpiContainer.addView(crearTarjetaKpi(facturasPeriodoCount.toString(), "Facturas emitidas", "#3B82F6", "#EFF6FF", "#1E40AF", R.drawable.ic_invoice))
        kpiContainer.addView(crearTarjetaKpi(diasConVenta.toString(), "Días con venta", "#8B5CF6", "#F5F3FF", "#5B21B6", R.drawable.ic_kpi_calendar))
    }

    private fun crearTarjetaKpi(valor: String, label: String, accentHex: String, bgHex: String, textHex: String, iconoRes: Int): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f).toInt()
            }
            radius = dp(14f)
            cardElevation = dp(1f)
            strokeWidth = dp(1f).toInt()
            strokeColor = Color.parseColor("#E2E8F0")
            setCardBackgroundColor(Color.WHITE)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f).toInt(), dp(14f).toInt(), dp(14f).toInt(), dp(14f).toInt())
        }
        val strip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor(accentHex))
        }
        val iconWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40f).toInt(), dp(40f).toInt()).apply { marginStart = dp(10f).toInt() }
            background = GradientDrawable().apply { cornerRadius = dp(10f); setColor(Color.parseColor(bgHex)) }
        }
        val ivIcono = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20f).toInt(), dp(20f).toInt()).apply { gravity = Gravity.CENTER }
            setImageResource(iconoRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(accentHex))
        }
        iconWrap.addView(ivIcono)
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f).toInt() }
        }
        val tvValor = TextView(this).apply {
            text = valor
            setTextColor(Color.parseColor(textHex))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }
        textCol.addView(tvValor)
        textCol.addView(tvLabel)
        row.addView(strip)
        row.addView(iconWrap)
        row.addView(textCol)
        card.addView(row)
        return card
    }

    private fun renderRacha() {
        val activa = rachaActual > 0
        cardRacha.setCardBackgroundColor(Color.parseColor(if (activa) "#FFF7ED" else "#F8FAFC"))
        cardRacha.strokeColor = Color.parseColor(if (activa) "#FED7AA" else "#E2E8F0")

        rachaFlameCard.setCardBackgroundColor(Color.parseColor(if (activa) "#EA580C" else "#F1F5F9"))

        val badgePrefix = when {
            rachaActual >= 7 -> "🔥 En racha\n"
            rachaActual in 3..6 -> "💪 Constante\n"
            else -> ""
        }

        if (rachaActual > 0) {
            tvRachaTitulo.text = "¡Racha de $rachaActual día${if (rachaActual == 1) "" else "s"} con ventas!"
            val base = if (rachaActivaHoy) "Hoy ya registraste al menos una factura. ¡Sigue así!"
            else "Ayer cerraste con ventas. Emite una factura hoy para no romper la racha."
            tvRachaDesc.text = "$badgePrefix$base Mejor racha del periodo: $mejorRacha día${if (mejorRacha == 1) "" else "s"}."
        } else {
            tvRachaTitulo.text = "Sin racha activa"
            val extra = if (mejorRacha > 0) " Tu mejor racha en este periodo fue de $mejorRacha día${if (mejorRacha == 1) "" else "s"}." else ""
            tvRachaDesc.text = "Emite una factura hoy para empezar una nueva racha.$extra"
        }
    }

    private fun renderComparativas() {
        tvComparativaSubtitulo.text = "Periodo actual vs anterior ($periodoDias días)"
        comparativasContainer.removeAllViews()
        comparativas.forEach { c ->
            comparativasContainer.addView(crearTarjetaComparativa(c))
        }
    }

    private fun crearTarjetaComparativa(c: ComparativaItemDto): View {
        val esMoneda = c.label.contains("Ventas")
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f).toInt()
            }
            radius = dp(14f)
            cardElevation = dp(0f)
            strokeWidth = dp(1f).toInt()
            strokeColor = Color.parseColor("#E2E8F0")
            setCardBackgroundColor(Color.WHITE)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt())
        }
        val tvLabel = TextView(this).apply {
            text = c.label.uppercase(Locale.getDefault())
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvActual = TextView(this).apply {
            text = if (esMoneda) "$${fmtMoney(c.actual)}" else round(c.actual).toInt().toString()
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4f).toInt(), 0, dp(8f).toInt())
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvAnterior = TextView(this).apply {
            text = "Antes: " + if (esMoneda) "$${fmtMoney(c.anterior)}" else round(c.anterior).toInt().toString()
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val (bgVar, colorVar) = when {
            c.variacion > 0 -> "#DCFCE7" to "#166534"
            c.variacion < 0 -> "#FEE2E2" to "#B91C1C"
            else -> "#F1F5F9" to "#64748B"
        }
        val tvVariacion = TextView(this).apply {
            text = (if (c.variacion > 0) "+" else "") + "${c.variacion}%"
            setTextColor(Color.parseColor(colorVar))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8f).toInt(), dp(3f).toInt(), dp(8f).toInt(), dp(3f).toInt())
            background = GradientDrawable().apply { cornerRadius = dp(12f); setColor(Color.parseColor(bgVar)) }
        }
        footer.addView(tvAnterior)
        footer.addView(tvVariacion)
        inner.addView(tvLabel)
        inner.addView(tvActual)
        inner.addView(footer)
        card.addView(inner)
        return card
    }

    private fun renderBarChart() {
        barChartContainer.removeAllViews()
        tvBarChartVacio.visibility = if (serieDiaria.isEmpty()) View.VISIBLE else View.GONE
        serieDiaria.forEach { item ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginStart = dp(3f).toInt(); marginEnd = dp(3f).toInt()
                }
                setOnClickListener {
                    Toast.makeText(this@RendimientoComercialActivity, "${item.label}: $${fmtMoney(item.total)}", Toast.LENGTH_SHORT).show()
                }
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28f).toInt(), dp((item.altura / 100f * 140f).coerceAtLeast(4f)).toInt())
                background = GradientDrawable().apply {
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    colors = intArrayOf(Color.parseColor("#FB923C"), Color.parseColor("#EA580C"))
                    cornerRadii = floatArrayOf(dp(6f), dp(6f), dp(6f), dp(6f), dp(2f), dp(2f), dp(2f), dp(2f))
                }
            }
            val tvLabel = TextView(this).apply {
                text = item.label
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 9f
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(4f).toInt(), 0, 0)
            }
            col.addView(bar)
            col.addView(tvLabel)
            barChartContainer.addView(col)
        }
    }

    private fun renderHeatmap() {
        heatmapGrid.removeAllViews()
        heatmapDias.forEach { dia ->
            val cell = TextView(this).apply {
                text = dia.label.split("/").firstOrNull() ?: ""
                gravity = Gravity.CENTER
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorTextoCalor(dia.intensidad))
                background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(colorCalor(dia.intensidad)) }
                val params = GridLayout.LayoutParams().apply {
                    width = dp(32f).toInt(); height = dp(32f).toInt()
                    setMargins(dp(2f).toInt(), dp(2f).toInt(), dp(2f).toInt(), dp(2f).toInt())
                }
                layoutParams = params
                setOnClickListener {
                    Toast.makeText(this@RendimientoComercialActivity, "${dia.diaSemana} ${dia.label}: $${fmtMoney(dia.total)} (${dia.cantidad} facturas)", Toast.LENGTH_SHORT).show()
                }
            }
            heatmapGrid.addView(cell)
        }
    }

    private fun renderWeekday() {
        weekdayContainer.removeAllViews()
        val maxTotal = max(calorPorDiaSemana.maxOfOrNull { it.total } ?: 1.0, 1.0)
        calorPorDiaSemana.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8f).toInt()
                }
            }
            val tvName = TextView(this).apply {
                text = item.nombre
                setTextColor(Color.parseColor("#475569"))
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(34f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val track = crearBarraProgreso(item.intensidad.coerceAtLeast(0.02), colorCalor(item.intensidad.coerceAtLeast(0.05)))
            track.layoutParams = LinearLayout.LayoutParams(0, dp(10f).toInt(), 1f).apply { marginStart = dp(8f).toInt(); marginEnd = dp(8f).toInt() }
            val tvVal = TextView(this).apply {
                text = "$${fmtMoney(item.total)}"
                setTextColor(Color.parseColor("#334155"))
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(70f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            row.addView(tvName)
            row.addView(track)
            row.addView(tvVal)
            weekdayContainer.addView(row)
        }
    }

    private fun crearBarraProgreso(fraccion: Double, colorFill: Int): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(Color.parseColor("#F1F5F9")) }
        }
        val pct = (fraccion.coerceIn(0.0, 1.0) * 100).toFloat().coerceAtLeast(2f)
        val fill = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct)
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(colorFill) }
        }
        val rest = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100f - pct)
        }
        container.addView(fill)
        container.addView(rest)
        return container
    }

    private fun renderHourHeat() {
        hourGrid.removeAllViews()
        calorPorHora.forEach { item ->
            val cell = TextView(this).apply {
                text = item.hora.substring(0, 2)
                gravity = Gravity.CENTER
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorTextoCalor(item.intensidad))
                background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(colorCalor(item.intensidad)) }
                val params = GridLayout.LayoutParams().apply {
                    width = 0; height = dp(34f).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2f).toInt(), dp(2f).toInt(), dp(2f).toInt(), dp(2f).toInt())
                }
                layoutParams = params
                setOnClickListener {
                    Toast.makeText(this@RendimientoComercialActivity, "${item.hora}: $${fmtMoney(item.total)}", Toast.LENGTH_SHORT).show()
                }
            }
            hourGrid.addView(cell)
        }
    }

    private fun renderProductos() {
        productosContainer.removeAllViews()
        if (topProductos.isEmpty()) {
            productosContainer.addView(crearVacio("Sin detalle de productos en las facturas del periodo."))
            return
        }
        topProductos.forEachIndexed { i, p ->
            productosContainer.addView(
                crearItemRanking(i + 1, p.nombre, "${p.unidades} uds. · $${fmtMoney(p.ingresos)}", p.porcentaje, "#EA580C")
            )
        }
    }

    private fun renderClientes() {
        clientesContainer.removeAllViews()
        if (topClientes.isEmpty()) {
            clientesContainer.addView(crearVacio("Aún no hay clientes con compras en este periodo."))
            return
        }
        topClientes.forEachIndexed { i, c ->
            val sufijo = if (c.facturas == 1) "" else "s"
            clientesContainer.addView(
                crearItemRanking(i + 1, c.nombre, "${c.facturas} factura$sufijo · $${fmtMoney(c.total)}", c.porcentaje, "#3B82F6")
            )
        }
    }

    private fun crearItemRanking(numero: Int, titulo: String, subtitulo: String, porcentaje: Int, colorHex: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12f).toInt()
            }
        }
        val top3 = numero <= 3
        val badge = TextView(this).apply {
            text = numero.toString()
            gravity = Gravity.CENTER
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (top3) "#EA580C" else "#64748B"))
            background = GradientDrawable().apply { cornerRadius = dp(8f); setColor(Color.parseColor(if (top3) "#FFF7ED" else "#F1F5F9")) }
            layoutParams = LinearLayout.LayoutParams(dp(26f).toInt(), dp(26f).toInt())
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12f).toInt() }
        }
        val tvTitulo = TextView(this).apply {
            text = titulo
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val tvSub = TextView(this).apply {
            text = subtitulo
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setPadding(0, dp(2f).toInt(), 0, dp(6f).toInt())
        }
        val progreso = crearBarraProgreso(porcentaje / 100.0, Color.parseColor(colorHex))
        progreso.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6f).toInt())

        col.addView(tvTitulo)
        col.addView(tvSub)
        col.addView(progreso)
        row.addView(badge)
        row.addView(col)
        return row
    }

    private fun renderPagos() {
        pagosContainer.removeAllViews()
        if (porFormaPago.isEmpty()) {
            pagosContainer.addView(crearVacio("Sin datos de formas de pago."))
            return
        }
        porFormaPago.forEach { p ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10f).toInt()
                }
            }
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val tvNombre = TextView(this).apply {
                text = p.nombre
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvPct = TextView(this).apply {
                text = "${p.porcentaje}%"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            }
            fila.addView(tvNombre)
            fila.addView(tvPct)

            val progreso = crearBarraProgreso(p.porcentaje / 100.0, Color.parseColor("#10B981"))
            progreso.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6f).toInt()).apply {
                topMargin = dp(4f).toInt(); bottomMargin = dp(4f).toInt()
            }

            val tvTotal = TextView(this).apply {
                text = "$${fmtMoney(p.total)}"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11f
            }
            item.addView(fila)
            item.addView(progreso)
            item.addView(tvTotal)
            pagosContainer.addView(item)
        }
    }

    private fun crearVacio(texto: String): View {
        return TextView(this).apply {
            text = texto
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(dp(10f).toInt(), dp(24f).toInt(), dp(10f).toInt(), dp(24f).toInt())
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun exportarPdf() {
        if (exportandoPdf || isLoading) return
        exportandoPdf = true
        btnExportarPdf.isEnabled = false
        btnExportarPdf.text = "Generando…"

        lifecycleScope.launch(Dispatchers.IO) {
            val archivo = runCatching { generarPdf() }.getOrNull()
            withContext(Dispatchers.Main) {
                exportandoPdf = false
                btnExportarPdf.isEnabled = true
                btnExportarPdf.text = "Exportar PDF"
                if (archivo != null) {
                    abrirPdf(archivo)
                } else {
                    Toast.makeText(this@RendimientoComercialActivity, "No se pudo generar el PDF.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportarPdfClientesGeneral() {
        if (exportandoPdf || isLoading) return
        exportandoPdf = true
        btnExportarPdf.isEnabled = false
        btnExportarPdf.text = "Generando…"

        lifecycleScope.launch(Dispatchers.IO) {
            val archivo = runCatching { generarPdfDirectorioClientes() }.getOrNull()
            withContext(Dispatchers.Main) {
                exportandoPdf = false
                btnExportarPdf.isEnabled = true
                btnExportarPdf.text = "Exportar PDF Clientes"
                if (archivo != null) {
                    abrirPdf(archivo)
                } else {
                    Toast.makeText(this@RendimientoComercialActivity, "No se pudo generar el reporte de clientes.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generarPdf(): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - margin * 2

        val cNavy = Color.parseColor("#0F172A")
        val cNavySubtitle = Color.parseColor("#CBD5E1")
        val cBorder = Color.parseColor("#E2E8F0")
        val cMuted = Color.parseColor("#64748B")
        val cMutedLight = Color.parseColor("#94A3B8")
        val cDark = Color.parseColor("#0F172A")
        val cOrange = Color.parseColor("#F97316")
        val cOrangeBg = Color.parseColor("#FFF7ED")
        val cOrangeText = Color.parseColor("#9A3412")
        val cBlue = Color.parseColor("#3B82F6")
        val cBlueBg = Color.parseColor("#EFF6FF")
        val cBlueText = Color.parseColor("#1E40AF")
        val cPurple = Color.parseColor("#8B5CF6")
        val cPurpleBg = Color.parseColor("#F5F3FF")
        val cPurpleText = Color.parseColor("#5B21B6")
        val cGreen = Color.parseColor("#10B981")
        val cGreenBg = Color.parseColor("#DCFCE7")
        val cGreenText = Color.parseColor("#166534")
        val cRedBg = Color.parseColor("#FEE2E2")
        val cRedText = Color.parseColor("#B91C1C")
        val cNeutralBg = Color.parseColor("#F1F5F9")
        val cFlame = Color.parseColor("#EA580C")
        val cRachaBg = Color.parseColor("#FFF7ED")
        val cRachaBorder = Color.parseColor("#FED7AA")
        val cRachaBgOff = Color.parseColor("#F8FAFC")
        val cRachaBorderOff = Color.parseColor("#E2E8F0")
        val cFlameOff = Color.parseColor("#F1F5F9")

        val doc = PdfDocument()
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = 0f
        var pageNum = 1

        fun rr(rect: RectF, radius: Float, color: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
            canvas.drawRoundRect(rect, radius, radius, p)
        }
        fun rrBorde(rect: RectF, radius: Float, color: Int, ancho: Float = 1f) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = ancho }
            canvas.drawRoundRect(rect, radius, radius, p)
        }
        fun wrap(texto: String, maxWidth: Float, paint: Paint): List<String> {
            val palabras = texto.split(" ")
            val lineas = mutableListOf<String>()
            var actual = StringBuilder()
            for (palabra in palabras) {
                val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (paint.measureText(prueba) > maxWidth && actual.isNotEmpty()) {
                    lineas.add(actual.toString()); actual = StringBuilder(palabra)
                } else actual = StringBuilder(prueba)
            }
            if (actual.isNotEmpty()) lineas.add(actual.toString())
            return lineas
        }
        fun iconoDinero(cx: Float, cy: Float, size: Float, color: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; textSize = size; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            val fm = p.fontMetrics
            canvas.drawText("$", cx, cy - (fm.ascent + fm.descent) / 2, p)
        }
        fun iconoFactura(cx: Float, cy: Float, size: Float, color: Int) {
            val w = size * 0.62f; val h = size * 0.8f
            val left = cx - w / 2; val top = cy - h / 2
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            canvas.drawRoundRect(RectF(left, top, left + w, top + h), size * 0.08f, size * 0.08f, p)
            val lx1 = left + w * 0.18f; val lx2 = left + w * 0.82f
            canvas.drawLine(lx1, top + h * 0.32f, lx2, top + h * 0.32f, p)
            canvas.drawLine(lx1, top + h * 0.52f, lx2, top + h * 0.52f, p)
            canvas.drawLine(lx1, top + h * 0.72f, left + w * 0.6f, top + h * 0.72f, p)
        }
        fun iconoCalendario(cx: Float, cy: Float, size: Float, color: Int) {
            val w = size * 0.72f; val h = size * 0.66f
            val left = cx - w / 2; val top = cy - h / 2 + size * 0.04f
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = size * 0.09f; strokeCap = Paint.Cap.ROUND }
            canvas.drawRoundRect(RectF(left, top, left + w, top + h), size * 0.08f, size * 0.08f, p)
            canvas.drawLine(left, top + h * 0.32f, left + w, top + h * 0.32f, p)
            canvas.drawLine(left + w * 0.26f, top - size * 0.06f, left + w * 0.26f, top + size * 0.08f, p)
            canvas.drawLine(left + w * 0.74f, top - size * 0.06f, left + w * 0.74f, top + size * 0.08f, p)
        }
        fun iconoFuego(cx: Float, cy: Float, size: Float, color: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
            val path = Path()
            path.moveTo(cx, cy - size * 0.5f)
            path.cubicTo(cx + size * 0.38f, cy - size * 0.08f, cx + size * 0.28f, cy + size * 0.2f, cx + size * 0.12f, cy + size * 0.08f)
            path.cubicTo(cx + size * 0.22f, cy + size * 0.4f, cx - size * 0.06f, cy + size * 0.52f, cx - size * 0.04f, cy + size * 0.28f)
            path.cubicTo(cx - size * 0.22f, cy + size * 0.42f, cx - size * 0.34f, cy + size * 0.12f, cx - size * 0.14f, cy - size * 0.16f)
            path.cubicTo(cx - size * 0.24f, cy + size * 0.04f, cx - size * 0.08f, cy - size * 0.08f, cx, cy - size * 0.5f)
            path.close()
            canvas.drawPath(path, p)
        }
        fun pillDerecha(rightX: Float, centerY: Float, texto: String, bg: Int, fg: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = fg; textSize = 9.5f; isFakeBoldText = true }
            val tw = p.measureText(texto)
            val padH = 7f; val h = 15f
            val rect = RectF(rightX - tw - padH * 2, centerY - h / 2, rightX, centerY + h / 2)
            rr(rect, h / 2, bg)
            canvas.drawText(texto, rect.left + padH, centerY + 3.3f, p)
        }
        fun barraProgreso(x: Float, yTop: Float, w: Float, h: Float, fraccion: Double, colorFill: Int) {
            rr(RectF(x, yTop, x + w, yTop + h), h / 2, cNeutralBg)
            val f = fraccion.coerceIn(0.02, 1.0).toFloat()
            if (f > 0f) rr(RectF(x, yTop, x + w * f, yTop + h), h / 2, colorFill)
        }

        val paintSeccion = Paint().apply { color = cNavy; textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val paintSubSeccion = Paint().apply { color = cMutedLight; textSize = 9f; isAntiAlias = true }
        val paintLabelMuted = Paint().apply { color = cMuted; textSize = 8.5f; isFakeBoldText = true; isAntiAlias = true }
        val paintValorBold = Paint().apply { color = cDark; textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
        val paintMuted = Paint().apply { color = cMuted; textSize = 9f; isAntiAlias = true }
        val paintMutedSmall = Paint().apply { color = cMutedLight; textSize = 8.5f; isAntiAlias = true }

        fun nuevaPagina() {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            val pFoot = Paint().apply { color = cMutedLight; textSize = 8f; isAntiAlias = true }
            canvas.drawText("Dilo · Rendimiento Comercial · $negocioNombre", margin, 16f, pFoot)
            y = 30f
        }

        fun asegurarEspacio(necesario: Float) {
            if (y + necesario > pageHeight - 34) nuevaPagina()
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, Paint().apply { color = cNavy })
        val paintTitle = Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val paintSubtitle = Paint().apply { color = cNavySubtitle; textSize = 10.5f; isAntiAlias = true }
        canvas.drawText("Rendimiento Comercial", margin, 34f, paintTitle)
        canvas.drawText(negocioNombre, margin, 52f, paintSubtitle)
        val sdf = java.text.SimpleDateFormat("d/M/yyyy, h:mm a", Locale("es", "EC"))
        canvas.drawText("Generado: ${sdf.format(java.util.Date())}", margin, 68f, Paint(paintMutedSmall).apply { color = cNavySubtitle })

        val periodoTxt = "$periodoDias días"
        val pChip = Paint().apply { color = Color.WHITE; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
        val chipW = pChip.measureText(periodoTxt) + 22f
        val chipRect = RectF(pageWidth - margin - chipW, 24f, pageWidth - margin, 44f)
        rr(chipRect, 10f, cFlame)
        canvas.drawText(periodoTxt, chipRect.left + 11f, chipRect.top + 14f, pChip)

        y = 116f

        val kpiH = 58f
        fun tarjetaKpi(valor: String, label: String, accent: Int, iconBg: Int, textColor: Int, icono: String) {
            asegurarEspacio(kpiH + 10f)
            val rect = RectF(margin, y, margin + contentWidth, y + kpiH)
            rr(rect, 12f, Color.WHITE)
            rrBorde(rect, 12f, cBorder, 1f)
            rr(RectF(margin, y, margin + 4f, y + kpiH), 2f, accent)
            val iconBoxSize = 38f
            val iconBoxRect = RectF(margin + 14f, y + (kpiH - iconBoxSize) / 2, margin + 14f + iconBoxSize, y + (kpiH - iconBoxSize) / 2 + iconBoxSize)
            rr(iconBoxRect, 9f, iconBg)
            val iconCx = iconBoxRect.centerX(); val iconCy = iconBoxRect.centerY()
            when (icono) {
                "dinero" -> iconoDinero(iconCx, iconCy, 19f, accent)
                "factura" -> iconoFactura(iconCx, iconCy, 22f, accent)
                "calendario" -> iconoCalendario(iconCx, iconCy, 22f, accent)
            }
            val textX = iconBoxRect.right + 12f
            canvas.drawText(valor, textX, y + kpiH / 2 - 3f, Paint(paintValorBold).apply { color = textColor })
            canvas.drawText(label, textX, y + kpiH / 2 + 14f, paintLabelMuted)
            y += kpiH + 10f
        }
        tarjetaKpi("$${fmtMoney(ventasPeriodo)}", "Ventas del periodo", cOrange, cOrangeBg, cOrangeText, "dinero")
        tarjetaKpi(facturasPeriodoCount.toString(), "Facturas emitidas", cBlue, cBlueBg, cBlueText, "factura")
        tarjetaKpi(diasConVenta.toString(), "Días con venta", cPurple, cPurpleBg, cPurpleText, "calendario")

        val activa = rachaActual > 0
        val badgePrefix = when {
            rachaActual >= 7 -> "En racha · "
            rachaActual in 3..6 -> "Constante · "
            else -> ""
        }
        val tituloRacha = if (activa) "¡Racha de $rachaActual día${if (rachaActual == 1) "" else "s"} con ventas!" else "Sin racha activa"
        val descRacha = if (activa) {
            val base = if (rachaActivaHoy) "Hoy ya registraste al menos una factura. ¡Sigue así!"
            else "Ayer cerraste con ventas. Emite una factura hoy para no romper la racha."
            "$badgePrefix$base Mejor racha del periodo: $mejorRacha día${if (mejorRacha == 1) "" else "s"}."
        } else {
            val extra = if (mejorRacha > 0) " Tu mejor racha en este periodo fue de $mejorRacha día${if (mejorRacha == 1) "" else "s"}." else ""
            "Emite una factura hoy para empezar una nueva racha.$extra"
        }
        val descLineas = wrap(descRacha, contentWidth - 78f, paintMuted)
        val rachaH = 26f + descLineas.size * 12f + 14f
        asegurarEspacio(rachaH + 16f)
        val rachaRect = RectF(margin, y, margin + contentWidth, y + rachaH)
        rr(rachaRect, 12f, if (activa) cRachaBg else cRachaBgOff)
        rrBorde(rachaRect, 12f, if (activa) cRachaBorder else cRachaBorderOff, 1f)
        val flameBoxRect = RectF(margin + 14f, y + 14f, margin + 56f, y + 56f)
        rr(flameBoxRect, 10f, if (activa) cFlame else cFlameOff)
        iconoFuego(flameBoxRect.centerX(), flameBoxRect.centerY(), 22f, if (activa) Color.WHITE else cMutedLight)
        val textoX = flameBoxRect.right + 12f
        canvas.drawText(tituloRacha, textoX, y + 24f, Paint().apply { color = cDark; textSize = 12f; isFakeBoldText = true; isAntiAlias = true })
        var yDesc = y + 38f
        descLineas.forEach { linea ->
            canvas.drawText(linea, textoX, yDesc, paintMuted)
            yDesc += 12f
        }
        y += rachaH + 18f

        asegurarEspacio(30f)
        canvas.drawText("Comparativa de desempeño", margin, y, paintSeccion)
        y += 12f
        canvas.drawText("Periodo actual vs anterior ($periodoDias días)", margin, y, paintSubSeccion)
        y += 14f
        comparativas.forEach { c ->
            val esMoneda = c.label.contains("Ventas")
            val actualTxt = if (esMoneda) "$${fmtMoney(c.actual)}" else round(c.actual).toInt().toString()
            val antTxt = "Antes: " + if (esMoneda) "$${fmtMoney(c.anterior)}" else round(c.anterior).toInt().toString()
            val cardH = 58f
            asegurarEspacio(cardH + 10f)
            val rect = RectF(margin, y, margin + contentWidth, y + cardH)
            rr(rect, 12f, Color.WHITE)
            rrBorde(rect, 12f, cBorder, 1f)
            val padX = 14f
            canvas.drawText(c.label.uppercase(Locale.getDefault()), margin + padX, y + 16f, paintLabelMuted)
            canvas.drawText(actualTxt, margin + padX, y + 36f, Paint().apply { color = cDark; textSize = 16f; isFakeBoldText = true; isAntiAlias = true })
            canvas.drawText(antTxt, margin + padX, y + 50f, Paint().apply { color = cMutedLight; textSize = 9f; isAntiAlias = true })
            val (bgVar, colorVar) = when {
                c.variacion > 0 -> cGreenBg to cGreenText
                c.variacion < 0 -> cRedBg to cRedText
                else -> cNeutralBg to cMuted
            }
            val txtVar = (if (c.variacion > 0) "+" else "") + "${c.variacion}%"
            pillDerecha(margin + contentWidth - padX, y + 46f, txtVar, bgVar, colorVar)
            y += cardH + 10f
        }

        y += 6f
        asegurarEspacio(30f)
        canvas.drawText("Demanda por día de la semana", margin, y, paintSeccion)
        y += 16f
        val maxDia = max(calorPorDiaSemana.maxOfOrNull { it.total } ?: 1.0, 1.0)
        calorPorDiaSemana.forEach { d ->
            asegurarEspacio(20f)
            canvas.drawText(d.nombre, margin, y + 8f, Paint().apply { color = cMuted; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true })
            val barX = margin + 34f
            val barW = contentWidth - 34f - 78f
            barraProgreso(barX, y, barW, 9f, d.total / maxDia, cOrange)
            val pv = Paint().apply { color = Color.parseColor("#334155"); textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
            canvas.drawText("$${fmtMoney(d.total)}", margin + contentWidth, y + 8f, pv)
            y += 18f
        }

        fun dibujarItemRanking(numero: Int, titulo: String, subtitulo: String, porcentaje: Int, colorAccent: Int, colorBadgeBg: Int, colorBadgeText: Int) {
            val itemH = 34f
            asegurarEspacio(itemH + 4f)
            val top3 = numero <= 3
            val badgeSize = 20f
            val badgeRect = RectF(margin, y, margin + badgeSize, y + badgeSize)
            rr(badgeRect, 6f, if (top3) colorBadgeBg else cNeutralBg)
            val pBadge = Paint().apply { color = if (top3) colorBadgeText else cMuted; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
            canvas.drawText(numero.toString(), badgeRect.centerX(), badgeRect.centerY() + 3.3f, pBadge)
            val textX = margin + badgeSize + 10f
            val maxTitleWidth = contentWidth - badgeSize - 10f
            val pTitulo = Paint().apply { color = cDark; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
            var tituloCorto = titulo
            if (pTitulo.measureText(tituloCorto) > maxTitleWidth) {
                while (tituloCorto.isNotEmpty() && pTitulo.measureText("$tituloCorto…") > maxTitleWidth) tituloCorto = tituloCorto.dropLast(1)
                tituloCorto += "…"
            }
            canvas.drawText(tituloCorto, textX, y + 9f, pTitulo)
            canvas.drawText(subtitulo, textX, y + 20f, paintMuted)
            barraProgreso(textX, y + 25f, maxTitleWidth, 4f, porcentaje / 100.0, colorAccent)
            y += itemH
        }

        y += 8f
        asegurarEspacio(30f)
        canvas.drawText("Productos con mayor demanda", margin, y, paintSeccion)
        y += 16f
        if (topProductos.isEmpty()) {
            canvas.drawText("Sin detalle de productos en el periodo.", margin, y, paintMuted)
            y += 16f
        } else {
            topProductos.forEachIndexed { i, p ->
                dibujarItemRanking(i + 1, p.nombre, "${p.unidades} uds. · $${fmtMoney(p.ingresos)}", p.porcentaje, cOrange, cOrangeBg, cOrangeText)
            }
        }

        y += 8f
        asegurarEspacio(30f)
        canvas.drawText("Clientes top", margin, y, paintSeccion)
        y += 16f
        if (topClientes.isEmpty()) {
            canvas.drawText("Sin clientes con compras en el periodo.", margin, y, paintMuted)
            y += 16f
        } else {
            topClientes.forEachIndexed { i, c ->
                val sufijo = if (c.facturas == 1) "" else "s"
                dibujarItemRanking(i + 1, c.nombre, "${c.facturas} factura$sufijo · $${fmtMoney(c.total)}", c.porcentaje, cBlue, cBlueBg, cBlueText)
            }
        }

        y += 8f
        asegurarEspacio(30f)
        canvas.drawText("Formas de pago", margin, y, paintSeccion)
        y += 16f
        if (porFormaPago.isEmpty()) {
            canvas.drawText("Sin datos de formas de pago.", margin, y, paintMuted)
            y += 16f
        } else {
            porFormaPago.forEach { p ->
                asegurarEspacio(40f)
                canvas.drawText(p.nombre, margin, y + 9f, Paint().apply { color = cDark; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true })
                val pPct = Paint().apply { color = cGreen; textSize = 10f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
                canvas.drawText("${p.porcentaje}%", margin + contentWidth, y + 9f, pPct)
                barraProgreso(margin, y + 14f, contentWidth, 5f, p.porcentaje / 100.0, cGreen)
                canvas.drawText("$${fmtMoney(p.total)}", margin, y + 30f, paintMuted)
                y += 40f
            }
        }

        y += 8f
        asegurarEspacio(40f)
        canvas.drawText("Ventas diarias (días con movimiento)", margin, y, paintSeccion)
        y += 16f
        val diasTabla = heatmapDias.filter { it.cantidad > 0 }.takeLast(20)
        if (diasTabla.isEmpty()) {
            canvas.drawText("No hubo ventas en el periodo seleccionado.", margin, y, paintMuted)
        } else {
            val colFacturasX = margin + contentWidth * 0.55f
            fun encabezadoTabla() {
                val headerH = 18f
                rr(RectF(margin, y, margin + contentWidth, y + headerH), 6f, cNeutralBg)
                val pHead = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawText("FECHA", margin + 10f, y + 12f, pHead)
                canvas.drawText("FACTURAS", colFacturasX, y + 12f, pHead)
                val pHeadR = Paint(pHead).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("TOTAL", margin + contentWidth - 10f, y + 12f, pHeadR)
                y += headerH + 3f
            }
            encabezadoTabla()
            diasTabla.forEachIndexed { i, d ->
                if (y + 18f > pageHeight - 34) {
                    nuevaPagina()
                    encabezadoTabla()
                }
                if (i % 2 == 0) rr(RectF(margin, y, margin + contentWidth, y + 16f), 4f, Color.parseColor("#F8FAFC"))
                val pRow = Paint().apply { color = cDark; textSize = 9f; isAntiAlias = true }
                canvas.drawText("${d.label} (${d.diaSemana})", margin + 10f, y + 11f, pRow)
                canvas.drawText("${d.cantidad}", colFacturasX, y + 11f, pRow)
                val pRowR = Paint(pRow).apply { textAlign = Paint.Align.RIGHT; isFakeBoldText = true }
                canvas.drawText("$${fmtMoney(d.total)}", margin + contentWidth - 10f, y + 11f, pRowR)
                y += 16f
            }
        }

        doc.finishPage(page)

        val carpeta = File(cacheDir, "reportes").apply { if (!exists()) mkdirs() }
        val nombreArchivo = "rendimiento_${negocioNombre.replace(Regex("[^a-zA-Z0-9 ]"), "").trim().replace(" ", "_").take(30)}_${periodoDias}d.pdf"
        val archivo = File(carpeta, nombreArchivo)
        FileOutputStream(archivo).use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    private fun generarPdfDirectorioClientes(): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - margin * 2

        val cNavy = Color.parseColor("#0F172A")
        val cNavySubtitle = Color.parseColor("#CBD5E1")
        val cNeutralBg = Color.parseColor("#F1F5F9")
        val cDark = Color.parseColor("#0F172A")
        val cMuted = Color.parseColor("#64748B")
        val cBorder = Color.parseColor("#E2E8F0")

        val doc = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = 0f

        fun rr(rect: RectF, radius: Float, color: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
            canvas.drawRoundRect(rect, radius, radius, p)
        }

        fun rrBorde(rect: RectF, radius: Float, color: Int, ancho: Float = 1f) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = ancho }
            canvas.drawRoundRect(rect, radius, radius, p)
        }

        fun dibujarPieDePagina(currentNum: Int) {
            val pFoot = Paint().apply { color = cMuted; textSize = 8f; isAntiAlias = true }
            canvas.drawText("Dilo · Reporte de Clientes", margin, pageHeight - 16f, pFoot)
            val pFootR = Paint(pFoot).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Página $currentNum", pageWidth - margin, pageHeight - 16f, pFootR)
        }

        dibujarPieDePagina(pageNum)

        fun nuevaPagina() {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            dibujarPieDePagina(pageNum)
            y = 30f
        }

        fun asegurarEspacio(necesario: Float) {
            if (y + necesario > pageHeight - 40) nuevaPagina()
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, Paint().apply { color = cNavy })
        canvas.drawText("Directorio de Clientes", margin, 34f, Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true; isAntiAlias = true })
        canvas.drawText(negocioNombre, margin, 52f, Paint().apply { color = cNavySubtitle; textSize = 10.5f; isAntiAlias = true })
        val sdf = java.text.SimpleDateFormat("d/M/yyyy, h:mm:ss a", Locale("es", "EC"))
        canvas.drawText("Generado: ${sdf.format(java.util.Date())}", margin, 68f, Paint().apply { color = cNavySubtitle; textSize = 8.5f; isAntiAlias = true })

        y = 116f

        val totalClientes = reporteClientesCompleto.size
        val conDeuda = reporteClientesCompleto.count { it.saldoPendiente > 0 }
        val totalPorCobrar = reporteClientesCompleto.sumOf { it.saldoPendiente }

        val kpiH = 46f
        asegurarEspacio(kpiH + 15f)
        val rectKpi = RectF(margin, y, margin + contentWidth, y + kpiH)
        rr(rectKpi, 8f, Color.WHITE)
        rrBorde(rectKpi, 8f, cBorder, 1f)

        val wTercio = contentWidth / 3f
        val pKpiHead = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val pKpiVal = Paint().apply { color = cDark; textSize = 13f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        canvas.drawText("TOTAL CLIENTES", margin + wTercio * 0.5f, y + 15f, pKpiHead)
        canvas.drawText("CON DEUDA", margin + wTercio * 1.5f, y + 15f, pKpiHead)
        canvas.drawText("POR COBRAR", margin + wTercio * 2.5f, y + 15f, pKpiHead)

        canvas.drawText(totalClientes.toString(), margin + wTercio * 0.5f, y + 34f, pKpiVal)
        canvas.drawText(conDeuda.toString(), margin + wTercio * 1.5f, y + 34f, pKpiVal)
        canvas.drawText("$${fmtMoney(totalPorCobrar)}", margin + wTercio * 2.5f, y + 34f, Paint(pKpiVal).apply { color = Color.parseColor("#EF4444") })

        y += kpiH + 20f

        asegurarEspacio(20f)
        canvas.drawText("Listado General de Clientes y Saldos", margin, y, Paint().apply { color = cDark; textSize = 13f; isFakeBoldText = true; isAntiAlias = true })
        y += 14f

        val headerH = 18f
        asegurarEspacio(headerH + 6f)
        rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
        val pHC = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText("CLIENTE / CÉDULA", margin + 10f, y + 12f, pHC)
        canvas.drawText("FACTURAS", margin + 240f, y + 12f, pHC)
        val pHR = Paint(pHC).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("TOTAL FACTURADO", margin + contentWidth - 110f, y + 12f, pHR)
        canvas.drawText("SALDO PENDIENTE", margin + contentWidth - 10f, y + 12f, pHR)
        y += headerH + 4f

        reporteClientesCompleto.forEachIndexed { i, c ->
            val rowH = 26f
            if (y + rowH > pageHeight - 40) {
                nuevaPagina()
                rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
                canvas.drawText("CLIENTE / CÉDULA", margin + 10f, y + 12f, pHC)
                canvas.drawText("FACTURAS", margin + 240f, y + 12f, pHC)
                canvas.drawText("TOTAL FACTURADO", margin + contentWidth - 110f, y + 12f, pHR)
                canvas.drawText("SALDO PENDIENTE", margin + contentWidth - 10f, y + 12f, pHR)
                y += headerH + 4f
            }
            if (i % 2 == 0) rr(RectF(margin, y, margin + contentWidth, y + rowH), 3f, Color.parseColor("#F8FAFC"))

            val pRow = Paint().apply { color = cDark; textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText(c.nombre, margin + 10f, y + 11f, pRow)

            val pSub = Paint().apply { color = cMuted; textSize = 8f; isAntiAlias = true }
            val idTxt = if (!c.identificacion.isNullOrBlank()) "CI/RUC: ${c.identificacion}" else "Sin identificación"
            canvas.drawText(idTxt, margin + 10f, y + 21f, pSub)

            val pRowNormal = Paint().apply { color = cDark; textSize = 9f; isAntiAlias = true }
            canvas.drawText(c.numFacturas.toString(), margin + 240f, y + 16f, pRowNormal)

            val pRowR = Paint(pRowNormal).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("$${fmtMoney(c.totalFacturado)}", margin + contentWidth - 110f, y + 16f, pRowR)

            val deudaTxt = if (c.saldoPendiente > 0) "$${fmtMoney(c.saldoPendiente)}" else "$0.00"
            canvas.drawText(deudaTxt, margin + contentWidth - 10f, y + 16f, Paint(pRowR).apply {
                color = if (c.saldoPendiente > 0) Color.parseColor("#EF4444") else cDark
                isFakeBoldText = true
            })

            y += rowH
        }

        doc.finishPage(page)

        val carpeta = File(cacheDir, "reportes").apply { if (!exists()) mkdirs() }
        val archivo = File(carpeta, "Reporte_General_Clientes.pdf")
        FileOutputStream(archivo).use { doc.writeTo(it) }
        doc.close()

        return archivo
    }

    private fun abrirPdf(archivo: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir PDF"))
        }
    }

    private fun exportarPdfCliente(cliente: ClienteReporteDto) {
        if (exportandoPdf || isLoading) return
        exportandoPdf = true

        Toast.makeText(this, "Generando PDF de cliente...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val archivo = runCatching { generarPdfIndividualCliente(cliente) }.getOrNull()
            withContext(Dispatchers.Main) {
                exportandoPdf = false
                if (archivo != null) {
                    abrirPdf(archivo)
                } else {
                    Toast.makeText(this@RendimientoComercialActivity, "Error al generar el PDF del cliente.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generarPdfIndividualCliente(cliente: ClienteReporteDto): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - margin * 2

        val cNavy = Color.parseColor("#0F172A")
        val cNavySubtitle = Color.parseColor("#CBD5E1")
        val cNeutralBg = Color.parseColor("#F1F5F9")
        val cDark = Color.parseColor("#0F172A")
        val cMuted = Color.parseColor("#64748B")
        val cBorder = Color.parseColor("#E2E8F0")

        val doc = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = 0f

        fun rr(rect: RectF, radius: Float, color: Int) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
            canvas.drawRoundRect(rect, radius, radius, p)
        }

        fun rrBorde(rect: RectF, radius: Float, color: Int, ancho: Float = 1f) {
            val p = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = ancho }
            canvas.drawRoundRect(rect, radius, radius, p)
        }

        fun dibujarPieDePagina(currentNum: Int) {
            val pFoot = Paint().apply { color = cMuted; textSize = 8f; isAntiAlias = true }
            canvas.drawText("Dilo", margin, pageHeight - 16f, pFoot)
            val pFootR = Paint(pFoot).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Página $currentNum", pageWidth - margin, pageHeight - 16f, pFootR)
        }

        dibujarPieDePagina(pageNum)

        fun nuevaPagina() {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            dibujarPieDePagina(pageNum)
            y = 30f
        }

        fun asegurarEspacio(necesario: Float) {
            if (y + necesario > pageHeight - 40) nuevaPagina()
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, Paint().apply { color = cNavy })
        val paintTitle = Paint().apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText("Reporte Individual de Cliente", margin, 34f, paintTitle)
        canvas.drawText(negocioNombre, margin, 52f, Paint().apply { color = cNavySubtitle; textSize = 10.5f; isAntiAlias = true })
        val sdf = java.text.SimpleDateFormat("d/M/yyyy, h:mm:ss a", Locale("es", "EC"))
        canvas.drawText("Generado: ${sdf.format(java.util.Date())}", margin, 68f, Paint().apply { color = cNavySubtitle; textSize = 8.5f; isAntiAlias = true })

        y = 116f

        canvas.drawText(cliente.nombre, margin, y, Paint().apply { color = cDark; textSize = 16f; isFakeBoldText = true; isAntiAlias = true })
        y += 14f
        val identificacionTxt = if (!cliente.identificacion.isNullOrBlank()) "CI/RUC: ${cliente.identificacion}" else "CI/RUC: S/N"
        canvas.drawText(identificacionTxt, margin, y, Paint().apply { color = cMuted; textSize = 11f; isAntiAlias = true })
        y += 20f

        val kpiH = 46f
        asegurarEspacio(kpiH + 10f)
        val rectKpi = RectF(margin, y, margin + contentWidth, y + kpiH)
        rr(rectKpi, 8f, Color.WHITE)
        rrBorde(rectKpi, 8f, cBorder, 1f)

        val wTercio = contentWidth / 3f
        val pKpiHead = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }
        val pKpiVal = Paint().apply { color = cDark; textSize = 13f; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.CENTER }

        canvas.drawText("Facturas Emitidas", margin + wTercio * 0.5f, y + 15f, pKpiHead)
        canvas.drawText("Total Facturado", margin + wTercio * 1.5f, y + 15f, pKpiHead)
        canvas.drawText("Saldo Pendiente", margin + wTercio * 2.5f, y + 15f, pKpiHead)

        canvas.drawText("${cliente.numFacturas}", margin + wTercio * 0.5f, y + 34f, pKpiVal)
        canvas.drawText("$${fmtMoney(cliente.totalFacturado)}", margin + wTercio * 1.5f, y + 34f, pKpiVal)
        canvas.drawText("$${fmtMoney(cliente.saldoPendiente)}", margin + wTercio * 2.5f, y + 34f, pKpiVal)

        y += kpiH + 20f
        val sdfShort = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.US)

        if (cliente.facturas.isNotEmpty()) {
            asegurarEspacio(30f)
            canvas.drawText("Historial de Facturas", margin, y, Paint().apply { color = cDark; textSize = 13f; isFakeBoldText = true; isAntiAlias = true })
            y += 14f

            val headerH = 18f
            asegurarEspacio(headerH + 6f)
            rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
            val pHC = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText("COMPROBANTE", margin + 10f, y + 12f, pHC)
            canvas.drawText("FECHA", margin + 130f, y + 12f, pHC)
            canvas.drawText("FORMA PAGO", margin + 220f, y + 12f, pHC)
            val pHR = Paint(pHC).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("ESTADO", margin + contentWidth - 85f, y + 12f, pHR)
            canvas.drawText("TOTAL", margin + contentWidth - 10f, y + 12f, pHR)
            y += headerH + 4f

            cliente.facturas.sortedByDescending { f -> try { sdfShort.parse(f.fecha ?: "")?.time ?: 0L } catch(e: Exception) { 0L } }.forEachIndexed { i, f ->
                val rowH = 16f
                if (y + rowH > pageHeight - 40) {
                    nuevaPagina()
                    rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
                    canvas.drawText("COMPROBANTE", margin + 10f, y + 12f, pHC)
                    canvas.drawText("FECHA", margin + 130f, y + 12f, pHC)
                    canvas.drawText("FORMA PAGO", margin + 220f, y + 12f, pHC)
                    canvas.drawText("ESTADO", margin + contentWidth - 85f, y + 12f, pHR)
                    canvas.drawText("TOTAL", margin + contentWidth - 10f, y + 12f, pHR)
                    y += headerH + 4f
                }
                if (i % 2 == 0) rr(RectF(margin, y, margin + contentWidth, y + rowH), 3f, Color.parseColor("#F8FAFC"))
                val pRow = Paint().apply { color = cDark; textSize = 9f; isAntiAlias = true }
                canvas.drawText("#${f.numero}", margin + 10f, y + 11f, pRow)
                canvas.drawText(f.fecha ?: "-", margin + 130f, y + 11f, pRow)
                canvas.drawText(f.tipo ?: "OTRO", margin + 220f, y + 11f, pRow)
                val pRowR = Paint(pRow).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(f.estado ?: "-", margin + contentWidth - 85f, y + 11f, pRowR)
                canvas.drawText("$${fmtMoney(f.monto)}", margin + contentWidth - 10f, y + 11f, Paint(pRowR).apply { isFakeBoldText = true })
                y += rowH
            }
            y += 16f
        }

        if (cliente.creditos.isNotEmpty()) {
            asegurarEspacio(30f)
            canvas.drawText("Cuentas de Crédito / Saldos Pendientes", margin, y, Paint().apply { color = cDark; textSize = 13f; isFakeBoldText = true; isAntiAlias = true })
            y += 14f

            val headerH = 18f
            asegurarEspacio(headerH + 6f)
            rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
            val pHC = Paint().apply { color = cMuted; textSize = 8f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText("REF. FACTURA", margin + 10f, y + 12f, pHC)
            canvas.drawText("VENCIMIENTO", margin + 130f, y + 12f, pHC)
            canvas.drawText("ESTADO", margin + 220f, y + 12f, pHC)
            val pHR = Paint(pHC).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("MONTO ORIGINAL", margin + contentWidth - 85f, y + 12f, pHR)
            canvas.drawText("DEUDA ACTUAL", margin + contentWidth - 10f, y + 12f, pHR)
            y += headerH + 4f

            cliente.creditos.sortedByDescending { c -> try { sdfShort.parse(c.fechaVencimiento ?: "")?.time ?: 0L } catch(e: Exception) { 0L } }.forEachIndexed { i, c ->
                val rowH = 16f
                if (y + rowH > pageHeight - 40) {
                    nuevaPagina()
                    rr(RectF(margin, y, margin + contentWidth, y + headerH), 4f, cNeutralBg)
                    canvas.drawText("REF. FACTURA", margin + 10f, y + 12f, pHC)
                    canvas.drawText("VENCIMIENTO", margin + 130f, y + 12f, pHC)
                    canvas.drawText("ESTADO", margin + 220f, y + 12f, pHC)
                    canvas.drawText("MONTO ORIGINAL", margin + contentWidth - 85f, y + 12f, pHR)
                    canvas.drawText("DEUDA ACTUAL", margin + contentWidth - 10f, y + 12f, pHR)
                    y += headerH + 4f
                }
                if (i % 2 == 0) rr(RectF(margin, y, margin + contentWidth, y + rowH), 3f, Color.parseColor("#F8FAFC"))
                val pRow = Paint().apply { color = cDark; textSize = 9f; isAntiAlias = true }
                canvas.drawText(c.factura ?: "S/N", margin + 10f, y + 11f, pRow)
                canvas.drawText(c.fechaVencimiento ?: "-", margin + 130f, y + 11f, pRow)
                canvas.drawText(c.estado ?: "PENDIENTE", margin + 220f, y + 11f, pRow)
                val pRowR = Paint(pRow).apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText("$${fmtMoney(c.montoTotal)}", margin + contentWidth - 85f, y + 11f, pRowR)
                canvas.drawText("$${fmtMoney(c.saldoPendiente)}", margin + contentWidth - 10f, y + 11f, Paint(pRowR).apply { isFakeBoldText = true })
                y += rowH
            }
        }

        doc.finishPage(page)

        val carpeta = File(cacheDir, "reportes").apply { if (!exists()) mkdirs() }
        val nombreLimpio = cliente.nombre.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
        val archivo = File(carpeta, "Estado_Cuenta_$nombreLimpio.pdf")
        FileOutputStream(archivo).use { doc.writeTo(it) }
        doc.close()

        return archivo
    }
}