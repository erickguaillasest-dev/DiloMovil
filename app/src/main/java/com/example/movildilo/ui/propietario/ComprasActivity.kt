package com.example.movildilo.ui.propietario

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.*
import com.example.movildilo.ui.adapters.CompraAdapter
import com.example.movildilo.ui.adapters.DetalleCompraModalAdapter
import com.example.movildilo.ui.adapters.DetalleTempAdapter
import com.example.movildilo.ui.productos.ProductoDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.RequestBody
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

    private var proveedores = mutableListOf<ProveedorResponseDto>()
    private var bodegas = mutableListOf<BodegaDto>()
    private var productos = mutableListOf<ProductoResponseDto>()
    private var categorias = mutableListOf<CategoriaDto>()

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
        fabNuevaCompra = findViewById(R.id.fabNuevaCompra)
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
                    Toast.makeText(this@ComprasActivity, "No se pudo cargar el historial (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@ComprasActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cargarCatalogos(onComplete: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val respProv = RetrofitClient.apiService.getProveedores(authHeader, negocioId)
                if (respProv.isSuccessful) {
                    proveedores = (respProv.body() ?: emptyList()).filter { it.estado != false }.toMutableList()
                }

                val respBod = RetrofitClient.apiService.getBodegas(authHeader, negocioId)
                if (respBod.isSuccessful) {
                    bodegas = (respBod.body() ?: emptyList()).toMutableList()
                }

                val respProd = RetrofitClient.apiService.getCatalogo(authHeader, negocioId)
                if (respProd.isSuccessful) {
                    productos = (respProd.body() ?: emptyList()).toMutableList()
                }

                val respCat = RetrofitClient.apiService.getCategorias(authHeader, negocioId)
                if (respCat.isSuccessful) {
                    categorias = (respCat.body() ?: emptyList()).toMutableList()
                }

                onComplete?.invoke()
            } catch (_: Exception) {
                onComplete?.invoke()
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

        rvProductos.layoutManager = LinearLayoutManager(this)
        rvProductos.adapter = DetalleCompraModalAdapter(compra.detalles ?: emptyList())

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCerrar.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun abrirDialogoNuevaCompra() {
        if (proveedores.isEmpty() || bodegas.isEmpty() || productos.isEmpty() || categorias.isEmpty()) {
            Toast.makeText(this, "Cargando catálogos del servidor...", Toast.LENGTH_SHORT).show()
            cargarCatalogos { abrirDialogoNuevaCompra() }
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_registrar_compra, null)

        val btnNuevoProveedor = view.findViewById<TextView>(R.id.btnNuevoProveedor)
        val btnNuevaBodega = view.findViewById<TextView>(R.id.btnNuevaBodega)
        val btnCrearProducto = view.findViewById<TextView>(R.id.btnCrearProducto)

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

        fun actualizarAdapterProveedor() {
            spProveedor.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, proveedores.map { it.nombreComercial ?: "Sin nombre" }))
        }

        fun actualizarAdapterBodega() {
            spBodega.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodegas.map { it.nombre }))
        }

        fun actualizarAdapterProducto() {
            spProducto.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, productos.map { "${it.codigoPrincipal ?: ""} - ${it.nombre ?: ""}" }))
        }

        actualizarAdapterProveedor()
        actualizarAdapterBodega()
        actualizarAdapterProducto()

        btnNuevoProveedor?.setOnClickListener {
            mostrarDialogoCrearProveedor { nuevoProv ->
                actualizarAdapterProveedor()
                spProveedor.setText(nuevoProv.nombreComercial ?: "", false)
            }
        }

        btnNuevaBodega?.setOnClickListener {
            mostrarDialogoCrearBodega { nuevaBod ->
                actualizarAdapterBodega()
                spBodega.setText(nuevaBod.nombre, false)
            }
        }

        btnCrearProducto?.setOnClickListener {
            mostrarDialogoCrearProducto { nuevoProd ->
                actualizarAdapterProducto()
                spProducto.setText("${nuevoProd.codigoPrincipal ?: ""} - ${nuevoProd.nombre ?: ""}", false)
            }
        }

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

    private fun mostrarDialogoCrearProducto(onCreado: (ProductoResponseDto) -> Unit) {
        if (categorias.isEmpty()) {
            val authHeader = sessionManager.getAuthHeader()
            if (authHeader.isNullOrBlank()) return

            layoutLoading.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    val respCat = RetrofitClient.apiService.getCategorias(authHeader, negocioId)
                    layoutLoading.visibility = View.GONE
                    if (respCat.isSuccessful) {
                        categorias = (respCat.body() ?: emptyList()).toMutableList()
                    }
                } catch (_: Exception) {
                    layoutLoading.visibility = View.GONE
                }
                lanzarDialogoProducto(onCreado)
            }
        } else {
            lanzarDialogoProducto(onCreado)
        }
    }

    private fun lanzarDialogoProducto(onCreado: (ProductoResponseDto) -> Unit) {
        val codigosExistentes = productos.mapNotNull { it.codigoPrincipal?.trim() }

        val dialog = ProductoDialog(
            productoEditar = null,
            listaCategoriasBD = categorias,
            codigosExistentes = codigosExistentes,
            onGuardarListener = { productoDto, catId ->
                guardarProductoEnApi(productoDto, catId, onCreado)
            }
        )
        dialog.show(supportFragmentManager, "ProductoDialog")
    }

    private fun guardarProductoEnApi(productoDto: ProductoDto, categoriaId: Long?, onSuccess: (ProductoResponseDto) -> Unit) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val productoAEnviar = productoDto.copy(
                    negocioId = productoDto.negocioId ?: negocioId,
                    categoriaId = productoDto.categoriaId ?: categoriaId
                )
                val json = Gson().toJson(productoAEnviar)
                val datosBody = RequestBody.create(MediaType.parse("application/json"), json)

                val response = RetrofitClient.apiService.crearProducto(authHeader, negocioId, datosBody, null)
                layoutLoading.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val nuevoProd = response.body()!!
                    productos.add(nuevoProd)
                    Toast.makeText(this@ComprasActivity, "Producto registrado correctamente", Toast.LENGTH_SHORT).show()
                    onSuccess(nuevoProd)
                } else {
                    val errorBodyStr = response.errorBody()?.string()
                    val mensajeError = try {
                        val jsonObject = org.json.JSONObject(errorBodyStr ?: "")
                        jsonObject.optString("message", "Error al procesar el producto")
                    } catch (_: Exception) {
                        "Error de respuesta (${response.code()})"
                    }

                    Toast.makeText(this@ComprasActivity, mensajeError, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@ComprasActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoCrearProveedor(onCreado: (ProveedorResponseDto) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_proveedor, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvDialogTitulo)
        val etRuc = dialogView.findViewById<TextInputEditText>(R.id.etRuc)
        val etTelefono = dialogView.findViewById<TextInputEditText>(R.id.etTelefono)
        val etRazonSocial = dialogView.findViewById<TextInputEditText>(R.id.etRazonSocial)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupCategoriasDialog)
        val cbActivo = dialogView.findViewById<MaterialCheckBox>(R.id.cbActivo)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnGuardar = dialogView.findViewById<MaterialButton>(R.id.btnGuardar)

        tvTitulo.text = "Nuevo Proveedor"
        cbActivo.isChecked = true

        val idsSeleccionados = mutableListOf<Long>()

        chipGroup.removeAllViews()
        categorias.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat.nombre
                isCheckable = true
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                setOnCheckedChangeListener { _, checked ->
                    cat.id?.let { id ->
                        if (checked) idsSeleccionados.add(id) else idsSeleccionados.remove(id)
                    }
                }
            }
            chipGroup.addView(chip)
        }

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnCancelar.setOnClickListener { alertDialog.dismiss() }

        btnGuardar.setOnClickListener {
            val ruc = etRuc.text.toString().trim()
            val nombreComercial = etRazonSocial.text.toString().trim()
            val telefonoRaw = etTelefono.text.toString().trim()
            val activo = cbActivo.isChecked

            if (!validarCamposProveedor(etRuc, ruc, etRazonSocial, nombreComercial, etTelefono, telefonoRaw)) {
                return@setOnClickListener
            }

            val requestDto = ProveedorRequestDto(
                dni = ruc,
                nombreComercial = nombreComercial,
                telefono = telefonoRaw.ifBlank { null },
                estado = activo,
                categoriasIds = idsSeleccionados
            )

            guardarProveedorEnApi(requestDto) { nuevoProveedor ->
                alertDialog.dismiss()
                onCreado(nuevoProveedor)
            }
        }

        alertDialog.show()
    }

    private fun validarCamposProveedor(
        etRuc: TextInputEditText,
        ruc: String,
        etRazonSocial: TextInputEditText,
        nombreComercial: String,
        etTelefono: TextInputEditText,
        telefonoRaw: String
    ): Boolean {
        var esValido = true
        var primerCampoError: View? = null

        if (ruc.isBlank()) {
            etRuc.error = "El RUC o DNI es obligatorio"
            if (primerCampoError == null) primerCampoError = etRuc
            esValido = false
        } else if (!ruc.matches(Regex("^[0-9]{10,13}$"))) {
            etRuc.error = "Ingresa un RUC o DNI válido (10 o 13 dígitos numéricos)"
            if (primerCampoError == null) primerCampoError = etRuc
            esValido = false
        } else {
            etRuc.error = null
        }

        if (nombreComercial.isBlank()) {
            etRazonSocial.error = "El Nombre Comercial es obligatorio"
            if (primerCampoError == null) primerCampoError = etRazonSocial
            esValido = false
        } else if (nombreComercial.length < 3) {
            etRazonSocial.error = "El Nombre Comercial debe tener al menos 3 caracteres"
            if (primerCampoError == null) primerCampoError = etRazonSocial
            esValido = false
        } else {
            etRazonSocial.error = null
        }

        if (telefonoRaw.isNotBlank()) {
            if (!telefonoRaw.matches(Regex("^[0-9]{7,10}$"))) {
                etTelefono.error = "El teléfono debe contener entre 7 y 10 dígitos"
                if (primerCampoError == null) primerCampoError = etTelefono
                esValido = false
            } else {
                etTelefono.error = null
            }
        } else {
            etTelefono.error = null
        }

        primerCampoError?.requestFocus()
        return esValido
    }

    private fun guardarProveedorEnApi(request: ProveedorRequestDto, onSuccess: (ProveedorResponseDto) -> Unit) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.crearProveedor(authHeader, negocioId, request)
                layoutLoading.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val nuevoProveedor = response.body()!!
                    proveedores.add(nuevoProveedor)
                    Toast.makeText(this@ComprasActivity, "Proveedor registrado", Toast.LENGTH_SHORT).show()
                    onSuccess(nuevoProveedor)
                } else {
                    Toast.makeText(this@ComprasActivity, "Error al crear proveedor (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@ComprasActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDialogoCrearBodega(onCreada: (BodegaDto) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_bodega, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val etNombre = view.findViewById<EditText>(R.id.etDialogNombre)
        val etDireccion = view.findViewById<EditText>(R.id.etDialogDireccion)
        val btnConfirmar = view.findViewById<Button>(R.id.btnDialogConfirmar)
        val btnCancelar = view.findViewById<Button>(R.id.btnDialogCancelar)

        tvTitle.text = "Nueva Bodega"
        btnConfirmar.text = "Crear Bodega"

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        val dialog = AlertDialog.Builder(this)
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
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.crearBodega(authHeader, negocioId, request)
                layoutLoading.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val nuevaBodega = response.body()!!
                    bodegas.add(nuevaBodega)
                    Toast.makeText(this@ComprasActivity, "Bodega registrada", Toast.LENGTH_SHORT).show()
                    onSuccess(nuevaBodega)
                } else {
                    Toast.makeText(this@ComprasActivity, "Error al crear bodega (${response.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                Toast.makeText(this@ComprasActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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