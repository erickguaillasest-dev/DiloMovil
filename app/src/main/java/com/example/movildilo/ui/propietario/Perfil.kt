package com.example.movildilo.ui.propietario

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.auth.CambiarPasswordRequestDto
import com.example.movildilo.data.model.dto.usuarios.EditarPerfilRequestDto
import com.example.movildilo.data.model.dto.negocio.ParroquiaResponseDto
import com.example.movildilo.data.model.dto.usuarios.UsuarioMeDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class Perfil : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val gson = Gson()

    private val baseServerUrl = "https://dilo-backend-mxlu.onrender.com"
    private lateinit var btnRegresar: ImageView
    private lateinit var scrollView: NestedScrollView
    private lateinit var swipeRefreshLayoutPerfil: SwipeRefreshLayout
    private lateinit var imgAvatar: ShapeableImageView
    private lateinit var tvCambiarFoto: TextView
    private lateinit var tvNombre: TextView
    private lateinit var tvDni: TextView
    private lateinit var tvCorreo: TextView
    private lateinit var tvTelefono: TextView
    private lateinit var tvFechaNacimiento: TextView
    private lateinit var tvDireccion: TextView
    private lateinit var tvParroquia: TextView
    private lateinit var btnEditarPerfil: MaterialButton

    private lateinit var containerNombreLectura: View
    private lateinit var containerNombreEdicion: View
    private lateinit var containerBotonesEdicion: View

    private lateinit var etPrimerNombre: TextInputEditText
    private lateinit var etSegundoNombre: TextInputEditText
    private lateinit var etApellidoPaterno: TextInputEditText
    private lateinit var etApellidoMaterno: TextInputEditText

    private lateinit var tilTelefono: TextInputLayout
    private lateinit var etTelefono: TextInputEditText

    private lateinit var tilFechaNacimiento: TextInputLayout
    private lateinit var etFechaNacimiento: TextInputEditText

    private lateinit var tilDireccion: TextInputLayout
    private lateinit var etDireccion: TextInputEditText

    private lateinit var tilParroquia: TextInputLayout
    private lateinit var spParroquia: AutoCompleteTextView

    private lateinit var btnCancelarEdicion: MaterialButton
    private lateinit var btnGuardarCambios: MaterialButton

    private lateinit var btnTogglePassword: MaterialButton
    private lateinit var containerFormularioPassword: View
    private lateinit var etNuevaContrasena: TextInputEditText
    private lateinit var etConfirmarContrasena: TextInputEditText
    private lateinit var btnGuardarContrasena: MaterialButton

    private var usuarioActual: UsuarioMeDto? = null
    private var isEditing = false
    private var isChangingPassword = false
    private var fotoSeleccionadaUri: Uri? = null

    private var parroquiasList: List<ParroquiaResponseDto> = emptyList()
    private var parroquiaSeleccionadaId: Long? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fotoSeleccionadaUri = uri
            imgAvatar.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil)

        scrollView = findViewById(R.id.main)
        sessionManager = SessionManager(this)

        initViews()
        setupListeners()
        cargarParroquias()
        cargarMiPerfil()
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        swipeRefreshLayoutPerfil = findViewById(R.id.swipeRefreshLayoutPerfil)
        imgAvatar = findViewById(R.id.imgAvatar)
        tvCambiarFoto = findViewById(R.id.tvCambiarFoto)
        tvNombre = findViewById(R.id.tvNombre)
        tvDni = findViewById(R.id.tvDni)
        tvCorreo = findViewById(R.id.tvCorreo)
        tvTelefono = findViewById(R.id.tvTelefono)
        tvFechaNacimiento = findViewById(R.id.tvFechaNacimiento)
        tvDireccion = findViewById(R.id.tvDireccion)
        tvParroquia = findViewById(R.id.tvParroquia)
        btnEditarPerfil = findViewById(R.id.btnEditarPerfil)

        containerNombreLectura = findViewById(R.id.containerNombreLectura)
        containerNombreEdicion = findViewById(R.id.containerNombreEdicion)
        containerBotonesEdicion = findViewById(R.id.containerBotonesEdicion)

        etPrimerNombre = findViewById(R.id.etPrimerNombre)
        etSegundoNombre = findViewById(R.id.etSegundoNombre)
        etApellidoPaterno = findViewById(R.id.etApellidoPaterno)
        etApellidoMaterno = findViewById(R.id.etApellidoMaterno)

        tilTelefono = findViewById(R.id.tilTelefono)
        etTelefono = findViewById(R.id.etTelefono)

        tilFechaNacimiento = findViewById(R.id.tilFechaNacimiento)
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento)

        tilDireccion = findViewById(R.id.tilDireccion)
        etDireccion = findViewById(R.id.etDireccion)

        tilParroquia = findViewById(R.id.tilParroquia)
        spParroquia = findViewById(R.id.spParroquia)

        btnCancelarEdicion = findViewById(R.id.btnCancelarEdicion)
        btnGuardarCambios = findViewById(R.id.btnGuardarCambios)

        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        containerFormularioPassword = findViewById(R.id.containerFormularioPassword)
        etNuevaContrasena = findViewById(R.id.etNuevaContrasena)
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena)
        btnGuardarContrasena = findViewById(R.id.btnGuardarContrasena)
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener { finish() }
        btnEditarPerfil.setOnClickListener { activarModoEdicion() }
        btnCancelarEdicion.setOnClickListener { desactivarModoEdicion() }
        btnGuardarCambios.setOnClickListener { guardarCambios() }

        btnTogglePassword.setOnClickListener { toggleChangePassword() }
        btnGuardarContrasena.setOnClickListener { guardarSoloContrasena() }

        swipeRefreshLayoutPerfil.setOnRefreshListener {
            cargarParroquias {
                cargarMiPerfil {
                    swipeRefreshLayoutPerfil.isRefreshing = false
                }
            }
        }

        tvCambiarFoto.setOnClickListener {
            if (isEditing) pickImageLauncher.launch("image/*")
        }
        imgAvatar.setOnClickListener {
            if (isEditing) pickImageLauncher.launch("image/*")
        }

        etFechaNacimiento.setOnClickListener {
            if (isEditing) mostrarDatePicker()
        }
    }

    private fun toggleChangePassword() {
        isChangingPassword = !isChangingPassword
        if (isChangingPassword) {
            containerFormularioPassword.visibility = View.VISIBLE
            btnTogglePassword.text = "Cancelar"
        } else {
            containerFormularioPassword.visibility = View.GONE
            btnTogglePassword.text = "Cambiar"
            etNuevaContrasena.setText("")
            etConfirmarContrasena.setText("")
            etNuevaContrasena.error = null
            etConfirmarContrasena.error = null
        }
    }

    private fun mostrarDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val fechaFormateada = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
                etFechaNacimiento.setText(fechaFormateada)
                tilFechaNacimiento.error = null
            },
            year,
            month,
            day
        )
        datePickerDialog.show()
    }

    private fun cargarMiPerfil(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader()
        if (authHeader == null) {
            onComplete?.invoke()
            Toast.makeText(this, "Sesión expirada, vuelve a iniciar sesión.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful && response.body() != null) {
                    pintarUsuario(response.body()!!)
                } else {
                    Toast.makeText(this@Perfil, "No se pudo cargar tu perfil (código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Perfil, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun cargarParroquias(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getParroquias(authHeader)
                if (response.isSuccessful) {
                    parroquiasList = response.body() ?: emptyList()
                    configurarDropdownParroquias()
                }
            } catch (e: Exception) {
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private fun configurarDropdownParroquias() {
        val nombres = parroquiasList.map { it.nombre }
        spParroquia.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombres))
        spParroquia.threshold = 0
        spParroquia.setOnClickListener { spParroquia.showDropDown() }
        spParroquia.setOnItemClickListener { _, _, position, _ ->
            parroquiasList.getOrNull(position)?.let { parroquiaSeleccionadaId = it.id }
        }
    }

    private fun pintarUsuario(usuario: UsuarioMeDto) {
        usuarioActual = usuario

        val nombreCompleto = listOfNotNull(usuario.primerNombre, usuario.segundoNombre, usuario.apellidoPaterno, usuario.apellidoMaterno)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        tvNombre.text = if (nombreCompleto.isNotBlank()) nombreCompleto else "Usuario"
        tvDni.text = "DNI / RUC: ${usuario.dni ?: "N/D"}"
        tvCorreo.text = usuario.email ?: "N/D"
        tvTelefono.text = usuario.telefono ?: "No registrado"
        tvFechaNacimiento.text = usuario.fechaNacimiento ?: "No registrada"
        tvDireccion.text = usuario.direccion ?: "No registrada"
        tvParroquia.text = usuario.nameParroquia ?: "No registrada"

        val urlFoto = construirUrlFoto(usuario.fotoPerfil)
        if (!urlFoto.isNullOrBlank()) {
            cargarFotoPerfilUsuario(urlFoto)
        }

        manejarAccionDeZoe()
    }

    private fun manejarAccionDeZoe() {
        when (intent.getStringExtra(ZoeActionRouter.EXTRA_ACCION)) {
            ZoeActionRouter.Accion.EDITAR_PERFIL -> activarModoEdicion()
            ZoeActionRouter.Accion.CAMBIAR_PASSWORD -> toggleChangePassword()
        }

        if (intent.getBooleanExtra(ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun abrirChatZoe() {
        val userMap = sessionManager.getUserMap()
        val nombreUsuario = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString() ?: "Usuario"
        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = nombreUsuario,
            negocioNombre = "Mi Empresa",
            contextoNegocioTexto = "Estás visualizando tu perfil.",
            alertasTexto = "Sin alertas recientes.",
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun construirUrlFoto(rutaFoto: String?): String? {
        if (rutaFoto.isNullOrBlank()) return null
        return if (rutaFoto.startsWith("http")) {
            rutaFoto
        } else {
            val prefijoBarra = if (rutaFoto.startsWith("/")) "" else "/"
            "$baseServerUrl$prefijoBarra$rutaFoto"
        }
    }

    private fun cargarFotoPerfilUsuario(urlOBase64: String) {
        Glide.with(this)
            .load(urlOBase64)
            .circleCrop()
            .placeholder(R.drawable.bg_avatar_circulo)
            .error(R.drawable.bg_avatar_circulo)
            .into(imgAvatar)
    }

    private fun activarModoEdicion() {
        val usuario = usuarioActual ?: return
        isEditing = true
        fotoSeleccionadaUri = null

        etPrimerNombre.setText(usuario.primerNombre)
        etSegundoNombre.setText(usuario.segundoNombre)
        etApellidoPaterno.setText(usuario.apellidoPaterno)
        etApellidoMaterno.setText(usuario.apellidoMaterno)
        etTelefono.setText(usuario.telefono)
        etFechaNacimiento.setText(usuario.fechaNacimiento)
        etDireccion.setText(usuario.direccion)
        parroquiaSeleccionadaId = usuario.idParroquia ?: usuario.parroquia?.id
        if (parroquiasList.isNotEmpty()) configurarDropdownParroquias()
        val nombreParroquiaActual = parroquiasList.find { it.id == parroquiaSeleccionadaId }?.nombre
            ?: usuario.nameParroquia.orEmpty()
        spParroquia.setText(nombreParroquiaActual, false)

        btnEditarPerfil.visibility = View.GONE
        containerNombreLectura.visibility = View.GONE
        containerNombreEdicion.visibility = View.VISIBLE
        containerBotonesEdicion.visibility = View.VISIBLE
        tvCambiarFoto.visibility = View.VISIBLE

        tvTelefono.visibility = View.GONE
        tilTelefono.visibility = View.VISIBLE

        tvFechaNacimiento.visibility = View.GONE
        tilFechaNacimiento.visibility = View.VISIBLE

        tvDireccion.visibility = View.GONE
        tilDireccion.visibility = View.VISIBLE

        tvParroquia.visibility = View.GONE
        tilParroquia.visibility = View.VISIBLE
    }

    private fun desactivarModoEdicion() {
        isEditing = false
        fotoSeleccionadaUri = null

        btnEditarPerfil.visibility = View.VISIBLE
        containerNombreLectura.visibility = View.VISIBLE
        containerNombreEdicion.visibility = View.GONE
        containerBotonesEdicion.visibility = View.GONE
        tvCambiarFoto.visibility = View.GONE

        tvTelefono.visibility = View.VISIBLE
        tilTelefono.visibility = View.GONE

        tvFechaNacimiento.visibility = View.VISIBLE
        tilFechaNacimiento.visibility = View.GONE

        tvDireccion.visibility = View.VISIBLE
        tilDireccion.visibility = View.GONE

        tvParroquia.visibility = View.VISIBLE
        tilParroquia.visibility = View.GONE

        etPrimerNombre.error = null
        etApellidoPaterno.error = null
        tilTelefono.error = null
        tilFechaNacimiento.error = null
        tilDireccion.error = null

        usuarioActual?.let {
            val urlFoto = construirUrlFoto(it.fotoPerfil)
            if (!urlFoto.isNullOrBlank()) cargarFotoPerfilUsuario(urlFoto)
        }
    }

    private fun guardarCambios() {
        val primerNombre = etPrimerNombre.text?.toString()?.trim().orEmpty()
        val segundoNombre = etSegundoNombre.text?.toString()?.trim().orEmpty()
        val apellidoPaterno = etApellidoPaterno.text?.toString()?.trim().orEmpty()
        val apellidoMaterno = etApellidoMaterno.text?.toString()?.trim().orEmpty()
        val telefono = etTelefono.text?.toString()?.trim().orEmpty()
        val direccion = etDireccion.text?.toString()?.trim().orEmpty()
        val fechaNacimiento = etFechaNacimiento.text?.toString()?.trim().orEmpty()

        var esValido = true

        if (primerNombre.isEmpty()) {
            etPrimerNombre.error = "El primer nombre es requerido"
            esValido = false
        } else {
            etPrimerNombre.error = null
        }

        if (apellidoPaterno.isEmpty()) {
            etApellidoPaterno.error = "El apellido paterno es requerido"
            esValido = false
        } else {
            etApellidoPaterno.error = null
        }

        if (telefono.isNotEmpty() && !telefono.matches(Regex("^[0-9]{9,10}$"))) {
            tilTelefono.error = "Ingresa un número de 9 o 10 dígitos"
            esValido = false
        } else {
            tilTelefono.error = null
        }

        if (!esValido) {
            Toast.makeText(this, "Por favor corrige los campos indicados.", Toast.LENGTH_SHORT).show()
            return
        }

        val authHeader = sessionManager.getAuthHeader() ?: return

        val datos = EditarPerfilRequestDto(
            primerNombre = primerNombre,
            segundoNombre = segundoNombre,
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno,
            telefono = telefono,
            direccion = direccion,
            fechaNacimiento = fechaNacimiento,
            idParroquia = parroquiaSeleccionadaId
        )

        val jsonBody = gson.toJson(datos)
        val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
        val datosRequestBody = RequestBody.create(jsonMediaType, jsonBody)

        val fotoPart: MultipartBody.Part? = fotoSeleccionadaUri?.let { uri ->
            val archivoTemporal = copiarUriAArchivoTemporal(uri)
            archivoTemporal?.let { archivo ->
                val imageMediaType = MediaType.parse(obtenerMimeType(archivo.name))
                val requestFile = RequestBody.create(imageMediaType, archivo)
                MultipartBody.Part.createFormData("foto", archivo.name, requestFile)
            }
        }

        btnGuardarCambios.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.actualizarMiPerfil(authHeader, datosRequestBody, fotoPart)
                if (response.isSuccessful && response.body() != null) {
                    pintarUsuario(response.body()!!)
                    desactivarModoEdicion()
                    Toast.makeText(this@Perfil, "¡Perfil actualizado correctamente!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@Perfil, "No se pudo actualizar (código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Perfil, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnGuardarCambios.isEnabled = true
            }
        }
    }

    private fun guardarSoloContrasena() {
        val nuevaContrasena = etNuevaContrasena.text?.toString()?.trim().orEmpty()
        val confirmarContrasena = etConfirmarContrasena.text?.toString()?.trim().orEmpty()

        var esValido = true

        if (nuevaContrasena.isEmpty()) {
            etNuevaContrasena.error = "Ingresa la nueva contraseña"
            esValido = false
        } else if (nuevaContrasena.length < 6) {
            etNuevaContrasena.error = "La contraseña debe tener al menos 6 caracteres"
            esValido = false
        } else {
            etNuevaContrasena.error = null
        }

        if (confirmarContrasena.isEmpty()) {
            etConfirmarContrasena.error = "Confirma la nueva contraseña"
            esValido = false
        } else if (nuevaContrasena != confirmarContrasena) {
            etConfirmarContrasena.error = "Las contraseñas no coinciden"
            esValido = false
        } else {
            etConfirmarContrasena.error = null
        }

        if (!esValido) return

        val authHeader = sessionManager.getAuthHeader() ?: return
        val requestDto = CambiarPasswordRequestDto(
            newPassword = nuevaContrasena,
            confirmPassword = confirmarContrasena
        )

        btnGuardarContrasena.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.cambiarContrasena(authHeader, requestDto)
                if (response.isSuccessful) {
                    toggleChangePassword()
                    Toast.makeText(this@Perfil, "¡Tu contraseña ha sido actualizada correctamente!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@Perfil, "No se pudo actualizar la contraseña (código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@Perfil, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnGuardarContrasena.isEnabled = true
            }
        }
    }

    private fun obtenerMimeType(nombreArchivo: String): String {
        return when {
            nombreArchivo.endsWith(".png", ignoreCase = true) -> "image/png"
            nombreArchivo.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun copiarUriAArchivoTemporal(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val archivo = File(cacheDir, "foto_perfil_${System.currentTimeMillis()}.jpg")
            FileOutputStream(archivo).use { output ->
                inputStream.copyTo(output)
            }
            archivo
        } catch (e: Exception) {
            null
        }
    }
}