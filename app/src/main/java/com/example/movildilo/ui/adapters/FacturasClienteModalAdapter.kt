package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.CreditoClienteResumenDto

class FacturasClienteModalAdapter(
    private val lista: List<CreditoClienteResumenDto>,
    private val onAbonarClick: (CreditoClienteResumenDto) -> Unit
) : RecyclerView.Adapter<FacturasClienteModalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumFactura: TextView = view.findViewById(R.id.tvNumFacturaModalItem)
        val tvVencimiento: TextView = view.findViewById(R.id.tvFechaVencimientoItem)
        val tvEstado: TextView = view.findViewById(R.id.tvBadgeEstadoModal)
        val tvSaldo: TextView = view.findViewById(R.id.tvSaldoModalItem)
        val btnAccion: Button = view.findViewById(R.id.btnAccionFacturaModal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_factura_cliente_modal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val saldo = item.saldoPendiente
        val esPendiente = item.estado.equals("PENDIENTE", ignoreCase = true) || saldo > 0

        holder.tvNumFactura.text = item.factura.ifEmpty { "N/A" }
        holder.tvVencimiento.text = item.fechaVencimiento.ifEmpty { "Sin fecha" }
        holder.tvSaldo.text = String.format("$%.2f", saldo)

        if (esPendiente) {
            holder.tvEstado.text = "PENDIENTE"
            holder.tvEstado.setBackgroundColor(Color.parseColor("#FEF3C7"))
            holder.tvEstado.setTextColor(Color.parseColor("#D97706"))
            holder.tvSaldo.setTextColor(Color.parseColor("#F97316"))
            holder.btnAccion.text = "Abonar"
            holder.btnAccion.setOnClickListener { onAbonarClick(item) }
        } else {
            holder.tvEstado.text = "PAGADA"
            holder.tvEstado.setBackgroundColor(Color.parseColor("#D1FAE5"))
            holder.tvEstado.setTextColor(Color.parseColor("#059669"))
            holder.tvSaldo.setTextColor(Color.parseColor("#10B981"))
            holder.btnAccion.text = "Historial"
            holder.btnAccion.setOnClickListener { onAbonarClick(item) }
        }
    }

    override fun getItemCount() = lista.size
}