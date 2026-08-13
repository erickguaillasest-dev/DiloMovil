package com.example.movildilo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ForgotPasswordRequestDto
import com.example.movildilo.data.model.dto.LoginRequestDto
import com.example.movildilo.data.model.dto.ResetPasswordRequestDto
import com.example.movildilo.ui.dashboard.AdminActivity
import com.example.movildilo.ui.dashboard.BodegueroActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.example.movildilo.ui.dashboard.VendedorActivity
import com.example.movildilo.ui.main.MainActivity
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private var passwordVisible = false

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private var btnTogglePassword: TextView? = null
    private var tvEmailError: TextView? = null
    private var tvPasswordError: TextView? = null

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Si ya hay sesión iniciada, redirige usando el rol guardado
        if (sessionManager.isLoggedIn()) {
            redirigirSegunRolGuardado()
            return
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        tvEmailError = findViewById(R.id.tvEmailError)
        tvPasswordError = findViewById(R.id.tvPasswordError)

        // Limpia el error de cada campo apenas el usuario empieza a corregirlo, para que
        // el mensaje no se quede pegado en pantalla aunque ya haya escrito algo válido.
        etEmail.setOnFocusChangeListener { _, tieneFoco -> if (tieneFoco) FormValidator.marcarErrorSimple(etEmail, tvEmailError, null) }
        etPassword.setOnFocusChangeListener { _, tieneFoco -> if (tieneFoco) FormValidator.marcarErrorSimple(etPassword, tvPasswordError, null) }

        btnTogglePassword?.setOnClickListener { togglePasswordVisibility() }
        btnLogin.setOnClickListener { onLoginClicked() }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            mostrarDialogoRecuperarPassword()
        }
    }

    private fun mostrarDialogoRecuperarPassword() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogError)
        val etInput = dialogView.findViewById<EditText>(R.id.etDialogInput)
        val btnAccion = dialogView.findViewById<MaterialButton>(R.id.btnDialogAccion)
        val tvCancelar = dialogView.findViewById<TextView>(R.id.tvDialogCancelar)

        etInput.hint = "correo@ejemplo.com"
        etInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        btnAccion.text = "Enviar código"

        btnAccion.setOnClickListener {
            val email = etInput.text.toString().trim()
            tvError.visibility = android.view.View.GONE

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvError.text = "Debes ingresar un correo válido"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            btnAccion.isEnabled = false
            btnAccion.text = "Enviando..."

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.forgotPassword(ForgotPasswordRequestDto(email))
                    if (response.isSuccessful) {
                        dialog.dismiss()
                        mostrarDialogoCodigo(email)
                    } else {
                        val mensaje = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                            ?: "No pudimos encontrar ese correo."
                        tvError.text = mensaje
                        tvError.visibility = android.view.View.VISIBLE
                        btnAccion.isEnabled = true
                        btnAccion.text = "Enviar código"
                    }
                } catch (e: Exception) {
                    tvError.text = "No se pudo conectar al servidor. Revisa tu conexión."
                    tvError.visibility = android.view.View.VISIBLE
                    btnAccion.isEnabled = true
                    btnAccion.text = "Enviar código"
                }
            }
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoCodigo(email: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitulo).text = "Verifica tu correo"
        dialogView.findViewById<TextView>(R.id.tvDialogSubtitulo).text =
            "Hemos enviado un código de 6 dígitos a $email.\nRevísalo e ingrésalo aquí:"

        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogError)
        val etInput = dialogView.findViewById<EditText>(R.id.etDialogInput)
        val btnAccion = dialogView.findViewById<MaterialButton>(R.id.btnDialogAccion)
        val tvCancelar = dialogView.findViewById<TextView>(R.id.tvDialogCancelar)

        etInput.hint = "Ej: 123456"
        etInput.inputType = InputType.TYPE_CLASS_NUMBER
        etInput.filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        btnAccion.text = "Verificar código"

        btnAccion.setOnClickListener {
            val codigo = etInput.text.toString().trim()
            tvError.visibility = android.view.View.GONE

            if (codigo.length < 6) {
                tvError.text = "El código debe tener 6 dígitos"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            dialog.dismiss()
            mostrarDialogoNuevaPassword(email, codigo)
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarDialogoNuevaPassword(email: String, codigo: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitulo).text = "Nueva Contraseña"
        dialogView.findViewById<TextView>(R.id.tvDialogSubtitulo).text = "Crea tu nueva contraseña segura."

        val tvError = dialogView.findViewById<TextView>(R.id.tvDialogError)
        val etInput = dialogView.findViewById<EditText>(R.id.etDialogInput)
        val btnAccion = dialogView.findViewById<MaterialButton>(R.id.btnDialogAccion)
        val tvCancelar = dialogView.findViewById<TextView>(R.id.tvDialogCancelar)

        etInput.hint = "Mínimo 8 caracteres"
        etInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        btnAccion.text = "Guardar cambios"

        btnAccion.setOnClickListener {
            val nuevaPassword = etInput.text.toString()
            tvError.visibility = android.view.View.GONE

            if (nuevaPassword.length < 6) {
                tvError.text = "La contraseña debe tener al menos 6 caracteres"
                tvError.visibility = android.view.View.VISIBLE
                return@setOnClickListener
            }

            btnAccion.isEnabled = false
            btnAccion.text = "Guardando..."

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.resetPassword(
                        ResetPasswordRequestDto(email = email, codigo = codigo, nuevaPassword = nuevaPassword)
                    )
                    if (response.isSuccessful) {
                        dialog.dismiss()
                        Toast.makeText(
                            this@LoginActivity,
                            "¡Contraseña actualizada! Ya puedes iniciar sesión con tu nueva contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val mensaje = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                            ?: "El código es inválido o ha expirado."
                        tvError.text = mensaje
                        tvError.visibility = android.view.View.VISIBLE
                        btnAccion.isEnabled = true
                        btnAccion.text = "Guardar cambios"
                    }
                } catch (e: Exception) {
                    tvError.text = "No se pudo conectar al servidor. Revisa tu conexión."
                    tvError.visibility = android.view.View.VISIBLE
                    btnAccion.isEnabled = true
                    btnAccion.text = "Guardar cambios"
                }
            }
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
        if (passwordVisible) {
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            btnTogglePassword?.text = "🙈"
        } else {
            etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            btnTogglePassword?.text = "👁️"
        }
        etPassword.setSelection(etPassword.text.length)
    }

    private fun onLoginClicked() {
        val correo = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        FormValidator.marcarErrorSimple(etEmail, tvEmailError, null)
        FormValidator.marcarErrorSimple(etPassword, tvPasswordError, null)

        val errorCorreo = if (correo.isEmpty()) {
            "El correo electrónico es obligatorio."
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            "Ingresa un correo con formato válido (ej: nombre@correo.com)."
        } else null

        val errorPassword = if (password.isEmpty()) "La contraseña es obligatoria." else null

        var valido = true
        if (errorCorreo != null) {
            FormValidator.marcarErrorSimple(etEmail, tvEmailError, errorCorreo)
            valido = false
        }
        if (errorPassword != null) {
            FormValidator.marcarErrorSimple(etPassword, tvPasswordError, errorPassword)
            valido = false
        }
        if (!valido) {
            if (errorCorreo != null) etEmail.requestFocus() else etPassword.requestFocus()
            return
        }

        realizarLogin(correo, password)
    }

    private fun realizarLogin(correo: String, password: String) {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.login(
                    LoginRequestDto(email = correo, password = password)
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {

                        // Guardar datos usando los métodos de SessionManager
                        sessionManager.saveToken(body.token, body.tokenType)
                        sessionManager.saveUserSession(body)

                        val estadoUsuario = body.rol ?: ""
                        if (estadoUsuario.equals("PENDIENTE", ignoreCase = true) || estadoUsuario.equals("PENDING", ignoreCase = true)) {
                            sessionManager.logout()
                            mostrarDialogoSolicitudPendiente()
                            return@launch
                        }

                        try {
                            val authHeader = sessionManager.getAuthHeader()
                            if (authHeader != null) {
                                val estadoRes = RetrofitClient.apiService.verificarEstado(authHeader)

                                if (estadoRes.isSuccessful) {
                                    val jsonResponse = estadoRes.body()?.string() ?: ""
                                    if (jsonResponse.contains("\"tienePendiente\":true") || jsonResponse.contains("\"tienePendiente\": true")) {
                                        sessionManager.logout()
                                        mostrarDialogoSolicitudPendiente()
                                        return@launch
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignorar error secundario de verificación
                        }

                        val negocioId = body.selectedBusinessId ?: body.negocioId

                        val usuarioInfo = mapOf(
                            "email" to body.email,
                            "nombre" to body.nombreCompleto,
                            "primerNombre" to body.primerNombre,
                            "apellidoPaterno" to body.apellidoPaterno,
                            "rol" to body.rol,
                            "roles" to body.roles,
                            "superAdmin" to body.superAdmin,
                            "negocioId" to negocioId,
                            "businesses" to body.businesses,
                            "needsBusinessSelection" to body.needsBusinessSelection,
                            "needsRoleSelection" to body.needsRoleSelection,
                            "fotoPerfil" to body.fotoPerfil
                        )

                        sessionManager.saveUser(usuarioInfo)

                        Toast.makeText(this@LoginActivity, "¡Hola de nuevo! Iniciando sesión...", Toast.LENGTH_SHORT).show()

                        // Identificación de Administrador igual a Angular Web
                        val rol = body.rol?.uppercase()?.trim() ?: ""
                        val isSuperAdmin = body.superAdmin == true ||
                                rol == "SUPER_ADMIN" ||
                                rol == "ADMIN" ||
                                body.roles?.any { it.uppercase().contains("ADMIN") } == true ||
                                sessionManager.isAdmin()

                        val tieneNegocio = negocioId != null && negocioId != -1L

                        // Prioridad calcada de la web: Admin va primero e ignora verificación de negocio
                        val intent = when {
                            isSuperAdmin -> {
                                Intent(this@LoginActivity, AdminActivity::class.java)
                            }
                            !tieneNegocio -> {
                                Intent(this@LoginActivity, SelectRoleActivity::class.java)
                            }
                            body.needsRoleSelection == true -> {
                                Intent(this@LoginActivity, SelectRoleActivity::class.java)
                            }
                            else -> {
                                obtenerIntentPorRol(rol)
                            }
                        }

                        startActivity(intent)
                        finish()

                    } else {
                        Toast.makeText(this@LoginActivity, "Respuesta vacía del servidor", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val mensaje = when (response.code()) {
                        401, 403 -> "Tu correo o contraseña son incorrectos. Por favor, intenta de nuevo."
                        else -> "Error del servidor (código ${response.code()})"
                    }
                    Toast.makeText(this@LoginActivity, mensaje, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(
                    this@LoginActivity,
                    "No se pudo conectar al servidor. Revisa tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Ocurrió un error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun mostrarDialogoSolicitudPendiente() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_solicitud_pendiente, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendido)
        btnEntendido.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = if (loading) "Ingresando..." else "Ingresar"
    }

    private fun redirigirSegunRolGuardado() {
        val intent = if (sessionManager.isAdmin()) {
            Intent(this, AdminActivity::class.java)
        } else if (!sessionManager.hasNegocio()) {
            Intent(this, SelectRoleActivity::class.java)
        } else {
            obtenerIntentPorRol(sessionManager.getUserRole() ?: "")
        }
        startActivity(intent)
        finish()
    }

    private fun obtenerIntentPorRol(rol: String): Intent {
        val rolLimpio = rol.uppercase().trim()
        return when {
            rolLimpio.contains("ADMIN") -> Intent(this, AdminActivity::class.java)
            rolLimpio.contains("PROPIETARIO") -> Intent(this, PropietarioActivity::class.java)
            rolLimpio.contains("VENDEDOR") -> Intent(this, VendedorActivity::class.java)
            rolLimpio.contains("BODEGUERO") -> Intent(this, BodegueroActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
    }
}