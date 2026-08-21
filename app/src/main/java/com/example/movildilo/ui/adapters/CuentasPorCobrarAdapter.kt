package com.example.movildilo.ui.propietario

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.CuentaPorCobrarResponseDto
import com.example.movildilo.data.model.dto.CuotaDto
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class CuentasPorCobrarAdapter(
    private var listaCuentas: List<CuentaPorCobrarResponseDto>,
    private val onAbonarClick: (CuentaPorCobrarResponseDto) -> Unit,
    private val onAbonarCuotaClick: (CuentaPorCobrarResponseDto, CuotaDto) -> Unit
) : RecyclerView.Adapter<CuentasPorCobrarAdapter.CuentaViewHolder>() {

    class CuentaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumeroFactura: TextView = view.findViewById(R.id.tvNumeroFactura)
        val tvClienteNombre: TextView = view.findViewById(R.id.tvClienteNombre)
        val tvVencimientoGlobal: TextView = view.findViewById(R.id.tvVencimientoGlobal)
        val tvMontoTotal: TextView = view.findViewById(R.id.tvMontoTotal)
        val tvSaldoPendiente: TextView = view.findViewById(R.id.tvSaldoPendiente)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val btnToggleCuotas: MaterialButton = view.findViewById(R.id.btnToggleCuotas)
        val btnAccionAbonar: MaterialButton = view.findViewById(R.id.btnAccionAbonar)
        val containerCuotas: LinearLayout = view.findViewById(R.id.containerCuotas)
        val layoutListaCuotas: LinearLayout = view.findViewById(R.id.layoutListaCuotas)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CuentaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cuenta_por_cobrar, parent, false)
        return CuentaViewHolder(view)
    }

    override fun onBindViewHolder(holder: CuentaViewHolder, position: Int) {
        val cuenta = listaCuentas[position]

        val numero = cuenta.numeroFactura ?: cuenta.factura?.numeroFactura ?: "S/N"
        val cliente = obtenerNombreCliente(cuenta)

        holder.tvNumeroFactura.text = numero
        holder.tvClienteNombre.text = cliente
        holder.tvVencimientoGlobal.text = "Vence: ${formatearFecha(cuenta.fechaVencimiento)}"

        val montoTotal = cuenta.montoTotal ?: 0.0
        val saldoPendiente = cuenta.saldoPendiente ?: 0.0

        holder.tvMontoTotal.text = String.format(Locale.US, "$%.2f", montoTotal)
        holder.tvSaldoPendiente.text = String.format(Locale.US, "$%.2f", saldoPendiente)

        val estado = cuenta.estado ?: if (saldoPendiente <= 0) "PAGADA" else "PENDIENTE"
        holder.tvEstado.text = estado.uppercase(Locale.ROOT)
        configurarBadgeEstado(holder.tvEstado, estado)

        if (saldoPendiente <= 0) {
            holder.btnAccionAbonar.text = "Completado"
            holder.btnAccionAbonar.isEnabled = false
            holder.btnAccionAbonar.setBackgroundColor(Color.parseColor("#E2E8F0"))
            holder.btnAccionAbonar.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            holder.btnAccionAbonar.text = "Abonar"
            holder.btnAccionAbonar.isEnabled = true
            holder.btnAccionAbonar.setBackgroundColor(Color.parseColor("#ED8936"))
            holder.btnAccionAbonar.setTextColor(Color.WHITE)
            holder.btnAccionAbonar.setOnClickListener { onAbonarClick(cuenta) }
        }

        val cuotas = cuenta.cuotas ?: emptyList()
        if (cuotas.isNotEmpty()) {
            holder.btnToggleCuotas.visibility = View.VISIBLE
            holder.btnToggleCuotas.text = if (cuenta.isExpanded) "Ocultar" else "Ver (${cuotas.size})"

            holder.btnToggleCuotas.setOnClickListener {
                cuenta.isExpanded = !cuenta.isExpanded
                notifyItemChanged(position)
            }

            if (cuenta.isExpanded) {
                holder.containerCuotas.visibility = View.VISIBLE
                renderizarCuotas(holder.layoutListaCuotas, cuenta, cuotas)
            } else {
                holder.containerCuotas.visibility = View.GONE
            }
        } else {
            holder.btnToggleCuotas.visibility = View.GONE
            holder.containerCuotas.visibility = View.GONE
        }
    }

    private fun obtenerNombreCliente(cuenta: CuentaPorCobrarResponseDto): String {
        val directo = cuenta.clienteNombre?.trim()
        if (!directo.isNullOrEmpty()) return directo

        val c = cuenta.factura?.cliente
        val nombre = c?.nombreCompleto?.trim()?.takeIf { it.isNotEmpty() }
            ?: c?.nombre?.trim()?.takeIf { it.isNotEmpty() }
            ?: c?.razonSocial?.trim()?.takeIf { it.isNotEmpty() }
            ?: c?.let { "${it.primerNombre ?: ""} ${it.apellidoPaterno ?: ""}".trim() }?.takeIf { it.isNotEmpty() }

        return nombre ?: "Consumidor Final"
    }

    private fun renderizarCuotas(parent: LinearLayout, cuenta: CuentaPorCobrarResponseDto, cuotas: List<CuotaDto>) {
        parent.removeAllViews()
        val inflater = LayoutInflater.from(parent.context)

        cuotas.forEach { cuota ->
            val view = inflater.inflate(R.layout.item_cuota, parent, false)
            val tvNum = view.findViewById<TextView>(R.id.tvNumeroCuota)
            val tvVenc = view.findViewById<TextView>(R.id.tvVencimientoCuota)
            val tvMonto = view.findViewById<TextView>(R.id.tvMontoCuota)
            val tvSaldo = view.findViewById<TextView>(R.id.tvSaldoCuota)
            val tvEstadoCuota = view.findViewById<TextView>(R.id.tvEstadoCuota)
            val btnAbonar = view.findViewById<MaterialButton>(R.id.btnAbonar)

            tvNum.text = "Cuota #${cuota.numeroCuota ?: 1}"
            tvVenc.text = formatearFecha(cuota.fechaVencimiento)
            tvMonto.text = String.format(Locale.US, "$%.2f", cuota.montoCuota ?: 0.0)

            val saldoCuota = cuota.saldoPendienteCuota ?: 0.0
            tvSaldo.text = "Pend: " + String.format(Locale.US, "$%.2f", saldoCuota)

            val st = cuota.estado ?: if (saldoCuota <= 0) "PAGADA" else "PENDIENTE"
            tvEstadoCuota.text = st.uppercase(Locale.ROOT)
            configurarBadgeEstado(tvEstadoCuota, st)

            if (st.equals("PAGADA", ignoreCase = true) || saldoCuota <= 0) {
                btnAbonar.visibility = View.GONE
            } else {
                btnAbonar.visibility = View.VISIBLE
                btnAbonar.setOnClickListener {
                    onAbonarCuotaClick(cuenta, cuota)
                }
            }

            parent.addView(view)
        }
    }

    private fun formatearFecha(fechaRaw: String?): String {
        if (fechaRaw.isNullOrBlank()) return "N/A"

        val formatosEntrada = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy"
        )
        val formatoSalida = SimpleDateFormat("dd 'de' MMM, yyyy", Locale("es", "ES"))

        for (formato in formatosEntrada) {
            try {
                val date = SimpleDateFormat(formato, Locale.getDefault()).parse(fechaRaw)
                if (date != null) {
                    return formatoSalida.format(date)
                }
            } catch (_: Exception) {
            }
        }
        return fechaRaw
    }

    private fun configurarBadgeEstado(textView: TextView, estado: String) {
        val bgDrawable = GradientDrawable()
        bgDrawable.cornerRadius = 10f

        when (estado.uppercase(Locale.ROOT)) {
            "PAGADA" -> {
                textView.setTextColor(Color.parseColor("#047857"))
                bgDrawable.setColor(Color.parseColor("#D1FAE5"))
                bgDrawable.setStroke(1, Color.parseColor("#A7F3D0"))
            }
            "VENCIDA" -> {
                textView.setTextColor(Color.parseColor("#B91C1C"))
                bgDrawable.setColor(Color.parseColor("#FEE2E2"))
                bgDrawable.setStroke(1, Color.parseColor("#FECACA"))
            }
            else -> {
                textView.setTextColor(Color.parseColor("#B45309"))
                bgDrawable.setColor(Color.parseColor("#FEF3C7"))
                bgDrawable.setStroke(1, Color.parseColor("#FDE68A"))
            }
        }
        textView.background = bgDrawable
    }

    override fun getItemCount(): Int = listaCuentas.size

    fun actualizarLista(nuevaLista: List<CuentaPorCobrarResponseDto>) {
        listaCuentas = nuevaLista
        notifyDataSetChanged()
    }
}