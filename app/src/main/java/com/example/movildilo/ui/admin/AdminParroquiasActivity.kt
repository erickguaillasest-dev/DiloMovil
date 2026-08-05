package com.example.movildilo.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminParroquiasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var rvParroquias: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var tvContador: TextView
    private lateinit var adapter: ParroquiaAdapter

    private var parroquiasList: List<ParroquiaResponseDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_parroquias)

        sessionManager = SessionManager(this)

        findViewById<MaterialButton>(R.id.btnRegresar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnNuevaParroquia).setOnClickListener { abrirModalCrear() }

        etBuscar = findViewById(R.id.etBuscar)
        tvContador = findViewById(R.id.tvContadorParroquias)
        rvParroquias = findViewById(R.id.rvParroquias)

        rvParroquias.layoutManager = LinearLayoutManager(this)
        adapter = ParroquiaAdapter(
            lista = emptyList(),
            onEditClick = { parroquia -> abrirModalEditar(parroquia) },
            onDeleteClick = { parroquia -> confirmarEliminacion(parroquia) }
        )
        rvParroquias.adapter = adapter

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarParroquias(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cargarParroquias()
    }

    private fun cargarParroquias() {
        val token = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getParroquias(token)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        parroquiasList = response.body() ?: emptyList()
                        filtrarParroquias(etBuscar.text.toString())
                    } else {
                        Toast.makeText(this@AdminParroquiasActivity, "Error al cargar parroquias", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminParroquiasActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filtrarParroquias(texto: String) {
        val query = texto.lowercase().trim()
        val filtradas = if (query.isEmpty()) {
            parroquiasList
        } else {
            parroquiasList.filter { it.nombre.lowercase().contains(query) }
        }
        tvContador.text = "${filtradas.size} parroquias"
        adapter.actualizarLista(filtradas)
    }

    private fun abrirModalCrear() {
        mostrarDialogoFormulario(null)
    }

    private fun abrirModalEditar(parroquia: ParroquiaResponseDto) {
        mostrarDialogoFormulario(parroquia)
    }

    private fun mostrarDialogoFormulario(parroquia: ParroquiaResponseDto?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_parroquia_form, null)
        val etNombre = view.findViewById<EditText>(R.id.etNombreParroquiaDialog)
        val tvTitulo = view.findViewById<TextView>(R.id.tvTituloDialog)

        val esEdicion = parroquia != null
        tvTitulo.text = if (esEdicion) "Editar Parroquia" else "Nueva Parroquia"
        if (esEdicion) {
            etNombre.setText(parroquia?.nombre)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<MaterialButton>(R.id.btnCancelarDialog).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnGuardarDialog).setOnClickListener {
            val nombreInput = etNombre.text.toString().trim()
            if (nombreInput.isEmpty()) {
                etNombre.error = "El nombre de la parroquia es obligatorio"
                return@setOnClickListener
            }
            dialog.dismiss()
            guardarParroquia(parroquia?.id, nombreInput)
        }

        dialog.show()
    }

    private fun guardarParroquia(id: Long?, nombre: String) {
        val token = sessionManager.getAuthHeader() ?: return
        val body = mapOf("nombre" to nombre)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (id != null) {
                    RetrofitClient.apiService.actualizarParroquia(token, id, body)
                } else {
                    RetrofitClient.apiService.crearParroquia(token, body)
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val msj = if (id != null) "Parroquia actualizada correctamente" else "Parroquia creada correctamente"
                        Toast.makeText(this@AdminParroquiasActivity, msj, Toast.LENGTH_SHORT).show()
                        cargarParroquias()
                    } else {
                        Toast.makeText(this@AdminParroquiasActivity, "Error al guardar parroquia", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminParroquiasActivity, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarEliminacion(parroquia: ParroquiaResponseDto) {
        AlertDialog.Builder(this)
            .setTitle("¿Estás seguro?")
            .setMessage("Esta acción no se puede deshacer. Se eliminará la parroquia '${parroquia.nombre}'.")
            .setPositiveButton("Sí, eliminar") { _, _ -> eliminarParroquia(parroquia.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarParroquia(id: Long) {
        val token = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.eliminarParroquia(token, id)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AdminParroquiasActivity, "Parroquia eliminada", Toast.LENGTH_SHORT).show()
                        cargarParroquias()
                    } else {
                        Toast.makeText(
                            this@AdminParroquiasActivity,
                            "No se pudo eliminar la parroquia (puede estar en uso por un usuario).",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminParroquiasActivity, "Error al conectar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}