package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ClienteAgrupado

class ClientesAgrupadosAdapter(
    private var lista: List<ClienteAgrupado>,
    private val onVerFacturasClick: (ClienteAgrupado) -> Unit
) : RecyclerView.Adapter<ClientesAgrupadosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatarInicial)
        val tvNombre: TextView = view.findViewById(R.id.tvNombreClienteGroup)
        val tvPendientes: TextView = view.findViewById(R.id.tvCuentasPendientesGroup)
        val tvDeuda: TextView = view.findViewById(R.id.tvTotalDeudaGroup)
        val btnVerFacturas: Button = view.findViewById(R.id.btnVerFacturasCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cliente_agrupado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val inicial = if (item.nombre.isNotEmpty()) item.nombre.substring(0, 1).uppercase() else "C"

        holder.tvAvatar.text = inicial
        val idTexto = if (!item.identificacion.isNullOrEmpty()) " (${item.identificacion})" else ""
        holder.tvNombre.text = "${item.nombre}$idTexto"
        holder.tvPendientes.text = "${item.cuentasPendientes} Pendientes"
        holder.tvDeuda.text = String.format("$%.2f", item.totalDeuda)

        holder.btnVerFacturas.setOnClickListener { onVerFacturasClick(item) }
    }

    override fun getItemCount() = lista.size

    fun actualizarLista(nuevaLista: List<ClienteAgrupado>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}