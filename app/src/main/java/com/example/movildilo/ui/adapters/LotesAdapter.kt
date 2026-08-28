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

        holder.tvCodigoLote.text = lote.codigoLote ?: "S/N"
        holder.tvIdSistema.text = "ID Sistema: ${lote.id ?: 0}"

        holder.tvFechaCreacion.text = "—" // Si tienes el campo de creación en el DTO, cámbialo aquí
        holder.tvCantidadInicial.text = (lote.cantidadInicial ?: 0).toString()
        holder.tvCantidadDisponible.text = (lote.cantidadDisponible ?: 0).toString()

        // Costo Unitario
        val costo = lote.costoUnitario ?: 0.0
        holder.tvCostoUnitario.text = String.format(Locale.US, "$%.2f", costo)

        // Vencimiento
        holder.tvVencimiento.text = lote.fechaCaducidad ?: "N/A"

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