package com.example.movildilo.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ParroquiaResponseDto

class ParroquiaAdapter(
    private var lista: List<ParroquiaResponseDto>,
    private val onEditClick: (ParroquiaResponseDto) -> Unit,
    private val onDeleteClick: (ParroquiaResponseDto) -> Unit
) : RecyclerView.Adapter<ParroquiaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreParroquia)
        val tvId: TextView = view.findViewById(R.id.tvIdInterno)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parroquia, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.tvNombre.text = item.nombre
        holder.tvId.text = "ID Interno: #${item.id}"

        holder.btnEditar.setOnClickListener { onEditClick(item) }
        holder.btnEliminar.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<ParroquiaResponseDto>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}