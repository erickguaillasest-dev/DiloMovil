package com.example.movildilo.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.inventario.KardexMovimientoDto
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import kotlin.math.abs

class KardexAdapter(
    private var listaMovimientos: List<KardexMovimientoDto>
) : RecyclerView.Adapter<KardexAdapter.KardexViewHolder>() {

    class KardexViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val cardTipoBadge: MaterialCardView = v.findViewById(R.id.cardTipoBadge)
        val tvTipoMovimiento: TextView = v.findViewById(R.id.tvTipoMovimiento)
        val tvFechaHora: TextView = v.findViewById(R.id.tvFechaHora)
        val tvProductoNombre: TextView = v.findViewById(R.id.tvProductoNombre)
        val tvLote: TextView = v.findViewById(R.id.tvLote)
        val tvDocReferencia: TextView = v.findViewById(R.id.tvDocReferencia)
        val tvCantidad: TextView = v.findViewById(R.id.tvCantidad)
        val tvCostoUnitario: TextView = v.findViewById(R.id.tvCostoUnitario)
        val tvTotalMovimiento: TextView = v.findViewById(R.id.tvTotalMovimiento)
        val tvUbicacion: TextView = v.findViewById(R.id.tvUbicacion)
        val tvMotivo: TextView = v.findViewById(R.id.tvMotivo)
        val tvUsuario: TextView = v.findViewById(R.id.tvUsuario)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KardexViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kardex_movimiento, parent, false)
        return KardexViewHolder(view)
    }

    override fun onBindViewHolder(holder: KardexViewHolder, position: Int) {
        val item = listaMovimientos[position]

        // 1. OBTENCIÓN Y NORMALIZACIÓN DEL TIPO DE MOVIMIENTO
        val tipoOriginal = (item.tipo ?: "").trim().uppercase(Locale.ROOT)

        val esIngreso = tipoOriginal.contains("INGRESO") || tipoOriginal.contains("ENTRADA") || tipoOriginal.contains("COMPRA")
        val esEgreso = tipoOriginal.contains("EGRESO") || tipoOriginal.contains("SALIDA") || tipoOriginal.contains("VENTA")
        val esTransferencia = tipoOriginal.contains("TRANSFER") || tipoOriginal.contains("TRASP")

        val tipoNormalizado = when {
            esIngreso -> "INGRESO"
            esEgreso -> "EGRESO"
            esTransferencia -> "TRANSFERENCIA"
            else -> tipoOriginal.ifBlank { "INGRESO" }
        }

        holder.tvTipoMovimiento.text = tipoNormalizado

        // 2. COLORES SEGÚN TIPO (IGUAL A LA WEB EN ANGULAR)
        when (tipoNormalizado) {
            "INGRESO" -> {
                holder.cardTipoBadge.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
                holder.tvTipoMovimiento.setTextColor(Color.parseColor("#166534"))
            }
            "EGRESO" -> {
                holder.cardTipoBadge.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                holder.tvTipoMovimiento.setTextColor(Color.parseColor("#991B1B"))
            }
            else -> { // TRANSFERENCIA
                holder.cardTipoBadge.setCardBackgroundColor(Color.parseColor("#E0F2FE"))
                holder.tvTipoMovimiento.setTextColor(Color.parseColor("#075985"))
            }
        }

        // 3. FECHA Y HORA
        holder.tvFechaHora.text = item.fechaTransaccion ?: "Sin fecha"

        // 4. PRODUCTO
        holder.tvProductoNombre.text = item.productoNombre ?: "Producto sin nombre"

        // 5. LOTE
        val lote = item.numeroLote
        if (lote.isNullOrBlank()) {
            holder.tvLote.visibility = View.GONE
        } else {
            holder.tvLote.visibility = View.VISIBLE
            holder.tvLote.text = "Lote: $lote"
        }

        // 6. DOCUMENTO DE REFERENCIA
        val docRef = item.documentoReferencia
        if (docRef.isNullOrBlank()) {
            holder.tvDocReferencia.visibility = View.GONE
        } else {
            holder.tvDocReferencia.visibility = View.VISIBLE
            holder.tvDocReferencia.text = "Doc: $docRef"
        }

        // 7. CANTIDAD, COSTO Y TOTAL
        val cant = item.cantidad ?: 0
        if (tipoNormalizado == "EGRESO") {
            val cantAbs = abs(cant)
            holder.tvCantidad.text = "-$cantAbs"
            holder.tvCantidad.setTextColor(Color.parseColor("#DC2626"))
        } else {
            holder.tvCantidad.text = "+$cant"
            holder.tvCantidad.setTextColor(Color.parseColor("#166534"))
        }

        val costo = item.costoUnitario ?: 0.0
        val totalMvto = item.totalMovimiento ?: (cant * costo)

        holder.tvCostoUnitario.text = String.format(Locale.US, "$%.2f", costo)
        holder.tvTotalMovimiento.text = String.format(Locale.US, "$%.2f", abs(totalMvto))

        // 8. UBICACIÓN (BODEGA ORIGEN Y DESTINO)
        val origen = item.bodegaOrigenNombre?.trim() ?: "Sin origen"
        val destino = item.bodegaDestinoNombre?.trim() ?: "Sin destino"

        val ubicacionTexto = when (tipoNormalizado) {
            "TRANSFERENCIA" -> "De: $origen ➔ A: $destino"
            "EGRESO" -> "Desde: $origen"
            else -> "Hacia: $destino"
        }
        holder.tvUbicacion.text = ubicacionTexto

        // 9. MOTIVO Y USUARIO RESPONSABLE
        val resp = item.usuarioResponsableNombre ?: "Sistema"
        holder.tvMotivo.text = "Motivo: ${item.motivo ?: "Sin especificar"}"
        holder.tvUsuario.text = "Resp: $resp"
    }

    override fun getItemCount(): Int = listaMovimientos.size

    fun actualizarLista(nuevaLista: List<KardexMovimientoDto>) {
        listaMovimientos = nuevaLista
        notifyDataSetChanged()
    }
}