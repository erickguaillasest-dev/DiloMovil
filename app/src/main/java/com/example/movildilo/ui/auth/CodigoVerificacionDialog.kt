package com.example.movildilo.ui.auth

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton

class CodigoVerificacionDialog(
    private val context: Context,
    private val email: String,
    private val onCodigoVerificado: (email: String, codigo: String) -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_recuperar_password, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
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
        etInput.filters = arrayOf(InputFilter.LengthFilter(6))
        btnAccion.text = "Verificar código"

        btnAccion.setOnClickListener {
            val codigo = etInput.text.toString().trim()
            tvError.visibility = View.GONE

            if (codigo.length < 6) {
                tvError.text = "El código debe tener 6 dígitos"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            dialog.dismiss()
            onCodigoVerificado(email, codigo)
        }

        tvCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}