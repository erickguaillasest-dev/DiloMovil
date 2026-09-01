package com.example.movildilo.ui.auth

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.model.dto.auth.RegistroDto
import com.example.movildilo.data.model.dto.negocio.ParroquiaResponseDto
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
    private lateinit var scrollViewRegistro: ScrollView
    private lateinit var containerFormulario: LinearLayout

    private var listaParroquias: List<ParroquiaResponseDto> = emptyList()

    private lateinit var ivFotoPerfil: ImageView
    private lateinit var fabSeleccionarFoto: FloatingActionButton

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
        scrollViewRegistro = findViewById(R.id.main)

        // Nota: Asegúrate de agregar android:id="@+id/containerFormulario" en el LinearLayout interno de tu XML de registro para que esto funcione perfectamente sin espacios vacíos.
        containerFormulario = findViewById(R.id.containerFormulario)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        fabSeleccionarFoto = findViewById(R.id.fabSeleccionarFoto)

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

        // Listener dinámico del teclado para ajustar el padding inferior exactamente a la altura del teclado virtual sin espacios extra
        scrollViewRegistro.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            scrollViewRegistro.getWindowVisibleDisplayFrame(r)
            val screenHeight = scrollViewRegistro.rootView.height
            val keypadHeight = screenHeight - r.bottom

            if (keypadHeight > screenHeight * 0.15) {
                containerFormulario.setPadding(
                    containerFormulario.paddingLeft,
                    containerFormulario.paddingTop,
                    containerFormulario.paddingRight,
                    keypadHeight
                )
            } else {
                containerFormulario.setPadding(
                    containerFormulario.paddingLeft,
                    containerFormulario.paddingTop,
                    containerFormulario.paddingRight,
                    resources.getDimensionPixelSize(R.dimen.espacio_medio)
                )
            }
        }

        configurarEnfoqueYTeclado()
        cargarParroquiasDesdeBackend()
    }

    private fun configurarEnfoqueYTeclado() {
        val campos = listOf(
            etCedula, etPrimerNombre, etSegundoNombre, etApellidoPaterno,
            etApellidoMaterno, etEmail, etTelefono, etDireccion, etPassword, etConfirmPassword
        )
        campos.forEach { campo ->
            campo.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollViewRegistro.post {

                        var topCoord = 0
                        var currentView: android.view.View? = view
                        while (currentView != null && currentView != scrollViewRegistro) {
                            topCoord += currentView.top
                            currentView = currentView.parent as? android.view.View
                        }

                        scrollViewRegistro.smoothScrollTo(0, (topCoord - 100).coerceAtLeast(0))
                    }
                }
            }
        }
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

        if (cursorPosition >= 0) {
            etPassword.setSelection(cursorPosition)
        }
    }

    private fun validarCedulaEcuatoriana(cedula: String): Boolean {
        if (cedula.length != 10 || !cedula.all { it.isDigit() }) return false
        val provincia = cedula.substring(0, 2).toIntOrNull() ?: return false
        if (provincia < 1 || (provincia > 24 && provincia !== 30)) return false
        val tercerDigito = cedula.substring(2, 3).toIntOrNull() ?: return false
        if (tercerDigito >= 6) return false

        var suma = 0
        for (i in 0 until 9) {
            var digito = cedula.substring(i, i + 1).toIntOrNull() ?: return false
            if (i % 2 == 0) {
                digito *= 2
                if (digito > 9) digito -= 9
            }
            suma += digito
        }

        val digitoVerificador = cedula.substring(9, 10).toIntOrNull() ?: return false
        val decenaSuperior = if (suma % 10 == 0) suma else ((suma / 10) + 1) * 10
        val resultado = decenaSuperior - suma
        return resultado == digitoVerificador
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

        listOf(etCedula, etPrimerNombre, etApellidoPaterno, etEmail, etTelefono, etFechaNacimiento, etDireccion, etPassword, etConfirmPassword)
            .forEach { FormValidator.marcarErrorEditText(it, null) }

        var campoConError: EditText? = null
        fun marcar(campo: EditText, mensaje: String?) {
            if (mensaje != null) {
                FormValidator.marcarErrorEditText(campo, mensaje)
                if (campoConError == null) campoConError = campo
            }
        }

        val soloLetrasRegex = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")
        val soloDiezNumRegex = Regex("^[0-9]{10}$")

        if (cedula.isBlank() || !soloDiezNumRegex.matches(cedula) || !validarCedulaEcuatoriana(cedula)) {
            marcar(etCedula, "Cédula ecuatoriana inválida")
        }
        if (primerNombre.isBlank() || primerNombre.length < 3 || !soloLetrasRegex.matches(primerNombre)) {
            marcar(etPrimerNombre, "El primer nombre debe tener al menos 3 letras y solo texto")
        }
        if (segundoNombre.isNotBlank() && !soloLetrasRegex.matches(segundoNombre)) {
            marcar(etSegundoNombre, "Solo se permiten letras")
        }
        if (apellidoPaterno.isBlank() || apellidoPaterno.length < 3 || !soloLetrasRegex.matches(apellidoPaterno)) {
            marcar(etApellidoPaterno, "El apellido paterno debe tener al menos 3 letras y solo texto")
        }
        if (apellidoMaterno.isNotBlank() && !soloLetrasRegex.matches(apellidoMaterno)) {
            marcar(etApellidoMaterno, "Solo se permiten letras")
        }
        if (correo.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            marcar(etEmail, "Correo electrónico inválido")
        }
        if (telefono.isBlank() || !soloDiezNumRegex.matches(telefono)) {
            marcar(etTelefono, "El teléfono debe tener exactamente 10 dígitos")
        }
        if (fechaNac.isBlank()) {
            marcar(etFechaNacimiento, "La fecha de nacimiento es requerida")
        }
        if (direccion.isBlank() || direccion.length < 5) {
            marcar(etDireccion, "La dirección debe tener al menos 5 caracteres")
        }
        if (password.isBlank() || password.length < 8) {
            marcar(etPassword, "La contraseña debe tener al menos 8 caracteres")
        }
        if (confirmPassword.isBlank() || password != confirmPassword) {
            marcar(etConfirmPassword, "Las contraseñas no coinciden")
        }

        if (campoConError != null) {
            campoConError?.requestFocus()
            scrollViewRegistro.smoothScrollTo(0, campoConError?.top ?: 0)
            return
        }

        if (fechaNac.isNotBlank()) {
            try {
                val partes = fechaNac.split("-")
                val birthDate = Calendar.getInstance().apply {
                    set(partes[0].toInt(), partes[1].toInt() - 1, partes[2].toInt())
                }
                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)
                val m = today.get(Calendar.MONTH) - birthDate.get(Calendar.MONTH)
                if (m < 0 || (m === 0 && today.get(Calendar.DAY_OF_MONTH) < birthDate.get(Calendar.DAY_OF_MONTH))) {
                    age--
                }
                if (age < 18) {
                    Toast.makeText(this, "Debes ser mayor de 18 años para registrarte.", Toast.LENGTH_LONG).show()
                    return
                }
                if (age > 99) {
                    Toast.makeText(this, "Edad no válida.", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Exception) {}
        }

        if (spinnerParroquia.selectedItemPosition <= 0) {
            Toast.makeText(this, "Selecciona tu parroquia de la lista antes de continuar.", Toast.LENGTH_SHORT).show()
            return
        }

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
            segundoNombre = segundoNombre.ifBlank { "" },
            apellidoPaterno = apellidoPaterno,
            apellidoMaterno = apellidoMaterno.ifBlank { "" },
            email = correo,
            password = password,
            fechaNacimiento = fechaNac,
            telefono = telefono,
            direccion = direccion,
            id_parroquia = idParroquia,
            fotoPerfil = ""
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
                        409 -> "Esta cédula o correo electrónico ya se encuentran registrados."
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
                Toast.makeText(this@RegistroActivity, "Ocurrió un error: ${e.message}", Toast.LENGTH_SHORT).show()
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