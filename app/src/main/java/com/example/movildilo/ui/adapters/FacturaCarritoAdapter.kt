package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ItemCarritoFactura
import java.util.Locale



class FacturaCarritoAdapter(
    private var lista: MutableList<ItemCarritoFactura>,
    private val onQuitar: (Int) -> Unit
) : RecyclerView.Adapter<FacturaCarritoAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvFacturaTempNombre)
        val tvCantidad: TextView = itemView.findViewById(R.id.tvFacturaTempCantidad)
        val tvDescuento: TextView = itemView.findViewById(R.id.tvFacturaTempDescuento)
        val tvSubtotal: TextView = itemView.findViewById(R.id.tvFacturaTempSubtotal)
        val btnQuitar: ImageView = itemView.findViewById(R.id.btnQuitarFacturaTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detalle_factura_temp, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = lista[position]
        val subtotal = item.subtotalConDescuento

        holder.tvNombre.text = item.nombreProducto
        holder.tvCantidad.text = "${item.cantidad} un. x ${String.format(Locale.US, "$%.2f", item.precioUnitario)}"
        holder.tvSubtotal.text = String.format(Locale.US, "$%.2f", subtotal)

        if (item.descuentoPorcentaje > 0) {
            holder.tvDescuento.visibility = View.VISIBLE
            holder.tvDescuento.text = "Descuento ${String.format(Locale.US, "%.0f", item.descuentoPorcentaje)}%: -${String.format(Locale.US, "$%.2f", item.descuentoMonto)}"
        } else {
            holder.tvDescuento.visibility = View.GONE
        }

        // Se usa holder.adapterPosition para máxima compatibilidad
        holder.btnQuitar.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onQuitar(currentPos)
            }
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizar(nuevaLista: MutableList<ItemCarritoFactura>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    fun eliminarItem(posicion: Int) {
        if (posicion in 0 until lista.size) {
            lista.removeAt(posicion)
            notifyItemRemoved(posicion)
            notifyItemRangeChanged(posicion, lista.size)
        }
    }

    fun total(): Double = lista.sumOf { it.subtotalConDescuento }
}