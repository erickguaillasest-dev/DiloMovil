package com.example.movildilo.ui.admin

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.UsuarioMeDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val ARG_MODO = "arg_modo"
private const val ARG_USUARIO_JSON = "arg_usuario_json"
private const val MODO_CREAR = "crear"
private const val MODO_EDITAR = "editar"

class UsuarioDialog : DialogFragment() {

    private var modo: String = MODO_CREAR
    private var usuarioOriginal: UsuarioMeDto? = null

    private lateinit var sessionManager: SessionManager
    private lateinit var tvDialogTitle: TextView
    private lateinit var btnCerrar: View
    private lateinit var imgAvatarUsuario: ShapeableImageView
    private lateinit var btnCambiarFoto: MaterialCardView

    // Layouts y Campos
    private lateinit var tilDni: TextInputLayout
    private lateinit var etDni: TextInputEditText

    private lateinit var tilPrimerNombre: TextInputLayout
    private lateinit var etPrimerNombre: TextInputEditText

    private lateinit var tilSegundoNombre: TextInputLayout
    private lateinit var etSegundoNombre: TextInputEditText

    private lateinit var tilApellidoPaterno: TextInputLayout
    private lateinit var etApellidoPaterno: TextInputEditText

    private lateinit var tilApellidoMaterno: TextInputLayout
    private lateinit var etApellidoMaterno: TextInputEditText

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText

    private lateinit var layoutContrasena: TextInputLayout
    private lateinit var etContrasena: TextInputEditText

    private lateinit var tilParroquia: TextInputLayout
    private lateinit var actvParroquia: AutoCompleteTextView

    private lateinit var tilTelefono: TextInputLayout
    private lateinit var etTelefono: TextInputEditText

    private lateinit var tilFechaNacimiento: TextInputLayout
    private lateinit var etFechaNacimiento: TextInputEditText

    private lateinit var tilDireccion: TextInputLayout
    private lateinit var etDireccion: TextInputEditText

    private lateinit var cbEsAdmin: CheckBox
    private lateinit var btnCancelarModal: MaterialButton
    private lateinit var btnGuardarModal: MaterialButton

