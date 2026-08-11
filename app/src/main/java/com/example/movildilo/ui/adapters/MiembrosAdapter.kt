package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.MiembroResponseDto

class MiembrosAdapter(
    private var listaMiembros: List<MiembroResponseDto>,
    private val onCambiarRolClick: (MiembroResponseDto) -> Unit,
    private val onDesactivarClick: (MiembroResponseDto) -> Unit,
    private val soloLectura: Boolean = false
) : RecyclerView.Adapter<MiembrosAdapter.MiembroViewHolder>() {

    class MiembroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIniciales: TextView = itemView.findViewById(R.id.tvIniciales)
        val tvNombreMiembro: TextView = itemView.findViewById(R.id.tvNombreMiembro)
        val tvEmailMiembro: TextView = itemView.findViewById(R.id.tvEmailMiembro)
        val tvRolMiembro: TextView = itemView.findViewById(R.id.tvRolMiembro)
        val btnOpcionesMiembro: ImageView = itemView.findViewById(R.id.btnOpcionesMiembro)
        val cardOpcionesMiembro: View = itemView.findViewById(R.id.cardOpcionesMiembro)
        val btnEditarRol: ImageView = itemView.findViewById(R.id.btnEditarRol)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiembroViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_miembro_equipo, parent, false)
        return MiembroViewHolder(view)
    }

    override fun onBindViewHolder(holder: MiembroViewHolder, position: Int) {
        val miembro = listaMiembros[position]

        val nombre = miembro.nombreUsuario ?: "Sin nombre"
        holder.tvNombreMiembro.text = nombre

        holder.tvEmailMiembro.visibility = View.GONE

        holder.tvRolMiembro.text = (miembro.rol ?: "Sin rol").uppercase()
        holder.tvRolMiembro.visibility = View.VISIBLE

        holder.tvIniciales.text = obtenerIniciales(nombre)

        if (soloLectura) {
            holder.cardOpcionesMiembro.visibility = View.GONE
            holder.btnOpcionesMiembro.setOnClickListener(null)
            holder.btnOpcionesMiembro.isClickable = false

            holder.btnEditarRol.visibility = View.GONE
            holder.btnEditarRol.setOnClickListener(null)
        } else {
            holder.cardOpcionesMiembro.visibility = View.VISIBLE
            holder.btnOpcionesMiembro.isClickable = true
            holder.btnOpcionesMiembro.setOnClickListener { view ->
                mostrarMenuOpciones(view, miembro)
            }

            holder.btnEditarRol.visibility = View.VISIBLE
            holder.btnEditarRol.setOnClickListener {
                onCambiarRolClick(miembro)
            }
        }
    }

    override fun getItemCount(): Int = listaMiembros.size

    private fun obtenerIniciales(nombreCompleto: String): String {
        val partes = nombreCompleto.trim().split(" ").filter { it.isNotBlank() }
        return when {
            partes.isEmpty() -> "?"
            partes.size == 1 -> partes[0].take(1).uppercase()
            else -> (partes[0].take(1) + partes[1].take(1)).uppercase()
        }
    }

    private fun mostrarMenuOpciones(view: View, miembro: MiembroResponseDto) {
        val popup = PopupMenu(view.context, view)

        popup.menu.add(0, 1, 0, "Cambiar Rol")
        popup.menu.add(0, 2, 1, "Desactivar Miembro")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    onCambiarRolClick(miembro)
                    true
                }
                2 -> {
                    onDesactivarClick(miembro)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun actualizarLista(nuevaLista: List<MiembroResponseDto>) {
        listaMiembros = nuevaLista
        notifyDataSetChanged()
    }
}