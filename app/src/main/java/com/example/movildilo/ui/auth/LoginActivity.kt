package com.example.movildilo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.auth.LoginRequestDto
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

    private lateinit var scrollViewLogin: ScrollView
    private lateinit var containerFormulario: LinearLayout

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        if (sessionManager.isSolicitudPendiente()) {
            sessionManager.clearSession()
            sessionManager.setSolicitudPendiente(false)
        } else if (sessionManager.isLoggedIn()) {
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

        scrollViewLogin = findViewById(R.id.main)
        containerFormulario = findViewById(R.id.containerFormulario)

        etEmail.setOnFocusChangeListener { _, tieneFoco -> if (tieneFoco) FormValidator.marcarErrorSimple(etEmail, tvEmailError, null) }
        etPassword.setOnFocusChangeListener { _, tieneFoco -> if (tieneFoco) FormValidator.marcarErrorSimple(etPassword, tvPasswordError, null) }

        btnTogglePassword?.setOnClickListener { togglePasswordVisibility() }
        btnLogin.setOnClickListener { onLoginClicked() }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            RecuperarPasswordDialog(this, lifecycleScope) { email ->
                CodigoVerificacionDialog(this, email) { emailVerificado, codigo ->
                    NuevaPasswordDialog(this, lifecycleScope, emailVerificado, codigo) {
                        Toast.makeText(
                            this,
                            "¡Contraseña actualizada! Ya puedes iniciar sesión con tu nueva contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    }.show()
                }.show()
            }.show()
        }

        // Ajuste dinámico del teclado virtual para evitar que tape los campos de texto
        scrollViewLogin.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            scrollViewLogin.getWindowVisibleDisplayFrame(r)
            val screenHeight = scrollViewLogin.rootView.height
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
    }

    private fun configurarEnfoqueYTeclado() {
        val campos = listOf(etEmail, etPassword)
        campos.forEach { campo ->
            campo.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollViewLogin.post {
                        var topCoord = 0
                        var currentView: android.view.View? = view
                        while (currentView != null && currentView != scrollViewLogin) {
                            topCoord += currentView.top
                            currentView = currentView.parent as? android.view.View
                        }
                        scrollViewLogin.smoothScrollTo(0, (topCoord - 100).coerceAtLeast(0))
                    }
                }
            }
        }
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
            val campoError = if (errorCorreo != null) etEmail else etPassword
            campoError.requestFocus()
            scrollViewLogin.post {
                var topCoord = 0
                var currentView: android.view.View? = campoError
                while (currentView != null && currentView != scrollViewLogin) {
                    topCoord += currentView.top
                    currentView = currentView.parent as? android.view.View
                }
                scrollViewLogin.smoothScrollTo(0, (topCoord - 100).coerceAtLeast(0))
            }
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

                        if (body.suspendido == true) {
                            setLoading(false)
                            sessionManager.clearSession()
                            CuentaSuspendidaDialog(this@LoginActivity).show()
                            return@launch
                        }

                        val rolAux = body.rol?.uppercase()?.trim() ?: ""
                        if (rolAux == "PENDIENTE" || rolAux == "PENDING") {
                            setLoading(false)
                            sessionManager.clearSession()
                            SolicitudPendienteDialog(this@LoginActivity).show()
                            return@launch
                        }

                        sessionManager.saveToken(body.token, body.tokenType)
                        sessionManager.saveUserSession(body)

                        try {
                            val authHeader = sessionManager.getAuthHeader()
                            if (authHeader != null) {
                                val estadoRes = RetrofitClient.apiService.verificarEstado(authHeader)

                                if (estadoRes.isSuccessful) {
                                    val jsonResponse = estadoRes.body()?.string() ?: ""

                                    val suspendidoServidor = try {
                                        if (jsonResponse.isNotBlank()) {
                                            org.json.JSONObject(jsonResponse).optBoolean("suspendido", false)
                                        } else {
                                            false
                                        }
                                    } catch (e: Exception) {
                                        false
                                    }

                                    if (suspendidoServidor) {
                                        setLoading(false)
                                        sessionManager.clearSession()
                                        CuentaSuspendidaDialog(this@LoginActivity).show()
                                        return@launch
                                    }

                                    val tienePendiente = try {
                                        if (jsonResponse.isNotBlank()) {
                                            org.json.JSONObject(jsonResponse).optBoolean("tienePendiente", false)
                                        } else {
                                            false
                                        }
                                    } catch (e: Exception) {
                                        false
                                    }

                                    if (tienePendiente) {
                                        setLoading(false)
                                        sessionManager.clearSession()
                                        SolicitudPendienteDialog(this@LoginActivity).show()
                                        return@launch
                                    }
                                }
                            }
                        } catch (e: Exception) {}

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

                        val rol = body.rol?.uppercase()?.trim() ?: ""
                        val isSuperAdmin = body.superAdmin == true ||
                                rol == "SUPER_ADMIN" ||
                                rol == "ADMIN" ||
                                body.roles?.any { it.uppercase().contains("ADMIN") } == true ||
                                sessionManager.isAdmin()

                        val tieneNegocio = negocioId != null && negocioId != -1L

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
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Respuesta vacía del servidor", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    setLoading(false)

                    val errorBodyStr = response.errorBody()?.string() ?: ""

                    if (errorBodyStr.contains("suspendido", ignoreCase = true) ||
                        errorBodyStr.contains("suspension", ignoreCase = true) ||
                        response.code() == 403 && errorBodyStr.contains("suspendido", ignoreCase = true)) {
                        sessionManager.clearSession()
                        CuentaSuspendidaDialog(this@LoginActivity).show()
                        return@launch
                    }

                    if (errorBodyStr.contains("pendiente", ignoreCase = true) || errorBodyStr.contains("pending", ignoreCase = true)) {
                        sessionManager.clearSession()
                        SolicitudPendienteDialog(this@LoginActivity).show()
                        return@launch
                    }

                    val mensaje = when (response.code()) {
                        401 -> "Tu correo o contraseña son incorrectos. Por favor, intenta de nuevo."
                        403 -> "Tu cuenta se encuentra suspendida o no autorizada."
                        else -> "Error del servidor (código ${response.code()})"
                    }

                    if (response.code() == 403) {
                        sessionManager.clearSession()
                        CuentaSuspendidaDialog(this@LoginActivity).show()
                    } else {
                        Toast.makeText(this@LoginActivity, mensaje, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    "No se pudo conectar al servidor. Revisa tu conexión.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@LoginActivity, "Ocurrió un error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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