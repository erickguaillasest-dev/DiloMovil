package com.example.movildilo.ui.propietario

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.CuentaPorCobrarResponseDto
import com.example.movildilo.data.model.dto.PagoRequestDto
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CuentasPorCobrarActivity : AppCompatActivity() {

    private lateinit var btnRegresar: ImageButton
    private lateinit var rvCuentas: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutVacio: View
    private lateinit var layoutSinResultados: View

    private lateinit var tvTotalPorCobrar: TextView
    private lateinit var tvTotalAbonado: TextView
    private lateinit var tvCuentasVencidas: TextView

    private lateinit var etBuscar: TextInputEditText
    private lateinit var chipTodas: TextView
    private lateinit var chipPendiente: TextView
    private lateinit var chipVencida: TextView
    private lateinit var chipPagada: TextView

    private lateinit var etFechaDesde: TextInputEditText
    private lateinit var etFechaHasta: TextInputEditText
    private lateinit var btnLimpiarFechas: ImageButton

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CuentasPorCobrarAdapter
    private var negocioId: Long = -1L

    private var cuentasBase: List<CuentaPorCobrarResponseDto> = emptyList()

    private var terminoBusqueda: String = ""
    private var filtroEstado: String = "TODAS"
    private var filtroFechaDesde: String = ""
    private var filtroFechaHasta: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cuentas_por_cobrar)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        setupFiltros()

        btnRegresar.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (negocioId != -1L) {
            cargarCuentas()
        } else {
            Toast.makeText(this, "No se encontró el negocio activo", Toast.LENGTH_SHORT).show()
            mostrarEstadoVacio()
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        rvCuentas = findViewById(R.id.rvCuentas)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutVacio = findViewById(R.id.layoutVacio)
        layoutSinResultados = findViewById(R.id.layoutSinResultados)

        tvTotalPorCobrar = findViewById(R.id.tvTotalPorCobrar)
        tvTotalAbonado = findViewById(R.id.tvTotalAbonado)
        tvCuentasVencidas = findViewById(R.id.tvCuentasVencidas)

        etBuscar = findViewById(R.id.etBuscar)
        chipTodas = findViewById(R.id.chipTodas)
        chipPendiente = findViewById(R.id.chipPendiente)
        chipVencida = findViewById(R.id.chipVencida)
        chipPagada = findViewById(R.id.chipPagada)

        etFechaDesde = findViewById(R.id.etFechaDesde)
        etFechaHasta = findViewById(R.id.etFechaHasta)
        btnLimpiarFechas = findViewById(R.id.btnLimpiarFechas)
    }

    private fun setupRecyclerView() {
        adapter = CuentasPorCobrarAdapter(emptyList()) { cuenta ->
            mostrarModalPago(cuenta)
        }
        rvCuentas.layoutManager = LinearLayoutManager(this)
        rvCuentas.adapter = adapter
    }

    private fun setupFiltros() {
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                terminoBusqueda = s?.toString() ?: ""
                aplicarFiltros()
            }
        })

        chipTodas.setOnClickListener { setFiltro("TODAS") }
        chipPendiente.setOnClickListener { setFiltro("PENDIENTE") }
        chipVencida.setOnClickListener { setFiltro("VENCIDA") }
        chipPagada.setOnClickListener { setFiltro("PAGADA") }

        etFechaDesde.setOnClickListener {
            mostrarDatePicker { fecha ->
                filtroFechaDesde = fecha
                etFechaDesde.setText(fecha)
                aplicarFiltros()
            }
        }

        etFechaHasta.setOnClickListener {
            mostrarDatePicker { fecha ->
                filtroFechaHasta = fecha
                etFechaHasta.setText(fecha)
                aplicarFiltros()
            }
        }

        btnLimpiarFechas.setOnClickListener {
            filtroFechaDesde = ""
            filtroFechaHasta = ""
            etFechaDesde.setText("")
            etFechaHasta.setText("")
            aplicarFiltros()
        }

        actualizarChipsActivos()
    }

    private fun mostrarDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                onDateSelected(sdf.format(cal.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun setFiltro(estado: String) {
        filtroEstado = estado
        actualizarChipsActivos()
        aplicarFiltros()
    }

    private fun actualizarChipsActivos() {
        val activo = Color.parseColor("#EA580C")
        val inactivo = Color.parseColor("#64748B")
        val cornerRadiusPx = 8f * resources.displayMetrics.density

        val listaChips = listOf(
            chipTodas to "TODAS",
            chipPendiente to "PENDIENTE",
            chipVencida to "VENCIDA",
            chipPagada to "PAGADA"
        )

        listaChips.forEach { (chip, valor) ->
            chip?.let { view ->
                val esActivo = filtroEstado == valor
                view.setTextColor(if (esActivo) activo else inactivo)
                if (esActivo) {
                    view.background = GradientDrawable().apply {
                        cornerRadius = cornerRadiusPx
                        setColor(Color.WHITE)
                    }
                } else {
                    view.background = null
                }
            }
        }
    }

    /** Igual que obtenerNombreCliente() en la web: nombreCliente -> nombreCompleto -> nombre -> razonSocial -> primerNombre+apellidoPaterno */
    private fun obtenerNombreClienteBusqueda(c: CuentaPorCobrarResponseDto): String {
        val directo = c.clienteNombre?.trim()
        if (!directo.isNullOrEmpty()) return directo

        val cl = c.factura?.cliente
        return cl?.nombreCompleto?.trim()?.takeIf { it.isNotEmpty() }
            ?: cl?.nombre?.trim()?.takeIf { it.isNotEmpty() }
            ?: cl?.razonSocial?.trim()?.takeIf { it.isNotEmpty() }
            ?: cl?.let { "${it.primerNombre ?: ""} ${it.apellidoPaterno ?: ""}".trim() }?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    private fun aplicarFiltros() {
        var filtradas = cuentasBase

        // 1. Filtro por Estado
        if (filtroEstado != "TODAS") {
            filtradas = filtradas.filter { c ->
                c.estado?.equals(filtroEstado, ignoreCase = true) == true
            }
        }

        // 2. Búsqueda por texto (Factura o Cliente, incluye DNI si viene en el nombre)
        val term = terminoBusqueda.trim().lowercase(Locale.getDefault())
        if (term.isNotEmpty()) {
            filtradas = filtradas.filter { c ->
                val numero = (c.numeroFactura ?: c.factura?.numeroFactura ?: "").lowercase(Locale.getDefault())
                val cliente = obtenerNombreClienteBusqueda(c).lowercase(Locale.getDefault())

                numero.contains(term) || cliente.contains(term)
            }
        }

        // 3. Filtro por Rango de Fechas de Vencimiento
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        if (filtroFechaDesde.isNotEmpty()) {
            try {
                val dateDesde = sdf.parse(filtroFechaDesde)
                if (dateDesde != null) {
                    filtradas = filtradas.filter { c ->
                        val fvStr = c.fechaVencimiento
                        if (fvStr.isNullOrEmpty()) return@filter false
                        val fv = sdf.parse(fvStr) ?: return@filter false
                        !fv.before(dateDesde) // fv >= dateDesde
                    }
                }
            } catch (ignored: Exception) {}
        }

        if (filtroFechaHasta.isNotEmpty()) {
            try {
                val dateHasta = sdf.parse(filtroFechaHasta)
                if (dateHasta != null) {
                    filtradas = filtradas.filter { c ->
                        val fvStr = c.fechaVencimiento
                        if (fvStr.isNullOrEmpty()) return@filter false
                        val fv = sdf.parse(fvStr) ?: return@filter false
                        !fv.after(dateHasta) // fv <= dateHasta
                    }
                }
            } catch (ignored: Exception) {}
        }

        adapter.actualizarLista(filtradas)

        val hayBase = cuentasBase.isNotEmpty()
        rvCuentas.visibility = if (hayBase && filtradas.isNotEmpty()) View.VISIBLE else View.GONE
        layoutSinResultados.visibility = if (hayBase && filtradas.isEmpty()) View.VISIBLE else View.GONE
        layoutVacio.visibility = if (!hayBase) View.VISIBLE else View.GONE
    }

    private fun mostrarEstadoVacio() {
        layoutLoading.visibility = View.GONE
        layoutVacio.visibility = View.VISIBLE
        layoutSinResultados.visibility = View.GONE
        rvCuentas.visibility = View.GONE
        tvTotalPorCobrar.text = "$0.00"
        tvTotalAbonado.text = "$0.00"
        tvCuentasVencidas.text = "0"
    }

    private fun cargarCuentas() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getCuentasPorCobrar(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        cuentasBase = response.body() ?: emptyList()
                        if (cuentasBase.isEmpty()) {
                            mostrarEstadoVacio()
                        } else {
                            calcularEstadisticas(cuentasBase)
                            aplicarFiltros()
                        }
                    } else {
                        val codigo = response.code()
                        Toast.makeText(
                            this@CuentasPorCobrarActivity,
                            "Error al cargar registros ($codigo)",
                            Toast.LENGTH_SHORT
                        ).show()
                        mostrarEstadoVacio()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(
                        this@CuentasPorCobrarActivity,
                        "Sin conexión o datos disponibles",
                        Toast.LENGTH_SHORT
                    ).show()
                    mostrarEstadoVacio()
                }
            }
        }
    }

    private fun calcularEstadisticas(cuentas: List<CuentaPorCobrarResponseDto>) {
        var totalPorCobrar = 0.0
        var totalAbonado = 0.0
        var cuentasVencidasCount = 0

        val hoy = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        cuentas.forEach { c ->
            val monto = c.montoTotal ?: 0.0
            val saldo = c.saldoPendiente ?: 0.0

            totalPorCobrar += saldo
            totalAbonado += (monto - saldo)

            val fechaStr = c.fechaVencimiento
            if (saldo > 0 && !fechaStr.isNullOrEmpty()) {
                try {
                    val dateVence = sdf.parse(fechaStr)
                    if (dateVence != null && dateVence.time < hoy) {
                        cuentasVencidasCount++
                    }
                } catch (ignored: Exception) {}
            }
        }

        tvTotalPorCobrar.text = String.format(Locale.US, "$%.2f", totalPorCobrar)
        tvTotalAbonado.text = String.format(Locale.US, "$%.2f", totalAbonado)
        tvCuentasVencidas.text = cuentasVencidasCount.toString()
    }

    private fun mostrarModalPago(cuenta: CuentaPorCobrarResponseDto) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_registrar_pago, null)
        val tvInfoFactura = dialogView.findViewById<TextView>(R.id.tvInfoFacturaDialog)
        val tvSaldoMaximo = dialogView.findViewById<TextView>(R.id.tvSaldoMaximoDialog)
        val etMonto = dialogView.findViewById<TextInputEditText>(R.id.etMontoAbono)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarDialog)
        val btnConfirmar = dialogView.findViewById<MaterialButton>(R.id.btnConfirmarPagoDialog)

        val numeroFactura = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "S/N"
        val saldoMaximo = cuenta.saldoPendiente ?: 0.0

        tvInfoFactura.text = "Factura #$numeroFactura"
        tvSaldoMaximo.text = String.format(Locale.US, "$%.2f", saldoMaximo)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnConfirmar.setOnClickListener {
            val montoText = etMonto.text.toString().trim()
            val montoAbono = montoText.toDoubleOrNull()

            if (montoAbono == null || montoAbono <= 0) {
                Toast.makeText(this, "Ingresa un monto válido mayor a 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (montoAbono > saldoMaximo) {
                Toast.makeText(this, "El abono no puede superar el saldo pendiente", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            ejecutarPagoApi(cuenta.id, montoAbono)
        }

        dialog.show()
    }

    private fun ejecutarPagoApi(cuentaId: Long, montoAbono: Double) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.registrarPagoCuenta(
                    authHeader,
                    cuentaId,
                    PagoRequestDto(montoPago = montoAbono)
                )
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@CuentasPorCobrarActivity, "¡Pago registrado correctamente!", Toast.LENGTH_SHORT).show()
                        cargarCuentas()
                    } else {
                        Toast.makeText(this@CuentasPorCobrarActivity, "Error al registrar el pago", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@CuentasPorCobrarActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}