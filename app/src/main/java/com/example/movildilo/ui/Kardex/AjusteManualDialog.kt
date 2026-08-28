package com.example.movildilo.ui.Kardex

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.BodegaDto
import com.example.movildilo.data.model.dto.inventario.BodegaRequest
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.data.model.dto.inventario.InventarioResponseDto
import com.example.movildilo.data.model.dto.inventario.NuevoAjusteRequestDto
import com.example.movildilo.data.model.dto.inventario.ProductoDto
import com.example.movildilo.data.model.dto.inventario.ProductoResponseDto
import com.example.movildilo.ui.productos.ProductoDialog
import com.example.movildilo.ui.abastecimiento.ComprasActivity
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody

class AjusteManualDialog(
    private val listaProductosBD: MutableList<ProductoDto>,
    private val listaBodegasBD: MutableList<BodegaDto>,
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

    private lateinit var tvAvisoInformativo: TextView
    private lateinit var btnNuevaBodega: TextView
    private lateinit var btnCrearProducto: TextView

    private lateinit var sessionManager: SessionManager
    private var negocioId: Long = -1L
    private val categorias = mutableListOf<CategoriaDto>()

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

        sessionManager = SessionManager(requireContext())
        negocioId = sessionManager.getNegocioId()

        initViews(view)
        setupDropdowns()
        setupEnlaceAbastecimiento()
        onTipoChange()

        btnCancelarAjuste.setOnClickListener { dismiss() }
        btnRegistrarAjuste.setOnClickListener { validarYEnviar() }

        btnNuevaBodega.setOnClickListener {
            mostrarDialogoCrearBodega { nuevaBod ->
                poblarSpinnerBodegaOrigen(listaBodegasBD)
                spinnerBodega.setText(nuevaBod.nombre, false)
                bodegaOrigenSeleccionadaId = nuevaBod.id
                actualizarMaxCantidad()
            }
        }

        btnCrearProducto.setOnClickListener {
            mostrarDialogoCrearProducto { nuevoProd ->
                val nombresActualizados = listaProductosBD.mapNotNull { it.nombre?.trim() }.filter { it.isNotBlank() }
                spinnerProducto.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresActualizados))
                spinnerProducto.setText(nuevoProd.nombre?.trim() ?: "", false)
                productoSeleccionadoId = nuevoProd.id
                onProductoChange()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
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

        tvAvisoInformativo = v.findViewById(R.id.tvAvisoInformativo)
        btnNuevaBodega = v.findViewById(R.id.btnNuevaBodega)
        btnCrearProducto = v.findViewById(R.id.btnCrearProducto)
    }

    private fun setupEnlaceAbastecimiento() {
        val textoCompleto = "ℹ️ Atención: Para ingresar facturas directas de proveedores, utiliza el módulo de Abastecimiento."
        val spannableString = SpannableString(textoCompleto)

        val inicio = textoCompleto.indexOf("Abastecimiento")
        if (inicio >= 0) {
            val fin = inicio + "Abastecimiento".length
            spannableString.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(requireContext(), ComprasActivity::class.java)
                    startActivity(intent)
                    dismiss()
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.parseColor("#2563EB")
                    ds.isUnderlineText = true
                }
            }, inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        tvAvisoInformativo.text = spannableString
        tvAvisoInformativo.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupDropdowns() {
        val adapterTipos = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        spinnerTipoMovimiento.setAdapter(adapterTipos)
        spinnerTipoMovimiento.setText(tipos[0], false)

        spinnerTipoMovimiento.setOnItemClickListener { _, _, _, _ -> onTipoChange() }

        val nombresProductos = listaProductosBD.mapNotNull { it.nombre?.trim() }.filter { it.isNotBlank() }
        spinnerProducto.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresProductos))
        spinnerProducto.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            productoSeleccionadoId = listaProductosBD.find { it.nombre?.trim() == nombreSeleccionado }?.id
            onProductoChange()
        }

        poblarSpinnerBodegaOrigen(listaBodegasBD)
        spinnerBodega.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            val bodegaEncontrada = bodegasOrigenDisponibles.find { it.nombre.trim() == nombreSeleccionado }
                ?: listaBodegasBD.find { it.nombre.trim() == nombreSeleccionado }
            bodegaOrigenSeleccionadaId = bodegaEncontrada?.id
            actualizarMaxCantidad()
        }

        val nombresBodegas = listaBodegasBD.mapNotNull { it.nombre.trim() }.filter { it.isNotBlank() }
        spinnerBodegaDestino.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresBodegas))
        spinnerBodegaDestino.setOnItemClickListener { parent, _, position, _ ->
            val nombreSeleccionado = parent.getItemAtPosition(position).toString()
            bodegaDestinoSeleccionadaId = listaBodegasBD.find { it.nombre.trim() == nombreSeleccionado }?.id
        }
    }

    private fun poblarSpinnerBodegaOrigen(bodegas: List<BodegaDto>) {
        val nombres = bodegas.mapNotNull { it.nombre.trim() }.filter { it.isNotBlank() }
        spinnerBodega.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres))
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
            else -> maxCantidad = null
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

    private fun validarCantidadContraMaximo() {
        val max = maxCantidad ?: return
        val actual = etCantidadAjuste.text.toString().toIntOrNull() ?: return
        if (actual > max) {
            etCantidadAjuste.setText(max.toString())
            etCantidadAjuste.setSelection(etCantidadAjuste.text?.length ?: 0)
        }
    }

    private fun mostrarDialogoCrearBodega(onCreada: (BodegaDto) -> Unit) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_bodega, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val etNombre = view.findViewById<EditText>(R.id.etDialogNombre)
        val etDireccion = view.findViewById<EditText>(R.id.etDialogDireccion)
        val btnConfirmar = view.findViewById<Button>(R.id.btnDialogConfirmar)
        val btnCancelar = view.findViewById<Button>(R.id.btnDialogCancelar)

        tvTitle.text = "Nueva Bodega"
        btnConfirmar.text = "Crear Bodega"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        btnConfirmar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val direccion = etDireccion.text.toString().trim()

            if (nombre.isEmpty()) {
                etNombre.error = "El nombre es obligatorio"
                return@setOnClickListener
            }

            val request = BodegaRequest(
                nombre = nombre,
                direccion = if (direccion.isEmpty()) null else direccion
            )

            guardarBodegaEnApi(request) { nuevaBodega ->
                dialog.dismiss()
                onCreada(nuevaBodega)
            }
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.show()

        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun guardarBodegaEnApi(request: BodegaRequest, onSuccess: (BodegaDto) -> Unit) {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.crearBodega(authHeader, negocioId, request)
                }

                if (response.isSuccessful && response.body() != null) {
                    val nuevaBodega = response.body()!!
                    listaBodegasBD.add(nuevaBodega)
                    Toast.makeText(requireContext(), "Bodega registrada", Toast.LENGTH_SHORT).show()
                    onSuccess(nuevaBodega)
                } else {
                    Toast.makeText(requireContext(), "Error al crear bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoCrearProducto(onCreado: (ProductoResponseDto) -> Unit) {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val respCat = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.getCategorias(authHeader, negocioId)
                }
                if (respCat.isSuccessful) {
                    categorias.clear()
                    categorias.addAll(respCat.body() ?: emptyList())
                }
            } catch (_: Exception) {}

            lanzarDialogoProducto(onCreado)
        }
    }

    private fun lanzarDialogoProducto(onCreado: (ProductoResponseDto) -> Unit) {
        val listaDtoExistentes = listaProductosBD.map { p ->
            ProductoDto(
                id = p.id,
                codigoPrincipal = p.codigoPrincipal,
                nombre = p.nombre,
                marca = p.marca,
                precioUnitario = p.precioUnitario,
                costoPromedioActual = p.costoPromedioActual,
                categoriaId = p.categoriaId,
                categoria = p.categoria,
                unidadMedida = p.unidadMedida,
                grabaIva = p.grabaIva,
                tieneCaducidad = p.tieneCaducidad,
                imagen = p.imagen
            )
        }

        val dialog = ProductoDialog(
            productoEditar = null,
            listaCategoriasBD = categorias,
            listaProductosExistentes = listaDtoExistentes,
            onGuardarListener = { productoDto, catId ->
                guardarProductoEnApi(productoDto, catId, onCreado)
            }
        )
        dialog.show(parentFragmentManager, "ProductoDialog")
    }

    private fun guardarProductoEnApi(productoDto: ProductoDto, categoriaId: Long?, onSuccess: (ProductoResponseDto) -> Unit) {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val productoAEnviar = productoDto.copy(
                    negocioId = productoDto.negocioId ?: negocioId,
                    categoriaId = productoDto.categoriaId ?: categoriaId
                )
                val json = Gson().toJson(productoAEnviar)
                val datosBody = RequestBody.create(MediaType.parse("application/json"), json)

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.apiService.crearProducto(authHeader, negocioId, datosBody, null)
                }

                if (response.isSuccessful && response.body() != null) {
                    val nuevoProd = response.body()!!

                    val pDto = ProductoDto(
                        id = nuevoProd.id,
                        codigoPrincipal = nuevoProd.codigoPrincipal,
                        nombre = nuevoProd.nombre,
                        marca = nuevoProd.marca,
                        precioUnitario = nuevoProd.precioUnitario,
                        costoPromedioActual = nuevoProd.costoPromedio,
                        categoriaId = nuevoProd.categoriaId,
                        categoria = nuevoProd.categoria,
                        unidadMedida = nuevoProd.unidadMedida,
                        grabaIva = nuevoProd.grabaIva,
                        tieneCaducidad = nuevoProd.tieneCaducidad,
                        imagen = nuevoProd.imagen
                    )
                    listaProductosBD.add(pDto)
                    Toast.makeText(requireContext(), "Producto registrado correctamente", Toast.LENGTH_SHORT).show()
                    onSuccess(nuevoProd)
                } else {
                    Toast.makeText(requireContext(), "Error al procesar el producto", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                Toast.makeText(requireContext(), "La cantidad se ajustó al stock máximo disponible ($maxCantidad)", Toast.LENGTH_SHORT).show()
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