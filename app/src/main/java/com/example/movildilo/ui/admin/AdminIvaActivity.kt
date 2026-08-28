package com.example.movildilo.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.negocio.IvaRequestDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class AdminIvaActivity : AppCompatActivity() {

    private lateinit var btnRegresar: MaterialButton
    private lateinit var tvIvaActual: TextView
    private lateinit var etNuevoIva: TextInputEditText
    private lateinit var tilNuevoIva: TextInputLayout
    private lateinit var btnActualizarIva: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var sessionManager: SessionManager
    private var ivaActualDecimal: Double = 0.15

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_iva)

        sessionManager = SessionManager(this)

        initViews()
        btnRegresar.setOnClickListener { finish() }
        btnActualizarIva.setOnClickListener { confirmarActualizarIva() }

        swipeRefreshLayout.setOnRefreshListener {
            cargarIvaActual()
        }

        cargarIvaActual()
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        tvIvaActual = findViewById(R.id.tvIvaActual)
        etNuevoIva = findViewById(R.id.etNuevoIva)
        tilNuevoIva = findViewById(R.id.tilNuevoIva)
        btnActualizarIva = findViewById(R.id.btnActualizarIva)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
    }

    private fun cargarIvaActual() {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            swipeRefreshLayout.isRefreshing = false
            return
        }
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getIva(authHeader)
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                if (response.isSuccessful) {
                    val bodyMap = response.body()
                    val ivaTexto = bodyMap?.get("ivaActual") ?: bodyMap?.get("iva") ?: "0.15"
                    ivaActualDecimal = ivaTexto.toDoubleOrNull() ?: 0.15
                    pintarIvaActual()
                } else {
                    Toast.makeText(this@AdminIvaActivity, "No se pudo cargar el IVA (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this@AdminIvaActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pintarIvaActual() {
        val porcentaje = (ivaActualDecimal * 100).let {
            if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString()
        }
        tvIvaActual.text = "$ivaActualDecimal ($porcentaje%)"
    }

    private fun confirmarActualizarIva() {
        val textoIngresado = etNuevoIva.text.toString().trim()

        val errorIva = FormValidator.numeroDecimal(textoIngresado, "El nuevo IVA", minimo = 0.0, maximo = 1.0)
        FormValidator.marcarError(tilNuevoIva, errorIva)
        if (errorIva != null) {
            tilNuevoIva.requestFocus()
            return
        }
        val nuevoIvaDecimal = textoIngresado.toDouble()

        val nuevoPorcentaje = nuevoIvaDecimal * 100

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Actualizar el IVA global?")
            .setMessage("El IVA cambiará a $nuevoPorcentaje%. Esto afectará a todos los negocios del sistema.")
            .setPositiveButton("Sí, actualizar") { d, _ ->
                d.dismiss()
                ejecutarActualizacionIva(textoIngresado, nuevoIvaDecimal)
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun ejecutarActualizacionIva(nuevoIvaTexto: String, nuevoIvaDecimal: Double) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        progressBar.visibility = View.VISIBLE
        btnActualizarIva.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.actualizarIva(
                    authHeader,
                    IvaRequestDto(nuevoIva = nuevoIvaTexto)
                )
                progressBar.visibility = View.GONE
                btnActualizarIva.isEnabled = true

                if (response.isSuccessful) {
                    ivaActualDecimal = nuevoIvaDecimal
                    pintarIvaActual()
                    etNuevoIva.setText("")
                    Toast.makeText(this@AdminIvaActivity, "¡IVA actualizado con éxito!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AdminIvaActivity, "No se pudo actualizar (código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                btnActualizarIva.isEnabled = true
                Toast.makeText(this@AdminIvaActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}