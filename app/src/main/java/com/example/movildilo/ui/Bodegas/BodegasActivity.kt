package com.example.movildilo.ui.bodegas

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.example.movildilo.R
import com.example.movildilo.data.api.ApiService
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.data.model.dto.BodegaRequest
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

    // Modal Crear
    private fun abrirModalCrear() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bodega, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etNombre = dialogView.findViewById<EditText>(R.id.etDialogNombre)
        val etDireccion = dialogView.findViewById<EditText>(R.id.etDialogDireccion)
        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnDialogConfirmar)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnDialogCancelar)

        tvTitle.text = "Nueva Bodega"
        btnConfirmar.text = "Crear Bodega"

        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnConfirmar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = "El nombre es obligatorio"
                return@setOnClickListener
            }

            val request = BodegaRequest(nombre, if (direccion.isEmpty()) null else direccion)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = apiService.crearBodega(token, negocioId, request)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@BodegasActivity, "¡Bodega Creada!", Toast.LENGTH_SHORT).show()
                            cargarBodegas()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@BodegasActivity, "Error al crear la bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BodegasActivity, "Error de red al crear", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    // Modal Editar
    private fun abrirModalEditar(bodega: BodegaDto) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bodega, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etNombre = dialogView.findViewById<EditText>(R.id.etDialogNombre)
        val etDireccion = dialogView.findViewById<EditText>(R.id.etDialogDireccion)
        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnDialogConfirmar)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnDialogCancelar)

        tvTitle.text = "Editar Bodega"
        btnConfirmar.text = "Guardar Cambios"
        etNombre.setText(bodega.nombre)
        etDireccion.setText(bodega.direccion ?: "")

        // Ajusta el padding inferior cuando sale el teclado (Compatible con Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnConfirmar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = "El nombre no puede estar vacío"
                return@setOnClickListener
            }

            val request = BodegaRequest(nombre, if (direccion.isEmpty()) null else direccion)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = apiService.editarBodega(token, negocioId, bodega.id, request)
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@BodegasActivity, "¡Actualizada!", Toast.LENGTH_SHORT).show()
                            cargarBodegas()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this@BodegasActivity, "Error al actualizar la bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BodegasActivity, "Error de red al actualizar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    // Eliminar Bodega
    private fun eliminarBodega(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("¿Estás seguro?")
            .setMessage("¡Esta acción eliminará la bodega y no se puede deshacer!")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val response = apiService.eliminarBodega(token, negocioId, id)
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                Toast.makeText(this@BodegasActivity, "¡Eliminada!", Toast.LENGTH_SHORT).show()
                                cargarBodegas()
                            } else {
                                Toast.makeText(this@BodegasActivity, "No se pudo eliminar la bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@BodegasActivity, "Error de red al eliminar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}