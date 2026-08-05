package com.example.movildilo.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.UsuarioMeDto
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class UsuariosAdapter(
    private var lista: MutableList<UsuarioMeDto>,
    private val onClickUsuario: (UsuarioMeDto) -> Unit
) : RecyclerView.Adapter<UsuariosAdapter.UsuarioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        holder.bind(lista[position], position, onClickUsuario)
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<UsuarioMeDto>) {
        lista = nuevaLista.toMutableList()
        notifyDataSetChanged()
    }

    class UsuarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIdUsuario: TextView = itemView.findViewById(R.id.tvIdUsuario)
        private val ivAvatarUsuario: ShapeableImageView = itemView.findViewById(R.id.ivAvatarUsuario)
        private val tvNombreCompleto: TextView = itemView.findViewById(R.id.tvNombreCompleto)
        private val tvDni: TextView = itemView.findViewById(R.id.tvDni)
        private val tvContacto: TextView = itemView.findViewById(R.id.tvContacto)
        private val btnEditar: MaterialButton = itemView.findViewById(R.id.btnEditar)
        private val btnEliminar: MaterialButton = itemView.findViewById(R.id.btnEliminar)

        fun bind(usuario: UsuarioMeDto, position: Int, onClickUsuario: (UsuarioMeDto) -> Unit) {
            tvIdUsuario.text = "#${position + 1}"

            val nombreCompleto = "${usuario.primerNombre.orEmpty()} ${usuario.apellidoPaterno.orEmpty()}".trim()
            tvNombreCompleto.text = if (nombreCompleto.isNotEmpty()) nombreCompleto else "Sin nombre"
            tvDni.text = "DNI: ${usuario.dni ?: "No registrado"}"
            tvContacto.text = "${usuario.email ?: "sin correo"} | ${usuario.telefono ?: "sin teléfono"}"

            // 🔥 Carga de la foto de perfil (igual que en negocios: Glide + placeholder/error)
            Glide.with(itemView)
                .load(usuario.fotoPerfil)
                .circleCrop()
                .placeholder(R.drawable.bg_avatar_circulo)
                .error(R.drawable.bg_avatar_circulo)
                .into(ivAvatarUsuario)

            // Al igual que en la web, no existe endpoint de eliminación de usuarios.
            // Ocultamos el botón en vez de dejarlo sin funcionar.
            btnEliminar.visibility = View.GONE

            btnEditar.setOnClickListener { onClickUsuario(usuario) }
            itemView.setOnClickListener { onClickUsuario(usuario) }
        }
    }
}