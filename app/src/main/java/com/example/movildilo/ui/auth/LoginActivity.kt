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
import com.example.movildilo.data.model.dto.LoginRequestDto
import com.example.movildilo.ui.admin.AdminActivity
import com.example.movildilo.ui.dashboard.BodegueroActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.example.movildilo.ui.dashboard.VendedorActivity
import com.example.movildilo.ui.main.MainActivity
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

        btnTogglePassword?.setOnClickListener { togglePasswordVisibility() }
        btnLogin.setOnClickListener { onLoginClicked() }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Contacta al administrador para restablecer tu contraseña", Toast.LENGTH_LONG).show()
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

        if (correo.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa tu correo electrónico y tu contraseña.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(this, "Ingresa un correo válido", Toast.LENGTH_SHORT).show()
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