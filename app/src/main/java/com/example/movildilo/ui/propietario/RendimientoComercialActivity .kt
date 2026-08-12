package com.example.movildilo.ui.propietario

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ClienteTopDto
import com.example.movildilo.data.model.dto.ComparativaItemDto
import com.example.movildilo.data.model.dto.DiaCalorDto
import com.example.movildilo.data.model.dto.DiaSemanaItemDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.FormaPagoItemDto
import com.example.movildilo.data.model.dto.HoraItemDto
import com.example.movildilo.data.model.dto.ProductoDemandaDto
import com.example.movildilo.data.model.dto.SerieDiariaItemDto
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

class RendimientoComercialActivity : AppCompatActivity() {


    private lateinit var chip7: TextView
    private lateinit var chip30: TextView
    private lateinit var chip90: TextView
    private lateinit var btnExportarPdf: com.google.android.material.button.MaterialButton
    private lateinit var loadingContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout

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

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private var negocioNombre: String = "Mi Negocio"
    private var isLoading = true
    private var exportandoPdf = false

    // ---------- Estado / periodo ----------
    private var periodoDias = 30
    private var facturasRaw: List<FacturaResponseDto> = emptyList()

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
        actualizarChipsActivos()

        if (negocioId != -1L) {
            cargarDatos()
        } else {
            mostrarLoading(false)
        }
    }

    private fun initViews() {
        findViewById<View>(R.id.btnRegresar).setOnClickListener { finish() }

        chip7 = findViewById(R.id.chip7)
        chip30 = findViewById(R.id.chip30)
        chip90 = findViewById(R.id.chip90)
        btnExportarPdf = findViewById(R.id.btnExportarPdf)
        loadingContainer = findViewById(R.id.loadingContainer)
        contentContainer = findViewById(R.id.contentContainer)

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
    }

    private fun setupListeners() {
        chip7.setOnClickListener { cambiarPeriodo(7) }
        chip30.setOnClickListener { cambiarPeriodo(30) }
        chip90.setOnClickListener { cambiarPeriodo(90) }
        btnExportarPdf.setOnClickListener { exportarPdf() }
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

    // ---------- Carga de datos ----------
    private fun cargarDatos() {
        mostrarLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val api = RetrofitClient.apiService
            val authHeader = sessionManager.getAuthHeader() ?: ""

            val facturas = runCatching { api.getFacturas(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList()
            val negocio = runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull()?.body()

            facturasRaw = facturas
            if (negocio != null) {
                negocioNombre = negocio.nombreComercial ?: negocio.razonSocial ?: "Mi Negocio"
            }

            procesarMetricas()

            withContext(Dispatchers.Main) {
                renderTodo()
                mostrarLoading(false)
            }
        }
    }

    private fun mostrarLoading(loading: Boolean) {
        isLoading = loading
        loadingContainer.visibility = if (loading) View.VISIBLE else View.GONE
        contentContainer.visibility = if (loading) View.GONE else View.VISIBLE
    }

    // ---------- Procesamiento de métricas (equivalente a procesarMetricas() del web) ----------
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
        val ivIcono = android.widget.ImageView(this).apply {
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
                    Toast.makeText(
                        this@RendimientoComercialActivity,
                        "${dia.diaSemana} ${dia.label}: $${fmtMoney(dia.total)} (${dia.cantidad} facturas)",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun generarPdf(): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 28f
        val contentWidth = pageWidth - margin * 2

        // Paleta de colores (misma que la UI de la app)
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
}