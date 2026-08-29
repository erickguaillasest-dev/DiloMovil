package com.example.movildilo.ui.bodega

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.movildilo.data.api.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EliminarBodegaDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val apiService: ApiService,
    private val token: String,
    private val negocioId: Long,
    private val bodegaId: Long,
    private val onEliminado: () -> Unit
) {

    fun show() {
        AlertDialog.Builder(context)
            .setTitle("¿Estás seguro?")
            .setMessage("¡Esta acción eliminará la bodega y no se puede deshacer!")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val response = apiService.eliminarBodega(token, negocioId, bodegaId)
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "¡Eliminada!", Toast.LENGTH_SHORT).show()
                                onEliminado()
                            } else {
                                Toast.makeText(context, "No se pudo eliminar la bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error de red al eliminar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}