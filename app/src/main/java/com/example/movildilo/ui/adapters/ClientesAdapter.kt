package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ClienteResponseDto

class ClientesAdapter(
    private var listaClientes: List<ClienteResponseDto>,
    private val onEditarClick: (ClienteResponseDto) -> Unit,
    private val onEliminarClick: (ClienteResponseDto) -> Unit
) : RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder>() {

    class ClienteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDni: TextView = itemView.findViewById(R.id.tvDni)
        val tvAvatarInicial: TextView = itemView.findViewById(R.id.tvAvatarInicial)
        val tvNombreCompleto: TextView = itemView.findViewById(R.id.tvNombreCompleto)
        val tvDireccion: TextView = itemView.findViewById(R.id.tvDireccion)
        val tvTelefono: TextView = itemView.findViewById(R.id.tvTelefono)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        val btnEditar: ImageView = itemView.findViewById(R.id.btnEditar)
        val btnEliminar: ImageView = itemView.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente, parent, false)
        return ClienteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = listaClientes[position]

        holder.tvDni.text = "DNI: ${cliente.dni ?: "N/A"}"

        val nombreDisplay = cliente.nombreCompleto
            ?: "${cliente.primerNombre ?: ""} ${cliente.apellidoPaterno ?: ""}".trim()

        holder.tvNombreCompleto.text = if (nombreDisplay.isNotBlank()) nombreDisplay else "Cliente sin nombre"
        holder.tvAvatarInicial.text = nombreDisplay.firstOrNull()?.uppercase() ?: "C"

        holder.tvDireccion.text = if (!cliente.direccion.isNullOrBlank()) "📍 ${cliente.direccion}" else "📍 Sin dirección"
        holder.tvTelefono.text = if (!cliente.telefono.isNullOrBlank()) "📞 ${cliente.telefono}" else "📞 Sin teléfono"
        holder.tvEmail.text = if (!cliente.email.isNullOrBlank()) "✉️ ${cliente.email}" else "✉️ Sin correo"

        holder.btnEditar.setOnClickListener { onEditarClick(cliente) }
        holder.btnEliminar.setOnClickListener { onEliminarClick(cliente) }
    }

    override fun getItemCount(): Int = listaClientes.size

    fun actualizarLista(nuevaLista: List<ClienteResponseDto>) {
        listaClientes = nuevaLista
        notifyDataSetChanged()
    }
}