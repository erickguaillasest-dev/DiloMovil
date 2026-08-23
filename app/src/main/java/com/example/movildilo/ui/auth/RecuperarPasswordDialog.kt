package com.example.movildilo.ui.auth

import android.content.Context
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.model.dto.ForgotPasswordRequestDto
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class RecuperarPasswordDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onCodigoEnviado: (email: String) -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
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
            tvError.visibility = View.GONE

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvError.text = "Debes ingresar un correo válido"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnAccion.isEnabled = false
            btnAccion.text = "Enviando..."

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.apiService.forgotPassword(ForgotPasswordRequestDto(email))
                    if (response.isSuccessful) {
                        dialog.dismiss()
                        onCodigoEnviado(email)
                    } else {
                        val mensaje = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                            ?: "No pudimos encontrar ese correo."
                        tvError.text = mensaje
                        tvError.visibility = View.VISIBLE
                        btnAccion.isEnabled = true
                        btnAccion.text = "Enviar código"
                    }
                } catch (e: Exception) {
                    tvError.text = "No se pudo conectar al servidor. Revisa tu conexión."
                    tvError.visibility = View.VISIBLE
                    btnAccion.isEnabled = true
                    btnAccion.text = "Enviar código"
                }
            }
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}