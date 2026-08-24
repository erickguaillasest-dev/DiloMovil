package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.DetalleFacturaResponseDto
import java.util.Locale

class ProductosFacturaAdapter(
    private val lista: List<DetalleFacturaResponseDto>
) : RecyclerView.Adapter<ProductosFacturaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreProductoItem)
        val tvCantidad: TextView = view.findViewById(R.id.tvCantidadProductoItem)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecioProductoItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_comprado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        val nombre = item.nombreProducto ?: item.producto?.nombre ?: "Producto"
        val cantidad = item.cantidad ?: 1
        val subtotal = item.subtotalItem ?: ((item.precioUnitario ?: 0.0) * cantidad)

        holder.tvNombre.text = nombre
        holder.tvCantidad.text = "Cant: $cantidad"
        holder.tvPrecio.text = String.format(Locale.US, "$%.2f", subtotal)
    }

    override fun getItemCount(): Int = lista.size
}