package com.example.movildilo.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.UnirseNegocioRequestDto
import com.example.movildilo.ui.dashboard.AdminActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.IOException

class SelectRoleActivity : AppCompatActivity() {

    private lateinit var cardSoyDueno: MaterialCardView
    private lateinit var cardSoyEquipo: MaterialCardView
    private lateinit var btnCerrarSesion: MaterialButton

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        if (sessionManager.isAdmin()) {
            val intent = Intent(this, AdminActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        ocultarBarrasSistema()
        setContentView(R.layout.activity_select_role)

        cardSoyDueno = findViewById(R.id.cardSoyDueno)
        cardSoyEquipo = findViewById(R.id.cardSoyEquipo)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        cardSoyDueno.setOnClickListener {
            startActivity(Intent(this, RegistroNegocioActivity::class.java))
        }

        cardSoyEquipo.setOnClickListener {
            mostrarModalIngresarCodigo()
        }

        btnCerrarSesion.setOnClickListener {
            cerrarSesionEIrAlLogin()
        }
    }

    private fun mostrarModalIngresarCodigo() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ingresar_codigo, null)

        val etCode = dialogView.findViewById<EditText>(R.id.etBusinessCode)
        val btnIngresar = dialogView.findViewById<MaterialButton>(R.id.btnIngresar)
        val btnVolver = dialogView.findViewById<TextView>(R.id.btnVolverOpciones)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnIngresar.setOnClickListener {
            val codigo = etCode.text.toString().trim()

            if (codigo.isEmpty() || codigo.length < 6) {
                etCode.error = "Ingresa un código de invitación válido (mínimo 6 caracteres)"
                etCode.requestFocus()
                return@setOnClickListener
            }

            dialog.dismiss()
            enviarCodigoServidor(codigo)
        }

        btnVolver.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun enviarCodigoServidor(codigo: String) {
        lifecycleScope.launch {
            try {
                val request = UnirseNegocioRequestDto(
                    codigoInvitacion = codigo,
                    idRol = 3
                )

                val token = sessionManager.getToken() ?: ""
                val authHeader = "Bearer $token"

                val response = RetrofitClient.apiService.unirseANegocio(authHeader, request)

                if (response.isSuccessful) {
                    sessionManager.removeNegocioId()

                    mostrarModalExitoYSalir()

                } else {
                    val errorBody = response.errorBody()?.string()
                    val mensaje = if (!errorBody.isNullOrBlank() && errorBody.contains("message")) {
                        errorBody
                    } else {
                        "Verifica el código de invitación e intenta nuevamente."
                    }

                    MaterialAlertDialogBuilder(this@SelectRoleActivity)
                        .setTitle("Error al unirse")
                        .setMessage(mensaje)
                        .setPositiveButton("Aceptar", null)
                        .show()
                }

            } catch (e: IOException) {
                Toast.makeText(this@SelectRoleActivity, "Error de conexión. Revisa tu internet.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@SelectRoleActivity, "Error inesperado: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarModalExitoYSalir() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_solicitud_pendiente, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendido)
        btnEntendido.setOnClickListener {
            dialog.dismiss()
            cerrarSesionEIrAlLogin()
        }

        dialog.show()
    }

    private fun cerrarSesionEIrAlLogin() {
        sessionManager.logout()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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