package com.example.movildilo.ui.auth

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.R
import com.example.movildilo.data.local.SessionManager
import com.google.android.material.button.MaterialButton

class SolicitudPendienteDialog(
    private val context: Context
) {

    private val sessionManager = SessionManager(context)

    fun show() {
        // 1. Inflar el XML exacto del diálogo
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_solicitud_pendiente, null)

        // 2. Mapear el único ID interactivo definido en la vista
        val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendido)

        // 3. Crear el AlertDialog con fondo transparente (para mantener bordes redondeados del MaterialCardView)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false) // Forzar al usuario a tocar el botón "Entendido"
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 4. Configurar la acción del botón
        btnEntendido.setOnClickListener {
            dialog.dismiss()
            cerrarSesionYRedirigirLogin()
        }

        dialog.show()

        // 5. Ajustar ancho para respetar el margin del XML
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun cerrarSesionYRedirigirLogin() {
        // Limpiar preferencias de sesión
        sessionManager.logout()

        // Ir al Login y limpiar la pila de actividades
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}