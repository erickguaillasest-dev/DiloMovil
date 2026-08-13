package com.example.movildilo.ui.Kardex

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.data.model.dto.NuevoAjusteRequestDto
import com.example.movildilo.data.model.dto.ProductoDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

class AjusteManualDialog(
    private val listaProductosBD: List<ProductoDto>,
    private val listaBodegasBD: List<BodegaDto>,
    private val inventarioTotal: List<InventarioResponseDto> = emptyList(),
    private val onAjusteRegistradoListener: (NuevoAjusteRequestDto) -> Unit
) : DialogFragment() {

    private lateinit var spinnerTipoMovimiento: AutoCompleteTextView
    private lateinit var spinnerProducto: AutoCompleteTextView
    private lateinit var tilBodega: TextInputLayout
    private lateinit var spinnerBodega: AutoCompleteTextView
    private lateinit var tilBodegaDestino: TextInputLayout
    private lateinit var spinnerBodegaDestino: AutoCompleteTextView
    private lateinit var tvStockDisponible: TextView
    private lateinit var etCantidadAjuste: EditText
    private lateinit var etCostoUnitarioAjuste: EditText
    private lateinit var etMotivoAjuste: EditText
    private lateinit var etDocReferenciaAjuste: EditText
    private lateinit var btnRegistrarAjuste: MaterialButton
    private lateinit var btnCancelarAjuste: MaterialButton
    private lateinit var btnCerrarModal: ImageButton

    private var productoSeleccionadoId: Long? = null
    private var bodegaOrigenSeleccionadaId: Long? = null
    private var bodegaDestinoSeleccionadaId: Long? = null

    private var bodegasOrigenDisponibles: List<BodegaDto> = emptyList()
    private var maxCantidad: Int? = null

    private val tipos = listOf(
        "INGRESO (Ajuste Positivo / Sobrante)",
        "EGRESO (Ajuste Negativo / Merma)",
        "TRANSFERENCIA (Entre Bodegas)"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_nuevo_ajuste_manual, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupDropdowns()
        onTipoChange()

        btnCerrarModal.setOnClickListener { dismiss() }
        btnCancelarAjuste.setOnClickListener { dismiss() }

        btnRegistrarAjuste.setOnClickListener {
            validarYEnviar()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun initViews(v: View) {
        spinnerTipoMovimiento = v.findViewById(R.id.spinnerTipoMovimiento)
        spinnerProducto = v.findViewById(R.id.spinnerProducto)
        tilBodega = v.findViewById(R.id.tilBodega)
        spinnerBodega = v.findViewById(R.id.spinnerBodega)
        tilBodegaDestino = v.findViewById(R.id.tilBodegaDestino)
        spinnerBodegaDestino = v.findViewById(R.id.spinnerBodegaDestino)
        tvStockDisponible = v.findViewById(R.id.tvStockDisponible)
        etCantidadAjuste = v.findViewById(R.id.etCantidadAjuste)
        etCostoUnitarioAjuste = v.findViewById(R.id.etCostoUnitarioAjuste)
        etMotivoAjuste = v.findViewById(R.id.etMotivoAjuste)
        etDocReferenciaAjuste = v.findViewById(R.id.etDocReferenciaAjuste)
        btnRegistrarAjuste = v.findViewById(R.id.btnRegistrarAjuste)
        btnCancelarAjuste = v.findViewById(R.id.btnCancelarAjuste)
        btnCerrarModal = v.findViewById(R.id.btnCerrarModal)
    }

    private fun setupDropdowns() {
        val adapterTipos =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        spinnerTipoMovimiento.setAdapter(adapterTipos)
        spinnerTipoMovimiento.setText(tipos[0], false)

        spinnerTipoMovimiento.setOnClickListener {
            (spinnerTipoMovimiento.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }
        spinnerTipoMovimiento.setOnItemClickListener { _, _, _, _ ->
            onTipoChange()
        }

        val nombresProductos = listaProductosBD.mapNotNull { it.nombre?.trim() }.filter { it.isNotBlank() }
        val adapterProductos = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            nombresProductos
        )
        spinnerProducto.setAdapter(adapterProductos)

        spinnerProducto.setOnClickListener {
            (spinnerProducto.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

        spinnerProducto.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val productoEncontrado = listaProductosBD.find { it.nombre?.trim() == nombreSeleccionado }
            productoSeleccionadoId = productoEncontrado?.id
            onProductoChange()
        }

        poblarSpinnerBodegaOrigen(listaBodegasBD)

        spinnerBodega.setOnClickListener {
            (spinnerBodega.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }
        spinnerBodega.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val bodegaEncontrada = bodegasOrigenDisponibles.find { it.nombre.trim() == nombreSeleccionado }
                ?: listaBodegasBD.find { it.nombre.trim() == nombreSeleccionado }
            bodegaOrigenSeleccionadaId = bodegaEncontrada?.id
            actualizarMaxCantidad()
        }

        val nombresBodegas = listaBodegasBD.mapNotNull { it.nombre.trim() }.filter { it.isNotBlank() }
        val adapterBodegasDestino = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            nombresBodegas
        )
        spinnerBodegaDestino.setAdapter(adapterBodegasDestino)

        spinnerBodegaDestino.setOnClickListener {
            (spinnerBodegaDestino.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }
        spinnerBodegaDestino.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val bodegaEncontrada = listaBodegasBD.find { it.nombre.trim() == nombreSeleccionado }
            bodegaDestinoSeleccionadaId = bodegaEncontrada?.id
        }
    }

    private fun poblarSpinnerBodegaOrigen(bodegas: List<BodegaDto>) {
        val nombres = bodegas.mapNotNull { it.nombre.trim() }.filter { it.isNotBlank() }
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres)
        spinnerBodega.setAdapter(adapter)
    }

    private fun tipoClaveActual(): String {
        val texto = spinnerTipoMovimiento.text.toString()
        return when {
            texto.startsWith("INGRESO") -> "INGRESO"
            texto.startsWith("TRANSFERENCIA") -> "TRANSFERENCIA"
            else -> "EGRESO"
        }
    }

    private fun onTipoChange() {
        bodegaOrigenSeleccionadaId = null
        bodegaDestinoSeleccionadaId = null
        spinnerBodega.setText("", false)
        spinnerBodegaDestino.setText("", false)
        etCantidadAjuste.setText("")
        etCostoUnitarioAjuste.setText("")
        etDocReferenciaAjuste.setText("")
        maxCantidad = null
        tvStockDisponible.visibility = View.GONE

        when (tipoClaveActual()) {
            "INGRESO" -> {
                tilBodega.hint = "Bodega Destino *"
                tilBodegaDestino.visibility = View.GONE
                poblarSpinnerBodegaOrigen(listaBodegasBD)
                bodegasOrigenDisponibles = listaBodegasBD
            }
            "EGRESO" -> {
                tilBodega.hint = "Bodega Origen *"
                tilBodegaDestino.visibility = View.GONE
                evaluarDisponibilidad()
            }
            "TRANSFERENCIA" -> {
                tilBodega.hint = "Bodega Origen *"
                tilBodegaDestino.visibility = View.VISIBLE
                evaluarDisponibilidad()
            }
        }
    }

    private fun onProductoChange() {
        bodegaOrigenSeleccionadaId = null
        spinnerBodega.setText("", false)
        etCantidadAjuste.setText("")
        maxCantidad = null
        evaluarDisponibilidad()
    }

    private fun evaluarDisponibilidad() {
        val tipo = tipoClaveActual()
        if (tipo == "INGRESO") {
            bodegasOrigenDisponibles = listaBodegasBD
            poblarSpinnerBodegaOrigen(bodegasOrigenDisponibles)
            return
        }

        val prodId = productoSeleccionadoId
        if (prodId == null) {
            bodegasOrigenDisponibles = listaBodegasBD
            poblarSpinnerBodegaOrigen(bodegasOrigenDisponibles)
            return
        }

        val idsBodegasConStock = inventarioTotal
            .filter { it.productoId == prodId && (it.cantidadActual ?: 0) > 0 }
            .mapNotNull { it.bodegaId }
            .toSet()

        bodegasOrigenDisponibles = listaBodegasBD.filter { it.id in idsBodegasConStock }
        poblarSpinnerBodegaOrigen(bodegasOrigenDisponibles)

        when {
            bodegasOrigenDisponibles.size == 1 -> {
                val unica = bodegasOrigenDisponibles[0]
                bodegaOrigenSeleccionadaId = unica.id
                spinnerBodega.setText(unica.nombre, false)
                actualizarMaxCantidad()
            }
            bodegasOrigenDisponibles.isEmpty() -> {
                maxCantidad = 0
                tvStockDisponible.text = "⚠️ Este producto no tiene stock en ninguna bodega."
                tvStockDisponible.visibility = View.VISIBLE
            }
            else -> {
                maxCantidad = null
            }
        }
    }

    private fun actualizarMaxCantidad() {
        val tipo = tipoClaveActual()
        if (tipo == "INGRESO") {
            maxCantidad = null
            tvStockDisponible.visibility = View.GONE
            return
        }

        val prodId = productoSeleccionadoId
        val bodId = bodegaOrigenSeleccionadaId
        if (prodId != null && bodId != null) {
            val inv = inventarioTotal.find { it.productoId == prodId && it.bodegaId == bodId }
            maxCantidad = inv?.cantidadActual ?: 0
            tvStockDisponible.text = "Stock disponible en esta bodega: ${maxCantidad ?: 0} uds."
            tvStockDisponible.visibility = View.VISIBLE
            validarCantidadContraMaximo()
        } else {
            maxCantidad = null
            tvStockDisponible.visibility = View.GONE
        }
    }

    /** Si la cantidad ingresada supera el máximo disponible, la recorta (igual que en la web). */
    private fun validarCantidadContraMaximo() {
        val max = maxCantidad ?: return
        val actual = etCantidadAjuste.text.toString().toIntOrNull() ?: return
        if (actual > max) {
            etCantidadAjuste.setText(max.toString())
            etCantidadAjuste.setSelection(etCantidadAjuste.text?.length ?: 0)
        }
    }

    private fun validarYEnviar() {
        val tipoClave = tipoClaveActual()

        val cantidadTexto = etCantidadAjuste.text.toString().trim()
        val costoTexto = etCostoUnitarioAjuste.text.toString().trim()
        val motivo = etMotivoAjuste.text.toString().trim()
        val doc = etDocReferenciaAjuste.text.toString().trim()

        if (productoSeleccionadoId == null) {
            FormValidator.marcarErrorEditText(spinnerProducto, "Selecciona un producto válido de la lista sugerida.")
            spinnerProducto.requestFocus()
            return
        }

        if (bodegaOrigenSeleccionadaId == null) {
            val mensaje = if (tipoClave == "INGRESO")
                "Selecciona la bodega destino de la lista sugerida."
            else
                "Selecciona la bodega origen de la lista sugerida."
            FormValidator.marcarErrorEditText(spinnerBodega, mensaje)
            spinnerBodega.requestFocus()
            return
        }

        if (tipoClave == "TRANSFERENCIA") {
            if (bodegaDestinoSeleccionadaId == null) {
                FormValidator.marcarErrorEditText(spinnerBodegaDestino, "Selecciona la bodega destino de la lista sugerida.")
                spinnerBodegaDestino.requestFocus()
                return
            }
            if (bodegaOrigenSeleccionadaId == bodegaDestinoSeleccionadaId) {
                FormValidator.marcarErrorEditText(spinnerBodegaDestino, "No puedes transferir a la misma bodega.")
                spinnerBodegaDestino.requestFocus()
                return
            }
        }

        val errorCantidad = FormValidator.numeroEntero(cantidadTexto, "La cantidad del ajuste", minimo = 1)
        if (errorCantidad != null) {
            FormValidator.marcarErrorEditText(etCantidadAjuste, errorCantidad)
            etCantidadAjuste.requestFocus()
            return
        }
        var cantidad = cantidadTexto.toInt()

        if (tipoClave != "INGRESO" && maxCantidad != null) {
            if (maxCantidad == 0) {
                FormValidator.marcarErrorEditText(etCantidadAjuste, "No hay existencias de este producto en la bodega seleccionada.")
                etCantidadAjuste.requestFocus()
                return
            }
            if (cantidad > maxCantidad!!) {
                cantidad = maxCantidad!!
                etCantidadAjuste.setText(cantidad.toString())
            }
        }

        val errorCosto = FormValidator.numeroDecimal(costoTexto, "El costo unitario", minimo = 0.0, obligatorio = false)
        if (errorCosto != null) {
            FormValidator.marcarErrorEditText(etCostoUnitarioAjuste, errorCosto)
            etCostoUnitarioAjuste.requestFocus()
            return
        }
        val costo = costoTexto.toDoubleOrNull()

        val errorMotivo = FormValidator.requerido(motivo, "El motivo del ajuste")
            ?: FormValidator.longitudMinima(motivo, 4, "El motivo del ajuste")
            ?: FormValidator.longitudMaxima(motivo, 200, "El motivo del ajuste")
        if (errorMotivo != null) {
            FormValidator.marcarErrorEditText(etMotivoAjuste, errorMotivo)
            etMotivoAjuste.requestFocus()
            return
        }

        val request = NuevoAjusteRequestDto(
            tipo = tipoClave,
            productoId = productoSeleccionadoId!!,
            bodegaOrigenId = if (tipoClave != "INGRESO") bodegaOrigenSeleccionadaId else null,
            bodegaDestinoId = when (tipoClave) {
                "INGRESO" -> bodegaOrigenSeleccionadaId
                "TRANSFERENCIA" -> bodegaDestinoSeleccionadaId
                else -> null
            },
            cantidad = cantidad,
            costoUnitario = costo,
            motivo = motivo,
            documentoReferencia = if (doc.isEmpty()) null else doc
        )

        onAjusteRegistradoListener(request)
        dismiss()
    }
}