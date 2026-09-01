package com.example.movildilo.ui.facturas

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object DetalleFacturaDialogHelper {

    data class ItemLinea(
        val nombre: String,
        val cantidad: Int,
        val precioUnitario: Double,
        val descuento: Double = 0.0,
        val subtotal: Double,
        val grabaIva: Boolean = true
    )

    data class DatosFactura(
        val numero: String,
        val fecha: String,
        val clienteNombre: String,
        val metodoPago: String,
        val estado: String,
        val total: Double,
        val descuentoGlobal: Double = 0.0,
        val porcentajeIva: Double = 15.0,
        val items: List<ItemLinea> = emptyList(),
        val mostrarBotonImprimir: Boolean = false,
        val onImprimir: (() -> Unit)? = null,
        val clienteIdentificacion: String? = null
    )

    fun mostrar(context: Context, datos: DatosFactura) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_detalle_factura, null)

        val tvNumero = view.findViewById<TextView>(R.id.tvNumeroFacturaDetalle)
        val tvFecha = view.findViewById<TextView>(R.id.tvFechaFacturaDetalle)
        val tvCliente = view.findViewById<TextView>(R.id.tvClienteNombreDetalle)
        val tvClienteIdentificacion = view.findViewById<TextView>(R.id.tvClienteIdentificacionDetalle)
        val tvMetodoPago = view.findViewById<TextView>(R.id.tvMetodoPagoDetalle)
        val tvEstadoBadge = view.findViewById<TextView>(R.id.tvEstadoBadge)
        val containerProductos = view.findViewById<LinearLayout>(R.id.containerProductosDetalle)

        // Nuevas referencias para desglose
        val tvLabelSubtotal15 = view.findViewById<TextView>(R.id.tvLabelSubtotal15)
        val tvSubtotal15 = view.findViewById<TextView>(R.id.tvSubtotal15Detalle)
        val tvSubtotal0 = view.findViewById<TextView>(R.id.tvSubtotal0Detalle)
        val tvSubtotalBase = view.findViewById<TextView>(R.id.tvSubtotalBaseDetalle)

        val tvLabelIva = view.findViewById<TextView>(R.id.tvLabelIva)
        val tvIva = view.findViewById<TextView>(R.id.tvIvaDetalle)

        val rowDescuento = view.findViewById<View>(R.id.rowDescuentoDetalle)
        val tvDescuento = view.findViewById<TextView>(R.id.tvDescuentoDetalle)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotalDetalle)

        val btnCerrarIcon = view.findViewById<ImageView>(R.id.btnCerrarDetalleModal)
        val btnCerrar = view.findViewById<MaterialButton>(R.id.btnCerrarDetalle)
        val btnImprimir = view.findViewById<MaterialButton>(R.id.btnImprimirPdfDetalle)

        tvNumero.text = "Nº ${datos.numero.ifBlank { "S/N" }}"
        tvFecha.text = datos.fecha
        tvCliente.text = datos.clienteNombre
        if (!datos.clienteIdentificacion.isNullOrBlank()) {
            tvClienteIdentificacion.text = "CI/RUC: ${datos.clienteIdentificacion}"
            tvClienteIdentificacion.visibility = View.VISIBLE
        } else {
            tvClienteIdentificacion.visibility = View.GONE
        }
        tvMetodoPago.text = datos.metodoPago
        tvEstadoBadge.text = datos.estado.uppercase(Locale.US)

        // Setear etiquetas dinámicas para el IVA actual
        val ivaStr = datos.porcentajeIva.toInt().toString()
        tvLabelSubtotal15.text = "Subtotal $ivaStr%"
        tvLabelIva.text = "IVA ($ivaStr%)"

        // Lógica de desglose SRI (Ecuador)
        var base15 = 0.0
        var base0 = 0.0
        var descuentoTotal = datos.descuentoGlobal

        if (datos.items.isNotEmpty()) {
            datos.items.forEach { item ->
                // Se asume que item.subtotal es la base ya afectada por descuento
                if (item.grabaIva) {
                    base15 += item.subtotal
                } else {
                    base0 += item.subtotal
                }
                descuentoTotal += item.descuento
            }
        } else {
            // Fallback si la factura viene sin detalles
            val tasaIva = datos.porcentajeIva / 100.0
            base15 = datos.total / (1.0 + tasaIva)
        }

        val subtotalNeto = base15 + base0
        val montoIvaCalculado = base15 * (datos.porcentajeIva / 100.0)

        // Asignación de valores
        tvSubtotal15.text = String.format(Locale.US, "$%.2f", base15)
        tvSubtotal0.text = String.format(Locale.US, "$%.2f", base0)
        tvSubtotalBase.text = String.format(Locale.US, "$%.2f", subtotalNeto)
        tvIva.text = String.format(Locale.US, "$%.2f", montoIvaCalculado)

        // Usamos datos.total para garantizar coincidencia de céntimos con el servidor
        tvTotal.text = String.format(Locale.US, "$%.2f", datos.total)

        if (descuentoTotal > 0.0) {
            rowDescuento.visibility = View.VISIBLE
            tvDescuento.text = "-${String.format(Locale.US, "$%.2f", descuentoTotal)}"
        } else {
            rowDescuento.visibility = View.GONE
        }

        containerProductos.removeAllViews()

        if (datos.items.isNotEmpty()) {
            datos.items.forEachIndexed { index, item ->
                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 12, 0, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvNombre = TextView(context).apply {
                    text = item.nombre
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF0F172A.toInt())
                }

                val tvDetallePrecio = TextView(context).apply {
                    // Marcador visual pequeño si grava o no grava
                    val ivaMarker = if (item.grabaIva) "" else " (*)"
                    text = "${item.cantidad} unit. x $${String.format(Locale.US, "%.2f", item.precioUnitario)}$ivaMarker"
                    textSize = 11f
                    setTextColor(0xFF64748B.toInt())
                }

                infoLayout.addView(tvNombre)
                infoLayout.addView(tvDetallePrecio)

                if (item.descuento > 0.0) {
                    val tvDescuentoItem = TextView(context).apply {
                        text = "Desc: -$${String.format(Locale.US, "%.2f", item.descuento)}"
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(0xFFEA580C.toInt())
                    }
                    infoLayout.addView(tvDescuentoItem)
                }

                val tvSubtotalItem = TextView(context).apply {
                    text = "$${String.format(Locale.US, "%.2f", item.subtotal)}"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF0F172A.toInt())
                }

                itemLayout.addView(infoLayout)
                itemLayout.addView(tvSubtotalItem)
                containerProductos.addView(itemLayout)

                if (index < datos.items.size - 1) {
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        setBackgroundColor(0xFFE2E8F0.toInt())
                    }
                    containerProductos.addView(divider)
                }
            }
        } else {
            val tvVacio = TextView(context).apply {
                text = "Sin detalles registrados"
                textSize = 13f
                setTextColor(0xFF64748B.toInt())
                setPadding(0, 12, 0, 12)
            }
            containerProductos.addView(tvVacio)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrarIcon.setOnClickListener { dialog.dismiss() }
        btnCerrar.setOnClickListener { dialog.dismiss() }

        if (datos.mostrarBotonImprimir && datos.onImprimir != null) {
            btnImprimir.visibility = View.VISIBLE
            btnImprimir.setOnClickListener {
                dialog.dismiss()
                datos.onImprimir.invoke()
            }
        } else {
            btnImprimir.visibility = View.GONE
        }

        dialog.show()
    }
}