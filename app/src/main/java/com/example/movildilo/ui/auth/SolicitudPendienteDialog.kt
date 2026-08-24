package com.example.movildilo.ui.auth

import android.app.Activity
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
    private var dialog: AlertDialog? = null

    fun show() {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }

        activity.runOnUiThread {
            try {
                // Limpiamos sesión y marcamos la bandera de pendiente
                sessionManager.clearSession()
                sessionManager.setSolicitudPendiente(true)

                val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_solicitud_pendiente, null)
                val btnEntendido = dialogView.findViewById<MaterialButton>(R.id.btnEntendido)

                dialog = AlertDialog.Builder(activity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create()

                dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

                btnEntendido?.setOnClickListener {
                    dismissDialog()
                    // Mantenemos la bandera en true para que el Login sepa que debe mostrarse limpio
                    redirigirLogin(activity)
                }

                if (!activity.isFinishing) {
                    dialog?.show()
                }

                dialog?.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            } catch (e: Exception) {
                // Prevenir excepciones de UI
            }
        }
    }

    private fun dismissDialog() {
        try {
            if (dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        } catch (e: Exception) {
        } finally {
            dialog = null
        }
    }

    private fun redirigirLogin(ctx: Context) {
        val intent = Intent(ctx, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        ctx.startActivity(intent)
    }
}