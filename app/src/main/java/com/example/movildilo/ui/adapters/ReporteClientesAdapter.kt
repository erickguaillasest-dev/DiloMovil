package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.ClienteReporteDto
import com.google.android.material.button.MaterialButton
import java.lang.String.format
import java.util.Locale

class ReporteClientesAdapter(
    private var lista: List<ClienteReporteDto>,
    private val onVerDetalle: (ClienteReporteDto) -> Unit
) : RecyclerView.Adapter<ReporteClientesAdapter.ViewHolder>() {

    fun actualizarLista(nuevaLista: List<ClienteReporteDto>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInicial: TextView = view.findViewById(R.id.tvInicial)
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvRuc: TextView = view.findViewById(R.id.tvRuc)
        val tvBadgeDebe: TextView = view.findViewById(R.id.tvBadgeDebe)
        val tvFacturasCount: TextView = view.findViewById(R.id.tvFacturasCount)
        val tvTotalFacturado: TextView = view.findViewById(R.id.tvTotalFacturado)
        val tvCuentasCredito: TextView = view.findViewById(R.id.tvCuentasCredito)
        val tvSaldoPendiente: TextView = view.findViewById(R.id.tvSaldoPendiente)
        val btnVerDetalle: MaterialButton = view.findViewById(R.id.btnVerDetalle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reporte_cliente, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cliente = lista[position]

        holder.tvNombre.text = cliente.nombre
        holder.tvInicial.text = if (cliente.nombre.isNotEmpty()) cliente.nombre.substring(0, 1).uppercase() else "C"

        holder.tvRuc.text = if (!cliente.identificacion.isNullOrEmpty()) "CI/RUC: ${cliente.identificacion}" else "Sin identificación"

        holder.tvFacturasCount.text = cliente.numFacturas.toString()
        holder.tvTotalFacturado.text = format(Locale.US, "$%,.2f", cliente.totalFacturado)
        holder.tvCuentasCredito.text = cliente.numCuentasCredito.toString()

        if (cliente.saldoPendiente > 0) {
            holder.tvSaldoPendiente.text = format(Locale.US, "$%,.2f", cliente.saldoPendiente)
            holder.tvBadgeDebe.visibility = View.VISIBLE
        } else {
            holder.tvSaldoPendiente.text = "$0.00"
            holder.tvSaldoPendiente.setTextColor(Color.parseColor("#64748B"))
            holder.tvBadgeDebe.visibility = View.GONE
        }

        holder.btnVerDetalle.setOnClickListener { onVerDetalle(cliente) }
    }

    override fun getItemCount(): Int = lista.size
}