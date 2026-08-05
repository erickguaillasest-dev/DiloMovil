package com.example.movildilo.ui.propietario

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ClienteResponseDto
import com.example.movildilo.ui.adapters.ClientesAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class ClientesActivity : AppCompatActivity() {

    private lateinit var rvClientes: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutEmptyState: View
    private lateinit var etSearch: EditText
    private lateinit var btnNuevoCliente: MaterialButton

    private lateinit var btnRegresar: ImageButton

    private lateinit var clientesAdapter: ClientesAdapter
    private lateinit var sessionManager: SessionManager

    private var negocioId: Long = -1L
    private var listaClientes: List<ClienteResponseDto> = emptyList()
    private var listaFiltrada: List<ClienteResponseDto> = emptyList()

    private var imgAvatarModal: ImageView? = null

    private val seleccionarGaleriaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            imgAvatarModal?.setImageURI(it)
        }
    }

    private val tomarFotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            imgAvatarModal?.setImageBitmap(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clientes)


        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        setupListeners()

        if (negocioId != -1L) {
            cargarClientes()
        } else {
            Toast.makeText(this, "No se detectó un negocio activo", Toast.LENGTH_SHORT).show()
        }

        if (intent.getStringExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_ACCION) ==
            com.example.movildilo.ia.ZoeActionRouter.Accion.CREAR_CLIENTE
        ) {
            rvClientes.postDelayed({ abrirModalFormulario(null) }, 500)
        }
    }

    private fun initViews() {
        rvClientes = findViewById(R.id.rvClientes)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        etSearch = findViewById(R.id.etSearch)
        btnNuevoCliente = findViewById(R.id.btnNuevoCliente)
        btnRegresar = findViewById(R.id.btnRegresar)
    }

    private fun setupRecyclerView() {
        clientesAdapter = ClientesAdapter(
            listaClientes = emptyList(),
            onEditarClick = { cliente -> abrirModalFormulario(cliente) },
            onEliminarClick = { cliente -> confirmarEliminacion(cliente) }
        )
        rvClientes.layoutManager = LinearLayoutManager(this)
        rvClientes.adapter = clientesAdapter
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }
        btnNuevoCliente.setOnClickListener { abrirModalFormulario(null) }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarClientes(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun cargarClientes() {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader == null) {
            mostrarAlertaSesionExpirada()
            return
        }

        layoutLoading.visibility = View.VISIBLE
        rvClientes.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getClientes(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        listaClientes = response.body()!!
                        filtrarClientes(etSearch.text.toString())
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(this@ClientesActivity, "Error al cargar clientes", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ClientesActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filtrarClientes(term: String) {
        val query = term.trim().lowercase()

        listaFiltrada = if (query.isEmpty()) {
            listaClientes
        } else {
            listaClientes.filter { c ->
                val dni = c.dni?.lowercase().orEmpty()
                val email = c.email?.lowercase().orEmpty()
                val nombreDto = c.nombreCompleto?.lowercase().orEmpty()

                val nombreConstruido = listOfNotNull(
                    c.primerNombre,
                    c.segundoNombre,
                    c.apellidoPaterno,
                    c.apellidoMaterno
                ).joinToString(" ").lowercase()

                dni.contains(query) ||
                        email.contains(query) ||
                        nombreDto.contains(query) ||
                        nombreConstruido.contains(query)
            }
        }

        clientesAdapter.actualizarLista(listaFiltrada)

        if (listaFiltrada.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvClientes.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvClientes.visibility = View.VISIBLE
        }
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Tomar foto", "Elegir de la galería")
        MaterialAlertDialogBuilder(this)
            .setTitle("Foto de perfil del cliente")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> tomarFotoLauncher.launch(null)
                    1 -> seleccionarGaleriaLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun abrirModalFormulario(cliente: ClienteResponseDto?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_cliente_form, null)

        val tvDialogTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val btnCerrarModal = view.findViewById<ImageView>(R.id.btnCerrar)

        imgAvatarModal = view.findViewById(R.id.imgAvatarCliente)
        val btnCambiarFoto = view.findViewById<View>(R.id.btnCambiarFoto)

        val etDni = view.findViewById<TextInputEditText>(R.id.etDni)
        val etPrimerNombre = view.findViewById<TextInputEditText>(R.id.etPrimerNombre)
        val etSegundoNombre = view.findViewById<TextInputEditText>(R.id.etSegundoNombre)
        val etApellidoPaterno = view.findViewById<TextInputEditText>(R.id.etApellidoPaterno)
        val etApellidoMaterno = view.findViewById<TextInputEditText>(R.id.etApellidoMaterno)
        val etEmail = view.findViewById<TextInputEditText>(R.id.etEmail)

        val layoutContrasena = view.findViewById<TextInputLayout>(R.id.layoutContrasena)
        val etContrasena = view.findViewById<TextInputEditText>(R.id.etContrasena)

        val etTelefono = view.findViewById<TextInputEditText>(R.id.etTelefono)
        val etFechaNacimiento = view.findViewById<TextInputEditText>(R.id.etFechaNacimiento)
        val etDireccion = view.findViewById<TextInputEditText>(R.id.etDireccion)

        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarModal)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardarModal)

        val isEditing = cliente != null
        tvDialogTitle.text = if (isEditing) "Editar Cliente" else "Nuevo Cliente"

        btnCambiarFoto.setOnClickListener {
            mostrarOpcionesImagen()
        }

        if (isEditing && cliente != null) {
            etDni.setText(cliente.dni)
            etPrimerNombre.setText(cliente.primerNombre)
            etSegundoNombre.setText(cliente.segundoNombre)
            etApellidoPaterno.setText(cliente.apellidoPaterno)
            etApellidoMaterno.setText(cliente.apellidoMaterno)
            etEmail.setText(cliente.email)
            etTelefono.setText(cliente.telefono)
            etFechaNacimiento.setText(cliente.fechaNacimiento)
            etDireccion.setText(cliente.direccion)

            layoutContrasena.hint = "Contraseña (dejar en blanco para no cambiar)"
        } else {
            layoutContrasena.hint = "Contraseña *"
        }

        etFechaNacimiento.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val fecha = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                etFechaNacimiento.setText(fecha)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrarModal.setOnClickListener { dialog.dismiss() }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val dni = etDni.text.toString().trim()
            val primerNombre = etPrimerNombre.text.toString().trim()
            val apellidoPaterno = etApellidoPaterno.text.toString().trim()
            val contrasena = etContrasena.text.toString().trim()

            if (dni.isEmpty() || primerNombre.isEmpty() || apellidoPaterno.isEmpty()) {
                Toast.makeText(this, "El DNI, Primer Nombre y Apellido Paterno son obligatorios", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!isEditing && contrasena.isEmpty()) {
                Toast.makeText(this, "La contraseña es obligatoria para nuevos clientes", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val requestDto = ClienteResponseDto(
                id = cliente?.id,
                dni = dni,
                primerNombre = primerNombre,
                segundoNombre = etSegundoNombre.text.toString().trim().takeIf { it.isNotEmpty() },
                apellidoPaterno = apellidoPaterno,
                apellidoMaterno = etApellidoMaterno.text.toString().trim().takeIf { it.isNotEmpty() },
                email = etEmail.text.toString().trim().takeIf { it.isNotEmpty() },
                contrasena = contrasena.takeIf { it.isNotEmpty() },
                fechaNacimiento = etFechaNacimiento.text.toString().trim().takeIf { it.isNotEmpty() },
                telefono = etTelefono.text.toString().trim().takeIf { it.isNotEmpty() },
                direccion = etDireccion.text.toString().trim().takeIf { it.isNotEmpty() }
            )

            dialog.dismiss()
            guardarClienteApi(requestDto, isEditing)
        }

        dialog.show()

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun guardarClienteApi(clienteDto: ClienteResponseDto, isEditing: Boolean) {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (isEditing && clienteDto.id != null) {
                    RetrofitClient.apiService.actualizarCliente(authHeader, negocioId, clienteDto.id, clienteDto)
                } else {
                    RetrofitClient.apiService.crearCliente(authHeader, negocioId, clienteDto)
                }

                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        val msg = if (isEditing) "¡Cliente actualizado!" else "¡Cliente creado exitosamente!"
                        Toast.makeText(this@ClientesActivity, msg, Toast.LENGTH_SHORT).show()
                        cargarClientes()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(this@ClientesActivity, "Revisa que el DNI no esté duplicado", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ClientesActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarEliminacion(cliente: ClienteResponseDto) {
        val clienteId = cliente.id ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Estás seguro?")
            .setMessage("Eliminarás a ${cliente.primerNombre ?: "este cliente"} de tu base de datos.")
            .setPositiveButton("Sí, eliminar") { dialog, _ ->
                dialog.dismiss()
                eliminarClienteApi(clienteId)
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun eliminarClienteApi(clienteId: Long) {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.eliminarCliente(authHeader, negocioId, clienteId)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(this@ClientesActivity, "El cliente ha sido borrado", Toast.LENGTH_SHORT).show()
                        cargarClientes()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(this@ClientesActivity, "No se pudo eliminar el cliente", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ClientesActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarAlertaSesionExpirada() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sesión Expirada")
            .setMessage("Tu sesión ha caducado. Vuelve a iniciar sesión.")
            .setPositiveButton("Aceptar") { _, _ ->
                sessionManager.clearSession()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}