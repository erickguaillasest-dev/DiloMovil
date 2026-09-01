package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.usuarios.CreditoClienteResumenDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FacturasClienteModalAdapter(
    private val lista: List<CreditoClienteResumenDto>,
    private val onAbonarClick: (CreditoClienteResumenDto) -> Unit,
    private val onEmailClick: (CreditoClienteResumenDto) -> Unit
) : RecyclerView.Adapter<FacturasClienteModalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumFactura: TextView = view.findViewById(R.id.tvNumFacturaModalItem)
        val tvVencimiento: TextView = view.findViewById(R.id.tvFechaVencimientoItem)
        val tvEstado: TextView = view.findViewById(R.id.tvBadgeEstadoModal)
        val tvSaldo: TextView = view.findViewById(R.id.tvSaldoModalItem)
        val btnAccion: Button = view.findViewById(R.id.btnAccionFacturaModal)
        val btnEmail: ImageButton = view.findViewById(R.id.btnEmailFacturaModal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_factura_cliente_modal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val saldo = item.saldoPendiente
        val esPendiente = item.estado.equals("PENDIENTE", ignoreCase = true) || saldo > 0

        holder.tvNumFactura.text = item.factura.ifEmpty { "N/A" }
        holder.tvSaldo.text = String.format("$%.2f", saldo)

        val fechaParseada = parsearFecha(item.fechaVencimiento)
        val esVencida = esPendiente && fechaParseada != null && esFechaPasada(fechaParseada)

        holder.tvVencimiento.text = formatearFechaBonita(item.fechaVencimiento, fechaParseada, esVencida)

        if (esVencida) {
            holder.tvVencimiento.setTextColor(Color.parseColor("#DC2626"))
            holder.tvVencimiento.setTypeface(null, Typeface.BOLD)
        } else {
            holder.tvVencimiento.setTextColor(Color.parseColor("#64748B"))
            holder.tvVencimiento.setTypeface(null, Typeface.NORMAL)
        }

        if (esPendiente) {
            holder.tvEstado.text = "PENDIENTE"
            holder.tvEstado.setBackgroundColor(Color.parseColor("#FEF3C7"))
            holder.tvEstado.setTextColor(Color.parseColor("#D97706"))
            holder.tvSaldo.setTextColor(Color.parseColor("#F97316"))
            holder.btnAccion.text = "Abonar"
            holder.btnAccion.setOnClickListener { onAbonarClick(item) }

            holder.btnEmail.visibility = View.VISIBLE
            holder.btnEmail.setOnClickListener { onEmailClick(item) }
        } else {
            holder.tvEstado.text = "PAGADA"
            holder.tvEstado.setBackgroundColor(Color.parseColor("#D1FAE5"))
            holder.tvEstado.setTextColor(Color.parseColor("#059669"))
            holder.tvSaldo.setTextColor(Color.parseColor("#10B981"))
            holder.btnAccion.text = "Historial"
            holder.btnAccion.setOnClickListener { onAbonarClick(item) }

            holder.btnEmail.visibility = View.GONE
        }
    }

    override fun getItemCount() = lista.size

    private fun parsearFecha(fechaRaw: String): Date? {
        if (fechaRaw.isBlank()) return null

        val formatosEntrada = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )

        for (formato in formatosEntrada) {
            try {
                val sdf = SimpleDateFormat(formato, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(fechaRaw)
                if (date != null) return date
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Formatea la fecha a algo legible como "25 nov 2026".
     * Si no se puede parsear, devuelve el texto original o "Sin fecha".
     */
    private fun formatearFechaBonita(fechaRaw: String, fechaParseada: Date?, esVencida: Boolean): String {
        if (fechaRaw.isBlank()) return "Sin fecha"

        val fechaLegible = if (fechaParseada != null) {
            val sdfSalida = SimpleDateFormat("dd MMM yyyy", Locale("es", "ES"))
            sdfSalida.format(fechaParseada).replace(".", "")
        } else {
            fechaRaw
        }

        return if (esVencida) "Vencida · $fechaLegible" else fechaLegible
    }

    private fun esFechaPasada(fecha: Date): Boolean {
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fechaComparar = Calendar.getInstance().apply {
            time = fecha
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return fechaComparar.before(hoy)
    }
}