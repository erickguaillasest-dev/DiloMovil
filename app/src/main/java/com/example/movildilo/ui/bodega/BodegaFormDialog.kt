package com.example.movildilo.ui.bodega

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.movildilo.R
import com.example.movildilo.data.api.ApiService
import com.example.movildilo.data.model.dto.inventario.BodegaDto
import com.example.movildilo.data.model.dto.inventario.BodegaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BodegaFormDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val apiService: ApiService,
    private val token: String,
    private val negocioId: Long,
    private val bodegaExistente: BodegaDto?,
    private val onGuardado: () -> Unit
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bodega, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etNombre = dialogView.findViewById<EditText>(R.id.etDialogNombre)
        val etDireccion = dialogView.findViewById<EditText>(R.id.etDialogDireccion)
        val btnConfirmar = dialogView.findViewById<Button>(R.id.btnDialogConfirmar)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnDialogCancelar)

        if (bodegaExistente != null) {
            tvTitle.text = "Editar Bodega"
            btnConfirmar.text = "Guardar Cambios"
            etNombre.setText(bodegaExistente.nombre)
            etDireccion.setText(bodegaExistente.direccion ?: "")
        } else {
            tvTitle.text = "Nueva Bodega"
            btnConfirmar.text = "Crear Bodega"
        }

        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnConfirmar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = if (bodegaExistente != null) "El nombre no puede estar vacío" else "El nombre es obligatorio"
                return@setOnClickListener
            }

            val request = BodegaRequest(nombre, if (direccion.isEmpty()) null else direccion)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = if (bodegaExistente != null) {
                        apiService.editarBodega(token, negocioId, bodegaExistente.id, request)
                    } else {
                        apiService.crearBodega(token, negocioId, request)
                    }
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(
                                context,
                                if (bodegaExistente != null) "¡Actualizada!" else "¡Bodega Creada!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onGuardado()
                            dialog.dismiss()
                        } else {
                            val accion = if (bodegaExistente != null) "actualizar" else "crear"
                            Toast.makeText(context, "Error al $accion la bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val accion = if (bodegaExistente != null) "actualizar" else "crear"
                        Toast.makeText(context, "Error de red al $accion", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}