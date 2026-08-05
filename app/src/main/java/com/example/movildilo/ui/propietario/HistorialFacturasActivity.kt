package com.example.movildilo.ui.propietario

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.ai.ResultadoVozFactura
import com.example.movildilo.ai.ZoeVoiceAI
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.BodegaDto
import com.example.movildilo.data.model.dto.ClienteResponseDto
import com.example.movildilo.data.model.dto.DetalleFacturaRequestDto
import com.example.movildilo.data.model.dto.FacturaRequestDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.data.model.dto.ItemCarritoFactura
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.example.movildilo.data.model.dto.ProductoResponseDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ui.adapters.FacturaCarritoAdapter
import com.example.movildilo.ui.adapters.FacturasAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

private enum class VoiceStep { OFF, ESCUCHANDO, PROCESANDO }

class HistorialFacturasActivity : AppCompatActivity() {

    private lateinit var btnRegresar: View
    private lateinit var btnInvocarZoeHeader: View
    private lateinit var rvFacturas: RecyclerView
    private lateinit var layoutLoading: View
    private lateinit var layoutVacio: View

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: FacturasAdapter
    private var negocioId: Long = -1L
    private lateinit var fabNuevaFactura: View

    private var clientesList: List<ClienteResponseDto> = emptyList()
    private var bodegasList: List<BodegaDto> = emptyList()
    private var productosList: List<ProductoResponseDto> = emptyList()
    private var inventarioList: List<InventarioResponseDto> = emptyList()
    private var negocioActual: NegocioResponseDto? = null

    private var facturaClienteId: Long? = null
    private var facturaEsConsumidorFinal: Boolean = false
    private var facturaMetodoPago: String = "EFECTIVO"
    private var facturaCuotas: Int = 0
    private var facturaDescuentoGlobalPorcentaje: Double = 0.0
    private val carritoTemporal = mutableListOf<ItemCarritoFactura>()
    private var carritoAdapter: FacturaCarritoAdapter? = null

    private var dialogFacturaActivo: AlertDialog? = null
    private var spClienteRef: AutoCompleteTextView? = null
    private var spMetodoPagoRef: AutoCompleteTextView? = null
    private var tilCuotasRef: TextInputLayout? = null
    private var etCuotasRef: TextInputEditText? = null
    private var spBodegaRef: AutoCompleteTextView? = null
    private var spProductoRef: AutoCompleteTextView? = null
    private var etCantidadRef: TextInputEditText? = null
    private var etDescuentoItemRef: TextInputEditText? = null
    private var etDescuentoGlobalRef: TextInputEditText? = null
    private var tvDescuentoGlobalMontoRef: TextView? = null
    private var tvStockRef: TextView? = null
    private var tvTotalRef: TextView? = null
    private var tvItemsCountRef: TextView? = null
    private var layoutEmptyCartRef: View? = null
    private var rvCarritoRef: RecyclerView? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var tvZoeTranscripcionRef: TextView? = null
    private var btnZoeMicRef: FloatingActionButton? = null
    private var voiceState: VoiceStep = VoiceStep.OFF
    private var pendingBodegaId: Long? = null

    private var layoutSimuladorTarjetaRef: View? = null
    private var etNumeroTarjetaRef: TextInputEditText? = null
    private var etVencimientoTarjetaRef: TextInputEditText? = null
    private var etCvcTarjetaRef: TextInputEditText? = null
    private var tvInfoPagoTarjetaRef: TextView? = null

