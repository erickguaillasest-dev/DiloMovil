package com.example.movildilo.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ProductoDto
import java.util.Locale

class ProductosAdapter(
    private var listaProductos: List<ProductoDto>,
    private val onEditClick: (ProductoDto) -> Unit,
    private val onDeleteClick: (ProductoDto) -> Unit
) : RecyclerView.Adapter<ProductosAdapter.ProductoViewHolder>() {

    class ProductoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProducto: ImageView = itemView.findViewById(R.id.imgProducto)
        val tvNombreProducto: TextView = itemView.findViewById(R.id.tvNombreProducto)
        val tvMarcaUnidad: TextView = itemView.findViewById(R.id.tvMarcaUnidad)
        val tvCategoriaBadge: TextView = itemView.findViewById(R.id.tvCategoriaBadge)
        val tvImpuestoBadge: TextView = itemView.findViewById(R.id.tvImpuestoBadge)
        val tvPvpVenta: TextView = itemView.findViewById(R.id.tvPvpVenta)
        val tvCostoPromedio: TextView = itemView.findViewById(R.id.tvCostoPromedio)
        val btnEditar: ImageButton = itemView.findViewById(R.id.btnEditarProducto)
        val btnEliminar: ImageButton = itemView.findViewById(R.id.btnEliminarProducto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto, parent, false)
        return ProductoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        val prod = listaProductos[position]

        holder.tvNombreProducto.text = prod.nombre ?: "Producto sin nombre"

        val marca = prod.marca ?: "Sin Marca"
        val unidad = prod.unidadMedida ?: "UNIDADES"
        holder.tvMarcaUnidad.text = "$marca | $unidad"

        holder.tvCategoriaBadge.text = prod.categoria ?: "General"

        val imagenStr = prod.imagen
        if (!imagenStr.isNullOrEmpty()) {
            if (imagenStr.startsWith("http://") || imagenStr.startsWith("https://")) {
                Glide.with(holder.itemView.context)
                    .load(imagenStr)
                    .placeholder(R.drawable.logo_dilo_sf)
                    .error(R.drawable.logo_dilo_sf)
                    .centerCrop()
                    .into(holder.imgProducto)
            } else {
                try {
                    val cleanBase64 = if (imagenStr.contains(",")) imagenStr.substringAfter(",") else imagenStr
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    Glide.with(holder.itemView.context)
                        .asBitmap()
                        .load(imageBytes)
                        .placeholder(R.drawable.logo_dilo_sf)
                        .error(R.drawable.logo_dilo_sf)
                        .centerCrop()
                        .into(holder.imgProducto)
                } catch (t: Throwable) {
                    holder.imgProducto.setImageResource(R.drawable.logo_dilo_sf)
                }
            }
        } else {
            holder.imgProducto.setImageResource(R.drawable.logo_dilo_sf)
        }

        val pvp = prod.precioUnitario ?: 0.0
        val costo = prod.costoPromedioActual ?: 0.0

        holder.tvPvpVenta.text = String.format(Locale.US, "$%.2f", pvp)
        holder.tvCostoPromedio.text = String.format(Locale.US, "$%.4f", costo)

        val grabaIva = prod.grabaIva ?: true
        if (grabaIva) {
            holder.tvImpuestoBadge.text = "CON IVA"
            holder.tvImpuestoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
            holder.tvImpuestoBadge.setTextColor(Color.parseColor("#166534"))
        } else {
            holder.tvImpuestoBadge.text = "SIN IVA"
            holder.tvImpuestoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
            holder.tvImpuestoBadge.setTextColor(Color.parseColor("#475569"))
        }

        holder.btnEditar.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onEditClick(listaProductos[currentPos])
            }
        }

        holder.btnEliminar.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onDeleteClick(listaProductos[currentPos])
            }
        }
    }

    override fun getItemCount(): Int = listaProductos.size

    fun actualizarLista(nuevaLista: List<ProductoDto>) {
        val diffCallback = ProductoDiffCallback(this.listaProductos, nuevaLista)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.listaProductos = nuevaLista
        diffResult.dispatchUpdatesTo(this)
    }

    class ProductoDiffCallback(
        private val antiguaLista: List<ProductoDto>,
        private val nuevaLista: List<ProductoDto>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = antiguaLista.size
        override fun getNewListSize(): Int = nuevaLista.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return antiguaLista[oldItemPosition].id == nuevaLista[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return antiguaLista[oldItemPosition] == nuevaLista[newItemPosition]
        }
    }
}