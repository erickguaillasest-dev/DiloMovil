package com.example.movildilo.ui.propietario

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
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
import com.example.movildilo.data.model.dto.CategoriaDto
import com.example.movildilo.data.model.dto.ProveedorRequestDto
import com.example.movildilo.data.model.dto.ProveedorResponseDto
import com.example.movildilo.ui.adapters.ProveedorAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProveedoresActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L

    private lateinit var rvProveedores: RecyclerView
    private lateinit var adapter: ProveedorAdapter
    private lateinit var etBuscar: TextInputEditText
    private lateinit var spinnerEstado: AutoCompleteTextView
    private lateinit var layoutLoading: FrameLayout
    private lateinit var tvListaVacia: TextView
    private lateinit var btnNuevoProveedor: MaterialButton
    private lateinit var btnRegresar: ImageButton

    // Vistas KPI
    private lateinit var tvKpiTotalProveedores: TextView
    private lateinit var tvKpiProveedoresActivos: TextView

    private var listaProveedoresOriginal: List<ProveedorResponseDto> = emptyList()
    private var listaCategoriasDisponibles: List<CategoriaDto> = emptyList()
    private var estadoFiltroSeleccionado: Int = 0 // 0: Todos, 1: Activos, 2: Inactivos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_proveedores)


        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupSpinner()
        setupListeners()
        cargarDatosBackend()

        if (intent.getStringExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_ACCION) ==
            com.example.movildilo.ia.ZoeActionRouter.Accion.CREAR_PROVEEDOR
        ) {
            rvProveedores.postDelayed({ abrirDialogoProveedor(null) }, 500)
        }
    }

    private fun initViews() {
        rvProveedores = findViewById(R.id.rvProveedores)
        etBuscar = findViewById(R.id.etBuscar)
        spinnerEstado = findViewById(R.id.spinnerEstado)
        layoutLoading = findViewById(R.id.layoutLoading)
        tvListaVacia = findViewById(R.id.tvListaVacia)
        btnNuevoProveedor = findViewById(R.id.btnNuevoProveedor)
        btnRegresar = findViewById(R.id.btnRegresar)

        tvKpiTotalProveedores = findViewById(R.id.tvKpiTotalProveedores)
        tvKpiProveedoresActivos = findViewById(R.id.tvKpiProveedoresActivos)

        rvProveedores.layoutManager = LinearLayoutManager(this)
        adapter = ProveedorAdapter(
            listaProveedores = emptyList(),
            onEditarClick = { proveedor -> abrirDialogoProveedor(proveedor) },
            onEliminarClick = { proveedor -> confirmarEliminacion(proveedor) }
        )
        rvProveedores.adapter = adapter
    }

    private fun setupSpinner() {
        val opciones = arrayOf("Todos", "Activos", "Inactivos")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, opciones)
        spinnerEstado.setAdapter(spinnerAdapter)
        spinnerEstado.setText(opciones[0], false)
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }
        btnNuevoProveedor.setOnClickListener { abrirDialogoProveedor(null) }

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        spinnerEstado.setOnItemClickListener { _, _, position, _ ->
            estadoFiltroSeleccionado = position
            aplicarFiltros()
        }
    }

    private fun cargarDatosBackend() {
        val authHeader = sessionManager.getAuthHeader()

        if (authHeader.isNullOrBlank() || negocioId <= 0) {
            Toast.makeText(this, "Sesión no válida o negocio no seleccionado", Toast.LENGTH_SHORT).show()
            return
        }

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.apiService

                val resProveedores = api.getProveedores(authHeader, negocioId)
                val resCategorias = api.getCategorias(authHeader, negocioId)

                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE

                    if (resCategorias.isSuccessful) {
                        listaCategoriasDisponibles = resCategorias.body() ?: emptyList()
                    }

                    if (resProveedores.isSuccessful) {
                        listaProveedoresOriginal = resProveedores.body() ?: emptyList()
                        aplicarFiltros()
                    } else {
                        Log.e("PROVEEDORES_ERR", "Error HTTP: ${resProveedores.code()}")
                        Toast.makeText(
                            this@ProveedoresActivity,
                            "Error ${resProveedores.code()} al cargar proveedores",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Log.e("PROVEEDORES_EXC", "Error de red", e)
                    Toast.makeText(this@ProveedoresActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun aplicarFiltros() {
        val query = etBuscar.text.toString().trim().lowercase()

        actualizarKpis()

        val filtrados = listaProveedoresOriginal.filter { p ->
            val nombre = p.nombreComercial?.lowercase() ?: ""
            val ruc = p.dni?.lowercase() ?: ""

            val coincideTexto = query.isEmpty() || nombre.contains(query) || ruc.contains(query)

            val coincideEstado = when (estadoFiltroSeleccionado) {
                1 -> p.estado == true
                2 -> p.estado == false
                else -> true
            }

            coincideTexto && coincideEstado
        }

        adapter.actualizarLista(filtrados)
        tvListaVacia.visibility = if (filtrados.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun actualizarKpis() {
        val total = listaProveedoresOriginal.size
        val activos = listaProveedoresOriginal.count { it.estado == true }

        tvKpiTotalProveedores.text = total.toString()
        tvKpiProveedoresActivos.text = activos.toString()
    }

    private fun abrirDialogoProveedor(proveedorParaEditar: ProveedorResponseDto?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_proveedor, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogTitulo)
        val etRuc = dialogView.findViewById<TextInputEditText>(R.id.etRuc)
        val etTelefono = dialogView.findViewById<TextInputEditText>(R.id.etTelefono)
        val etRazonSocial = dialogView.findViewById<TextInputEditText>(R.id.etRazonSocial)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupCategoriasDialog)
        val cbActivo = dialogView.findViewById<MaterialCheckBox>(R.id.cbActivo)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnGuardar = dialogView.findViewById<MaterialButton>(R.id.btnGuardar)

        val esEdicion = proveedorParaEditar != null
        tvTitulo.text = if (esEdicion) "Editar Proveedor" else "Nuevo Proveedor"

        val idsSeleccionados = proveedorParaEditar?.categorias?.mapNotNull { it.id }?.toMutableList() ?: mutableListOf()

        chipGroup.removeAllViews()
        listaCategoriasDisponibles.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat.nombre
                isCheckable = true
                isChecked = idsSeleccionados.contains(cat.id)
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                setOnCheckedChangeListener { _, checked ->
                    cat.id?.let { id ->
                        if (checked) idsSeleccionados.add(id) else idsSeleccionados.remove(id)
                    }
                }
            }
            chipGroup.addView(chip)
        }

        if (esEdicion) {
            etRuc.setText(proveedorParaEditar?.dni)
            etTelefono.setText(proveedorParaEditar?.telefono)
            etRazonSocial.setText(proveedorParaEditar?.nombreComercial ?: "")
            cbActivo.isChecked = proveedorParaEditar?.estado == true
        } else {
            cbActivo.isChecked = true
        }

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnCancelar.setOnClickListener {
            alertDialog.dismiss()
        }

        btnGuardar.setOnClickListener {
            val ruc = etRuc.text.toString().trim()
            val nombreComercial = etRazonSocial.text.toString().trim()
            val telefonoRaw = etTelefono.text.toString().trim()
            val telefono = if (telefonoRaw.isBlank()) null else telefonoRaw
            val activo = cbActivo.isChecked

            if (ruc.isBlank() || nombreComercial.isBlank()) {
                Toast.makeText(this, "Completa el RUC/DNI y el Nombre Comercial", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestDto = ProveedorRequestDto(
                id = proveedorParaEditar?.id,
                dni = ruc,
                nombreComercial = nombreComercial,
                telefono = telefono,
                estado = activo,
                categoriasIds = idsSeleccionados
            )

            alertDialog.dismiss()
            guardarEnBackend(esEdicion, proveedorParaEditar?.id, requestDto)
        }

        alertDialog.show()
    }

    private fun guardarEnBackend(esEdicion: Boolean, id: Long?, dto: ProveedorRequestDto) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrBlank() || negocioId <= 0) return

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.apiService
                val response = if (esEdicion && id != null) {
                    api.actualizarProveedor(authHeader, negocioId, id, dto)
                } else {
                    api.crearProveedor(authHeader, negocioId, dto)
                }

                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@ProveedoresActivity,
                            if (esEdicion) "Proveedor actualizado" else "Proveedor guardado con éxito",
                            Toast.LENGTH_SHORT
                        ).show()
                        cargarDatosBackend()
                    } else {
                        val errDetail = response.errorBody()?.string()
                        Log.e("PROVEEDOR_SAVE_ERR", "Error ${response.code()}: $errDetail")
                        Toast.makeText(this@ProveedoresActivity, "Error ${response.code()}: $errDetail", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Log.e("PROVEEDOR_SAVE_EXC", "Error al guardar", e)
                    Toast.makeText(this@ProveedoresActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarEliminacion(proveedor: ProveedorResponseDto) {
        val nombreDisplay = proveedor.nombreComercial?.takeIf { it.isNotBlank() } ?: "este proveedor"

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Eliminar Proveedor?")
            .setMessage("¿Estás seguro de eliminar a $nombreDisplay? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                dialog.dismiss()
                proveedor.id?.let { ejecutarEliminacion(it) }
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun ejecutarEliminacion(id: Long) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader.isNullOrBlank() || negocioId <= 0) return

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.eliminarProveedor(authHeader, negocioId, id)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@ProveedoresActivity, "Proveedor eliminado", Toast.LENGTH_SHORT).show()
                        cargarDatosBackend()
                    } else {
                        val errDetail = response.errorBody()?.string()
                        Log.e("PROVEEDOR_DEL_ERR", "Error ${response.code()}: $errDetail")
                        Toast.makeText(this@ProveedoresActivity, "No se pudo eliminar el proveedor", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ProveedoresActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}