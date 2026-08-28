package com.example.movildilo.ui.auth

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.negocio.UnirseNegocioRequestDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.IOException

class UnirseNegocioDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private val sessionManager = SessionManager(context)

    fun show() {
        // 1. Inflar la vista del XML
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ingresar_codigo, null)

        // 2. Mapear los IDs del XML
        val etBusinessCode = dialogView.findViewById<EditText>(R.id.etBusinessCode)
        val btnIngresar = dialogView.findViewById<MaterialButton>(R.id.btnIngresar)
        val btnVolverOpciones = dialogView.findViewById<TextView>(R.id.btnVolverOpciones)

        // 3. Crear el AlertDialog
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        // Fondo transparente para preservar las esquinas redondeadas del MaterialCardView
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Botón: Unirme al Negocio
        btnIngresar.setOnClickListener {
            val codigo = etBusinessCode.text.toString().trim()

            // Validaciones
            val errorCodigo = FormValidator.requerido(codigo, "El código de invitación")
                ?: FormValidator.longitudMinima(codigo, 6, "El código de invitación")
                ?: FormValidator.longitudMaxima(codigo, 30, "El código de invitación")
            if (errorCodigo != null) {
                FormValidator.marcarErrorEditText(etBusinessCode, errorCodigo)
                etBusinessCode.requestFocus()
                return@setOnClickListener
            }

            // Deshabilitar botón durante la petición
            btnIngresar.isEnabled = false
            btnIngresar.text = "Enviando..."

            // Ejecutar la petición al servidor
            enviarSolicitud(codigo, dialog, btnIngresar)
        }

        // Botón: Volver a opciones
        btnVolverOpciones.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Ajustar dimensiones del diálogo en pantalla
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun enviarSolicitud(
        codigo: String,
        dialog: AlertDialog,
        btnIngresar: MaterialButton
    ) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                // Instancia del DTO con idRol = 3 (Vendedor/Equipo) por defecto
                val request = UnirseNegocioRequestDto(
                    codigoInvitacion = codigo,
                    idRol = 3
                )

                val token = sessionManager.getToken() ?: ""
                val authHeader = "Bearer $token"

                // Petición a api/v1/negocios/unirse
                val response = RetrofitClient.apiService.unirseANegocio(authHeader, request)

                if (response.isSuccessful) {
                    dialog.dismiss()

                    // Limpiar negocio local
                    sessionManager.removeNegocioId()

                    // Diálogo informativo
                    MaterialAlertDialogBuilder(context)
                        .setTitle("¡Solicitud enviada!")
                        .setMessage("Te has registrado correctamente. Debes esperar a que el administrador apruebe tu invitación para ingresar al sistema.")
                        .setCancelable(false)
                        .setPositiveButton("Entendido") { _, _ ->
                            cerrarSesionEIrAlLogin()
                        }
                        .show()

                } else {
                    btnIngresar.isEnabled = true
                    btnIngresar.text = "Unirme al Negocio"

                    val mensaje = when (response.code()) {
                        404 -> "El código de invitación no existe o ha expirado."
                        400 -> "Ya tienes una solicitud pendiente o ya perteneces a este negocio."
                        else -> "No se pudo procesar la solicitud (${response.code()})"
                    }

                    MaterialAlertDialogBuilder(context)
                        .setTitle("Error al unirse")
                        .setMessage(mensaje)
                        .setPositiveButton("Aceptar", null)
                        .show()
                }

            } catch (e: IOException) {
                btnIngresar.isEnabled = true
                btnIngresar.text = "Unirme al Negocio"
                Toast.makeText(context, "Error de red. Revisa tu conexión.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                btnIngresar.isEnabled = true
                btnIngresar.text = "Unirme al Negocio"
                Toast.makeText(context, "Error inesperado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cerrarSesionEIrAlLogin() {
        sessionManager.logout()

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}