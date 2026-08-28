package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.inventario.BodegaDto
import com.google.android.material.button.MaterialButton

class BodegaAdapter(
    private var bodegas: List<BodegaDto>,
    private val onEditar: (BodegaDto) -> Unit,
    private val onEliminar: (BodegaDto) -> Unit
) : RecyclerView.Adapter<BodegaAdapter.BodegaViewHolder>() {

    class BodegaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreBodega)
        val tvDireccion: TextView = view.findViewById(R.id.tvDireccionBodega)
        val tvId: TextView = view.findViewById(R.id.tvIdBodega)
        val btnEditar: MaterialButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: MaterialButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BodegaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bodega, parent, false)
        return BodegaViewHolder(view)
    }

    override fun onBindViewHolder(holder: BodegaViewHolder, position: Int) {
        val bodega = bodegas[position]
        holder.tvNombre.text = bodega.nombre
        holder.tvDireccion.text = if (!bodega.direccion.isNull_or_Empty()) "📍 ${bodega.direccion}" else "📍 Sin dirección"
        holder.tvId.text = "ID Bodega: #${bodega.id}"

        holder.btnEditar.setOnClickListener { onEditar(bodega) }
        holder.btnEliminar.setOnClickListener { onEliminar(bodega) }
    }

    override fun getItemCount(): Int = bodegas.size

    fun updateList(newList: List<BodegaDto>) {
        bodegas = newList
        notifyDataSetChanged()
    }
}

private fun String?.isNull_or_Empty(): Boolean = this == null || this.trim().isEmpty()