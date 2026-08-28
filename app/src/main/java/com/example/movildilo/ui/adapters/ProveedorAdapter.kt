package com.example.movildilo.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.ProveedorResponseDto
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ProveedorAdapter(
    private var listaProveedores: List<ProveedorResponseDto>,
    private val onEditarClick: (ProveedorResponseDto) -> Unit,
    private val onEliminarClick: (ProveedorResponseDto) -> Unit
) : RecyclerView.Adapter<ProveedorAdapter.ProveedorViewHolder>() {

    fun actualizarLista(nuevaLista: List<ProveedorResponseDto>) {
        this.listaProveedores = nuevaLista
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProveedorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_proveedor, parent, false)
        return ProveedorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProveedorViewHolder, position: Int) {
        holder.bind(listaProveedores[position])
    }

    override fun getItemCount(): Int = listaProveedores.size

    inner class ProveedorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre: TextView = itemView.findViewById(R.id.tvNombreProveedor)
        private val tvRuc: TextView = itemView.findViewById(R.id.tvRuc)
        private val tvContacto: TextView = itemView.findViewById(R.id.tvContacto)
        private val tvEstadoBadge: TextView = itemView.findViewById(R.id.tvEstadoBadge)
        private val tvFechaRegistro: TextView = itemView.findViewById(R.id.tvFechaRegistro)
        private val chipGroupCategorias: ChipGroup = itemView.findViewById(R.id.chipGroupCategorias)
        private val btnEditar: View = itemView.findViewById(R.id.btnEditar)
        private val btnEliminar: View = itemView.findViewById(R.id.btnEliminar)

        fun bind(proveedor: ProveedorResponseDto) {
            // Nombre comercial (alineado a la columna nombre_comercial de BD)
            val nombreDisplay = proveedor.nombreComercial?.takeIf { it.isNotBlank() } ?: "Proveedor sin nombre"
            tvNombre.text = nombreDisplay

            // RUC / DNI
            tvRuc.text = "RUC: ${proveedor.dni ?: "N/D"}"

            // Teléfono
            tvContacto.text = proveedor.telefono?.takeIf { it.isNotBlank() } ?: "Sin teléfono"

            // Fecha de registro
            tvFechaRegistro.text = "Registrado: ${formatearFecha(proveedor.fechaCreacion)}"

            // Estado Badge
            val esActivo = proveedor.estado == true
            if (esActivo) {
                tvEstadoBadge.text = "ACTIVO"
                tvEstadoBadge.setBackgroundResource(R.drawable.bg_badge_activo)
                tvEstadoBadge.setTextColor(Color.parseColor("#03543F"))
            } else {
                tvEstadoBadge.text = "INACTIVO"
                tvEstadoBadge.setBackgroundResource(R.drawable.bg_badge_rojo)
                tvEstadoBadge.setTextColor(Color.parseColor("#991B1B"))
            }

            // Categorías
            chipGroupCategorias.removeAllViews()
            proveedor.categorias?.forEach { cat ->
                val chip = Chip(itemView.context).apply {
                    text = cat.nombre
                    isClickable = false
                    isCheckable = false
                    chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                    setTextColor(Color.parseColor("#475569"))
                    textSize = 11f
                    chipMinHeight = 0f
                    chipCornerRadius = 12f
                }
                chipGroupCategorias.addView(chip)
            }

            btnEditar.setOnClickListener { onEditarClick(proveedor) }
            btnEliminar.setOnClickListener { onEliminarClick(proveedor) }
        }

        private fun formatearFecha(fechaRaw: String?): String {
            if (fechaRaw.isNullOrBlank()) return "Sin fecha"
            return try {
                // Remueve fracciones de segundos si vienen en la respuesta (ej. .123456)
                val fechaLimpia = fechaRaw.split(".").firstOrNull() ?: fechaRaw

                val formatoEntrada = if (fechaLimpia.contains("T")) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                }

                val date = formatoEntrada.parse(fechaLimpia)
                val formatoSalida = SimpleDateFormat("dd MMM, yyyy", Locale("es", "ES"))
                date?.let { formatoSalida.format(it) } ?: fechaRaw
            } catch (e: Exception) {
                fechaRaw.split("T").firstOrNull() ?: fechaRaw
            }
        }
    }
}