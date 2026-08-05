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

        // 1. Nombre del producto
        holder.tvNombreProducto.text = prod.nombre ?: "Producto sin nombre"

        // 2. Marca y Unidad de medida
        val marca = prod.marca ?: "Sin Marca"
        val unidad = prod.unidadMedida ?: "UNIDADES"
        holder.tvMarcaUnidad.text = "$marca | $unidad"

        // 3. Categoría
        holder.tvCategoriaBadge.text = prod.categoria ?: "General"

        // 4. Imagen (URL o Base64)
        val imagenStr = prod.imagen
        if (!imagenStr.isNullOrEmpty()) {
            if (imagenStr.startsWith("http://") || imagenStr.startsWith("https://")) {
                // Carga desde URL de red
                Glide.with(holder.itemView.context)
                    .load(imagenStr)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(holder.imgProducto)
            } else {
                // Carga desde string en Base64
                try {
                    val cleanBase64 = if (imagenStr.contains(",")) imagenStr.substringAfter(",") else imagenStr
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    Glide.with(holder.itemView.context)
                        .asBitmap()
                        .load(imageBytes)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .centerCrop()
                        .into(holder.imgProducto)
                } catch (e: Exception) {
                    holder.imgProducto.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        } else {
            holder.imgProducto.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // 5. Precios (PVP con 2 decimales y Costo Promedio con 4 decimales)
        val pvp = prod.precioUnitario ?: 0.0
        val costo = prod.costoPromedio ?: 0.0

        holder.tvPvpVenta.text = String.format(Locale.US, "$%.2f", pvp)
        holder.tvCostoPromedio.text = String.format(Locale.US, "$%.4f", costo)

        // 6. Impuesto Badge (CON IVA / SIN IVA)
        val grabaIva = prod.grabaIva ?: true
        if (grabaIva) {
            holder.tvImpuestoBadge.text = "CON IVA"
            holder.tvImpuestoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7")) // Verde claro
            holder.tvImpuestoBadge.setTextColor(Color.parseColor("#166534"))                                 // Verde oscuro
        } else {
            holder.tvImpuestoBadge.text = "SIN IVA"
            holder.tvImpuestoBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9")) // Gris claro
            holder.tvImpuestoBadge.setTextColor(Color.parseColor("#475569"))                                 // Gris oscuro
        }

        // 7. Acciones de los botones
        holder.btnEditar.setOnClickListener { onEditClick(prod) }
        holder.btnEliminar.setOnClickListener { onDeleteClick(prod) }
    }

    override fun getItemCount(): Int = listaProductos.size

    fun actualizarLista(nuevaLista: List<ProductoDto>) {
        listaProductos = nuevaLista
        notifyDataSetChanged()
    }
}