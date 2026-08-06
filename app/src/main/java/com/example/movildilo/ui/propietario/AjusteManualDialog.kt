package com.example.movildilo.ui.propietario

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
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.data.model.dto.NuevoAjusteRequestDto
import com.example.movildilo.data.model.dto.ProductoDto
import com.google.android.material.button.MaterialButton

class AjusteManualDialog(
    private val listaProductosBD: List<ProductoDto>,
    private val listaBodegasBD: List<BodegaDto>,
    private val onAjusteRegistradoListener: (NuevoAjusteRequestDto) -> Unit
) : DialogFragment() {

    private lateinit var spinnerTipoMovimiento: AutoCompleteTextView
    private lateinit var spinnerProducto: AutoCompleteTextView
    private lateinit var spinnerBodega: AutoCompleteTextView
    private lateinit var etCantidadAjuste: EditText
    private lateinit var etCostoUnitarioAjuste: EditText
    private lateinit var etMotivoAjuste: EditText
    private lateinit var etDocReferenciaAjuste: EditText
    private lateinit var btnRegistrarAjuste: MaterialButton
    private lateinit var btnCancelarAjuste: MaterialButton
    private lateinit var btnCerrarModal: ImageButton

    private var productoSeleccionadoId: Long? = null
    private var bodegaSeleccionadaId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_nuevo_ajuste_manual, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupDropdowns()

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
        spinnerBodega = v.findViewById(R.id.spinnerBodega)
        etCantidadAjuste = v.findViewById(R.id.etCantidadAjuste)
        etCostoUnitarioAjuste = v.findViewById(R.id.etCostoUnitarioAjuste)
        etMotivoAjuste = v.findViewById(R.id.etMotivoAjuste)
        etDocReferenciaAjuste = v.findViewById(R.id.etDocReferenciaAjuste)
        btnRegistrarAjuste = v.findViewById(R.id.btnRegistrarAjuste)
        btnCancelarAjuste = v.findViewById(R.id.btnCancelarAjuste)
        btnCerrarModal = v.findViewById(R.id.btnCerrarModal)
    }

    private fun setupDropdowns() {
        val tipos = listOf("INGRESO (Ajuste Positivo / Sobrante)", "EGRESO (Ajuste Negativo / Merma)")
        val adapterTipos = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        spinnerTipoMovimiento.setAdapter(adapterTipos)
        spinnerTipoMovimiento.setText(tipos[0], false)

        spinnerTipoMovimiento.setOnClickListener {
            (spinnerTipoMovimiento.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

        val nombresProductos = listaProductosBD.mapNotNull { it.nombre?.trim() }.filter { it.isNotBlank() }
        val adapterProductos = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresProductos)
        spinnerProducto.setAdapter(adapterProductos)

        spinnerProducto.setOnClickListener {
            (spinnerProducto.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

        spinnerProducto.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val productoEncontrado = listaProductosBD.find { it.nombre?.trim() == nombreSeleccionado }
            productoSeleccionadoId = productoEncontrado?.id
        }

        val nombresBodegas = listaBodegasBD.mapNotNull { it.nombre?.trim() }.filter { it.isNotBlank() }
        val adapterBodegas = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresBodegas)
        spinnerBodega.setAdapter(adapterBodegas)

        spinnerBodega.setOnClickListener {
            (spinnerBodega.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

        spinnerBodega.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val bodegaEncontrada = listaBodegasBD.find { it.nombre?.trim() == nombreSeleccionado }
            bodegaSeleccionadaId = bodegaEncontrada?.id
        }
    }

    private fun validarYEnviar() {
        val tipoTexto = spinnerTipoMovimiento.text.toString()
        val tipoClave = if (tipoTexto.startsWith("INGRESO")) "INGRESO" else "EGRESO"

        val cantidad = etCantidadAjuste.text.toString().trim().toIntOrNull()
        val costo = etCostoUnitarioAjuste.text.toString().trim().toDoubleOrNull()
        val motivo = etMotivoAjuste.text.toString().trim()
        val doc = etDocReferenciaAjuste.text.toString().trim()

        if (productoSeleccionadoId == null) {
            Toast.makeText(requireContext(), "Selecciona un producto de la lista", Toast.LENGTH_SHORT).show()
            return
        }

        if (bodegaSeleccionadaId == null) {
            Toast.makeText(requireContext(), "Selecciona una bodega de la lista", Toast.LENGTH_SHORT).show()
            return
        }

        if (cantidad == null || cantidad <= 0) {
            etCantidadAjuste.error = "Ingresa una cantidad válida mayor a cero"
            return
        }

        if (motivo.isEmpty()) {
            etMotivoAjuste.error = "Ingresa el motivo del ajuste"
            return
        }

        val esIngreso = tipoClave == "INGRESO"

        val request = NuevoAjusteRequestDto(
            tipo = tipoClave,
            productoId = productoSeleccionadoId!!,
            bodegaOrigenId = if (!esIngreso) bodegaSeleccionadaId else null,
            bodegaDestinoId = if (esIngreso) bodegaSeleccionadaId else null,
            cantidad = cantidad,
            costoUnitario = costo,
            motivo = motivo,
            documentoReferencia = if (doc.isEmpty()) null else doc
        )

        onAjusteRegistradoListener(request)
        dismiss()
    }
}