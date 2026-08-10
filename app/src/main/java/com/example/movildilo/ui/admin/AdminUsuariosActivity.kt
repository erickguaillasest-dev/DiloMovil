package com.example.movildilo.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.example.movildilo.data.model.dto.UsuarioMeDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

interface OnUsuarioActualizadoListener {
    fun onUsuarioActualizado()
}

class AdminUsuariosActivity : AppCompatActivity(), OnUsuarioActualizadoListener {

    private lateinit var mainRoot: View
    private lateinit var btnRegresar: MaterialButton
    private lateinit var btnNuevoUsuario: MaterialButton
    private lateinit var etBuscar: EditText
    private lateinit var tvTotalUsuarios: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvUsuarios: RecyclerView

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: UsuariosAdapter

    private var listaCompleta = mutableListOf<UsuarioMeDto>()
    private var listaFiltrada = mutableListOf<UsuarioMeDto>()

    var parroquias: MutableList<ParroquiaResponseDto> = mutableListOf()
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_usuarios)

        sessionManager = SessionManager(this)

        initViews()
        setupEdgeToEdgeInsets()
        setupRecyclerView()

        btnRegresar.setOnClickListener { finish() }
        btnNuevoUsuario.setOnClickListener {
            UsuarioDialog.newInstanceCrear().show(supportFragmentManager, "UsuarioDialog")
        }

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltro()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cargarParroquias()
        cargarUsuarios()
    }

    private fun initViews() {
        mainRoot = findViewById(R.id.mainRoot)
        btnRegresar = findViewById(R.id.btnRegresar)
        btnNuevoUsuario = findViewById(R.id.btnNuevoUsuario)
        etBuscar = findViewById(R.id.etBuscar)
        tvTotalUsuarios = findViewById(R.id.tvTotalUsuarios)
        progressBar = findViewById(R.id.progressBar)
        rvUsuarios = findViewById(R.id.rvUsuarios)
    }

    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val headerLayout = findViewById<View>(R.id.headerLayout)
            headerLayout.setPadding(
                headerLayout.paddingLeft,
                systemBars.top + 12,
                headerLayout.paddingRight,
                headerLayout.paddingBottom
            )
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = UsuariosAdapter(
            lista = listaFiltrada,
            onClickUsuario = { usuario ->
                UsuarioDialog.newInstanceEditar(usuario).show(supportFragmentManager, "UsuarioDialog")
            }
        )
        rvUsuarios.layoutManager = LinearLayoutManager(this)
        rvUsuarios.adapter = adapter
    }

    private fun cargarParroquias() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getParroquias()
                if (response.isSuccessful) {
                    parroquias = (response.body() ?: emptyList()).toMutableList()
                }
            } catch (_: Exception) {
                // Si falla la carga, la app continúa funcional
            }
        }
    }

    private fun cargarUsuarios() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getAllUsuarios(authHeader)
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    listaCompleta = (response.body() ?: emptyList()).toMutableList()
                    aplicarFiltro()
                } else {
                    Toast.makeText(
                        this@AdminUsuariosActivity,
                        "No se pudieron cargar los usuarios (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@AdminUsuariosActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun aplicarFiltro() {
        val query = FormValidator.normalizar(etBuscar.text.toString())

        listaFiltrada = if (query.isEmpty()) {
            listaCompleta.toMutableList()
        } else {
            listaCompleta.filter { u ->
                FormValidator.normalizar(u.primerNombre).contains(query) ||
                        FormValidator.normalizar(u.segundoNombre).contains(query) ||
                        FormValidator.normalizar(u.apellidoPaterno).contains(query) ||
                        FormValidator.normalizar(u.apellidoMaterno).contains(query) ||
                        FormValidator.normalizar(u.dni).contains(query) ||
                        FormValidator.normalizar(u.email).contains(query)
            }.toMutableList()
        }

        tvTotalUsuarios.text = "${listaFiltrada.size} usuarios"
        adapter.actualizarLista(listaFiltrada)
    }

    override fun onUsuarioActualizado() {
        cargarUsuarios()
    }
}