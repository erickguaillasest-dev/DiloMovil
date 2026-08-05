package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.CompraResponseDto
import java.util.Locale

class CompraAdapter(
    private var lista: List<CompraResponseDto>,
    private val onClick: (CompraResponseDto) -> Unit
) : RecyclerView.Adapter<CompraAdapter.CompraViewHolder>() {

    class CompraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumeroComprobante: TextView = itemView.findViewById(R.id.tvNumeroComprobante)
        val tvProveedorNombre: TextView = itemView.findViewById(R.id.tvProveedorNombre)
        val tvFechaCompra: TextView = itemView.findViewById(R.id.tvFechaCompra)
        val tvTotalCompra: TextView = itemView.findViewById(R.id.tvTotalCompra)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompraViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_compra, parent, false)
        return CompraViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompraViewHolder, position: Int) {
        val compra = lista[position]
        holder.tvNumeroComprobante.text = "Comprobante #${compra.numeroComprobante ?: "S/N"}"
        holder.tvProveedorNombre.text = "${compra.proveedorNombre ?: "Proveedor"} → ${compra.bodegaIngresoNombre ?: "Bodega"}"
        holder.tvFechaCompra.text = compra.fechaCompra?.take(10) ?: ""
        holder.tvTotalCompra.text = String.format(Locale.US, "$%.2f", compra.totalCompra ?: 0.0)
        holder.itemView.setOnClickListener { onClick(compra) }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<CompraResponseDto>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}