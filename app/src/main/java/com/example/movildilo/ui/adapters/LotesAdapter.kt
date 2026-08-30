package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.inventario.LoteResponseDto
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class LotesAdapter(
    private val lotes: List<LoteResponseDto>
) : RecyclerView.Adapter<LotesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lote_producto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lote = lotes[position]

        holder.tvCodigoLote.text = lote.codigoLote?.takeIf { it.isNotBlank() } ?: "S/N"
        holder.tvIdSistema.text = "ID Sistema: ${lote.id ?: 0}"

        holder.tvFechaCreacion.text = "Creado: ${formatearFecha(lote.fechaCreacion)}"
        holder.tvCantidadInicial.text = "Inicial: ${lote.cantidadInicial ?: 0}"
        holder.tvCantidadDisponible.text = "Disponible: ${lote.cantidadDisponible ?: 0}"

        // Costo Unitario
        val costo = lote.costoUnitario ?: 0.0
        holder.tvCostoUnitario.text = String.format(Locale.US, "$%.2f", costo)

        // Vencimiento
        holder.tvVencimiento.text = "Vence: ${formatearFecha(lote.fechaCaducidad, soloFecha = true)}"

        // Estado y color del Badge
        val estado = lote.estado?.uppercase() ?: "VIGENTE"
        holder.tvEstado.text = estado

        if (estado.contains("CADUCADO") || estado.contains("INACTIVO")) {
            holder.cardBadgeEstado.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            holder.cardBadgeEstado.strokeColor = Color.parseColor("#FECACA")
            holder.tvEstado.setTextColor(Color.parseColor("#991B1B"))
        } else {
            holder.cardBadgeEstado.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
            holder.cardBadgeEstado.strokeColor = Color.parseColor("#BBF7D0")
            holder.tvEstado.setTextColor(Color.parseColor("#166534"))
        }
    }

    override fun getItemCount(): Int = lotes.size

    /**
     * El backend suele mandar fechas en ISO-8601 ("2026-08-01T00:00:00" o con offset/milisegundos).
     * Las probamos con los formatos más comunes; si ninguno matchea, mostramos el texto crudo
     * en vez de "N/A" para no perder el dato aunque no se formatee bonito.
     */
    private fun formatearFecha(fechaCruda: String?, soloFecha: Boolean = false): String {
        if (fechaCruda.isNullOrBlank()) return "N/A"

        val formatosEntrada = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        val formatoSalida = if (soloFecha) "dd/MM/yyyy" else "dd/MM/yyyy HH:mm"

        for (patron in formatosEntrada) {
            try {
                val parser = SimpleDateFormat(patron, Locale.US)
                val fecha = parser.parse(fechaCruda) ?: continue
                return SimpleDateFormat(formatoSalida, Locale.US).format(fecha)
            } catch (_: Exception) {
                // probamos el siguiente formato
            }
        }
        // Ningún formato conocido matcheó: devolvemos el dato crudo en vez de ocultarlo.
        return fechaCruda
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCodigoLote: TextView = itemView.findViewById(R.id.tvCodigoLote)
        val tvIdSistema: TextView = itemView.findViewById(R.id.tvIdSistemaLote)
        val tvFechaCreacion: TextView = itemView.findViewById(R.id.tvFechaCreacionLote)
        val tvCantidadInicial: TextView = itemView.findViewById(R.id.tvCantidadInicialLote)
        val tvCantidadDisponible: TextView = itemView.findViewById(R.id.tvCantidadDisponibleLote)
        val tvCostoUnitario: TextView = itemView.findViewById(R.id.tvCostoUnitarioLote)
        val tvVencimiento: TextView = itemView.findViewById(R.id.tvVencimientoLote)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoLote)
        val cardBadgeEstado: MaterialCardView = itemView.findViewById(R.id.cardBadgeEstadoLote)
    }
}