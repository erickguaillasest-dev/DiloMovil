package com.example.movildilo.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.MiembroResponseDto
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MiembroEquipoAdapter(
    private var lista: MutableList<MiembroResponseDto>,
    private val onAprobar: ((MiembroResponseDto) -> Unit)? = null,
    private val onRechazar: ((MiembroResponseDto) -> Unit)? = null,
    private val onDesactivar: ((MiembroResponseDto) -> Unit)? = null,
    private val onEditarRol: ((MiembroResponseDto) -> Unit)? = null
) : RecyclerView.Adapter<MiembroEquipoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMiembro: MaterialCardView = view.findViewById(R.id.cardMiembro)
        val cardAvatar: MaterialCardView = view.findViewById(R.id.cardAvatar)
        val tvIniciales: TextView = view.findViewById(R.id.tvIniciales)
        val tvNombre: TextView = view.findViewById(R.id.tvNombreMiembro)
        val tvEmail: TextView = view.findViewById(R.id.tvEmailMiembro)
        val tvRol: TextView = view.findViewById(R.id.tvRolMiembro)

        val layoutBadgeActivo: LinearLayout = view.findViewById(R.id.layoutBadgeActivo)
        val cardBadgePendiente: View = view.findViewById(R.id.cardBadgePendiente)
        val layoutAccionesPendiente: LinearLayout = view.findViewById(R.id.layoutAccionesPendiente)

        val btnAprobar: MaterialButton = view.findViewById(R.id.btnAprobar)
        val btnRechazar: MaterialButton = view.findViewById(R.id.btnRechazar)
        val btnEditarRol: ImageView = view.findViewById(R.id.btnEditarRol)
        val cardOpciones: MaterialCardView = view.findViewById(R.id.cardOpcionesMiembro)
        val btnOpciones: ImageView = view.findViewById(R.id.btnOpcionesMiembro)
        val btnDesactivar: MaterialButton = view.findViewById(R.id.btnDesactivar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_miembro_equipo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        holder.tvNombre.text = item.nombreUsuario?.takeIf { it.isNotBlank() } ?: "Sin Nombre"
        holder.tvEmail.text = item.emailUsuario?.takeIf { it.isNotBlank() } ?: "Sin Correo"
        holder.tvIniciales.text = obtenerIniciales(item.nombreUsuario)

        val rolTexto = item.rol?.takeIf { it.isNotBlank() } ?: "COLABORADOR"
        holder.tvRol.text = rolTexto.uppercase()
        holder.tvRol.visibility = View.VISIBLE

        val estadoInvitacionUpper = item.estadoInvitacion?.trim()?.uppercase() ?: ""
        val estadoLaboralUpper = item.estadoLaboral?.trim()?.uppercase() ?: ""

        val esPendiente = estadoInvitacionUpper == "PENDIENTE"
        val esInactivo = estadoLaboralUpper == "INACTIVO" || estadoLaboralUpper == "DESACTIVADO"
        val esActivo = !esPendiente && !esInactivo

        when {
            esPendiente -> {
                holder.cardMiembro.setCardBackgroundColor(Color.parseColor("#FFFBEB"))
                holder.cardMiembro.strokeColor = Color.parseColor("#FDE68A")
                holder.cardAvatar.setCardBackgroundColor(Color.parseColor("#D97706"))

                holder.layoutBadgeActivo.visibility = View.GONE
                holder.cardOpciones.visibility = View.GONE
                holder.btnEditarRol.visibility = View.GONE
                holder.btnDesactivar.visibility = View.GONE

                holder.cardBadgePendiente.visibility = View.VISIBLE
                holder.layoutAccionesPendiente.visibility = View.VISIBLE

                holder.btnAprobar.setOnClickListener { onAprobar?.invoke(item) }
                holder.btnRechazar.setOnClickListener { onRechazar?.invoke(item) }
            }

            esActivo -> {
                holder.cardMiembro.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                holder.cardMiembro.strokeColor = Color.parseColor("#E2E8F0")
                holder.cardAvatar.setCardBackgroundColor(Color.parseColor("#0F172A"))

                holder.cardBadgePendiente.visibility = View.GONE
                holder.layoutAccionesPendiente.visibility = View.GONE

                holder.layoutBadgeActivo.visibility = View.VISIBLE
                holder.layoutBadgeActivo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))

                val esCreadorAbsoluto = item.esCreador

                if (esCreadorAbsoluto) {
                    holder.cardOpciones.visibility = View.GONE
                    holder.btnEditarRol.visibility = View.GONE
                    holder.btnDesactivar.visibility = View.GONE
                } else {
                    holder.cardOpciones.visibility = View.VISIBLE
                    holder.btnEditarRol.visibility = View.VISIBLE
                    holder.btnDesactivar.visibility = View.VISIBLE

                    holder.btnDesactivar.text = "Desactivar"
                    holder.btnDesactivar.setTextColor(Color.parseColor("#EF4444"))
                    holder.btnDesactivar.strokeColor = ColorStateList.valueOf(Color.parseColor("#FCA5A5"))

                    holder.btnDesactivar.setOnClickListener { onDesactivar?.invoke(item) }
                    holder.btnEditarRol.setOnClickListener { onEditarRol?.invoke(item) }

                    holder.btnOpciones.setOnClickListener { view ->
                        val popup = PopupMenu(view.context, view)
                        popup.menu.add("Editar Rol")
                        popup.menu.add("Desactivar")
                        popup.setOnMenuItemClickListener { menuItem ->
                            when (menuItem.title) {
                                "Editar Rol" -> { onEditarRol?.invoke(item); true }
                                "Desactivar" -> { onDesactivar?.invoke(item); true }
                                else -> false
                            }
                        }
                        popup.show()
                    }
                }
            }

            else -> {
                holder.cardMiembro.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                holder.cardMiembro.strokeColor = Color.parseColor("#E2E8F0")
                holder.cardAvatar.setCardBackgroundColor(Color.parseColor("#64748B"))

                holder.cardBadgePendiente.visibility = View.GONE
                holder.layoutAccionesPendiente.visibility = View.GONE

                holder.layoutBadgeActivo.visibility = View.VISIBLE
                holder.layoutBadgeActivo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9CA3AF"))

                holder.cardOpciones.visibility = View.VISIBLE
                holder.btnEditarRol.visibility = View.GONE
                holder.btnDesactivar.visibility = View.VISIBLE

                holder.btnDesactivar.text = "Reactivar"
                holder.btnDesactivar.setTextColor(Color.parseColor("#16A34A"))
                holder.btnDesactivar.strokeColor = ColorStateList.valueOf(Color.parseColor("#22C55E"))

                holder.btnDesactivar.setOnClickListener { onDesactivar?.invoke(item) }

                holder.btnOpciones.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.menu.add("Reactivar")
                    popup.setOnMenuItemClickListener { menuItem ->
                        if (menuItem.title == "Reactivar") {
                            onDesactivar?.invoke(item)
                            true
                        } else false
                    }
                    popup.show()
                }
            }
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<MiembroResponseDto>) {
        lista = nuevaLista.toMutableList()
        notifyDataSetChanged()
    }

    private fun obtenerIniciales(nombreCompleto: String?): String {
        val nombre = nombreCompleto?.trim()
        if (nombre.isNullOrEmpty()) return "??"
        val partes = nombre.split(" ").filter { it.isNotBlank() }
        return when {
            partes.size >= 2 -> "${partes[0].first().uppercaseChar()}${partes[1].first().uppercaseChar()}"
            partes.size == 1 -> partes[0].take(2).uppercase()
            else -> "??"
        }
    }
}