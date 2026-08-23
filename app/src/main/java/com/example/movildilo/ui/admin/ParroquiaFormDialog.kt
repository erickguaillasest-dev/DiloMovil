package com.example.movildilo.ui.admin

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ParroquiaResponseDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton

class ParroquiaFormDialog(
    private val context: Context,
    private val parroquia: ParroquiaResponseDto?,
    private val parroquiasList: List<ParroquiaResponseDto>,
    private val onGuardar: (id: Long?, nombre: String) -> Unit
) {

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_parroquia_form, null)
        val etNombre = view.findViewById<EditText>(R.id.etNombreParroquiaDialog)
        val tvTitulo = view.findViewById<TextView>(R.id.tvTituloDialog)

        val esEdicion = parroquia != null
        tvTitulo.text = if (esEdicion) "Editar Parroquia" else "Nueva Parroquia"
        if (esEdicion) {
            etNombre.setText(parroquia?.nombre)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<MaterialButton>(R.id.btnCancelarDialog).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnGuardarDialog).setOnClickListener {
            val nombreInput = etNombre.text.toString().trim()

            val errorNombre = FormValidator.requerido(nombreInput, "El nombre de la parroquia")
                ?: FormValidator.longitudMinima(nombreInput, 3, "El nombre de la parroquia")
                ?: FormValidator.longitudMaxima(nombreInput, 80, "El nombre de la parroquia")
                ?: run {
                    val yaExiste = parroquiasList.any {
                        FormValidator.normalizar(it.nombre) == FormValidator.normalizar(nombreInput) && it.id != parroquia?.id
                    }
                    if (yaExiste) "Ya existe una parroquia registrada con ese nombre." else null
                }

            if (errorNombre != null) {
                FormValidator.marcarErrorEditText(etNombre, errorNombre)
                etNombre.requestFocus()
                return@setOnClickListener
            }

            dialog.dismiss()
            onGuardar(parroquia?.id, nombreInput)
        }

        dialog.show()
    }
}