package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.HistorialAbonoDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HistorialAbonosAdapter(
    private val lista: List<HistorialAbonoDto>
) : RecyclerView.Adapter<HistorialAbonosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frameIcono: FrameLayout = view.findViewById(R.id.frameIconoAbono)
        val tvIcono: TextView = view.findViewById(R.id.tvIconoAbono)
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloAbono)
        val tvSubtitulo: TextView = view.findViewById(R.id.tvSubtituloAbono)
        val tvRef: TextView = view.findViewById(R.id.tvRefAbono)
        val tvMonto: TextView = view.findViewById(R.id.tvMontoAbono)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial_abono, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val abono = lista[position]

        val esEfectivo = abono.metodoPago?.equals("EFECTIVO", ignoreCase = true) == true
        holder.tvIcono.text = if (esEfectivo) "💵" else "🏦"
        holder.frameIcono.setBackgroundResource(
            if (esEfectivo) R.drawable.bg_icon_cash_green else R.drawable.bg_icon_bank_blue
        )


        val metodo = abono.metodoPago ?: "EFECTIVO"
        holder.tvTitulo.text = "Abono en $metodo"


        val fechaFormateada = formatearFechaEstiloWeb(abono.fechaAbono)
        val recibioTexto = if (!abono.usuarioRecibio.isNullOrBlank()) " • Recibió: ${abono.usuarioRecibio}" else ""
        holder.tvSubtitulo.text = "$fechaFormateada$recibioTexto"


        if (!abono.referencia.isNullOrBlank()) {
            holder.tvRef.visibility = View.VISIBLE
            holder.tvRef.text = "Ref: ${abono.referencia}"
        } else {
            holder.tvRef.visibility = View.GONE
        }


        val montoAbonado = abono.montoAbonado ?: 0.0
        holder.tvMonto.text = String.format(Locale.US, "+$%.2f", montoAbonado)
    }

    private fun formatearFechaEstiloWeb(fechaRaw: String?): String {
        if (fechaRaw.isNullOrBlank()) return "N/A"
        return try {
            val limpiarStr = if (fechaRaw.length > 19) fechaRaw.substring(0, 19) else fechaRaw

            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(limpiarStr) ?: return fechaRaw

            val formatter = SimpleDateFormat("dd/MMM/yyyy, hh:mm a", Locale.ENGLISH)
            formatter.format(date)
        } catch (_: Exception) {
            fechaRaw
        }
    }

    override fun getItemCount() = lista.size
}
