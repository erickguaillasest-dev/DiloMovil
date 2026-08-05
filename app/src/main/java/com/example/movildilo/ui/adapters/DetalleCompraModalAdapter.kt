package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.DetalleCompraResponseDto
import java.lang.String.format
import java.util.Locale

class DetalleCompraModalAdapter(
    private val lista: List<DetalleCompraResponseDto>
) : RecyclerView.Adapter<DetalleCompraModalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvItemNombre)
        val tvCantCosto: TextView = view.findViewById(R.id.tvItemCantidadCosto)
        val tvSubtotal: TextView = view.findViewById(R.id.tvItemSubtotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detalle_compra_modal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val cant = item.cantidad ?: 0
        val costoUnitario = item.costoUnitario ?: 0.0
        val subtotal = item.costoTotal ?: (cant * costoUnitario)

        holder.tvNombre.text = item.productoNombre ?: "Producto sin nombre"
        holder.tvCantCosto.text = format(Locale.US, "%d uds. x $%.2f", cant, costoUnitario)
        holder.tvSubtotal.text = format(Locale.US, "$%.2f", subtotal)
    }

    override fun getItemCount(): Int = lista.size
}