package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import java.util.Locale

class NegocioAdminAdapter(
    private var lista: List<NegocioResponseDto>,
    private val onEditar: (NegocioResponseDto) -> Unit,
    private val onEliminar: (NegocioResponseDto) -> Unit
) : RecyclerView.Adapter<NegocioAdminAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgLogo: ImageView = itemView.findViewById(R.id.imgLogoNegocio)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoContabilidad)
        val tvRazonSocial: TextView = itemView.findViewById(R.id.tvRazonSocial)
        val tvNombreComercial: TextView = itemView.findViewById(R.id.tvNombreComercial)
        val tvRuc: TextView = itemView.findViewById(R.id.tvRuc)
        val tvIdNegocio: TextView = itemView.findViewById(R.id.tvIdNegocio)
        val tvMetodoCosteo: TextView = itemView.findViewById(R.id.tvMetodoCosteo)
        val tvFechaCreacion: TextView = itemView.findViewById(R.id.tvFechaCreacion)
        val btnEditar: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnEditarNegocio)
        val btnEliminar: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.btnEliminarNegocio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_negocio_admin, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val negocio = lista[position]

        holder.tvRazonSocial.text = negocio.razonSocial ?: "Sin razón social"
        holder.tvNombreComercial.text = negocio.nombreComercial ?: "Sin nombre comercial"
        holder.tvRuc.text = negocio.ruc ?: "--"
        holder.tvIdNegocio.text = "#${negocio.id ?: "--"}"
        holder.tvMetodoCosteo.text = negocio.metodoCosteo ?: "PROMEDIO"
        holder.tvFechaCreacion.text = formatearFecha(negocio.fechaCreacion)

        val obligado = negocio.obligadoContabilidad == true
        holder.tvEstado.text = if (obligado) "Obligado Cont." else "No Obligado"

        if (!negocio.rutaImagen.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(negocio.rutaImagen)
                .circleCrop()
                .placeholder(R.drawable.bg_avatar_circulo)
                .error(R.drawable.bg_avatar_circulo)
                .into(holder.imgLogo)
        } else {
            holder.imgLogo.setImageResource(R.drawable.bg_avatar_circulo)
        }

        holder.btnEditar.setOnClickListener { onEditar(negocio) }
        holder.btnEliminar.setOnClickListener { onEliminar(negocio) }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<NegocioResponseDto>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    // El backend a veces manda la fecha como texto ISO y a veces como arreglo [año, mes, día, ...]
    private fun formatearFecha(fecha: com.google.gson.JsonElement?): String {
        if (fecha == null || fecha.isJsonNull) return "Sin fecha"
        return try {
            if (fecha.isJsonArray) {
                val arr = fecha.asJsonArray
                val year = arr[0].asInt
                val month = arr[1].asInt
                val day = arr[2].asInt
                String.format(Locale.US, "%02d/%02d/%04d", day, month, year)
            } else if (fecha.isJsonPrimitive) {
                val texto = fecha.asJsonPrimitive.asString
                texto.take(10)
            } else {
                "Sin fecha"
            }
        } catch (e: Exception) {
            "Sin fecha"
        }
    }
}