package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.google.android.material.button.MaterialButton
import java.util.Locale

class InventarioAdapter(
    private var listaInventario: MutableList<InventarioResponseDto>,
    private val onEditarStockMinClick: (InventarioResponseDto) -> Unit,
    private val onVerLotesClick: (InventarioResponseDto) -> Unit
) : RecyclerView.Adapter<InventarioAdapter.ViewHolder>() {

    fun actualizarLista(nuevaLista: List<InventarioResponseDto>) {
        listaInventario.clear()
        listaInventario.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventario_bodega, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listaInventario[position], onEditarStockMinClick, onVerLotesClick)
    }

    override fun getItemCount(): Int = listaInventario.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombre: TextView = itemView.findViewById(R.id.tvProductoNombre)
        private val tvCodigo: TextView = itemView.findViewById(R.id.tvProductoCodigo)
        private val tvBodega: TextView = itemView.findViewById(R.id.tvBodegaNombre)
        private val tvCantidad: TextView = itemView.findViewById(R.id.tvCantidadActual)
        private val tvStockMin: TextView = itemView.findViewById(R.id.tvStockMinimo)
        private val tvCostoProm: TextView = itemView.findViewById(R.id.tvCostoPromedio)
        private val tvValoracion: TextView = itemView.findViewById(R.id.tvValoracionTotal)
        private val btnEditarStock: ImageButton = itemView.findViewById(R.id.btnEditarStockMinimo)
        private val btnVerLotes: MaterialButton = itemView.findViewById(R.id.btnVerLotes)

        fun bind(
            item: InventarioResponseDto,
            onEditarStock: (InventarioResponseDto) -> Unit,
            onVerLotes: (InventarioResponseDto) -> Unit
        ) {
            val cantidad = item.cantidadActual ?: 0
            val stockMinimo = item.stockMinimo ?: 0
            val costoPromedio = item.costoPromedio ?: 0.0
            val valorTotal = item.valorInventario ?: (cantidad * costoPromedio)

            tvNombre.text = item.productoNombre ?: "Producto sin nombre"
            tvCodigo.text = "CÓD: ${item.codigoPrincipal ?: item.productoCodigo ?: "N/A"}"
            tvBodega.text = item.bodegaNombre ?: "Bodega"

            tvCantidad.text = "$cantidad uds."
            tvStockMin.text = "Mínimo: $stockMinimo"
            tvCostoProm.text = String.format(Locale.US, "$%.2f", costoPromedio)
            tvValoracion.text = String.format(Locale.US, "$%.2f", valorTotal)

            if (cantidad <= stockMinimo) {
                tvCantidad.setTextColor(Color.parseColor("#DC2626")) // Rojo
            } else {
                tvCantidad.setTextColor(Color.parseColor("#16A34A")) // Verde
            }

            btnEditarStock.setOnClickListener { onEditarStock(item) }
            btnVerLotes.setOnClickListener { onVerLotes(item) }
        }
    }
}