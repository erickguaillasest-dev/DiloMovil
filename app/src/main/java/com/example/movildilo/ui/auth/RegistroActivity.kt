package com.example.movildilo.ui.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.model.dto.RegistroDto
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class RegistroActivity : AppCompatActivity() {

    private var passwordVisible = false
    private var fotoPerfilUri: Uri? = null

    // Componentes UI
    private lateinit var btnRegresar: MaterialButton
    private lateinit var etCedula: EditText
    private lateinit var etPrimerNombre: EditText
    private lateinit var etSegundoNombre: EditText
    private lateinit var etApellidoPaterno: EditText
    private lateinit var etApellidoMaterno: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etFechaNacimiento: EditText
    private lateinit var etDireccion: EditText
    private lateinit var spinnerParroquia: Spinner
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var cbTerminos: CheckBox
    private lateinit var btnContinuar: MaterialButton
    private lateinit var tvGoToLogin: TextView
    private lateinit var ivTogglePassword: ImageView
    private lateinit var tvTerminos: TextView

    private var listaParroquias: List<ParroquiaResponseDto> = emptyList()

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var fabSeleccionarFoto: FloatingActionButton

    // Launcher para seleccionar imagen de la galería
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            fotoPerfilUri = uri
            ivFotoPerfil.setImageURI(uri)
            ivFotoPerfil.colorFilter = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)

        // Inicializar vistas
        btnRegresar = findViewById(R.id.btnRegresar)
        etCedula = findViewById(R.id.etCedula)
        etPrimerNombre = findViewById(R.id.etPrimerNombre)
        etSegundoNombre = findViewById(R.id.etSegundoNombre)
        etApellidoPaterno = findViewById(R.id.etApellidoPaterno)
        etApellidoMaterno = findViewById(R.id.etApellidoMaterno)
        etEmail = findViewById(R.id.etEmail)
        etTelefono = findViewById(R.id.etTelefono)
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento)
        etDireccion = findViewById(R.id.etDireccion)
        spinnerParroquia = findViewById(R.id.spinnerParroquia)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        cbTerminos = findViewById(R.id.cbTerminos)
        btnContinuar = findViewById(R.id.btnContinuar)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)
        tvTerminos = findViewById(R.id.tvTerminos)
        ivTogglePassword = findViewById(R.id.ivTogglePassword)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        fabSeleccionarFoto = findViewById(R.id.fabSeleccionarFoto)

        // Eventos
        btnRegresar.setOnClickListener { finish() }
        etFechaNacimiento.setOnClickListener { mostrarCalendario() }
        ivTogglePassword.setOnClickListener { togglePasswordVisibility() }
        btnContinuar.setOnClickListener { onRegisterClicked() }
        tvGoToLogin.setOnClickListener { volverAlLogin() }

        val abrirGaleriaLambda = { selectImageLauncher.launch("image/*") }
        fabSeleccionarFoto.setOnClickListener { abrirGaleriaLambda() }
        ivFotoPerfil.setOnClickListener { abrirGaleriaLambda() }

        tvTerminos.setOnClickListener {
            val intent = Intent(this, TerminosActivity::class.java)
            startActivity(intent)
        }

        // Cargar datos
        cargarParroquiasDesdeBackend()
    }

    private fun cargarParroquiasDesdeBackend() {
        actualizarSpinner(listOf("Cargando parroquias..."))

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getParroquias()

                if (response.isSuccessful) {
                    val parroquias = response.body().orEmpty()
                    listaParroquias = parroquias

                    val nombres = mutableListOf("Seleccione una parroquia")
                    nombres.addAll(parroquias.map { it.nombre })
                    actualizarSpinner(nombres)

                    if (parroquias.isEmpty()) {
                        Toast.makeText(
                            this@RegistroActivity,
                            "No hay parroquias registradas en el sistema",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    actualizarSpinner(listOf("Error al cargar parroquias"))
                    Toast.makeText(
                        this@RegistroActivity,
                        "No se pudieron cargar las parroquias (código ${response.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: IOException) {
                actualizarSpinner(listOf("Sin conexión"))
                Toast.makeText(
                    this@RegistroActivity,
                    "No se pudo conectar al servidor para cargar parroquias.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                actualizarSpinner(listOf("Error al cargar parroquias"))
                Toast.makeText(this@RegistroActivity, "Ocurrió un error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarSpinner(opciones: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opciones)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerParroquia.adapter = adapter
    }

    private fun mostrarCalendario() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val fechaFormateada = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth)
                etFechaNacimiento.setText(fechaFormateada)
            },
            calendar.get(Calendar.YEAR) - 18,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
        val cursorPosition = etPassword.selectionStart

        if (passwordVisible) {
            etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        } else {
            etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        }

        // Mantiene la posición del cursor
        if (cursorPosition >= 0) {
            etPassword.setSelection(cursorPosition)
        }
    }

    private fun onRegisterClicked() {
        val cedula = etCedula.text.toString().trim()
        val primerNombre = etPrimerNombre.text.toString().trim()
        val segundoNombre = etSegundoNombre.text.toString().trim()
        val apellidoPaterno = etApellidoPaterno.text.toString().trim()
        val apellidoMaterno = etApellidoMaterno.text.toString().trim()
        val correo = etEmail.text.toString().trim()
        val telefono = etTelefono.text.toString().trim()
        val fechaNac = etFechaNacimiento.text.toString().trim()
        val direccion = etDireccion.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        // Limpia errores previos de una corrida anterior
        listOf(etCedula, etPrimerNombre, etApellidoPaterno, etEmail, etTelefono, etFechaNacimiento, etPassword, etConfirmPassword)
            .forEach { FormValidator.marcarErrorEditText(it, null) }

        var campoConError: EditText? = null
        fun marcar(campo: EditText, mensaje: String?) {
            if (mensaje != null) {
                FormValidator.marcarErrorEditText(campo, mensaje)
                if (campoConError == null) campoConError = campo
            }
        }

        marcar(etCedula, FormValidator.cedulaEcuatoriana(cedula, "La cédula"))
        marcar(etPrimerNombre, FormValidator.soloTexto(primerNombre, "El primer nombre"))
        marcar(etApellidoPaterno, FormValidator.soloTexto(apellidoPaterno, "El apellido paterno"))
        if (apellidoMaterno.isNotBlank()) marcar(etApellidoMaterno, FormValidator.soloTexto(apellidoMaterno, "El apellido materno", obligatorio = false))
        marcar(etEmail, FormValidator.correo(correo))
        if (telefono.isNotBlank()) marcar(etTelefono, FormValidator.telefono(telefono, obligatorio = false))
        marcar(etFechaNacimiento, FormValidator.requerido(fechaNac, "La fecha de nacimiento"))
        marcar(etPassword, FormValidator.password(password, minimo = 8))
        marcar(etConfirmPassword, FormValidator.confirmarPassword(password, confirmPassword))

        if (campoConError != null) {
            campoConError?.requestFocus()
            return
        }

        // Validar edad mínima (18 años) a partir de la fecha seleccionada
        if (fechaNac.isNotBlank()) {
            try {
                val partes = fechaNac.split("-")
                val fecha = Calendar.getInstance().apply {
                    set(partes[0].toInt(), partes[1].toInt() - 1, partes[2].toInt())
                }
                val hoy = Calendar.getInstance()
                var edad = hoy.get(Calendar.YEAR) - fecha.get(Calendar.YEAR)
                if (hoy.get(Calendar.DAY_OF_YEAR) < fecha.get(Calendar.DAY_OF_YEAR)) edad--
                if (edad < 18) {
                    Toast.makeText(this, "Debes ser mayor de 18 años para registrarte (tienes $edad años según la fecha ingresada).", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Exception) {
                // Si la fecha no se pudo interpretar, se deja pasar (el backend la validará de todas formas)
            }
        }

        // Validar selección de parroquia
        if (spinnerParroquia.selectedItemPosition <= 0) {
            Toast.makeText(this, "Selecciona tu parroquia de la lista antes de continuar.", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar Términos y Condiciones
        if (!cbTerminos.isChecked) {
            Toast.makeText(
                this,
                "Debes aceptar los Términos y Condiciones para poder crear tu cuenta.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val posicion = spinnerParroquia.selectedItemPosition
        val idParroquia: Long? = if (posicion in 1..listaParroquias.size) {
            listaParroquias[posicion - 1].id
        } else null

        if (idParroquia == null) {
            Toast.makeText(this, "La parroquia seleccionada no es válida, elige otra de la lista.", Toast.LENGTH_SHORT).show()
            return
        }

        val registroDto = RegistroDto(
            dni = cedula,
            primerNombre = primerNombre,
            segundoNombre = segundoNombre.ifBlank { null },
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno.ifBlank { null },
            email = correo,
            password = password,
            fechaNacimiento = fechaNac,
            telefono = telefono.ifBlank { null },
            direccion = direccion.ifBlank { null },
            id_parroquia = idParroquia,
            fotoPerfil = null
        )

        realizarRegistro(registroDto)
    }

    private fun realizarRegistro(dto: RegistroDto) {
        setLoading(true)

        val dtoJson = com.google.gson.Gson().toJson(dto)
        val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
        val datosRequestBody = RequestBody.create(jsonMediaType, dtoJson)

        var fotoPart: MultipartBody.Part? = null

        fotoPerfilUri?.let { uri ->
            try {
                val file = uriToFile(uri)
                if (file != null) {
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                    val requestFile = RequestBody.create(MediaType.parse(mimeType), file)
                    fotoPart = MultipartBody.Part.createFormData("foto", file.name, requestFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.register(datosRequestBody, fotoPart)

                if (response.isSuccessful) {
                    Toast.makeText(this@RegistroActivity, "¡Cuenta creada con éxito!", Toast.LENGTH_LONG).show()
                    volverAlLogin()
                } else {
                    setLoading(false)
                    val mensaje = when (response.code()) {
                        400 -> "La cédula o el correo ya se encuentran registrados o los datos son inválidos."
                        else -> "Error en el servidor (código ${response.code()})"
                    }
                    Toast.makeText(this@RegistroActivity, mensaje, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                setLoading(false)
                Toast.makeText(
                    this@RegistroActivity,
                    "No se pudo conectar al servidor.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@RegistroActivity, "Ocurrió un error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uriToFile(uri: Uri): File? {
        val contentResolver = contentResolver ?: return null
        val tempFile = File(cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun setLoading(loading: Boolean) {
        btnContinuar.isEnabled = !loading
        btnContinuar.text = if (loading) "Registrando..." else "Continuar"
    }

    private fun volverAlLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}