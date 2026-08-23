package com.example.movildilo.ui.bodegas

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
import com.example.movildilo.data.api.ApiService
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.ui.Bodegas.BodegaFormDialog
import com.example.movildilo.ui.adapters.BodegaAdapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BodegasActivity : AppCompatActivity() {

    private lateinit var rvBodegas: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var etBuscar: EditText
    private lateinit var adapter: BodegaAdapter

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private var token: String = ""
    private val apiService: ApiService by lazy { RetrofitClient.apiService }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bodegas)


        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()
        token = sessionManager.getAuthHeader() ?: ""

        rvBodegas = findViewById(R.id.rvBodegas)
        progressBar = findViewById(R.id.progressBar)
        etBuscar = findViewById(R.id.etBuscar)

        findViewById<View>(R.id.btnRegresar).setOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupSearch()

        findViewById<View>(R.id.btnNuevaBodega).setOnClickListener {
            abrirModalCrear()
        }

        if (negocioId != -1L && negocioId != 0L && token.isNotEmpty()) {
            cargarBodegas()
        } else {
            Toast.makeText(this, "No se encontró sesión activa o ID del negocio", Toast.LENGTH_SHORT).show()
        }

        if (intent.getStringExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_ACCION) ==
            com.example.movildilo.ia.ZoeActionRouter.Accion.CREAR_BODEGA
        ) {
            rvBodegas.postDelayed({ abrirModalCrear() }, 500)
        }
    }

    private fun setupRecyclerView() {
        adapter = BodegaAdapter(emptyList(),
            onEditar = { bodega -> abrirModalEditar(bodega) },
            onEliminar = { bodega -> eliminarBodega(bodega.id) }
        )

        rvBodegas.layoutManager = LinearLayoutManager(this)
        rvBodegas.adapter = adapter
    }

    private fun cargarBodegas() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.getBodegas(token, negocioId)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        adapter.updateList(response.body() ?: emptyList())
                    } else {
                        Toast.makeText(this@BodegasActivity, "Error al obtener bodegas (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@BodegasActivity, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSearch() {
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    cargarBodegas()
                } else {
                    buscarBodegas(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun buscarBodegas(termino: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = apiService.buscarBodegas(token, negocioId, termino)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        adapter.updateList(response.body() ?: emptyList())
                    } else {
                        adapter.updateList(emptyList())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateList(emptyList())
                }
            }
        }
    }

    private fun abrirModalCrear() {
        BodegaFormDialog(this, lifecycleScope, apiService, token, negocioId, null) {
            cargarBodegas()
        }.show()
    }

    private fun abrirModalEditar(bodega: BodegaDto) {
        BodegaFormDialog(this, lifecycleScope, apiService, token, negocioId, bodega) {
            cargarBodegas()
        }.show()
    }

    private fun eliminarBodega(id: Long) {
        EliminarBodegaDialog(this, lifecycleScope, apiService, token, negocioId, id) {
            cargarBodegas()
        }.show()
    }
}