    private var facturaTarjetaNumero: String = ""
    private var facturaTarjetaVence: String = ""
    private var facturaTarjetaCvc: String = ""
    private val voiceHandler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            iniciarFlujoVoz()
        } else {
            Toast.makeText(this, "Se requiere permiso de micrófono para usar a Zoe.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial_facturas)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        inicializarTextToSpeech()

        btnRegresar.setOnClickListener { finish() }
        btnInvocarZoeHeader.setOnClickListener { abrirDialogoNuevaFactura(iniciarConVoz = true) }
        fabNuevaFactura.setOnClickListener { abrirDialogoNuevaFactura(iniciarConVoz = false) }

        if (negocioId != -1L) {
            cargarCatalogosFactura {
                cargarFacturas()
            }
        } else {
            Toast.makeText(this, "No se encontró un negocio activo en la sesión.", Toast.LENGTH_SHORT).show()
        }

        if (intent.getStringExtra(ZoeActionRouter.EXTRA_ACCION) == ZoeActionRouter.Accion.CREAR_FACTURA) {
            abrirDialogoNuevaFactura(iniciarConVoz = false)
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        btnInvocarZoeHeader = findViewById(R.id.btnInvocarZoeHeader)
        rvFacturas = findViewById(R.id.rvFacturas)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutVacio = findViewById(R.id.layoutVacio)
        fabNuevaFactura = findViewById(R.id.fabNuevaFactura)
    }

    private fun setupRecyclerView() {
        adapter = FacturasAdapter(emptyList()) { factura ->
            mostrarDetalleFacturaDialog(factura)
        }
        rvFacturas.layoutManager = LinearLayoutManager(this)
        rvFacturas.adapter = adapter
    }

    private fun inicializarTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("es", "EC")
                val resultado = textToSpeech?.setLanguage(locale)
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale("es", "ES"))
                }
                textToSpeech?.setSpeechRate(1.05f)
                textToSpeech?.setPitch(1.05f)
            }
        }
    }

    private fun cargarCatalogosFactura(onListo: (() -> Unit)? = null) {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val respClientes = RetrofitClient.apiService.getClientes(authHeader, negocioId)
                if (respClientes.isSuccessful) clientesList = respClientes.body() ?: emptyList()
            } catch (_: Exception) {}

            try {
                val respBodegas = RetrofitClient.apiService.getBodegas(authHeader, negocioId)
                if (respBodegas.isSuccessful) bodegasList = respBodegas.body() ?: emptyList()
            } catch (_: Exception) {}

            try {
                val respProductos = RetrofitClient.apiService.getCatalogo(authHeader, negocioId)
                if (respProductos.isSuccessful) productosList = respProductos.body() ?: emptyList()
            } catch (_: Exception) {}

            try {
                val respInventario = RetrofitClient.apiService.getInventario(authHeader, negocioId)
                if (respInventario.isSuccessful) inventarioList = respInventario.body() ?: emptyList()
            } catch (_: Exception) {}

            try {
                val respNegocio = RetrofitClient.apiService.getNegocio(authHeader, negocioId)
                if (respNegocio.isSuccessful) negocioActual = respNegocio.body()
            } catch (_: Exception) {}

            onListo?.invoke()
        }
    }

    private fun cargarFacturas() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE
        rvFacturas.visibility = View.GONE
        layoutVacio.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Se utiliza la función correspondiente definida en ApiService
                val response = RetrofitClient.apiService.getFacturas(authHeader, negocioId)
                layoutLoading.visibility = View.GONE

                if (response.isSuccessful) {
                    val lista = response.body() ?: emptyList()
                    if (lista.isEmpty()) {
                        layoutVacio.visibility = View.VISIBLE
                    } else {
                        rvFacturas.visibility = View.VISIBLE
                        adapter.actualizarLista(lista)
                    }
                } else {
                    layoutVacio.visibility = View.VISIBLE
                    Toast.makeText(this@HistorialFacturasActivity, "Error al cargar facturas.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                layoutLoading.visibility = View.GONE
                layoutVacio.visibility = View.VISIBLE
                Toast.makeText(this@HistorialFacturasActivity, "Error de red: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun abrirDialogoNuevaFactura(iniciarConVoz: Boolean = false) {
        if (clientesList.isEmpty() && productosList.isEmpty() && bodegasList.isEmpty()) {
            Toast.makeText(this, "Cargando catálogos de venta...", Toast.LENGTH_SHORT).show()
            cargarCatalogosFactura { mostrarDialogoFacturaReal(iniciarConVoz) }
        } else {
            mostrarDialogoFacturaReal(iniciarConVoz)
        }
    }

    private fun resetearEstadoFactura() {
        facturaClienteId = null
        facturaEsConsumidorFinal = false
        facturaMetodoPago = "EFECTIVO"
        facturaCuotas = 0
        facturaDescuentoGlobalPorcentaje = 0.0
        facturaTarjetaNumero = ""
        facturaTarjetaVence = ""
        facturaTarjetaCvc = ""
        carritoTemporal.clear()
        pendingBodegaId = null
        voiceState = VoiceStep.OFF
    }

    private fun mostrarDialogoFacturaReal(iniciarConVoz: Boolean) {
        resetearEstadoFactura()

        val dialogView = layoutInflater.inflate(R.layout.dialog_nueva_factura, null)

        val btnCerrarModal = dialogView.findViewById<ImageView>(R.id.btnCerrarModal)
        val spCliente = dialogView.findViewById<AutoCompleteTextView>(R.id.spClienteFactura)
        val spMetodoPago = dialogView.findViewById<AutoCompleteTextView>(R.id.spMetodoPago)
        val tilCuotas = dialogView.findViewById<TextInputLayout>(R.id.tilCuotas)
        val etCuotas = dialogView.findViewById<TextInputEditText>(R.id.etCuotasFactura)
        val spBodega = dialogView.findViewById<AutoCompleteTextView>(R.id.spBodegaFactura)
        val spProducto = dialogView.findViewById<AutoCompleteTextView>(R.id.spProductoFactura)
        val etCantidad = dialogView.findViewById<TextInputEditText>(R.id.etCantidadFactura)
        val etDescuentoItem = dialogView.findViewById<TextInputEditText>(R.id.etDescuentoItemFactura)
        val etDescuentoGlobal = dialogView.findViewById<TextInputEditText>(R.id.etDescuentoGlobalFactura)
        val tvDescuentoGlobalMonto = dialogView.findViewById<TextView>(R.id.tvDescuentoGlobalMonto)
        val tvStock = dialogView.findViewById<TextView>(R.id.tvStockDisponible)
        val btnAgregar = dialogView.findViewById<MaterialButton>(R.id.btnAgregarAlCarrito)
        val rvCarrito = dialogView.findViewById<RecyclerView>(R.id.rvCarritoFactura)
        val layoutEmptyCart = dialogView.findViewById<View>(R.id.layoutEmptyCart)
        val tvItemsCount = dialogView.findViewById<TextView>(R.id.tvItemsCount)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalFacturaDialogo)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarFactura)
        val btnEmitir = dialogView.findViewById<MaterialButton>(R.id.btnEmitirFactura)

        val btnZoeMic = dialogView.findViewById<FloatingActionButton>(R.id.btnZoeMic)
        val tvZoeTranscripcion = dialogView.findViewById<TextView>(R.id.tvZoeTranscripcion)

        val layoutSimuladorTarjeta = dialogView.findViewById<View>(R.id.layoutSimuladorTarjeta)
        val etNumeroTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etNumeroTarjeta)
        val etVencimientoTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etVencimientoTarjeta)
        val etCvcTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etCvcTarjeta)
        val tvInfoPagoTarjeta = dialogView.findViewById<TextView>(R.id.tvInfoPagoTarjeta)

        layoutSimuladorTarjetaRef = layoutSimuladorTarjeta
        etNumeroTarjetaRef = etNumeroTarjeta
        etVencimientoTarjetaRef = etVencimientoTarjeta
        etCvcTarjetaRef = etCvcTarjeta
        tvInfoPagoTarjetaRef = tvInfoPagoTarjeta

        btnZoeMicRef = btnZoeMic
        tvZoeTranscripcionRef = tvZoeTranscripcion
        spClienteRef = spCliente
        spMetodoPagoRef = spMetodoPago
        tilCuotasRef = tilCuotas
        etCuotasRef = etCuotas
        spBodegaRef = spBodega
        spProductoRef = spProducto
        etCantidadRef = etCantidad
        etDescuentoItemRef = etDescuentoItem
        etDescuentoGlobalRef = etDescuentoGlobal
        tvDescuentoGlobalMontoRef = tvDescuentoGlobalMonto
        tvStockRef = tvStock
        tvTotalRef = tvTotal
        tvItemsCountRef = tvItemsCount
        layoutEmptyCartRef = layoutEmptyCart
        rvCarritoRef = rvCarrito

        carritoAdapter = FacturaCarritoAdapter(
            carritoTemporal,
            onQuitar = { posicion ->
                if (posicion in carritoTemporal.indices) {
                    carritoTemporal.removeAt(posicion)
                    actualizarUiCarrito()
                }
            },
            onEditar = { posicion ->
                if (posicion in carritoTemporal.indices) {
                    mostrarDialogoEditarItemCarrito(posicion)
                }
            }
        )
        rvCarrito.layoutManager = LinearLayoutManager(this)
        rvCarrito.adapter = carritoAdapter
        actualizarUiCarrito()

        val opcionesCliente = mutableListOf("Consumidor Final")
        opcionesCliente.addAll(clientesList.map { nombreClienteDto(it) })
        spCliente.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, opcionesCliente))
        spCliente.threshold = 0
        spCliente.setOnClickListener { spCliente.showDropDown() }

        spCliente.setOnItemClickListener { parent, _, position, _ ->
            val seleccion = parent.getItemAtPosition(position) as String
            if (seleccion == "Consumidor Final") {
                setConsumidorFinalUi()
            } else {
                val clienteEncontrado = clientesList.find { nombreClienteDto(it) == seleccion }
                if (clienteEncontrado != null) {
                    seleccionarClienteUi(clienteEncontrado)
                }
            }
        }

        val prodNombres = productosList.map { it.nombre ?: "" }
        spProducto.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, prodNombres))
        spProducto.threshold = 1
        spProducto.setOnItemClickListener { _, _, _, _ ->
            actualizarStockDisponibleUi()
        }

        val bodegNombres = bodegasList.map { it.nombre }
        spBodega.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodegNombres))
        spBodega.threshold = 0
        spBodega.setOnClickListener { spBodega.showDropDown() }
        spBodega.setOnItemClickListener { _, _, _, _ ->
            actualizarStockDisponibleUi()
        }
        if (bodegasList.size == 1) spBodega.setText(bodegasList[0].nombre, false)

        val metodosPago = listOf("EFECTIVO", "TRANSFERENCIA", "TARJETA_CREDITO")
        spMetodoPago.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metodosPago))
        spMetodoPago.threshold = 0
        spMetodoPago.setText("EFECTIVO", false)
        spMetodoPago.setOnClickListener { spMetodoPago.showDropDown() }
        spMetodoPago.setOnItemClickListener { _, _, position, _ ->
            facturaMetodoPago = metodosPago[position]
            tilCuotas.visibility = if (facturaMetodoPago == "TARJETA_CREDITO") View.VISIBLE else View.GONE
            layoutSimuladorTarjeta.visibility = if (facturaMetodoPago == "TARJETA_CREDITO") View.VISIBLE else View.GONE
            if (facturaMetodoPago != "TARJETA_CREDITO") {
                facturaCuotas = 0
                etCuotas.setText("")
                facturaTarjetaNumero = ""
                facturaTarjetaVence = ""
                facturaTarjetaCvc = ""
                etNumeroTarjeta.setText("")
                etVencimientoTarjeta.setText("")
                etCvcTarjeta.setText("")
            }
            actualizarUiCarrito()
        }

        etNumeroTarjeta.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                facturaTarjetaNumero = s?.toString()?.trim().orEmpty()
                actualizarUiCarrito()
            }
        })

        etVencimientoTarjeta.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                facturaTarjetaVence = s?.toString()?.trim().orEmpty()
            }
        })

        etCvcTarjeta.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                facturaTarjetaCvc = s?.toString()?.trim().orEmpty()
            }
        })

        etCuotas.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                facturaCuotas = s?.toString()?.trim()?.toIntOrNull() ?: 0
            }
        })

        etCantidad.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { actualizarStockDisponibleUi() }
        })

        btnAgregar.setOnClickListener {
            agregarDesdeFormularioManual()
        }

        etDescuentoGlobal.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                facturaDescuentoGlobalPorcentaje = s?.toString()?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
                actualizarUiCarrito()
            }
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setOnDismissListener {
                cancelarAsistenteVoz()
                dialogFacturaActivo = null
            }
            .create()

        dialogFacturaActivo = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCerrarModal?.setOnClickListener { dialog.dismiss() }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnEmitir.setOnClickListener { emitirFactura() }

        btnZoeMic?.setOnClickListener {
            if (voiceState == VoiceStep.OFF) {
                comprobarPermisosYEscuchar()
            } else {
                cancelarAsistenteVoz()
            }
        }

        dialog.show()

        if (iniciarConVoz) {
            comprobarPermisosYEscuchar()
        }
    }

    private fun nombreClienteDto(cliente: ClienteResponseDto): String {
        cliente.nombreCompleto?.takeIf { it.isNotBlank() }?.let { return it }
        return "${cliente.primerNombre ?: ""} ${cliente.apellidoPaterno ?: ""}".trim()
    }

    private fun setConsumidorFinalUi() {
        facturaEsConsumidorFinal = true
        facturaClienteId = null
        spClienteRef?.setText("Consumidor Final", false)
    }

    private fun seleccionarClienteUi(cliente: ClienteResponseDto) {
        facturaEsConsumidorFinal = false
        facturaClienteId = cliente.id
        spClienteRef?.setText(nombreClienteDto(cliente), false)
    }

    private fun actualizarStockDisponibleUi() {
        val nombreProd = spProductoRef?.text?.toString()?.trim().orEmpty()
        val nombreBod = spBodegaRef?.text?.toString()?.trim().orEmpty()
        val producto = productosList.find { it.nombre == nombreProd }
        val bodega = bodegasList.find { it.nombre == nombreBod }

        if (producto?.id != null && bodega != null) {
            val stock = stockDisponibleNeto(producto.id, bodega.id)
            tvStockRef?.text = "Stock disponible: $stock"
            tvStockRef?.setTextColor(if (stock > 0) Color.parseColor("#059669") else Color.parseColor("#DC2626"))
        } else {
            tvStockRef?.text = ""
        }
    }

    private fun agregarDesdeFormularioManual() {
        val nombreProd = spProductoRef?.text?.toString()?.trim().orEmpty()
        val nombreBod = spBodegaRef?.text?.toString()?.trim().orEmpty()
        val cantidadTxt = etCantidadRef?.text?.toString()?.trim().orEmpty()

        if (nombreProd.isBlank() || nombreBod.isBlank() || cantidadTxt.isBlank()) {
            Toast.makeText(this, "Completa producto, bodega y cantidad.", Toast.LENGTH_SHORT).show()
            return
        }

        val producto = productosList.find { it.nombre == nombreProd }
        val bodega = bodegasList.find { it.nombre == nombreBod }
        val cantidadDeseada = cantidadTxt.toIntOrNull() ?: 0

        if (producto?.id == null || bodega == null || cantidadDeseada <= 0) {
            Toast.makeText(this, "Selecciona un producto y bodega válidos.", Toast.LENGTH_SHORT).show()
            return
        }

        val stockActual = stockDisponibleNeto(producto.id, bodega.id)
        if (stockActual <= 0) {
            Toast.makeText(this, "No hay stock disponible de este producto en esa bodega.", Toast.LENGTH_SHORT).show()
            return
        }

        var cantidadFinal = cantidadDeseada
        if (cantidadFinal > stockActual) {
            cantidadFinal = stockActual
            Toast.makeText(this, "Stock limitado: solo hay $stockActual unidades disponibles.", Toast.LENGTH_SHORT).show()
        }

        val descuentoItemPct = etDescuentoItemRef?.text?.toString()?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        agregarProductoAlCarrito(producto, cantidadFinal, bodega.id, descuentoItemPct)

        spProductoRef?.setText("", false)
        etCantidadRef?.setText("1")
        etDescuentoItemRef?.setText("")
        tvStockRef?.text = ""
    }

    private fun mostrarDialogoEditarItemCarrito(posicion: Int) {
        val item = carritoTemporal.getOrNull(posicion) ?: return
        val stockMaximo = stockDisponibleNeto(item.productoId, item.bodegaId, excluirIndice = posicion)

        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, 0)
        }

        val tvInfo = TextView(this).apply {
            text = "${item.nombreProducto}\nPrecio unitario: $${String.format(Locale.US, "%.2f", item.precioUnitario)}"
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        }

        val etCantidad = EditText(this).apply {
            hint = "Cantidad (stock disponible: $stockMaximo)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(item.cantidad.toString())
        }

        val etDescuento = EditText(this).apply {
            hint = "Descuento % (0-100)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (item.descuentoPorcentaje > 0) item.descuentoPorcentaje.toString() else "")
        }
        val margenDescuento = (12 * resources.displayMetrics.density).toInt()

        contenedor.addView(tvInfo)
        contenedor.addView(etCantidad)
        contenedor.addView(
            etDescuento,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = margenDescuento }
        )

        AlertDialog.Builder(this)
            .setTitle("Editar producto")
            .setView(contenedor)
            .setPositiveButton("Guardar") { dialog, _ ->
                dialog.dismiss()
                val nuevaCantidad = etCantidad.text?.toString()?.trim()?.toIntOrNull()
                if (nuevaCantidad == null || nuevaCantidad <= 0) {
                    Toast.makeText(this, "La cantidad debe ser un número mayor a 0.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (nuevaCantidad > stockMaximo) {
                    Toast.makeText(this, "Solo hay $stockMaximo disponibles de ${item.nombreProducto}.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val nuevoDescuento = etDescuento.text?.toString()?.trim()?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0

                if (posicion in carritoTemporal.indices) {
                    carritoTemporal[posicion] = item.copy(cantidad = nuevaCantidad, descuentoPorcentaje = nuevoDescuento)
                    actualizarUiCarrito()
                    Toast.makeText(this, "Producto actualizado.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Quitar del ticket") { dialog, _ ->
                dialog.dismiss()
                if (posicion in carritoTemporal.indices) {
                    carritoTemporal.removeAt(posicion)
                    actualizarUiCarrito()
                }
            }
            .show()
    }

    private fun agregarProductoAlCarrito(producto: ProductoResponseDto, cantidad: Int, bodegaId: Long, descuentoPorcentaje: Double = 0.0) {
        if (cantidad <= 0) return
        var precio = producto.precioUnitario ?: 0.0
        if (precio <= 0.0) precio = producto.costoPromedio ?: 0.0

        val existenteIndex = carritoTemporal.indexOfFirst { it.productoId == producto.id && it.bodegaId == bodegaId }
        if (existenteIndex != -1) {
            val actual = carritoTemporal[existenteIndex]
            val nuevoDescuento = if (descuentoPorcentaje > 0.0) descuentoPorcentaje else actual.descuentoPorcentaje
            carritoTemporal[existenteIndex] = actual.copy(cantidad = actual.cantidad + cantidad, descuentoPorcentaje = nuevoDescuento)
        } else {
            carritoTemporal.add(
                ItemCarritoFactura(
                    productoId = producto.id ?: 0L,
                    bodegaId = bodegaId,
                    cantidad = cantidad,
                    nombreProducto = producto.nombre ?: "Producto",
                    precioUnitario = precio,
                    descuentoPorcentaje = descuentoPorcentaje
                )
            )
        }
        actualizarUiCarrito()
    }

    private fun subtotalCarritoConDescuentosDeLinea(): Double = carritoTemporal.sumOf { it.subtotalConDescuento }

    private fun montoDescuentoGlobal(): Double =
        subtotalCarritoConDescuentosDeLinea() * (facturaDescuentoGlobalPorcentaje.coerceIn(0.0, 100.0) / 100.0)

    private fun totalCarritoFinal(): Double {
        val total = subtotalCarritoConDescuentosDeLinea() - montoDescuentoGlobal()
        return if (total > 0) total else 0.0
    }

    private fun actualizarUiCarrito() {
        carritoAdapter?.actualizar(carritoTemporal)
        val montoDescGlobal = montoDescuentoGlobal()
        val total = totalCarritoFinal()
        tvTotalRef?.text = "A COBRAR: $${String.format(Locale.US, "%.2f", total)}"
        tvItemsCountRef?.text = "${carritoTemporal.size} items"

        if (facturaMetodoPago == "TARJETA_CREDITO") {
            tvInfoPagoTarjetaRef?.visibility = View.VISIBLE
            val numeroLimpio = facturaTarjetaNumero.filter { it.isDigit() }
            tvInfoPagoTarjetaRef?.text = if (numeroLimpio.length >= 4) {
                "💳 Pago con Tarjeta (**** ${numeroLimpio.takeLast(4)})"
            } else {
                "💳 Pago con Tarjeta"
            }
        } else {
            tvInfoPagoTarjetaRef?.visibility = View.GONE
        }

        if (montoDescGlobal > 0.0) {
            tvDescuentoGlobalMontoRef?.visibility = View.VISIBLE
            tvDescuentoGlobalMontoRef?.text = "Descuento global aplicado: -$${String.format(Locale.US, "%.2f", montoDescGlobal)}"
        } else {
            tvDescuentoGlobalMontoRef?.visibility = View.GONE
        }
        val vacio = carritoTemporal.isEmpty()
        layoutEmptyCartRef?.visibility = if (vacio) View.VISIBLE else View.GONE
        rvCarritoRef?.visibility = if (vacio) View.GONE else View.VISIBLE
    }

    private fun obtenerStock(productoId: Long?, bodegaId: Long?): Int {
        if (productoId == null || bodegaId == null) return 0
        val inv = inventarioList.find { it.productoId == productoId && it.bodegaId == bodegaId }
        return inv?.cantidadActual ?: 0
    }

    private fun stockDisponibleNeto(productoId: Long?, bodegaId: Long?, excluirIndice: Int = -1): Int {
        if (productoId == null || bodegaId == null) return 0
        val stockFisico = obtenerStock(productoId, bodegaId)
        val reservadoEnCarrito = carritoTemporal.withIndex()
            .filter { (indice, linea) -> indice != excluirIndice && linea.productoId == productoId && linea.bodegaId == bodegaId }
            .sumOf { it.value.cantidad }
        return (stockFisico - reservadoEnCarrito).coerceAtLeast(0)
    }

    private fun buscarBodegaConStock(productoId: Long, bodegaPreferidaId: Long?): BodegaDto? {
        if (bodegaPreferidaId != null) {
            val stockPreferido = stockDisponibleNeto(productoId, bodegaPreferidaId)
            if (stockPreferido > 0) {
                return bodegasList.find { it.id == bodegaPreferidaId }
            }
        }
        return bodegasList.firstOrNull { bodega ->
            stockDisponibleNeto(productoId, bodega.id) > 0
        }
    }

    // ==========================================
    // RECONOCIMIENTO Y PROCESAMIENTO DE VOZ (ZOE)
    // ==========================================

    private fun comprobarPermisosYEscuchar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            iniciarFlujoVoz()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarFlujoVoz() {
        voiceState = VoiceStep.ESCUCHANDO
        btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
        tvZoeTranscripcionRef?.visibility = View.VISIBLE
        tvZoeTranscripcionRef?.text = "Escuchando... Di algo como 'Agregar 2 cocas a Consumidor Final'"
        escucharVoz()
    }

    private fun escucharVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            cancelarAsistenteVoz()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceState = VoiceStep.PROCESANDO
                    tvZoeTranscripcionRef?.text = "Procesando con Zoe..."
                }

                override fun onError(error: Int) {
                    tvZoeTranscripcionRef?.text = "No entendí bien. Toca el micrófono para intentar de nuevo."
                    cancelarAsistenteVoz()
                }

                override fun onResults(results: Bundle?) {
                    val coincidencias = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!coincidencias.isNullOrEmpty()) {
                        val textoEscuchado = coincidencias[0]
                        tvZoeTranscripcionRef?.text = "\"$textoEscuchado\""
                        procesarFraseConIA(textoEscuchado)
                    } else {
                        tvZoeTranscripcionRef?.text = "No escuché ninguna palabra."
                        cancelarAsistenteVoz()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun procesarFraseConIA(frase: String) {
        lifecycleScope.launch {
            val nombresClientes = clientesList.map { nombreClienteDto(it) }
            val nombresProductos = productosList.mapNotNull { it.nombre }
            val nombresBodegas = bodegasList.map { it.nombre }

            val resultado = ZoeVoiceAI.interpretar(
                fraseUsuario = frase,
                nombresClientes = nombresClientes,
                nombresProductos = nombresProductos,
                nombresBodegas = nombresBodegas
            )

            if (resultado != null) {
                aplicarResultadoIA(resultado)
            } else {
                tvZoeTranscripcionRef?.text = "No pude interpretar la frase. Intenta nuevamente."
                hablar("No logré entender la instrucción. ¿Podrías repetirla?")
            }
            cancelarAsistenteVoz()
        }
    }

    private fun aplicarResultadoIA(res: ResultadoVozFactura) {
        val resumenAcciones = mutableListOf<String>()

        // 1. Cliente
        res.cliente?.let { clienteStr ->
            if (clienteStr.equals("CONSUMIDOR_FINAL", ignoreCase = true)) {
                setConsumidorFinalUi()
                resumenAcciones.add("Cliente: Consumidor Final")
            } else {
                val clienteEncontrado = clientesList.find { c ->
                    nombreClienteDto(c).contains(clienteStr, ignoreCase = true) ||
                            c.dni?.contains(clienteStr) == true
                }
                if (clienteEncontrado != null) {
                    seleccionarClienteUi(clienteEncontrado)
                    resumenAcciones.add("Cliente: ${nombreClienteDto(clienteEncontrado)}")
                }
            }
        }

        // 2. Método de pago
        res.metodoPago?.let { metodo ->
            facturaMetodoPago = metodo
            spMetodoPagoRef?.setText(metodo, false)
            tilCuotasRef?.visibility = if (metodo == "TARJETA_CREDITO") View.VISIBLE else View.GONE
            layoutSimuladorTarjetaRef?.visibility = if (metodo == "TARJETA_CREDITO") View.VISIBLE else View.GONE
            resumenAcciones.add("Pago: $metodo")
        }

        // 3. Cuotas
        res.cuotas?.let { c ->
            facturaCuotas = c
            etCuotasRef?.setText(c.toString())
        }

        // 4. Bodega preferida
        res.bodega?.let { bodStr ->
            val bodegaEncontrada = bodegasList.find { it.nombre.contains(bodStr, ignoreCase = true) }
            if (bodegaEncontrada != null) {
                pendingBodegaId = bodegaEncontrada.id
                spBodegaRef?.setText(bodegaEncontrada.nombre, false)
                resumenAcciones.add("Bodega: ${bodegaEncontrada.nombre}")
            }
        }

        // 5. Eliminar producto
        res.eliminarProducto?.let { elimStr ->
            val pos = carritoTemporal.indexOfFirst { it.nombreProducto.contains(elimStr, ignoreCase = true) }
            if (pos != -1) {
                val eliminado = carritoTemporal.removeAt(pos)
                resumenAcciones.add("Quitado: ${eliminado.nombreProducto}")
            }
        }

        // 6. Agregar Items
        val bodegaIdUsar = pendingBodegaId ?: bodegasList.firstOrNull()?.id
        for (item in res.items) {
            val productoEncontrado = productosList.find { p ->
                p.nombre?.contains(item.producto, ignoreCase = true) == true
            }

            if (productoEncontrado?.id != null) {
                val bodegaOptima = buscarBodegaConStock(productoEncontrado.id, bodegaIdUsar)
                if (bodegaOptima != null) {
                    val cant = item.cantidad ?: 1
                    val desc = item.descuentoPorcentaje?.toDouble() ?: 0.0
                    agregarProductoAlCarrito(productoEncontrado, cant, bodegaOptima.id, desc)
                    resumenAcciones.add("Agregado: $cant x ${productoEncontrado.nombre}")
                } else {
                    resumenAcciones.add("Sin stock: ${productoEncontrado.nombre}")
                }
            }
        }

        // 7. Descuento Global
        res.descuentoGlobalPorcentaje?.let { descGlobal ->
            facturaDescuentoGlobalPorcentaje = descGlobal.toDouble()
            etDescuentoGlobalRef?.setText(descGlobal.toString())
            resumenAcciones.add("Descuento general: $descGlobal%")
        }

        actualizarUiCarrito()

        // Respuesta hablada de Zoe
        if (resumenAcciones.isNotEmpty()) {
            val textoVoz = resumenAcciones.joinToString(". ")
            hablar(textoVoz)
        }

        // 8. Emitir Factura si la IA confirmó la orden explícita
        if (res.emitirFactura) {
            emitirFactura()
        }
    }

    private fun hablar(texto: String) {
        textToSpeech?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "ZOE_SPEECH")
    }

    private fun cancelarAsistenteVoz() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceState = VoiceStep.OFF
        btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563EB"))
    }

    // ==========================================
    // EMISIÓN Y DETALLE DE FACTURA
    // ==========================================

    private fun emitirFactura() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        if (!facturaEsConsumidorFinal && facturaClienteId == null) {
            Toast.makeText(this, "Selecciona un cliente o 'Consumidor Final'", Toast.LENGTH_SHORT).show()
            hablar("Por favor selecciona un cliente antes de emitir.")
            return
        }

        if (carritoTemporal.isEmpty()) {
            Toast.makeText(this, "El carrito está vacío.", Toast.LENGTH_SHORT).show()
            hablar("El carrito está vacío. Agrega al menos un producto.")
            return
        }

        if (facturaMetodoPago == "TARJETA_CREDITO" && facturaTarjetaNumero.filter { it.isDigit() }.length < 13) {
            Toast.makeText(this, "Ingresa un número de tarjeta válido.", Toast.LENGTH_SHORT).show()
            hablar("Ingresa los datos de la tarjeta de crédito para continuar.")
            return
        }

        val detallesDto = carritoTemporal.map { item ->
            DetalleFacturaRequestDto(
                productoId = item.productoId,
                bodegaId = item.bodegaId,
                cantidad = item.cantidad,
                descuento = item.descuentoMonto,
                tarjeta = null
            )
        }

        val tarjetaInfoCompleta = if (facturaMetodoPago == "TARJETA_CREDITO") {
            "Tarjeta ****${facturaTarjetaNumero.takeLast(4)} | Vence: $facturaTarjetaVence"
        } else null

        val facturaRequest = FacturaRequestDto(
            clienteId = if (facturaEsConsumidorFinal) null else facturaClienteId,
            metodoPago = facturaMetodoPago,
            tarjeta = tarjetaInfoCompleta,
            numeroCuotas = if (facturaMetodoPago == "TARJETA_CREDITO") facturaCuotas else null,
            descuentoGlobal = montoDescuentoGlobal(),
            detalles = detallesDto
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.crearFactura(authHeader, negocioId, facturaRequest)
                if (response.isSuccessful) {
                    Toast.makeText(this@HistorialFacturasActivity, "¡Factura emitida exitosamente!", Toast.LENGTH_LONG).show()
                    hablar("Factura emitida exitosamente.")
                    dialogFacturaActivo?.dismiss()
                    cargarFacturas()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Error al emitir factura"
                    Toast.makeText(this@HistorialFacturasActivity, errorMsg, Toast.LENGTH_LONG).show()
                    hablar("Hubo un error al emitir la factura.")
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistorialFacturasActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarDetalleFacturaDialog(factura: FacturaResponseDto) {
        val webView = WebView(this).apply {
            webViewClient = WebViewClient()
            loadDataWithBaseURL(null, generarHtmlFactura(factura), "text/html", "UTF-8", null)
        }

        AlertDialog.Builder(this)
            .setTitle("Factura #${factura.numeroFactura ?: factura.id}")
            .setView(webView)
            .setPositiveButton("Imprimir") { _, _ -> imprimirFactura(webView) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun generarHtmlFactura(f: FacturaResponseDto): String {
        val negocio = negocioActual?.nombreComercial ?: "Mi Negocio"
        val ruc = negocioActual?.ruc ?: "9999999999001"
        val clienteNom = f.nombreClienteFormateado

        val filasItems = StringBuilder()
        f.detalles?.forEach { det ->
            val nombreItem = det.nombreProducto ?: det.producto?.nombre ?: "Producto"
            val cantidadItem = det.cantidad ?: 0
            val precioUnit = det.precioUnitario ?: 0.0
            val subtotal = det.subtotalItem ?: 0.0

            filasItems.append("""
                <tr>
                    <td>$nombreItem</td>
                    <td style="text-align:center">$cantidadItem</td>
                    <td style="text-align:right">$${String.format(Locale.US, "%.2f", precioUnit)}</td>
                    <td style="text-align:right">$${String.format(Locale.US, "%.2f", subtotal)}</td>
                </tr>
            """.trimIndent())
        }

        return """
            <html>
            <head><style>
                body { font-family: sans-serif; padding: 12px; color: #333; }
                h2 { margin-bottom: 2px; }
                table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                th, td { border-bottom: 1px solid #ddd; padding: 6px; font-size: 12px; }
                th { text-align: left; background: #f1f5f9; }
                .total { font-weight: bold; font-size: 14px; text-align: right; margin-top: 12px; }
            </style></head>
            <body>
                <h2>$negocio</h2>
                <p style="font-size:11px; margin-top:0;">RUC: $ruc<br>Factura N°: ${f.numeroFactura ?: f.id}</p>
                <hr>
                <p style="font-size:12px;"><strong>Cliente:</strong> $clienteNom<br><strong>Método de Pago:</strong> ${f.metodoPago ?: "EFECTIVO"}</p>
                <table>
                    <tr><th>Producto</th><th>Cant</th><th>P.U.</th><th>Total</th></tr>
                    $filasItems
                </table>
                <div class="total">TOTAL A PAGAR: $${String.format(Locale.US, "%.2f", f.totalCalculado)}</div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun imprimirFactura(webView: WebView) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        val printAdapter = webView.createPrintDocumentAdapter("Factura_Movildilo")
        printManager?.print("Factura_Movildilo", printAdapter, PrintAttributes.Builder().build())
    }

    override fun onDestroy() {
        voiceHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}