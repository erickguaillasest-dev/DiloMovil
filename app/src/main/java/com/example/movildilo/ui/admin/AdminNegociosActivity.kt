package com.example.movildilo.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.negocio.NegocioResponseDto
import com.example.movildilo.ui.adapters.NegocioAdminAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

interface OnNegocioActualizadoListener {
    fun onNegocioActualizado()
}

class AdminNegociosActivity : AppCompatActivity(), OnNegocioActualizadoListener {

    private lateinit var btnRegresar: MaterialButton
    private lateinit var etBuscar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var rvNegocios: RecyclerView

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: NegocioAdminAdapter

    private var listaCompleta = mutableListOf<NegocioResponseDto>()
    private var listaFiltrada = mutableListOf<NegocioResponseDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_negocios)

        sessionManager = SessionManager(this)

        initViews()
        setupRecyclerView()

        btnRegresar.setOnClickListener { finish() }

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { aplicarFiltro() }
            override fun afterTextChanged(s: Editable?) {}
        })

        cargarNegocios()
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        etBuscar = findViewById(R.id.etBuscar)
        progressBar = findViewById(R.id.progressBar)
        rvNegocios = findViewById(R.id.rvNegocios)
    }

    private fun setupRecyclerView() {
        adapter = NegocioAdminAdapter(
            lista = listaFiltrada,
            onEditar = { negocio -> abrirDialogoEditar(negocio) },
            onEliminar = { negocio -> confirmarEliminar(negocio) }
        )
        rvNegocios.layoutManager = LinearLayoutManager(this)
        rvNegocios.adapter = adapter
    }

    private fun cargarNegocios() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getAllNegocios(authHeader)
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    listaCompleta = (response.body() ?: emptyList()).toMutableList()
                    aplicarFiltro()
                } else {
                    Toast.makeText(this@AdminNegociosActivity, "No se pudieron cargar los negocios (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AdminNegociosActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun aplicarFiltro() {
        val texto = etBuscar.text.toString().trim().lowercase(Locale.ROOT)
        listaFiltrada = if (texto.isEmpty()) {
            listaCompleta.toMutableList()
        } else {
            listaCompleta.filter {
                (it.razonSocial?.lowercase(Locale.ROOT)?.contains(texto) == true) ||
                        (it.nombreComercial?.lowercase(Locale.ROOT)?.contains(texto) == true) ||
                        (it.ruc?.lowercase(Locale.ROOT)?.contains(texto) == true)
            }.toMutableList()
        }
        adapter.actualizarLista(listaFiltrada)
    }

    private fun abrirDialogoEditar(negocio: NegocioResponseDto) {
        val dialog = EditarNegocioDialog.newInstance(negocio)
        dialog.show(supportFragmentManager, "EditarNegocioDialog")
    }

    private fun confirmarEliminar(negocio: NegocioResponseDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Estás seguro?")
            .setMessage("Esta acción eliminará permanentemente a \"${negocio.razonSocial}\" y no se puede deshacer.")
            .setPositiveButton("Sí, eliminar") { d, _ ->
                d.dismiss()
                ejecutarEliminacion(negocio)
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun ejecutarEliminacion(negocio: NegocioResponseDto) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        val id = negocio.id ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Se utiliza eliminarNegocio unificado de ApiService
                val response = RetrofitClient.apiService.eliminarNegocio(authHeader, id)
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    Toast.makeText(this@AdminNegociosActivity, "Negocio eliminado", Toast.LENGTH_SHORT).show()
                    cargarNegocios()
                } else {
                    Toast.makeText(this@AdminNegociosActivity, "No se pudo eliminar (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AdminNegociosActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNegocioActualizado() {
        cargarNegocios()
    }
}