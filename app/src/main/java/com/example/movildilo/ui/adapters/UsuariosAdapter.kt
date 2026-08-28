package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.UsuarioMeDto
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class UsuariosAdapter(
    private var lista: MutableList<UsuarioMeDto>,
    private val onClickUsuario: (UsuarioMeDto) -> Unit,
    private val onSuspenderUsuario: (UsuarioMeDto) -> Unit
) : RecyclerView.Adapter<UsuariosAdapter.UsuarioViewHolder>() {

    private val baseServerUrl = "https://dilo-backend-mxlu.onrender.com"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsuarioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario, parent, false)
        return UsuarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsuarioViewHolder, position: Int) {
        holder.bind(lista[position], baseServerUrl, onClickUsuario, onSuspenderUsuario)
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<UsuarioMeDto>) {
        lista = nuevaLista.toMutableList()
        notifyDataSetChanged()
    }

    class UsuarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEstadoBadge: TextView = itemView.findViewById(R.id.tvEstadoBadge)
        private val ivAvatarUsuario: ShapeableImageView = itemView.findViewById(R.id.ivAvatarUsuario)
        private val tvIniciales: TextView = itemView.findViewById(R.id.tvIniciales)
        private val tvNombreCompleto: TextView = itemView.findViewById(R.id.tvNombreCompleto)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        private val tvDni: TextView = itemView.findViewById(R.id.tvDni)
        private val tvTelefono: TextView = itemView.findViewById(R.id.tvTelefono)
        private val btnVerDetalles: MaterialButton = itemView.findViewById(R.id.btnVerDetalles)
        private val btnSuspender: MaterialButton = itemView.findViewById(R.id.btnSuspender)

        fun bind(
            usuario: UsuarioMeDto,
            baseUrl: String,
            onClickUsuario: (UsuarioMeDto) -> Unit,
            onSuspenderUsuario: (UsuarioMeDto) -> Unit
        ) {
            val nombreCompleto = "${usuario.primerNombre.orEmpty()} ${usuario.apellidoPaterno.orEmpty()}".trim()
            tvNombreCompleto.text = if (nombreCompleto.isNotEmpty()) nombreCompleto else "Sin nombre"
            tvEmail.text = usuario.email ?: "Sin correo"
            tvDni.text = usuario.dni ?: "Sin DNI"
            tvTelefono.text = usuario.telefono?.takeIf { it.isNotBlank() } ?: "Sin teléfono"

            val estaSuspendido = usuario.suspendido == true
            if (estaSuspendido) {
                tvEstadoBadge.text = "SUSPENDIDO"
                tvEstadoBadge.setTextColor(Color.parseColor("#DC2626"))
                btnSuspender.text = "✔️ Activar Cuenta"
                btnSuspender.setTextColor(Color.parseColor("#059669"))
            } else {
                tvEstadoBadge.text = "ACTIVO"
                tvEstadoBadge.setTextColor(Color.parseColor("#0D9488"))
                btnSuspender.text = "⛔ Suspender Cuenta"
                btnSuspender.setTextColor(Color.parseColor("#DC2626"))
            }

            val urlFoto = construirUrlFoto(usuario.fotoPerfil, baseUrl)
            val inicial = tvNombreCompleto.text.toString().firstOrNull()?.uppercase() ?: "U"

            if (!urlFoto.isNullOrBlank()) {
                ivAvatarUsuario.visibility = View.VISIBLE
                tvIniciales.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(urlFoto)
                    .circleCrop()
                    .placeholder(R.drawable.bg_avatar_circulo)
                    .error(R.drawable.bg_avatar_circulo)
                    .into(ivAvatarUsuario)
            } else {
                ivAvatarUsuario.visibility = View.GONE
                tvIniciales.visibility = View.VISIBLE
                tvIniciales.text = inicial
            }

            btnVerDetalles.setOnClickListener { onClickUsuario(usuario) }
            btnSuspender.setOnClickListener { onSuspenderUsuario(usuario) }
        }

        private fun construirUrlFoto(rutaFoto: String?, baseUrl: String): String? {
            if (rutaFoto.isNullOrBlank()) return null
            return if (rutaFoto.startsWith("http")) rutaFoto else "$baseUrl$rutaFoto"
        }
    }
}