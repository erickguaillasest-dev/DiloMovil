package com.example.movildilo.ui.auth

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton

class IngresarCodigoDialog(
    private val context: Context,
    private val onCodigoIngresado: (codigo: String) -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ingresar_codigo, null)

        val etCode = dialogView.findViewById<EditText>(R.id.etBusinessCode)
        val btnIngresar = dialogView.findViewById<MaterialButton>(R.id.btnIngresar)
        val btnVolver = dialogView.findViewById<TextView>(R.id.btnVolverOpciones)

        val dialog = AlertDialog.Builder(context)
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
            onCodigoIngresado(codigo)
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
}