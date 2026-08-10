package com.example.movildilo.ui.propietario

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.movildilo.ui.adapters.CompraAdapter
import com.example.movildilo.ui.adapters.DetalleCompraModalAdapter
import com.example.movildilo.ui.adapters.DetalleTempAdapter
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ComprasActivity : AppCompatActivity() {

    private lateinit var btnRegresar: View
    private lateinit var etBuscarCompra: TextInputEditText
    private lateinit var rvCompras: RecyclerView
    private lateinit var tvSinCompras: TextView
    private lateinit var fabNuevaCompra: ExtendedFloatingActionButton
    private lateinit var layoutLoading: View

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: CompraAdapter
    private var negocioId: Long = -1L

    private var listaCompras = mutableListOf<CompraResponseDto>()
    private var listaFiltrada = mutableListOf<CompraResponseDto>()

    private var proveedores = listOf<ProveedorResponseDto>()
    private var bodegas = listOf<BodegaDto>()
    private var productos = listOf<ProductoResponseDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compras)


        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()

        btnRegresar.setOnClickListener { finish() }
        fabNuevaCompra.setOnClickListener { abrirDialogoNuevaCompra() }

        etBuscarCompra.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltro()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        if (negocioId == -1L) {
            Toast.makeText(this, "No se encontró tu negocio activo", Toast.LENGTH_SHORT).show()
        } else {
            cargarCompras()
            cargarCatalogos()
        }

        if (intent.getStringExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_ACCION) ==
            com.example.movildilo.ia.ZoeActionRouter.Accion.CREAR_COMPRA
        ) {
            fabNuevaCompra.postDelayed({ abrirDialogoNuevaCompra() }, 1200)
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        etBuscarCompra = findViewById(R.id.etBuscarCompra)
        rvCompras = findViewById(R.id.rvCompras)
        tvSinCompras = findViewById(R.id.tvSinCompras)
        fabNuevaCompra = findViewById<ExtendedFloatingActionButton>(R.id.fabNuevaCompra)
        layoutLoading = findViewById(R.id.layoutLoading)
    }

    private fun setupRecyclerView() {
        adapter = CompraAdapter(listaFiltrada) { compra -> mostrarDetalleCompra(compra) }
        rvCompras.layoutManager = LinearLayoutManager(this)
        rvCompras.adapter = adapter
    }

    private fun cargarCompras() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getCompras(authHeader, negocioId)
                layoutLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    listaCompras = (response.body() ?: emptyList()).toMutableList()
                    aplicarFiltro()
                } else {
                    Toast.makeText(
                        this@ComprasActivity,
                        "No se pudo cargar el historial (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(
                    this@ComprasActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun cargarCatalogos() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val respProv = RetrofitClient.apiService.getProveedores(authHeader, negocioId)
                if (respProv.isSuccessful) {
                    proveedores = (respProv.body() ?: emptyList()).filter { it.estado != false }
                }
            } catch (_: Exception) {
            }

            try {
                val respBod = RetrofitClient.apiService.getBodegas(authHeader, negocioId)
                if (respBod.isSuccessful) {
                    bodegas = respBod.body() ?: emptyList()
                }
            } catch (_: Exception) {
            }

            try {
                val respProd = RetrofitClient.apiService.getCatalogo(authHeader, negocioId)
                if (respProd.isSuccessful) {
                    productos = respProd.body() ?: emptyList()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun aplicarFiltro() {
        val texto = etBuscarCompra.text.toString().trim().lowercase(Locale.ROOT)
        listaFiltrada = if (texto.isEmpty()) {
            listaCompras.toMutableList()
        } else {
            listaCompras.filter {
                (it.numeroComprobante?.lowercase(Locale.ROOT)?.contains(texto) == true) ||
                        (it.proveedorNombre?.lowercase(Locale.ROOT)?.contains(texto) == true)
            }.toMutableList()
        }
        adapter.actualizarLista(listaFiltrada)
        tvSinCompras.visibility = if (listaFiltrada.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun mostrarDetalleCompra(compra: CompraResponseDto) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_detalle_compra, null)

        val tvNumeroComprobante = view.findViewById<TextView>(R.id.tvModalNumeroComprobante)
        val tvProveedor = view.findViewById<TextView>(R.id.tvModalProveedor)
        val tvBodega = view.findViewById<TextView>(R.id.tvModalBodega)
        val tvFecha = view.findViewById<TextView>(R.id.tvModalFecha)
        val tvTotal = view.findViewById<TextView>(R.id.tvModalTotal)
        val rvProductos = view.findViewById<RecyclerView>(R.id.rvModalProductosDetalle)
        val btnCerrar = view.findViewById<MaterialButton>(R.id.btnModalCerrar)

        tvNumeroComprobante.text = "N° ${compra.numeroComprobante ?: "S/N"}"
        tvProveedor.text = compra.proveedorNombre ?: "No especificado"
        tvBodega.text = compra.bodegaIngresoNombre ?: "No especificada"
        tvFecha.text = compra.fechaCompra?.take(10) ?: "-"
        tvTotal.text = String.format(Locale.US, "$%.2f", compra.totalCompra ?: 0.0)

        val detalles = compra.detalles ?: emptyList()
        rvProductos.layoutManager = LinearLayoutManager(this)
        rvProductos.adapter = DetalleCompraModalAdapter(detalles)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCerrar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun abrirDialogoNuevaCompra() {
        if (proveedores.isEmpty() || bodegas.isEmpty() || productos.isEmpty()) {
            Toast.makeText(this, "Espera a que carguen los proveedores, bodegas y productos...", Toast.LENGTH_SHORT).show()
            cargarCatalogos()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_registrar_compra, null)

        val spProveedor = view.findViewById<AutoCompleteTextView>(R.id.spProveedor)
        val spBodega = view.findViewById<AutoCompleteTextView>(R.id.spBodega)
        val etNumeroComprobante = view.findViewById<TextInputEditText>(R.id.etNumeroComprobante)
        val spProducto = view.findViewById<AutoCompleteTextView>(R.id.spProducto)
        val etCantidad = view.findViewById<TextInputEditText>(R.id.etCantidad)
        val etCostoUnitario = view.findViewById<TextInputEditText>(R.id.etCostoUnitario)
        val tilFechaCaducidad = view.findViewById<TextInputLayout>(R.id.tilFechaCaducidad)
        val etFechaCaducidad = view.findViewById<TextInputEditText>(R.id.etFechaCaducidad)
        val btnAgregarDetalle = view.findViewById<MaterialButton>(R.id.btnAgregarDetalle)
        val rvDetallesTemp = view.findViewById<RecyclerView>(R.id.rvDetallesTemp)
        val tvTotalDialogo = view.findViewById<TextView>(R.id.tvTotalDialogo)
        val btnCancelarCompra = view.findViewById<MaterialButton>(R.id.btnCancelarCompra)
        val btnGuardarCompra = view.findViewById<MaterialButton>(R.id.btnGuardarCompra)

        spProveedor.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, proveedores.map { it.nombreComercial ?: "Sin nombre" }))
        spBodega.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodegas.map { it.nombre }))
        spProducto.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, productos.map { "${it.codigoPrincipal ?: ""} - ${it.nombre ?: ""}" }))

        var productoSeleccionado: ProductoResponseDto? = null

        spProducto.setOnItemClickListener { parent, _, position, _ ->
            val textoSeleccionado = parent.getItemAtPosition(position).toString()
            productoSeleccionado = productos.find { "${it.codigoPrincipal ?: ""} - ${it.nombre ?: ""}" == textoSeleccionado }

            val requiereCaducidad = productoSeleccionado?.tieneCaducidad == true
            tilFechaCaducidad.visibility = if (requiereCaducidad) View.VISIBLE else View.GONE
            etFechaCaducidad.setText("")
        }

        etFechaCaducidad.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                etFechaCaducidad.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val detallesTemp = mutableListOf<Pair<DetalleCompraRequestDto, String>>()
        val detalleAdapter = DetalleTempAdapter(detallesTemp) { posicion ->
            detallesTemp.removeAt(posicion)
            actualizarTotalDialogo(detallesTemp, tvTotalDialogo)
        }
        rvDetallesTemp.layoutManager = LinearLayoutManager(this)
        rvDetallesTemp.adapter = detalleAdapter

        btnAgregarDetalle.setOnClickListener {
            val prod = productoSeleccionado
            val cantidad = etCantidad.text.toString().toIntOrNull()
            val costo = etCostoUnitario.text.toString().toDoubleOrNull()

            if (prod == null) {
                Toast.makeText(this, "Selecciona un producto de la lista.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cantidad == null || cantidad <= 0) {
                Toast.makeText(this, "Ingresa una cantidad válida.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (costo == null || costo < 0) {
                Toast.makeText(this, "Ingresa un costo unitario válido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (prod.tieneCaducidad == true && etFechaCaducidad.text.isNullOrBlank()) {
                Toast.makeText(this, "Este producto requiere fecha de caducidad.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoDetalle = DetalleCompraRequestDto(
                productoId = prod.id ?: return@setOnClickListener,
                cantidad = cantidad,
                costoUnitario = costo,
                fechaCaducidad = etFechaCaducidad.text?.toString()?.ifBlank { null }
            )
            detallesTemp.add(nuevoDetalle to (prod.nombre ?: "Producto"))
            detalleAdapter.actualizar(detallesTemp)
            actualizarTotalDialogo(detallesTemp, tvTotalDialogo)

            spProducto.setText("", false)
            etCantidad.setText("")
            etCostoUnitario.setText("")
            etFechaCaducidad.setText("")
            tilFechaCaducidad.visibility = View.GONE
            productoSeleccionado = null
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnCancelarCompra.setOnClickListener { dialog.dismiss() }

        btnGuardarCompra.setOnClickListener {
            val nombreProveedor = spProveedor.text.toString()
            val nombreBodega = spBodega.text.toString()
            val numeroComprobante = etNumeroComprobante.text.toString().trim()

            val proveedor = proveedores.find { it.nombreComercial == nombreProveedor }
            val bodega = bodegas.find { it.nombre == nombreBodega }

            if (proveedor == null) {
                Toast.makeText(this, "Selecciona un proveedor válido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (bodega == null) {
                Toast.makeText(this, "Selecciona una bodega válida.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (numeroComprobante.isEmpty()) {
                Toast.makeText(this, "Ingresa el número de comprobante.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (detallesTemp.isEmpty()) {
                Toast.makeText(this, "Agrega al menos un producto al listado.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestBody = CompraRequestDto(
                proveedorId = proveedor.id ?: return@setOnClickListener,
                bodegaIngresoId = bodega.id,
                numeroComprobante = numeroComprobante,
                detalles = detallesTemp.map { it.first }
            )

            registrarCompraEnApi(requestBody, dialog)
        }

        dialog.show()
    }

    private fun actualizarTotalDialogo(detalles: List<Pair<DetalleCompraRequestDto, String>>, tvTotal: TextView) {
        val total = detalles.sumOf { it.first.cantidad * it.first.costoUnitario }
        tvTotal.text = String.format(Locale.US, "Total: $%.2f", total)
    }

    private fun registrarCompraEnApi(request: CompraRequestDto, dialog: AlertDialog) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.registrarCompra(authHeader, negocioId, request)
                layoutLoading.visibility = View.GONE
                if (response.isSuccessful) {
                    val total = response.body()?.totalCompra ?: 0.0
                    Toast.makeText(
                        this@ComprasActivity,
                        "¡Abastecimiento registrado! Total invertido: ${String.format(Locale.US, "$%.2f", total)}",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                    cargarCompras()
                } else {
                    Toast.makeText(this@ComprasActivity, "Error al registrar (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@ComprasActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}