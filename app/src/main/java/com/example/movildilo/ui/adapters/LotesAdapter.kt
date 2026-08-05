package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.LoteResponseDto

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
        holder.tvCodigoLote.text = "Lote: ${lote.codigoLote ?: "S/N"}"
        holder.tvVencimiento.text = "Vence: ${lote.fechaVencimiento ?: "N/A"}"
        holder.tvCantidad.text = "${lote.cantidad ?: 0} uds."
    }

    override fun getItemCount(): Int = lotes.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCodigoLote: TextView = itemView.findViewById(R.id.tvCodigoLote)
        val tvVencimiento: TextView = itemView.findViewById(R.id.tvFechaVencimiento)
        val tvCantidad: TextView = itemView.findViewById(R.id.tvCantidadLote)
    }
}