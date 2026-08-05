package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class FacturasAdapter(
    private var listaFacturas: List<FacturaResponseDto>,
    private val onPdfClick: (FacturaResponseDto) -> Unit
) : RecyclerView.Adapter<FacturasAdapter.FacturaViewHolder>() {

    class FacturaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumero: TextView = view.findViewById(R.id.tvNumeroFactura)
        val tvCliente: TextView = view.findViewById(R.id.tvClienteNombre)
        val tvFechaEmision: TextView = view.findViewById(R.id.tvFechaEmision)
        val tvMetodoPago: TextView = view.findViewById(R.id.tvMetodoPago)
        val tvTotal: TextView = view.findViewById(R.id.tvTotalFactura)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val cardBadgeEstado: MaterialCardView = view.findViewById(R.id.cardBadgeEstado)
        val btnContainerPdf: MaterialCardView = view.findViewById(R.id.btnContainerPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacturaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_factura, parent, false)
        return FacturaViewHolder(view)
    }

    override fun onBindViewHolder(holder: FacturaViewHolder, position: Int) {
        val fac = listaFacturas[position]
        
        val numero = fac.numeroFactura ?: "S/N"
        val cliente = fac.nombreClienteFormateado
        val fecha = fac.fechaFormateada
        val metodoPago = fac.metodoPago ?: "EFECTIVO"
        val total = fac.totalCalculado
        val estado = fac.estadoFormateado

        holder.tvNumero.text = numero
        holder.tvCliente.text = cliente
        holder.tvFechaEmision.text = fecha
        holder.tvMetodoPago.text = metodoPago.uppercase(Locale.ROOT)
        holder.tvTotal.text = String.format(Locale.US, "$%.2f", total)
        holder.tvEstado.text = estado.uppercase(Locale.ROOT)

        configurarBadgeEstado(holder.cardBadgeEstado, holder.tvEstado, estado)

        holder.btnContainerPdf.setOnClickListener { onPdfClick(fac) }
    }

    private fun configurarBadgeEstado(cardBadge: MaterialCardView, tvEstado: TextView, estado: String) {
        when (estado.uppercase(Locale.ROOT)) {
            "PENDIENTE" -> {
                cardBadge.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
                cardBadge.strokeColor = Color.parseColor("#FDE68A")
                cardBadge.strokeWidth = 1
                tvEstado.setTextColor(Color.parseColor("#B45309"))
            }
            "ANULADA", "ERROR_SRI", "DEVUELTA", "RECHAZADA" -> {
                cardBadge.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                cardBadge.strokeColor = Color.parseColor("#FECACA")
                cardBadge.strokeWidth = 1
                tvEstado.setTextColor(Color.parseColor("#B91C1C"))
            }
            else -> {
                cardBadge.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
                cardBadge.strokeColor = Color.parseColor("#BBF7D0")
                cardBadge.strokeWidth = 1
                tvEstado.setTextColor(Color.parseColor("#166534"))
            }
        }
    }

    override fun getItemCount(): Int = listaFacturas.size

    fun actualizarLista(nuevaLista: List<FacturaResponseDto>) {
        listaFacturas = nuevaLista
        notifyDataSetChanged()
    }
}