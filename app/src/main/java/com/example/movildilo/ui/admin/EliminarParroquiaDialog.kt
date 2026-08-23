package com.example.movildilo.ui.admin

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.data.model.dto.ParroquiaResponseDto

class EliminarParroquiaDialog(
    private val context: Context,
    private val parroquia: ParroquiaResponseDto,
    private val onConfirmar: (id: Long) -> Unit
) {

    fun show() {
        AlertDialog.Builder(context)
            .setTitle("¿Estás seguro?")
            .setMessage("Esta acción no se puede deshacer. Se eliminará la parroquia '${parroquia.nombre}'.")
            .setPositiveButton("Sí, eliminar") { _, _ -> onConfirmar(parroquia.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}