    private var parroquiaSeleccionadaId: Long? = null
    private var fotoSeleccionadaUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fotoSeleccionadaUri = uri
            imgAvatarUsuario.setImageURI(uri)
        }
    }

    companion object {
        fun newInstanceCrear(): UsuarioDialog {
            val dialog = UsuarioDialog()
            dialog.arguments = Bundle().apply { putString(ARG_MODO, MODO_CREAR) }
            return dialog
        }

        fun newInstanceEditar(usuario: UsuarioMeDto): UsuarioDialog {
            val dialog = UsuarioDialog()
            dialog.arguments = Bundle().apply {
                putString(ARG_MODO, MODO_EDITAR)
                putString(ARG_USUARIO_JSON, Gson().toJson(usuario))
            }
            return dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_usuario, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        modo = arguments?.getString(ARG_MODO) ?: MODO_CREAR
        val usuarioJson = arguments?.getString(ARG_USUARIO_JSON)
        usuarioOriginal = usuarioJson?.let { Gson().fromJson(it, UsuarioMeDto::class.java) }

        initViews(view)
        configurarParroquias()

        if (modo == MODO_EDITAR) {
            tvDialogTitle.text = "Editar Usuario"
            btnGuardarModal.text = "Guardar Cambios"
            precargarDatos()
        } else {
            tvDialogTitle.text = "Registrar Nuevo Usuario"
            btnGuardarModal.text = "Registrar Usuario"
        }

        btnCerrar.setOnClickListener { dismiss() }
        btnCancelarModal.setOnClickListener { dismiss() }
        btnCambiarFoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        etFechaNacimiento.setOnClickListener { mostrarSelectorFecha() }
        btnGuardarModal.setOnClickListener { validarYGuardar() }
    }

    private fun initViews(view: View) {
        tvDialogTitle = view.findViewById(R.id.tvDialogTitle)
        btnCerrar = view.findViewById(R.id.btnCerrar)
        imgAvatarUsuario = view.findViewById(R.id.imgAvatarUsuario)
        btnCambiarFoto = view.findViewById(R.id.btnCambiarFoto)

        tilDni = view.findViewById(R.id.tilDni)
        etDni = view.findViewById(R.id.etDni)

        tilPrimerNombre = view.findViewById(R.id.tilPrimerNombre)
        etPrimerNombre = view.findViewById(R.id.etPrimerNombre)

        tilSegundoNombre = view.findViewById(R.id.tilSegundoNombre)
        etSegundoNombre = view.findViewById(R.id.etSegundoNombre)

        tilApellidoPaterno = view.findViewById(R.id.tilApellidoPaterno)
        etApellidoPaterno = view.findViewById(R.id.etApellidoPaterno)

        tilApellidoMaterno = view.findViewById(R.id.tilApellidoMaterno)
        etApellidoMaterno = view.findViewById(R.id.etApellidoMaterno)

        tilEmail = view.findViewById(R.id.tilEmail)
        etEmail = view.findViewById(R.id.etEmail)

        layoutContrasena = view.findViewById(R.id.layoutContrasena)
        etContrasena = view.findViewById(R.id.etContrasena)

        tilParroquia = view.findViewById(R.id.tilParroquia)
        actvParroquia = view.findViewById(R.id.actvParroquia)

        tilTelefono = view.findViewById(R.id.tilTelefono)
        etTelefono = view.findViewById(R.id.etTelefono)

        tilFechaNacimiento = view.findViewById(R.id.tilFechaNacimiento)
        etFechaNacimiento = view.findViewById(R.id.etFechaNacimiento)

        tilDireccion = view.findViewById(R.id.tilDireccion)
        etDireccion = view.findViewById(R.id.etDireccion)

        cbEsAdmin = view.findViewById(R.id.cbEsAdmin)
        btnCancelarModal = view.findViewById(R.id.btnCancelarModal)
        btnGuardarModal = view.findViewById(R.id.btnGuardarModal)
    }

    private fun configurarParroquias() {
        val parroquias = (activity as? AdminUsuariosActivity)?.parroquias ?: emptyList()
        val nombres = parroquias.map { it.nombre ?: "Sin nombre" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, nombres)
        actvParroquia.setAdapter(adapter)
        actvParroquia.setOnItemClickListener { _, _, position, _ ->
            parroquiaSeleccionadaId = parroquias[position].id
            FormValidator.marcarError(tilParroquia, null)
        }
    }

    private fun precargarDatos() {
        val usuario = usuarioOriginal ?: return
        etDni.setText(usuario.dni ?: "")
        etPrimerNombre.setText(usuario.primerNombre ?: "")
        etSegundoNombre.setText(usuario.segundoNombre ?: "")
        etApellidoPaterno.setText(usuario.apellidoPaterno ?: "")
        etApellidoMaterno.setText(usuario.apellidoMaterno ?: "")
        etEmail.setText(usuario.email ?: "")
        etTelefono.setText(usuario.telefono ?: "")
        etFechaNacimiento.setText(usuario.fechaNacimiento ?: "")
        etDireccion.setText(usuario.direccion ?: "")

        layoutContrasena.hint = "Nueva contraseña (opcional)"
        etContrasena.setText("")

        cbEsAdmin.visibility = View.GONE

        if (!usuario.fotoPerfil.isNullOrBlank()) {
            Glide.with(this)
                .load(usuario.fotoPerfil)
                .circleCrop()
                .placeholder(R.drawable.bg_avatar_circulo)
                .error(R.drawable.bg_avatar_circulo)
                .into(imgAvatarUsuario)
        }

        val parroquias = (activity as? AdminUsuariosActivity)?.parroquias ?: emptyList()
        val parroquiaActual = parroquias.find { it.nombre == usuario.nameParroquia }
        parroquiaSeleccionadaId = parroquiaActual?.id
        actvParroquia.setText(parroquiaActual?.nombre ?: usuario.nameParroquia ?: "", false)
    }

    private fun mostrarSelectorFecha() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, y, m, d ->
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val fecha = Calendar.getInstance().apply { set(y, m, d) }
            etFechaNacimiento.setText(sdf.format(fecha.time))
            FormValidator.marcarError(tilFechaNacimiento, null)
        }, year, month, day).show()
    }

    private fun validarYGuardar() {
        val dni = etDni.text.toString().trim()
        val primerNombre = etPrimerNombre.text.toString().trim()
        val segundoNombre = etSegundoNombre.text.toString().trim()
        val apellidoPaterno = etApellidoPaterno.text.toString().trim()
        val apellidoMaterno = etApellidoMaterno.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etContrasena.text.toString()
        val fechaNacimiento = etFechaNacimiento.text.toString().trim()

        // 1. Cédula Ecuatoriana (dígito verificador)
        val errorDni = FormValidator.cedulaEcuatoriana(dni)
        FormValidator.marcarError(tilDni, errorDni)

        // 2. Primer Nombre
        val errorPrimerNombre = FormValidator.requerido(primerNombre, "El primer nombre")
            ?: FormValidator.longitudMinima(primerNombre, 2, "El primer nombre")
        FormValidator.marcarError(tilPrimerNombre, errorPrimerNombre)

        // 3. Apellido Paterno
        val errorApellidoPaterno = FormValidator.requerido(apellidoPaterno, "El apellido paterno")
            ?: FormValidator.longitudMinima(apellidoPaterno, 2, "El apellido paterno")
        FormValidator.marcarError(tilApellidoPaterno, errorApellidoPaterno)

        // 4. Correo electrónico
        val errorEmail = FormValidator.correo(email)
        FormValidator.marcarError(tilEmail, errorEmail)

        // 5. Contraseña (Obligatoria al crear; opcional al editar salvo si se ingresan caracteres)
        val errorPassword = if (modo == MODO_CREAR) {
            FormValidator.requerido(password, "La contraseña")
                ?: FormValidator.longitudMinima(password, 8, "La contraseña")
        } else if (password.isNotEmpty()) {
            FormValidator.longitudMinima(password, 8, "La contraseña")
        } else null
        FormValidator.marcarError(layoutContrasena, errorPassword)

        // 6. Selección de Parroquia
        val errorParroquia = if (modo == MODO_CREAR && parroquiaSeleccionadaId == null) {
            "Debe seleccionar una parroquia obligatoria."
        } else null
        FormValidator.marcarError(tilParroquia, errorParroquia)

        // 7. Fecha de Nacimiento / Edad (>= 18 y < 99)
        val errorFecha = FormValidator.requerido(fechaNacimiento, "La fecha de nacimiento")
            ?: run {
                val edad = calcularEdad(fechaNacimiento)
                when {
                    edad == null -> "La fecha de nacimiento no es válida."
                    edad < 18 -> "El usuario debe tener al menos 18 años."
                    edad >= 99 -> "La edad debe ser menor a 99 años."
                    else -> null
                }
            }
        FormValidator.marcarError(tilFechaNacimiento, errorFecha)

        // Detener ejecución si existe algún error activo
        if (errorDni != null || errorPrimerNombre != null || errorApellidoPaterno != null ||
            errorEmail != null || errorPassword != null || errorParroquia != null || errorFecha != null) {
            return
        }

        val dto = JSONObject().apply {
            put("dni", dni)
            put("primerNombre", primerNombre)
            put("segundoNombre", segundoNombre)
            put("apellidoPaterno", apellidoPaterno)
            put("apellidoMaterno", apellidoMaterno)
            put("email", email)
            put("telefono", etTelefono.text.toString().trim())
            put("direccion", etDireccion.text.toString().trim())
            put("id_parroquia", parroquiaSeleccionadaId ?: JSONObject.NULL)
            put("fechaNacimiento", fechaNacimiento)

            if (modo == MODO_CREAR || password.isNotEmpty()) {
                put("password", password)
            }

            if (modo == MODO_CREAR) {
                put("fotoPerfil", "")
                put("esAdmin", cbEsAdmin.isChecked)
            }
        }

        btnGuardarModal.isEnabled = false
        if (modo == MODO_CREAR) {
            registrarUsuario(dto)
        } else {
            actualizarUsuario(dto)
        }
    }

    private fun calcularEdad(fechaTexto: String): Int? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val fechaNacimiento = sdf.parse(fechaTexto) ?: return null
            val nacimiento = Calendar.getInstance().apply { time = fechaNacimiento }
            val hoy = Calendar.getInstance()
            var edad = hoy.get(Calendar.YEAR) - nacimiento.get(Calendar.YEAR)
            if (hoy.get(Calendar.DAY_OF_YEAR) < nacimiento.get(Calendar.DAY_OF_YEAR)) edad--
            edad
        } catch (e: Exception) {
            null
        }
    }

    private fun construirFotoPart(): MultipartBody.Part? {
        val uri = fotoSeleccionadaUri ?: return null
        val archivo = copiarUriAArchivoTemporal(uri) ?: return null
        val mimeType = obtenerMimeType(archivo.name)
        val requestFile = RequestBody.create(MediaType.parse(mimeType), archivo)
        return MultipartBody.Part.createFormData("foto", archivo.name, requestFile)
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
            val archivo = File(requireContext().cacheDir, "foto_usuario_${System.currentTimeMillis()}.jpg")
            FileOutputStream(archivo).use { output -> inputStream.copyTo(output) }
            archivo
        } catch (e: Exception) {
            null
        }
    }

    private fun registrarUsuario(dto: JSONObject) {
        val body = RequestBody.create(MediaType.parse("application/json"), dto.toString())
        val fotoPart = construirFotoPart()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.register(body, fotoPart)
                btnGuardarModal.isEnabled = true
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "El usuario ha sido creado y registrado en el sistema.", Toast.LENGTH_SHORT).show()
                    (activity as? OnUsuarioActualizadoListener)?.onUsuarioActualizado()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "No se pudo crear el usuario (${response.code()}). Revisa que el DNI o Email no estén repetidos.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                btnGuardarModal.isEnabled = true
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarUsuario(dto: JSONObject) {
        val authHeader = sessionManager.getAuthHeader()
        val id = usuarioOriginal?.id
        if (authHeader == null || id == null) {
            btnGuardarModal.isEnabled = true
            Toast.makeText(requireContext(), "No se pudo identificar al usuario a editar.", Toast.LENGTH_LONG).show()
            return
        }
        val body = RequestBody.create(MediaType.parse("application/json"), dto.toString())
        val fotoPart = construirFotoPart()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.actualizarUsuarioAdmin(authHeader, id, body, fotoPart)
                btnGuardarModal.isEnabled = true
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Los datos del usuario han sido actualizados.", Toast.LENGTH_SHORT).show()
                    (activity as? OnUsuarioActualizadoListener)?.onUsuarioActualizado()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "No se pudo guardar la información (${response.code()}).", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnGuardarModal.isEnabled = true
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}