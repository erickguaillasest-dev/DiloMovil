package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.DetalleCompraRequestDto
import java.util.Locale

class DetalleTempAdapter(
    private var lista: MutableList<Pair<DetalleCompraRequestDto, String>>, // detalle + nombre del producto
    private val onQuitar: (Int) -> Unit
) : RecyclerView.Adapter<DetalleTempAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInfo: TextView = itemView.findViewById(R.id.tvDetalleTempInfo)
        val tvSubtotal: TextView = itemView.findViewById(R.id.tvDetalleTempSubtotal)
        val btnQuitar: ImageView = itemView.findViewById(R.id.btnQuitarDetalleTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detalle_compra_temp, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (detalle, nombreProducto) = lista[position]
        holder.tvInfo.text = "$nombreProducto  x${detalle.cantidad}"
        val subtotal = detalle.cantidad * detalle.costoUnitario
        holder.tvSubtotal.text = String.format(Locale.US, "$%.2f", subtotal)
        holder.btnQuitar.setOnClickListener { onQuitar(holder.adapterPosition) }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizar(nuevaLista: MutableList<Pair<DetalleCompraRequestDto, String>>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}