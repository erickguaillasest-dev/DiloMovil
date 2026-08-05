package com.example.movildilo.ui.propietario

import android.os.Bundle
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
import java.util.Locale

class CuentasPorCobrarActivity : AppCompatActivity() {

    private lateinit var btnRegresar: ImageButton
    private lateinit var rvCuentas: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutVacio: View

    private lateinit var tvTotalPorCobrar: TextView
    private lateinit var tvTotalAbonado: TextView
    private lateinit var tvCuentasVencidas: TextView

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CuentasPorCobrarAdapter
    private var negocioId: Long = -1L

    private var cuentasList: List<CuentaPorCobrarResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cuentas_por_cobrar)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()

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

        tvTotalPorCobrar = findViewById(R.id.tvTotalPorCobrar)
        tvTotalAbonado = findViewById(R.id.tvTotalAbonado)
        tvCuentasVencidas = findViewById(R.id.tvCuentasVencidas)
    }

    private fun setupRecyclerView() {
        adapter = CuentasPorCobrarAdapter(emptyList()) { cuenta ->
            mostrarModalPago(cuenta)
        }
        rvCuentas.layoutManager = LinearLayoutManager(this)
        rvCuentas.adapter = adapter
    }

    private fun mostrarEstadoVacio() {
        layoutLoading.visibility = View.GONE
        layoutVacio.visibility = View.VISIBLE
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
                        cuentasList = response.body() ?: emptyList()
                        if (cuentasList.isEmpty()) {
                            mostrarEstadoVacio()
                        } else {
                            layoutVacio.visibility = View.GONE
                            rvCuentas.visibility = View.VISIBLE
                            adapter.actualizarLista(cuentasList)
                            calcularEstadisticas(cuentasList)
                        }
                    } else {
                        val codigo = response.code()
                        if (codigo == 403) {
                            Toast.makeText(
                                this@CuentasPorCobrarActivity,
                                "",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this@CuentasPorCobrarActivity,
                                "No hay registros disponibles o error en servidor ($codigo)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        mostrarEstadoVacio()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@CuentasPorCobrarActivity, "Sin conexión o datos disponibles", Toast.LENGTH_SHORT).show()
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
            val montoText = etMontosValid(etMonto)
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

    private fun etMontosValid(etMonto: TextInputEditText): String {
        return etMonto.text.toString().trim()
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