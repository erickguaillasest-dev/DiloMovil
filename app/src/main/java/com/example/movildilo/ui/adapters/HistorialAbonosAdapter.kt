package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.CuotaDto
import java.util.Locale

class HistorialAbonosAdapter(
    private val lista: List<CuotaDto>,
    private val emailUsuario: String = ""
) : RecyclerView.Adapter<HistorialAbonosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloAbono)
        val tvSubtitulo: TextView = view.findViewById(R.id.tvSubtituloAbono)
        val tvMonto: TextView = view.findViewById(R.id.tvMontoAbono)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial_abono, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        holder.tvTitulo.text = "Abono en EFECTIVO"

        val fecha = item.fechaVencimiento ?: "N/A"
        val recibioTexto = if (emailUsuario.isNotEmpty()) " • Recibió: $emailUsuario" else ""
        holder.tvSubtitulo.text = "$fecha$recibioTexto"

        val monto = item.montoCuota ?: 0.0
        holder.tvMonto.text = String.format(Locale.US, "+$%.2f", monto)
    }

    override fun getItemCount() = lista.size
}