package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.DocumentoUiModel
import java.lang.String.format
import java.util.Locale

class DetalleDocumentoAdapter(private var lista: List<DocumentoUiModel>) :
    RecyclerView.Adapter<DetalleDocumentoAdapter.ViewHolder>() {

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
        val ivDocChevron: ImageView = view.findViewById(R.id.ivDocChevron)

        val containerDetalleFactura: View = view.findViewById(R.id.containerDetalleFactura)
        val containerProductosDoc: LinearLayout = view.findViewById(R.id.containerProductosDoc)
        val tvDocSubtotal: TextView = view.findViewById(R.id.tvDocSubtotal)
        val rowDocDescuento: View = view.findViewById(R.id.rowDocDescuento)
        val tvDocDescuento: TextView = view.findViewById(R.id.tvDocDescuento)
        val tvDocIva: TextView = view.findViewById(R.id.tvDocIva)
        val tvDocTotal: TextView = view.findViewById(R.id.tvDocTotal)

        val containerDetalleCredito: View = view.findViewById(R.id.containerDetalleCredito)
        val tvCreditoMontoTotal: TextView = view.findViewById(R.id.tvCreditoMontoTotal)
        val tvCreditoSaldoPendiente: TextView = view.findViewById(R.id.tvCreditoSaldoPendiente)
        val barCreditoProgreso: View = view.findViewById(R.id.barCreditoProgreso)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_documento_cliente, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val doc = lista[position]
        val ctx = holder.itemView.context

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

        // Contenido del detalle expandible según el tipo de documento
        if (doc.isCredito) {
            holder.tvCreditoMontoTotal.text = format(Locale.US, "$%,.2f", doc.monto)
            holder.tvCreditoSaldoPendiente.text = format(Locale.US, "$%,.2f", doc.saldoPendiente)
            holder.barCreditoProgreso.post {
                val padre = holder.barCreditoProgreso.parent as? FrameLayout ?: return@post
                val anchoTotal = padre.width
                val fraccionPendiente = if (doc.monto > 0) (doc.saldoPendiente / doc.monto).coerceIn(0.0, 1.0) else 0.0
                val params = holder.barCreditoProgreso.layoutParams
                params.width = (anchoTotal * fraccionPendiente).toInt().coerceAtLeast(0)
                holder.barCreditoProgreso.layoutParams = params
            }
        } else {
            holder.containerProductosDoc.removeAllViews()
            if (doc.detalles.isNotEmpty()) {
                doc.detalles.forEachIndexed { index, item ->
                    val fila = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 10, 0, 10)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val info = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val tvNombre = TextView(ctx).apply {
                        text = item.productoNombre
                        textSize = 12.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#0F172A"))
                    }
                    val tvCantPrecio = TextView(ctx).apply {
                        text = "${item.cantidad} unit. x ${format(Locale.US, "$%.2f", item.precioUnitario)}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#64748B"))
                    }
                    info.addView(tvNombre)
                    info.addView(tvCantPrecio)
                    if (item.descuento > 0.0) {
                        val tvDescItem = TextView(ctx).apply {
                            text = "Descuento: -${format(Locale.US, "$%.2f", item.descuento)}"
                            textSize = 10f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.parseColor("#EA580C"))
                        }
                        info.addView(tvDescItem)
                    }
                    val tvSubItem = TextView(ctx).apply {
                        text = format(Locale.US, "$%.2f", item.subtotalItem)
                        textSize = 12.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.parseColor("#0F172A"))
                    }
                    fila.addView(info)
                    fila.addView(tvSubItem)
                    holder.containerProductosDoc.addView(fila)

                    if (index < doc.detalles.size - 1) {
                        val divisor = View(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                            setBackgroundColor(Color.parseColor("#E2E8F0"))
                        }
                        holder.containerProductosDoc.addView(divisor)
                    }
                }
            } else {
                holder.containerProductosDoc.addView(TextView(ctx).apply {
                    text = "Sin detalle de productos registrado"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, 8, 0, 8)
                })
            }

            val total = doc.monto
            val subtotal = total / 1.15
            val iva = total - subtotal
            holder.tvDocSubtotal.text = format(Locale.US, "$%.2f", subtotal)
            holder.tvDocIva.text = format(Locale.US, "$%.2f", iva)
            holder.tvDocTotal.text = format(Locale.US, "$%.2f", total)

            if (doc.descuentoGlobal > 0.0) {
                holder.rowDocDescuento.visibility = View.VISIBLE
                holder.tvDocDescuento.text = "-${format(Locale.US, "$%.2f", doc.descuentoGlobal)}"
            } else {
                holder.rowDocDescuento.visibility = View.GONE
            }
        }

        aplicarEstadoExpandido(holder, doc)

        holder.rowDocHeader.setOnClickListener {
            doc.expandido = !doc.expandido
            aplicarEstadoExpandido(holder, doc)
        }
    }

    private fun aplicarEstadoExpandido(holder: ViewHolder, doc: DocumentoUiModel) {
        val expandido = doc.expandido
        holder.containerDetalleFactura.visibility = if (!doc.isCredito && expandido) View.VISIBLE else View.GONE
        holder.containerDetalleCredito.visibility = if (doc.isCredito && expandido) View.VISIBLE else View.GONE
        holder.ivDocChevron.rotation = if (expandido) 180f else 0f
    }

    override fun getItemCount(): Int = lista.size
}
