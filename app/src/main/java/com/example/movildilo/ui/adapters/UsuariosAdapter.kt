package com.example.movildilo.ui.adapters

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

    private val baseServerUrl = "https://dilo-backend-mxlu.onrender.com"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        holder.bind(lista[position], position, baseServerUrl, onClickUsuario)
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<UsuarioMeDto>) {
        lista = nuevaLista.toMutableList()
        notifyDataSetChanged()
    }

    class UsuarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIdUsuario: TextView = itemView.findViewById(R.id.tvIdUsuario)
        private val ivAvatarUsuario: ShapeableImageView = itemView.findViewById(R.id.ivAvatarUsuario)
        private val tvAvatarInicial: TextView? = itemView.findViewById<TextView?>(R.id.tvAvatarInicial)
            ?: itemView.findViewById(R.id.tvIniciales)
        private val tvNombreCompleto: TextView = itemView.findViewById(R.id.tvNombreCompleto)
        private val tvDni: TextView = itemView.findViewById(R.id.tvDni)
        private val tvContacto: TextView = itemView.findViewById(R.id.tvContacto)
        private val btnEditar: MaterialButton = itemView.findViewById(R.id.btnEditar)
        private val btnEliminar: MaterialButton = itemView.findViewById(R.id.btnEliminar)

        fun bind(
            usuario: UsuarioMeDto,
            position: Int,
            baseUrl: String,
            onClickUsuario: (UsuarioMeDto) -> Unit
        ) {
            tvIdUsuario.text = "#${position + 1}"

            val nombreCompleto = "${usuario.primerNombre.orEmpty()} ${usuario.apellidoPaterno.orEmpty()}".trim()
            val nombreDisplay = if (nombreCompleto.isNotEmpty()) nombreCompleto else "Sin nombre"

            tvNombreCompleto.text = nombreDisplay
            tvDni.text = "DNI: ${usuario.dni ?: "No registrado"}"
            tvContacto.text = "${usuario.email ?: "sin correo"} | ${usuario.telefono ?: "sin teléfono"}"

            val urlFoto = construirUrlFoto(usuario.fotoPerfil, baseUrl)
            val inicial = nombreDisplay.firstOrNull()?.uppercase() ?: "U"

            if (!urlFoto.isNullOrBlank()) {
                ivAvatarUsuario.visibility = View.VISIBLE
                tvAvatarInicial?.visibility = View.GONE

                Glide.with(itemView.context)
                    .load(urlFoto)
                    .circleCrop()
                    .placeholder(R.drawable.bg_avatar_circulo)
                    .error(R.drawable.bg_avatar_circulo)
                    .into(ivAvatarUsuario)
            } else {
                if (tvAvatarInicial != null) {
                    ivAvatarUsuario.visibility = View.GONE
                    tvAvatarInicial.visibility = View.VISIBLE
                    tvAvatarInicial.text = inicial
                } else {
                    ivAvatarUsuario.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .clear(ivAvatarUsuario)
                    ivAvatarUsuario.setImageResource(R.drawable.bg_avatar_circulo)
                }
            }

            btnEliminar.visibility = View.GONE

            btnEditar.setOnClickListener { onClickUsuario(usuario) }
            itemView.setOnClickListener { onClickUsuario(usuario) }
        }

        private fun construirUrlFoto(rutaFoto: String?, baseUrl: String): String? {
            if (rutaFoto.isNullOrBlank()) return null
            return if (rutaFoto.startsWith("http")) rutaFoto else "$baseUrl$rutaFoto"
        }
    }
}