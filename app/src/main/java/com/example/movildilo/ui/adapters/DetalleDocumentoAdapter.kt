package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.DocumentoUiModel
import java.lang.String.format
import java.util.Locale

class DetalleDocumentoAdapter(
    private var lista: List<DocumentoUiModel>,
    private val onDocumentoClick: (DocumentoUiModel) -> Unit = {}
) : RecyclerView.Adapter<DetalleDocumentoAdapter.ViewHolder>() {

    fun actualizarLista(nuevaLista: List<DocumentoUiModel>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowDocHeader: View = view.findViewById(R.id.rowDocHeader)
        val tvDocNumero: TextView = view.findViewById(R.id.tvDocNumero)
        val tvDocFecha: TextView = view.findViewById(R.id.tvDocFecha)
        val tvDocTipo: TextView = view.findViewById(R.id.tvDocTipo)
        val tvDocEstado: TextView = view.findViewById(R.id.tvDocEstado)
        val tvDocMonto: TextView = view.findViewById(R.id.tvDocMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_documento_cliente, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doc = lista[position]

        holder.tvDocNumero.text = doc.numero
        holder.tvDocFecha.text = doc.fecha
        holder.tvDocTipo.text = doc.tipo.uppercase()
        holder.tvDocMonto.text = format(Locale.US, "$%,.2f", doc.monto)
        holder.tvDocEstado.text = doc.estado.uppercase()

        if (doc.estado.equals("AUTORIZADO", true) || doc.estado.equals("PAGADA", true)) {
            holder.tvDocEstado.setTextColor(Color.parseColor("#10B981"))
        } else if (doc.estado.equals("PENDIENTE", true)) {
            holder.tvDocEstado.setTextColor(Color.parseColor("#F59E0B"))
        } else {
            holder.tvDocEstado.setTextColor(Color.parseColor("#64748B"))
        }

        holder.rowDocHeader.setOnClickListener {
            onDocumentoClick(doc)
        }
    }

    override fun getItemCount(): Int = lista.size
}