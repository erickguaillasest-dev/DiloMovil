package com.example.movildilo.ui.admin

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
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

class EditarNegocioDialog : DialogFragment() {

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_RUC = "arg_ruc"
        private const val ARG_RAZON = "arg_razon"
        private const val ARG_COMERCIAL = "arg_comercial"
        private const val ARG_DIRECCION = "arg_direccion"
        private const val ARG_COSTEO = "arg_costeo"
        private const val ARG_OBLIGADO = "arg_obligado"
        private const val ARG_IMAGEN = "arg_imagen"

        fun newInstance(negocio: NegocioResponseDto): EditarNegocioDialog {
            val fragment = EditarNegocioDialog()
            fragment.arguments = Bundle().apply {
                putLong(ARG_ID, negocio.id ?: -1L)
                putString(ARG_RUC, negocio.ruc)
                putString(ARG_RAZON, negocio.razonSocial)
                putString(ARG_COMERCIAL, negocio.nombreComercial)
                putString(ARG_DIRECCION, negocio.direccion)
                putString(ARG_COSTEO, negocio.metodoCosteo)
                putBoolean(ARG_OBLIGADO, negocio.obligadoContabilidad == true)
                putString(ARG_IMAGEN, negocio.rutaImagen)
            }
            return fragment
        }
    }

    private lateinit var sessionManager: SessionManager
    private val gson = Gson()

    private lateinit var tvDialogTitle: android.widget.TextView
    private lateinit var btnCerrar: ImageView
    private lateinit var imgAvatarNegocio: ShapeableImageView
    private lateinit var btnCambiarFoto: MaterialCardView
    private lateinit var etRuc: TextInputEditText
    private lateinit var etRazonSocial: TextInputEditText
    private lateinit var etNombreComercial: TextInputEditText
    private lateinit var etDireccion: TextInputEditText
    private lateinit var tilRuc: TextInputLayout
    private lateinit var tilRazonSocial: TextInputLayout
    private lateinit var tilNombreComercial: TextInputLayout
    private lateinit var spinnerMetodoCosteo: AutoCompleteTextView
    private lateinit var cbObligadoContabilidad: MaterialCheckBox
    private lateinit var btnCancelarModal: MaterialButton
    private lateinit var btnGuardarModal: MaterialButton

    private var negocioId: Long = -1L
    private var fotoSeleccionadaUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fotoSeleccionadaUri = uri
            imgAvatarNegocio.setImageURI(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_editar_negocio, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        negocioId = arguments?.getLong(ARG_ID) ?: -1L

        initViews(view)
        prellenarCampos()
        setupListeners()
    }

    private fun initViews(view: View) {
        tvDialogTitle = view.findViewById(R.id.tvDialogTitle)
        btnCerrar = view.findViewById(R.id.btnCerrar)
        imgAvatarNegocio = view.findViewById(R.id.imgAvatarNegocio)
        btnCambiarFoto = view.findViewById(R.id.btnCambiarFoto)
        etRuc = view.findViewById(R.id.etRuc)
        etRazonSocial = view.findViewById(R.id.etRazonSocial)
        etNombreComercial = view.findViewById(R.id.etNombreComercial)
        etDireccion = view.findViewById(R.id.etDireccion)
        tilRuc = view.findViewById(R.id.tilRuc)
        tilRazonSocial = view.findViewById(R.id.tilRazonSocial)
        tilNombreComercial = view.findViewById(R.id.tilNombreComercial)
        spinnerMetodoCosteo = view.findViewById(R.id.spinnerMetodoCosteo)
        cbObligadoContabilidad = view.findViewById(R.id.cbObligadoContabilidad)
        btnCancelarModal = view.findViewById(R.id.btnCancelarModal)
        btnGuardarModal = view.findViewById(R.id.btnGuardarModal)
    }

    private fun prellenarCampos() {
        tvDialogTitle.text = "Editar Negocio"

        etRuc.setText(arguments?.getString(ARG_RUC))
        etRazonSocial.setText(arguments?.getString(ARG_RAZON))
        etNombreComercial.setText(arguments?.getString(ARG_COMERCIAL))
        etDireccion.setText(arguments?.getString(ARG_DIRECCION))
        cbObligadoContabilidad.isChecked = arguments?.getBoolean(ARG_OBLIGADO) ?: false

        val opcionesCosteo = listOf("PROMEDIO", "FIFO", "LIFO")
        spinnerMetodoCosteo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, opcionesCosteo))
        spinnerMetodoCosteo.setText(arguments?.getString(ARG_COSTEO) ?: "PROMEDIO", false)

        val rutaImagen = arguments?.getString(ARG_IMAGEN)
        if (!rutaImagen.isNullOrBlank()) {
            Glide.with(this)
                .load(rutaImagen)
                .circleCrop()
                .placeholder(R.drawable.bg_avatar_circulo)
                .error(R.drawable.bg_avatar_circulo)
                .into(imgAvatarNegocio)
        }
    }

    private fun setupListeners() {
        btnCerrar.setOnClickListener { dismiss() }
        btnCancelarModal.setOnClickListener { dismiss() }
        btnCambiarFoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnGuardarModal.setOnClickListener { guardarCambios() }
    }

    private fun guardarCambios() {
        if (negocioId == -1L) {
            Toast.makeText(requireContext(), "No se pudo identificar el negocio.", Toast.LENGTH_SHORT).show()
            return
        }

        val ruc = etRuc.text.toString().trim()
        val razonSocial = etRazonSocial.text.toString().trim()
        val nombreComercial = etNombreComercial.text.toString().trim()
        val direccion = etDireccion.text.toString().trim()
        val metodoCosteo = spinnerMetodoCosteo.text.toString().trim().ifBlank { "PROMEDIO" }

        val ok = FormValidator.validar(
            FormValidator.Campo(tilRuc) { FormValidator.rucEcuatoriano(ruc) },
            FormValidator.Campo(tilRazonSocial) {
                FormValidator.requerido(razonSocial, "La razón social")
                    ?: FormValidator.longitudMinima(razonSocial, 3, "La razón social")
                    ?: FormValidator.longitudMaxima(razonSocial, 150, "La razón social")
            },
            FormValidator.Campo(tilNombreComercial) {
                FormValidator.longitudMaxima(nombreComercial, 150, "El nombre comercial")
            }
        )
        if (!ok) return

        val authHeader = sessionManager.getAuthHeader() ?: return

        val datosJson = mapOf(
            "ruc" to ruc,
            "razonSocial" to razonSocial,
            "nombreComercial" to nombreComercial,
            "direccion" to direccion,
            "obligadoContabilidad" to cbObligadoContabilidad.isChecked,
            "metodoCosteo" to metodoCosteo
        )
        val jsonBody = gson.toJson(datosJson)
        val jsonMediaType = MediaType.parse("application/json; charset=utf-8")
        val datosRequestBody = RequestBody.create(jsonMediaType, jsonBody)

        val imagenPart: MultipartBody.Part? = fotoSeleccionadaUri?.let { uri ->
            val archivo = copiarUriAArchivoTemporal(uri)
            archivo?.let {
                val mimeType = obtenerMimeType(it.name)
                val requestFile = RequestBody.create(MediaType.parse(mimeType), it)
                MultipartBody.Part.createFormData("imagen", it.name, requestFile)
            }
        }

        btnGuardarModal.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.actualizarNegocio(authHeader, negocioId, datosRequestBody, imagenPart)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Negocio actualizado correctamente.", Toast.LENGTH_SHORT).show()
                    (activity as? OnNegocioActualizadoListener)?.onNegocioActualizado()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "No se pudo actualizar (código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnGuardarModal.isEnabled = true
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
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val archivo = File(requireContext().cacheDir, "logo_negocio_${System.currentTimeMillis()}.jpg")
            FileOutputStream(archivo).use { output -> inputStream.copyTo(output) }
            archivo
        } catch (e: Exception) {
            null
        }
    }
}