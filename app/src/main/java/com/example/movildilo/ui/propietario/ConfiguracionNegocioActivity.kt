package com.example.movildilo.ui.propietario

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream

class ConfiguracionNegocioActivity : AppCompatActivity() {

    private lateinit var btnRegresar: ImageButton
    private lateinit var imgLogoNegocio: ShapeableImageView
    private lateinit var btnSubirLogo: MaterialCardView
    private lateinit var etRuc: TextInputEditText
    private lateinit var etNombreComercial: TextInputEditText
    private lateinit var etRazonSocial: TextInputEditText
    private lateinit var etDireccion: TextInputEditText
    private lateinit var spinnerMetodoCosteo: AutoCompleteTextView
    private lateinit var cbObligadoContabilidad: MaterialCheckBox
    private lateinit var btnGuardarConfiguracion: MaterialButton
    private lateinit var layoutLoading: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L

    private var selectedImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null

    private val opcionesCosteo = arrayOf(
        "PROMEDIO" to "Promedio Ponderado",
        "FIFO" to "FIFO (Primeras Entradas, Primeras Salidas)",
        "LIFO" to "LIFO (Últimas Entradas, Primeras Salidas)"
    )

    private val seleccGaleriaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            selectedBitmap = null
            imgLogoNegocio.setImageURI(it)
        }
    }

    private val tomarFotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            selectedBitmap = it
            selectedImageUri = null
            imgLogoNegocio.setImageBitmap(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion_negocio)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupDropdownCosteo()
        setupListeners()

        if (negocioId != -1L) {
            cargarNegocio(negocioId)
        } else {
            mostrarAlertaSinNegocio()
        }

        if (intent.getStringExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_ACCION) ==
            com.example.movildilo.ia.ZoeActionRouter.Accion.EDITAR_NEGOCIO
        ) {
            etRazonSocial.postDelayed({
                etRazonSocial.requestFocus()
                Toast.makeText(
                    this,
                    "Aquí puedes editar los datos de tu negocio. Cuando termines, toca \"Guardar\".",
                    Toast.LENGTH_LONG
                ).show()
            }, 700)
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        imgLogoNegocio = findViewById(R.id.imgLogoNegocio)
        btnSubirLogo = findViewById(R.id.btnSubirLogo)
        etRuc = findViewById(R.id.etRuc)
        etNombreComercial = findViewById(R.id.etNombreComercial)
        etRazonSocial = findViewById(R.id.etRazonSocial)
        etDireccion = findViewById(R.id.etDireccion)
        spinnerMetodoCosteo = findViewById(R.id.spinnerMetodoCosteo)
        cbObligadoContabilidad = findViewById(R.id.cbObligadoContabilidad)
        btnGuardarConfiguracion = findViewById(R.id.btnGuardarConfiguracion)
        layoutLoading = findViewById(R.id.layoutLoading)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupDropdownCosteo() {
        val adapterLabels = opcionesCosteo.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, adapterLabels)
        spinnerMetodoCosteo.setAdapter(adapter)
    }

    private fun setupListeners() {
        btnRegresar.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        btnSubirLogo.setOnClickListener { mostrarOpcionesFoto() }
        btnGuardarConfiguracion.setOnClickListener { guardarCambios() }

        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId != -1L) {
                cargarNegocio(negocioId)
            } else {
                swipeRefreshLayout.isRefreshing = false
                mostrarAlertaSinNegocio()
            }
        }
    }

    private fun cargarNegocio(id: Long) {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            swipeRefreshLayout.isRefreshing = false
            return
        }

        if (!swipeRefreshLayout.isRefreshing) {
            layoutLoading.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getNegocio(authHeader, id)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val data: NegocioResponseDto = response.body()!!

                        etRuc.setText(data.ruc.orEmpty())
                        etNombreComercial.setText(data.nombreComercial.orEmpty())
                        etRazonSocial.setText(data.razonSocial.orEmpty())
                        etDireccion.setText(data.direccion.orEmpty())
                        cbObligadoContabilidad.isChecked = data.obligadoContabilidad ?: false

                        val selectedPair = opcionesCosteo.find { it.first.equals(data.metodoCosteo, ignoreCase = true) }
                        val textoMostrar = selectedPair?.second ?: data.metodoCosteo ?: opcionesCosteo[0].second
                        spinnerMetodoCosteo.setText(textoMostrar, false)

                        if (!data.rutaImagen.isNullOrEmpty()) {
                            Glide.with(this@ConfiguracionNegocioActivity)
                                .load(data.rutaImagen)
                                .placeholder(R.drawable.bg_avatar_circulo)
                                .error(R.drawable.bg_avatar_circulo)
                                .into(imgLogoNegocio)
                        } else {
                            imgLogoNegocio.setImageResource(R.drawable.bg_avatar_circulo)
                        }
                    } else {
                        Toast.makeText(this@ConfiguracionNegocioActivity, "Error al cargar la información del negocio", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ConfiguracionNegocioActivity, "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun validarFormulario(
        ruc: String,
        razonSocial: String,
        nombreComercial: String,
        direccion: String
    ): Boolean {
        var esValido = true
        var primerCampoError: View? = null

        if (ruc.isEmpty()) {
            etRuc.error = "El RUC es obligatorio"
            if (primerCampoError == null) primerCampoError = etRuc
            esValido = false
        } else if (!ruc.matches(Regex("^[0-9]{13}$"))) {
            etRuc.error = "El RUC debe ser numérico y contener exactamente 13 dígitos"
            if (primerCampoError == null) primerCampoError = etRuc
            esValido = false
        } else {
            etRuc.error = null
        }

        if (razonSocial.isEmpty()) {
            etRazonSocial.error = "La razón social es obligatoria"
            if (primerCampoError == null) primerCampoError = etRazonSocial
            esValido = false
        } else if (razonSocial.length < 3) {
            etRazonSocial.error = "La razón social debe tener al menos 3 caracteres"
            if (primerCampoError == null) primerCampoError = etRazonSocial
            esValido = false
        } else {
            etRazonSocial.error = null
        }

        if (nombreComercial.isEmpty()) {
            etNombreComercial.error = "El nombre comercial es obligatorio"
            if (primerCampoError == null) primerCampoError = etNombreComercial
            esValido = false
        } else if (nombreComercial.length < 2) {
            etNombreComercial.error = "El nombre comercial debe tener al menos 2 caracteres"
            if (primerCampoError == null) primerCampoError = etNombreComercial
            esValido = false
        } else {
            etNombreComercial.error = null
        }

        if (direccion.isEmpty()) {
            etDireccion.error = "La dirección es obligatoria"
            if (primerCampoError == null) primerCampoError = etDireccion
            esValido = false
        } else if (direccion.length < 5) {
            etDireccion.error = "La dirección debe tener al menos 5 caracteres"
            if (primerCampoError == null) primerCampoError = etDireccion
            esValido = false
        } else {
            etDireccion.error = null
        }

        if (!esValido) {
            primerCampoError?.requestFocus()
            Toast.makeText(this, "Por favor, corrige los errores señalados en el formulario.", Toast.LENGTH_SHORT).show()
        }

        return esValido
    }

    private fun guardarCambios() {
        if (negocioId == -1L) {
            Toast.makeText(this, "No se encontró el ID del negocio.", Toast.LENGTH_SHORT).show()
            return
        }

        val ruc = etRuc.text.toString().trim()
        val razonSocial = etRazonSocial.text.toString().trim()
        val nombreComercial = etNombreComercial.text.toString().trim()
        val direccion = etDireccion.text.toString().trim()

        if (!validarFormulario(ruc, razonSocial, nombreComercial, direccion)) {
            return
        }

        val authHeader = sessionManager.getAuthHeader() ?: run {
            Toast.makeText(this, "Sesión no válida.", Toast.LENGTH_SHORT).show()
            return
        }


        val selectedText = spinnerMetodoCosteo.text.toString()
        val metodoCosteo = opcionesCosteo.find { it.second == selectedText }?.first ?: selectedText

        val dto = NegocioResponseDto(
            ruc = ruc,
            razonSocial = razonSocial,
            nombreComercial = nombreComercial,
            direccion = direccion,
            obligadoContabilidad = cbObligadoContabilidad.isChecked,
            metodoCosteo = metodoCosteo
        )

        val dtoJson = Gson().toJson(dto)
        val datosRequestBody = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            dtoJson
        )

        var imagenPart: MultipartBody.Part? = null
        val file = obtenerArchivoImagen()
        if (file != null) {
            val requestFile = RequestBody.create(
                MediaType.parse("image/jpeg"),
                file
            )
            imagenPart = MultipartBody.Part.createFormData("imagen", file.name, requestFile)
        }

        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.actualizarNegocio(
                    authHeader,
                    negocioId,
                    datosRequestBody,
                    imagenPart
                )

                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        MaterialAlertDialogBuilder(this@ConfiguracionNegocioActivity)
                            .setTitle("¡Éxito!")
                            .setMessage("Los datos de tu negocio han sido actualizados correctamente.")
                            .setPositiveButton("Aceptar") { _, _ -> cargarNegocio(negocioId) }
                            .setCancelable(false)
                            .show()
                    } else {
                        Toast.makeText(this@ConfiguracionNegocioActivity, "Error al actualizar la configuración (${response.code()}).", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@ConfiguracionNegocioActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarOpcionesFoto() {
        val opciones = arrayOf("Tomar foto", "Elegir de la galería")
        MaterialAlertDialogBuilder(this)
            .setTitle("Logo del Negocio")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> tomarFotoLauncher.launch(null)
                    1 -> seleccGaleriaLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun obtenerArchivoImagen(): File? {
        return try {
            val file = File(cacheDir, "temp_logo.jpg")
            if (selectedBitmap != null) {
                val fos = FileOutputStream(file)
                selectedBitmap?.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()
                file
            } else if (selectedImageUri != null) {
                val inputStream = contentResolver.openInputStream(selectedImageUri!!)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                file
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun mostrarAlertaSinNegocio() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sesión desactualizada")
            .setMessage("No logramos detectar tu negocio actual. Por favor, cierra sesión y vuelve a ingresar para sincronizar tus datos.")
            .setPositiveButton("Ir al Login") { _, _ ->
                sessionManager.clearSession()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}