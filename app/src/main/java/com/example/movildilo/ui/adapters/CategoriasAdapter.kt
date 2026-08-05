package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.CategoriaDto

class CategoriasAdapter(
    private var lista: List<CategoriaDto>,
    private val onEditar: (CategoriaDto) -> Unit,
    private val onEliminar: (CategoriaDto) -> Unit
) : RecyclerView.Adapter<CategoriasAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView = view.findViewById(R.id.tvIdCategoria)
        val tvNombre: TextView = view.findViewById(R.id.tvNombreCategoria)
        val tvDescripcion: TextView = view.findViewById(R.id.tvDescripcionCategoria)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_categoria, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = lista[position]
        holder.tvId.text = "#${cat.id ?: "-"}"
        holder.tvNombre.text = cat.nombre
        holder.tvDescripcion.text = if (cat.descripcion.isNull_or_empty()) "Sin descripción" else cat.descripcion

        holder.btnEditar.setOnClickListener { onEditar(cat) }
        holder.btnEliminar.setOnClickListener { onEliminar(cat) }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<CategoriaDto>) {
        this.lista = nuevaLista
        notifyDataSetChanged()
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}