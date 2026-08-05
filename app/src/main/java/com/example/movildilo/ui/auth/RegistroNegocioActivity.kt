package com.example.movildilo.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.ui.admin.AdminActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class RegistroNegocioActivity : AppCompatActivity() {

    private lateinit var etRuc: EditText
    private lateinit var etRazonSocial: EditText
    private lateinit var etNombreComercial: EditText
    private lateinit var etDireccion: EditText
    private lateinit var spMetodoInventario: AutoCompleteTextView
    private lateinit var cbObligadoContabilidad: CheckBox
    private lateinit var btnSubirLogo: LinearLayout
    private lateinit var ivLogoPreview: ImageView
    private lateinit var btnRegistrarNegocio: MaterialButton
    private lateinit var btnVolverOpciones: TextView

    private lateinit var sessionManager: SessionManager

    // Mapeo seguro para el método de costeo seleccionado
    private val mapaMetodosCosteo = mutableMapOf<String, String>()

    // Guarda temporalmente la imagen elegida
    private var archivoImagenSeleccionado: File? = null

    // Selector de imágenes nativo de Android
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            archivoImagenSeleccionado = uriToFile(it)
            ivLogoPreview.setImageURI(it)
            ivLogoPreview.colorFilter = null
            Toast.makeText(this, "Imagen seleccionada correctamente", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // 🔥 ESCUDO PARA ADMINS: Ningún Admin/SuperAdmin puede estar en esta pantalla
        if (sessionManager.isAdmin()) {
            val intent = Intent(this, AdminActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        ocultarBarrasSistema()
        setContentView(R.layout.activity_registro_negocio)

        // Inicializar vistas
        etRuc = findViewById(R.id.etRuc)
        etRazonSocial = findViewById(R.id.etRazonSocial)
        etNombreComercial = findViewById(R.id.etNombreComercial)
        etDireccion = findViewById(R.id.etDireccion)
        spMetodoInventario = findViewById(R.id.spMetodoInventario)
        cbObligadoContabilidad = findViewById(R.id.cbObligadoContabilidad)
        btnSubirLogo = findViewById(R.id.btnSubirLogo)
        ivLogoPreview = findViewById(R.id.ivLogoPreview)
        btnRegistrarNegocio = findViewById(R.id.btnRegistrarNegocio)
        btnVolverOpciones = findViewById(R.id.btnVolverOpciones)

        // Cargar opciones del método de inventario
        configurarDropdownMetodoInventario()

        // Botón de regreso físico
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                regresarASelectRole()
            }
        })

        // Eventos
        btnSubirLogo.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnRegistrarNegocio.setOnClickListener {
            validarYRegistrarNegocio()
        }

        btnVolverOpciones.setOnClickListener {
            regresarASelectRole()
        }
    }

    /**
     * Convierte la URI seleccionada de la galería en un archivo local temporal
     */
    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("logo_negocio", ".jpg", cacheDir)
        tempFile.deleteOnExit()
        val outputStream = FileOutputStream(tempFile)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun obtenerMimeType(nombreArchivo: String): String {
        return when {
            nombreArchivo.endsWith(".png", ignoreCase = true) -> "image/png"
            nombreArchivo.endsWith(".webp", ignoreCase = true) -> "image/webp"
            nombreArchivo.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            nombreArchivo.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            else -> "image/jpeg" // valor seguro por defecto
        }
    }

    /**
     * Configura el menú desplegable con los métodos exactos que soporta el sistema
     */
    private fun configurarDropdownMetodoInventario() {
        mapaMetodosCosteo.clear()
        mapaMetodosCosteo["Promedio Ponderado (Recomendado)"] = "PROMEDIO"
        mapaMetodosCosteo["FIFO (Primeras Entradas, Primeras Salidas)"] = "FIFO"
        mapaMetodosCosteo["LIFO (Últimas Entradas, Primeras Salidas)"] = "LIFO"

        val opcionesMostrar = mapaMetodosCosteo.keys.toList()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            opcionesMostrar
        )
        spMetodoInventario.setAdapter(adapter)
        spMetodoInventario.setText(opcionesMostrar[0], false)
    }

    /**
     * Obtiene la clave seleccionada directamente del mapa para enviarla al backend
     */
    private fun obtenerCodigoMetodoCosteo(texto: String): String {
        return mapaMetodosCosteo[texto] ?: "PROMEDIO"
    }

    private fun validarYRegistrarNegocio() {
        val ruc = etRuc.text.toString().trim()
        val razonSocial = etRazonSocial.text.toString().trim()
        val nombreComercial = etNombreComercial.text.toString().trim()
        val direccion = etDireccion.text.toString().trim()
        val metodoTexto = spMetodoInventario.text.toString().trim()
        val obligadoContabilidad = cbObligadoContabilidad.isChecked

        // Validaciones estrictas
        if (ruc.isEmpty()) {
            etRuc.error = "El RUC es obligatorio"
            etRuc.requestFocus()
            return
        }

        if (ruc.length != 13) {
            etRuc.error = "El RUC debe tener exactamente 13 dígitos"
            etRuc.requestFocus()
            return
        }

        if (razonSocial.isEmpty()) {
            etRazonSocial.error = "La razón social es obligatoria"
            etRazonSocial.requestFocus()
            return
        }

        if (nombreComercial.isEmpty()) {
            etNombreComercial.error = "El nombre comercial es obligatorio"
            etNombreComercial.requestFocus()
            return
        }

        if (direccion.isEmpty()) {
            etDireccion.error = "La dirección es obligatoria"
            etDireccion.requestFocus()
            return
        }

        if (metodoTexto.isEmpty()) {
            Toast.makeText(this, "Debe seleccionar un método de inventario válido de la lista", Toast.LENGTH_LONG).show()
            return
        }

        val metodoCosteo = obtenerCodigoMetodoCosteo(metodoTexto)

        guardarNegocioEnServidor(
            ruc = ruc,
            razonSocial = razonSocial,
            nombreComercial = nombreComercial,
            direccion = direccion,
            metodoCosteo = metodoCosteo,
            obligadoContabilidad = obligadoContabilidad
        )
    }

    private fun guardarNegocioEnServidor(
        ruc: String,
        razonSocial: String,
        nombreComercial: String,
        direccion: String,
        metodoCosteo: String,
        obligadoContabilidad: Boolean
    ) {
        setLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 0. Token de autorización seguro
                val authHeader = sessionManager.getAuthHeader()
                if (authHeader == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@RegistroNegocioActivity,
                            "Tu sesión expiró. Vuelve a iniciar sesión.",
                            Toast.LENGTH_LONG
                        ).show()
                        setLoading(false)
                    }
                    return@launch
                }

                // 1. Construcción exacta del JSON idéntico al que procesa la web
                val jsonObject = JSONObject().apply {
                    put("ruc", ruc)
                    put("razonSocial", razonSocial)
                    put("nombreComercial", nombreComercial)
                    put("direccion", direccion)
                    put("metodoCosteo", metodoCosteo)
                    put("obligadoContabilidad", obligadoContabilidad)
                }

                // 2. RequestBody con el tipo JSON y codificación correcta para Multipart
                val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
                val datosRequestBody = RequestBody.create(jsonMediaType, jsonObject.toString())

                // 3. Preparar la imagen opcional (Content-Type real, no un comodín)
                val imagenPart: MultipartBody.Part? = archivoImagenSeleccionado?.let { file ->
                    val mimeType = obtenerMimeType(file.name)
                    val imageMediaType = MediaType.parse(mimeType)
                    val requestFile = RequestBody.create(imageMediaType, file)
                    MultipartBody.Part.createFormData("imagen", file.name, requestFile)
                }

                // 4. Llamada al Backend con los parámetros exactos de la interfaz
                val response = RetrofitClient.apiService.registrarNegocio(
                    token = authHeader,
                    datos = datosRequestBody,
                    imagen = imagenPart
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()

                        if (body != null) {
                            Toast.makeText(
                                this@RegistroNegocioActivity,
                                "¡Negocio creado exitosamente!",
                                Toast.LENGTH_SHORT
                            ).show()

                            val idGenerado = body["idNegocio"] ?: body["id"] ?: body["negocioId"]
                            val idNegocioLong = when (idGenerado) {
                                is Double -> idGenerado.toLong()
                                is Int -> idGenerado.toLong()
                                is Long -> idGenerado
                                is String -> idGenerado.toDoubleOrNull()?.toLong()
                                else -> null
                            }

                            if (idNegocioLong != null) {
                                sessionManager.saveNegocioId(idNegocioLong)
                            } else {
                                Toast.makeText(
                                    this@RegistroNegocioActivity,
                                    "El negocio se creó, pero no se pudo guardar su ID. Cierra sesión y vuelve a entrar.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            val intent = Intent(this@RegistroNegocioActivity, PropietarioActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@RegistroNegocioActivity, "Respuesta vacía del servidor", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val mensajeError = when (response.code()) {
                            401, 403 -> "Sesión expirada o sin permisos. Inicia sesión nuevamente."
                            400 -> "Datos inválidos o el RUC ya se encuentra registrado."
                            500 -> "Error en el servidor. Verifica que el RUC sea válido y no esté repetido."
                            else -> "Error en el servidor (${response.code()})"
                        }
                        Toast.makeText(this@RegistroNegocioActivity, mensajeError, Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegistroNegocioActivity,
                        "Error de conexión. Revisa tu internet.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RegistroNegocioActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnRegistrarNegocio.isEnabled = !loading
        btnRegistrarNegocio.text = if (loading) "Registrando..." else "Registrar Negocio"
    }

    private fun regresarASelectRole() {
        val intent = Intent(this, SelectRoleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun ocultarBarrasSistema() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}