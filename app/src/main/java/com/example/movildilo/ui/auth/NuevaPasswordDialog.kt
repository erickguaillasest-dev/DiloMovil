package com.example.movildilo.ui.auth

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.model.dto.ResetPasswordRequestDto
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class NuevaPasswordDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val email: String,
    private val codigo: String,
    private val onPasswordActualizada: () -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
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
            tvError.visibility = View.GONE

            if (nuevaPassword.length < 6) {
                tvError.text = "La contraseña debe tener al menos 6 caracteres"
                tvError.visibility = View.VISIBLE
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
                        onPasswordActualizada()
                    } else {
                        val mensaje = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                            ?: "El código es inválido o ha expirado."
                        tvError.text = mensaje
                        tvError.visibility = View.VISIBLE
                        btnAccion.isEnabled = true
                        btnAccion.text = "Guardar cambios"
                    }
                } catch (e: Exception) {
                    tvError.text = "No se pudo conectar al servidor. Revisa tu conexión."
                    tvError.visibility = View.VISIBLE
                    btnAccion.isEnabled = true
                    btnAccion.text = "Guardar cambios"
                }
            }
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}