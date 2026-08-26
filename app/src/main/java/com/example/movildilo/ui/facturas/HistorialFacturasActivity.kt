package com.example.movildilo.ui.facturas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import com.example.movildilo.data.model.dto.DetalleFacturaResponseDto
import com.example.movildilo.data.model.dto.FacturaRequestDto
import com.example.movildilo.data.model.dto.FacturaResponseDto
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.data.model.dto.ItemCarritoFactura
import com.example.movildilo.data.model.dto.NegocioResponseDto
import com.example.movildilo.data.model.dto.ProductoResponseDto
import com.example.movildilo.ui.adapters.FacturaCarritoAdapter
import com.example.movildilo.ui.adapters.FacturasAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

private enum class VoiceStep { OFF, ESCUCHANDO, CONFIRMAR, SELECCIONAR_OPCION, CONFIRMAR_VACIAR_CARRITO }

private data class AmbiguoPendiente(
    val opciones: List<ProductoResponseDto>,
    val cantidad: Int,
    val descuentoPorcentaje: Double
)

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
    private var dialogOpcionesAmbiguas: AlertDialog? = null
    private var spClienteRef: AutoCompleteTextView? = null
    private var spMetodoPagoRef: AutoCompleteTextView? = null
    private var tilCuotasRef: TextInputLayout? = null
    private var etCuotasRef: TextInputEditText? = null
    private var layoutSimuladorTarjetaRef: View? = null
    private var etNumeroTarjetaRef: TextInputEditText? = null
    private var etVencimientoTarjetaRef: TextInputEditText? = null
    private var etCvcTarjetaRef: TextInputEditText? = null
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
    private var metodoPagoConfirmadoPorVoz: Boolean = false
    private var pendingBodegaId: Long? = null
    private var intentosReconexion: Int = 0
    private var intentosSinReconocer: Int = 0
    private val idiomasVoz = listOf("es-AR", "es-419", "es-EC", "es-ES", "es-US")
    private var idiomaVozIndex: Int = 0
    private val voiceHandler = Handler(Looper.getMainLooper())

    private var productosOpcionesPendientes: List<ProductoResponseDto> = emptyList()
    private var cantidadPendienteOpcion: Int = 1
    private var descuentoPendienteOpcion: Double = 0.0
    private val colaAmbiguos = ArrayDeque<AmbiguoPendiente>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            iniciarFlujoVoz()
        } else {
            val puedeVolverAPreguntar = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            if (puedeVolverAPreguntar) {
                Toast.makeText(this, "Se requiere permiso de micrófono para usar a Zoe.", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Falta el permiso de micrófono")
                    .setMessage("Para usar a Zoe por voz, activa el permiso de Micrófono en Ajustes > Apps > MovilDilo > Permisos.")
                    .setPositiveButton("Abrir Ajustes") { d, _ ->
                        d.dismiss()
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
                    .show()
            }
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
            cargarCatalogosFactura { cargarFacturas() }
        } else {
            Toast.makeText(this, "No se encontró un negocio activo en la sesión.", Toast.LENGTH_SHORT).show()
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
                configurarVozZoe()
            }
        }
    }

    private fun configurarVozZoe() {
        val tts = textToSpeech ?: return

        val localesPreferidos = listOf(
            Locale("es", "AR"),
            Locale("es", "419"),
            Locale("es", "US"),
            Locale("es", "ES")
        )
        var localeAplicado: Locale? = null
        for (locale in localesPreferidos) {
            val resultado = tts.setLanguage(locale)
            if (resultado != TextToSpeech.LANG_MISSING_DATA && resultado != TextToSpeech.LANG_NOT_SUPPORTED) {
                localeAplicado = locale
                break
            }
        }
        if (localeAplicado == null) {
            tts.setLanguage(Locale("es", "ES"))
        }

        val vocesDisponibles = try { tts.voices } catch (_: Exception) { null }
        if (!vocesDisponibles.isNullOrEmpty()) {
            val candidatas = vocesDisponibles.filter {
                it.locale.language == "es" &&
                        !it.isNetworkConnectionRequired &&
                        it.quality >= android.speech.tts.Voice.QUALITY_NORMAL
            }
            val vozArgentina = candidatas
                .filter {
                    it.locale.country.equals("AR", ignoreCase = true) ||
                            it.name.contains("ar", ignoreCase = true) && it.name.contains("es", ignoreCase = true)
                }
                .maxByOrNull { it.quality }
            val vozFemenina = candidatas
                .filter { it.name.contains("female", ignoreCase = true) || it.name.contains("#female", ignoreCase = true) }
                .maxByOrNull { it.quality }
            val mejorVoz = vozArgentina
                ?: vozFemenina
                ?: candidatas.maxByOrNull { it.quality }
                ?: vocesDisponibles.filter { it.locale.language == "es" }.maxByOrNull { it.quality }
            if (mejorVoz != null) {
                tts.voice = mejorVoz
            }
        }

        tts.setSpeechRate(0.97f)
        tts.setPitch(1.0f)
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
        carritoTemporal.clear()
        metodoPagoConfirmadoPorVoz = false
        colaAmbiguos.clear()
        pendingBodegaId = null
        productosOpcionesPendientes = emptyList()
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

        val layoutSimuladorTarjeta = dialogView.findViewById<View>(R.id.layoutSimuladorTarjeta)
        val etNumeroTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etNumeroTarjeta)
        val etVencimientoTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etVencimientoTarjeta)
        val etCvcTarjeta = dialogView.findViewById<TextInputEditText>(R.id.etCvcTarjeta)

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

        btnZoeMicRef = btnZoeMic
        tvZoeTranscripcionRef = tvZoeTranscripcion
        spClienteRef = spCliente
        spMetodoPagoRef = spMetodoPago
        tilCuotasRef = tilCuotas
        etCuotasRef = etCuotas
        layoutSimuladorTarjetaRef = layoutSimuladorTarjeta
        etNumeroTarjetaRef = etNumeroTarjeta
        etVencimientoTarjetaRef = etVencimientoTarjeta
        etCvcTarjetaRef = etCvcTarjeta
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

        carritoAdapter = FacturaCarritoAdapter(carritoTemporal) { posicion ->
            if (posicion in carritoTemporal.indices) {
                carritoTemporal.removeAt(posicion)
                actualizarUiCarrito()
            }
        }
        rvCarrito.layoutManager = LinearLayoutManager(this)
        rvCarrito.adapter = carritoAdapter
        actualizarUiCarrito()

        val opcionesCliente = mutableListOf("Consumidor Final")
        opcionesCliente.addAll(clientesList.map { nombreClienteDto(it) })
        spCliente.setAdapter(crearAdapterBusquedaParcial(opcionesCliente))
        spCliente.threshold = 0
        spCliente.setOnClickListener { spCliente.showDropDown() }
        spCliente.setOnItemClickListener { _, _, _, _ ->
            val textoElegido = spCliente.text?.toString()?.trim().orEmpty()
            if (textoElegido.equals("Consumidor Final", ignoreCase = true)) {
                setConsumidorFinalUi()
            } else {
                val cliente = clientesList.find { nombreClienteDto(it) == textoElegido }
                if (cliente != null) seleccionarClienteUi(cliente)
            }
        }

        spProducto.setAdapter(crearAdapterBusquedaProducto(productosList))
        spProducto.threshold = 1
        spProducto.setOnItemClickListener { _, _, _, _ -> actualizarStockDisponibleUi() }

        val bodegNombres = bodegasList.map { it.nombre }
        spBodega.setAdapter(crearAdapterBusquedaParcial(bodegNombres))
        spBodega.threshold = 0
        spBodega.setOnClickListener { spBodega.showDropDown() }
        spBodega.setOnItemClickListener { _, _, _, _ -> actualizarStockDisponibleUi() }
        if (bodegasList.size == 1) spBodega.setText(bodegasList[0].nombre, false)

        val metodosPago = listOf("EFECTIVO", "TRANSFERENCIA", "TARJETA_CREDITO")
        spMetodoPago.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metodosPago))
        spMetodoPago.threshold = 0
        spMetodoPago.setText("EFECTIVO", false)
        spMetodoPago.setOnClickListener { spMetodoPago.showDropDown() }
        spMetodoPago.setOnItemClickListener { _, _, position, _ ->
            onMetodoPagoSeleccionado(metodosPago[position])
        }

        etCantidad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { actualizarStockDisponibleUi() }
        })

        btnAgregar.setOnClickListener { agregarDesdeFormularioManual() }

        etDescuentoGlobal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, before: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
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

    private fun actualizarMetodoPagoUi(metodo: String) {
        facturaMetodoPago = metodo
        spMetodoPagoRef?.setText(metodo, false)
        val esTarjeta = metodo == "TARJETA_CREDITO"
        tilCuotasRef?.visibility = if (esTarjeta) View.VISIBLE else View.GONE
        layoutSimuladorTarjetaRef?.visibility = if (esTarjeta) View.VISIBLE else View.GONE
    }

    private fun permiteTarjetaCredito(): Boolean {
        return !facturaEsConsumidorFinal && facturaClienteId != null
    }

    private fun bloquearTarjetaSiConsumidorFinal(avisar: Boolean = false): Boolean {
        if (facturaEsConsumidorFinal && facturaMetodoPago == "TARJETA_CREDITO") {
            actualizarMetodoPagoUi("EFECTIVO")
            facturaCuotas = 0
            etCuotasRef?.setText("")
            etNumeroTarjetaRef?.setText("")
            etVencimientoTarjetaRef?.setText("")
            etCvcTarjetaRef?.setText("")
            metodoPagoConfirmadoPorVoz = true
            if (avisar) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Tarjeta no permitida")
                    .setMessage("Con Consumidor Final solo se puede pagar en efectivo o transferencia. Elige un cliente registrado para pagar con tarjeta de crédito.")
                    .setPositiveButton("Entendido", null)
                    .show()
            }
            return true
        }
        return false
    }

    private fun onMetodoPagoSeleccionado(metodo: String) {
        if (metodo == "TARJETA_CREDITO" && !permiteTarjetaCredito()) {
            actualizarMetodoPagoUi("EFECTIVO")
            facturaCuotas = 0
            etCuotasRef?.setText("")
            MaterialAlertDialogBuilder(this)
                .setTitle("Tarjeta no permitida")
                .setMessage(
                    if (facturaEsConsumidorFinal)
                        "Consumidor Final no puede pagar con tarjeta de crédito. Usa efectivo o transferencia, o selecciona un cliente registrado."
                    else
                        "Selecciona primero un cliente registrado para pagar con tarjeta."
                )
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        actualizarMetodoPagoUi(metodo)
        metodoPagoConfirmadoPorVoz = true
    }

    private fun nombreClienteDto(cliente: ClienteResponseDto): String {
        val baseNombre = when {
            !cliente.nombreCompleto.isNullOrBlank() -> cliente.nombreCompleto
            else -> "${cliente.primerNombre ?: ""} ${cliente.apellidoPaterno ?: ""}".trim()
        }
        val identificacion = cliente.dni?.takeIf { it.isNotBlank() }
        return if (identificacion != null) {
            "$baseNombre ($identificacion)"
        } else {
            baseNombre
        }
    }

    private fun setConsumidorFinalUi() {
        facturaEsConsumidorFinal = true
        facturaClienteId = null
        spClienteRef?.setText("Consumidor Final", false)
        bloquearTarjetaSiConsumidorFinal(avisar = true)
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
            val stock = obtenerStock(producto.id, bodega.id)
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

        val stockActual = obtenerStock(producto.id, bodega.id)
        if (stockActual <= 0) {
            Toast.makeText(this, "No hay stock disponible de este producto en esa bodega.", Toast.LENGTH_SHORT).show()
            return
        }

        var cantidadFinal = cantidadDeseada
        if (cantidadFinal > stockActual) {
            cantidadFinal = stockActual
            Toast.makeText(this, "Stock limitado: solo hay $stockActual unidades.", Toast.LENGTH_SHORT).show()
        }

        val descuentoItemPct = etDescuentoItemRef?.text?.toString()?.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
        agregarProductoAlCarrito(producto, cantidadFinal, bodega.id, descuentoItemPct)

        spProductoRef?.setText("", false)
        etCantidadRef?.setText("1")
        etDescuentoItemRef?.setText("")
        tvStockRef?.text = ""
    }

    private fun obtenerPrecioFactura(producto: ProductoResponseDto): Double {
        val costo = producto.costoPromedio ?: 0.0
        return if (costo > 0.0) costo else (producto.precioUnitario ?: 0.0)
    }

    private fun agregarProductoAlCarrito(producto: ProductoResponseDto, cantidad: Int, bodegaId: Long, descuentoPorcentaje: Double = 0.0) {
        if (cantidad <= 0) return
        val precio = obtenerPrecioFactura(producto)

        val existenteIndex = carritoTemporal.indexOfFirst { it.productoId == producto.id && it.bodegaId == bodegaId }
        if (existenteIndex != -1) {
            val actual = carritoTemporal[existenteIndex]
            val nuevoDescuento = if (descuentoPorcentaje > 0.0) descuentoPorcentaje else actual.descuentoPorcentaje
            carritoTemporal[existenteIndex] = actual.copy(
                cantidad = actual.cantidad + cantidad,
                precioUnitario = precio,
                descuentoPorcentaje = nuevoDescuento
            )
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
        tvTotalRef?.text = "A COBRAR: ${String.format(Locale.US, "$%.2f", total)}"
        tvItemsCountRef?.text = "${carritoTemporal.size} items"
        if (montoDescGlobal > 0.0) {
            tvDescuentoGlobalMontoRef?.visibility = View.VISIBLE
            tvDescuentoGlobalMontoRef?.text = "Descuento global aplicado: -${String.format(Locale.US, "$%.2f", montoDescGlobal)}"
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

    private fun emitirFactura(confirmadaPorVoz: Boolean = false) {
        if ((facturaClienteId == null && !facturaEsConsumidorFinal) || carritoTemporal.isEmpty()) {
            if (confirmadaPorVoz) {
                avanzarPaso(VoiceStep.ESCUCHANDO, "Todavía faltan datos: cliente o productos. ¿Qué agregamos?")
            } else {
                Toast.makeText(this, "Faltan datos para emitir la factura (cliente y/o productos).", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (facturaMetodoPago == "TARJETA_CREDITO" && !permiteTarjetaCredito()) {
            bloquearTarjetaSiConsumidorFinal(avisar = !confirmadaPorVoz)
            if (confirmadaPorVoz) {
                avanzarPaso(VoiceStep.ESCUCHANDO, "Con Consumidor Final no se puede pagar con tarjeta. Cambié el pago a efectivo. ¿Qué más hacemos?")
            }
            return
        }

        if (facturaMetodoPago == "TARJETA_CREDITO" && facturaCuotas <= 0) {
            if (confirmadaPorVoz) {
                avanzarPaso(VoiceStep.ESCUCHANDO, "Falta elegir las cuotas de la tarjeta. ¿En cuántas cuotas?")
            } else {
                Toast.makeText(this, "Selecciona en cuántas cuotas se paga con la tarjeta.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (confirmadaPorVoz) {
            procesarEmisionFactura()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar emisión de factura")
            .setMessage("¿Deseas emitir la factura? Sí o no.")
            .setPositiveButton("Sí, emitir") { dialogConfirm, _ ->
                dialogConfirm.dismiss()
                procesarEmisionFactura()
            }
            .setNegativeButton("No") { dialogConfirm, _ -> dialogConfirm.dismiss() }
            .setCancelable(true)
            .show()
    }

    private fun procesarEmisionFactura() {
        val loadingDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Emitiendo Factura...")
            .setMessage("Un momento por favor.")
            .setCancelable(false)
            .show()

        val authHeader = sessionManager.getAuthHeader()
        if (authHeader == null) {
            loadingDialog.dismiss()
            Toast.makeText(this, "Sesión no válida.", Toast.LENGTH_SHORT).show()
            return
        }

        val payload = FacturaRequestDto(
            clienteId = facturaClienteId,
            metodoPago = facturaMetodoPago,
            numeroCuotas = if (facturaMetodoPago == "TARJETA_CREDITO") facturaCuotas else null,
            descuentoGlobal = montoDescuentoGlobal(),
            detalles = carritoTemporal.map {
                DetalleFacturaRequestDto(
                    productoId = it.productoId,
                    bodegaId = it.bodegaId,
                    cantidad = it.cantidad,
                    descuento = it.descuentoMonto
                )
            }
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.crearFactura(authHeader, negocioId, payload)
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    if (response.isSuccessful) {
                        val factura = response.body()
                        Toast.makeText(this@HistorialFacturasActivity, "¡Factura emitida correctamente!", Toast.LENGTH_SHORT).show()
                        dialogFacturaActivo?.dismiss()
                        cargarFacturas()
                        if (factura != null) prepararGeneracionPDF(factura)
                    } else {
                        Toast.makeText(this@HistorialFacturasActivity, "Error al emitir la factura (${response.code()})", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    Toast.makeText(this@HistorialFacturasActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun comprobarPermisosYEscuchar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            iniciarFlujoVoz()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarFlujoVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible.", Toast.LENGTH_SHORT).show()
            return
        }
        btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))

        val (paso, mensaje) = calcularPasoYMensaje()
        voiceState = paso
        hablar(mensaje) { escucharVoz() }
    }

    private fun calcularPasoYMensaje(): Pair<VoiceStep, String> {
        return when {
            facturaClienteId == null && !facturaEsConsumidorFinal ->
                VoiceStep.ESCUCHANDO to "¡Hola! ¿A quién le facturamos?"

            !metodoPagoConfirmadoPorVoz ->
                VoiceStep.ESCUCHANDO to "Cliente listo. ¿Con qué método de pago cancela? Efectivo, transferencia o tarjeta."

            facturaMetodoPago == "TARJETA_CREDITO" && facturaCuotas <= 0 ->
                VoiceStep.ESCUCHANDO to "¿En cuántas cuotas?"

            carritoTemporal.isEmpty() ->
                VoiceStep.ESCUCHANDO to "El ticket está vacío. ¿Qué producto agregamos?"

            else -> {
                val totalFmt = String.format(Locale.US, "%.2f", totalCarritoFinal())
                VoiceStep.CONFIRMAR to "El precio total a cobrar con el descuento aplicado es de $totalFmt dólares. ¿Deseas emitir la factura o agregar algo más?"
            }
        }
    }

    private fun cancelarAsistenteVoz() {
        voiceState = VoiceStep.OFF
        intentosReconexion = 0
        intentosSinReconocer = 0
        idiomaVozIndex = 0
        escuchaEnCurso = false
        productosOpcionesPendientes = emptyList()
        colaAmbiguos.clear()
        dialogOpcionesAmbiguas?.dismiss()
        dialogOpcionesAmbiguas = null
        voiceHandler.removeCallbacksAndMessages(null)
        textToSpeech?.stop()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7C3AED"))
        tvZoeTranscripcionRef?.text = "Toca el micrófono para interactuar con Zoe por voz."
    }

    private fun hablar(texto: String, alTerminar: (() -> Unit)? = null) {
        tvZoeTranscripcionRef?.text = texto
        val utteranceId = "zoe_${System.currentTimeMillis()}"
        var yaContinuo = false

        fun continuarUnaVez() {
            if (yaContinuo) return
            yaContinuo = true
            if (voiceState != VoiceStep.OFF) {
                voiceHandler.postDelayed({ if (voiceState != VoiceStep.OFF) alTerminar?.invoke() }, 350)
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { runOnUiThread { continuarUnaVez() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { runOnUiThread { continuarUnaVez() } }
        })

        val tts = textToSpeech
        if (tts == null) {
            continuarUnaVez()
        } else {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            val tiempoEstimado = 1800L + (texto.length * 65L)
            voiceHandler.postDelayed({ continuarUnaVez() }, tiempoEstimado)
        }
    }

    private var escuchaEnCurso = false

    private fun nombreErrorVoz(codigo: Int): String = when (codigo) {
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "SIN_RED"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "RED_LENTA"
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENTE"
        SpeechRecognizer.ERROR_SERVER -> "SERVIDOR"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "OCUPADO"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "SIN_PERMISO"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "IDIOMA_NO_SOPORTADO"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "IDIOMA_NO_DISPONIBLE"
        else -> "COD_$codigo"
    }

    private fun escucharVoz(interrupcionActiva: Boolean = false) {
        if (voiceState == VoiceStep.OFF) return
        if (escuchaEnCurso) return

        escuchaEnCurso = true
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val idiomaActual = idiomasVoz[idiomaVozIndex % idiomasVoz.size]
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idiomaActual)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 6000)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 5000)
            putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvZoeTranscripcionRef?.text = "🎙️ Escuchando... Habla ahora"
                btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                tvZoeTranscripcionRef?.text = "⚡ Zoe está pensando..."
                btnZoeMicRef?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7C3AED"))
            }

            override fun onError(error: Int) {
                escuchaEnCurso = false
                if (voiceState == VoiceStep.OFF) return
                val nombreError = nombreErrorVoz(error)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        intentosSinReconocer++
                        if (intentosSinReconocer >= 2) {
                            idiomaVozIndex++
                            intentosSinReconocer = 0
                            tvZoeTranscripcionRef?.text = "No te escuché bien ($nombreError, probando ${idiomasVoz[idiomaVozIndex % idiomasVoz.size]}). Intenta de nuevo 🎙️"
                        } else {
                            tvZoeTranscripcionRef?.text = "No capté nada ($nombreError). Sigo escuchando 🎙️"
                        }
                        reintentarEscucha()
                    }
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        idiomaVozIndex++
                        tvZoeTranscripcionRef?.text = "Idioma no soportado ($nombreError). Probando otro..."
                        reintentarEscucha()
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        cancelarAsistenteVoz()
                        Toast.makeText(this@HistorialFacturasActivity, "Falta permiso de micrófono para usar a Zoe.", Toast.LENGTH_LONG).show()
                    }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        intentosReconexion++
                        tvZoeTranscripcionRef?.text = "Sin conexión de datos para reconocer voz ($nombreError). Reintentando..."
                        val espera = (600L * intentosReconexion).coerceAtMost(4000L)
                        voiceHandler.postDelayed({ if (voiceState != VoiceStep.OFF) escucharVoz() }, espera)
                    }
                    else -> {
                        intentosReconexion++
                        tvZoeTranscripcionRef?.text = "Error del reconocedor ($nombreError). Reintentando..."
                        val espera = (400L * intentosReconexion).coerceAtMost(3000L)
                        voiceHandler.postDelayed({ if (voiceState != VoiceStep.OFF) escucharVoz() }, espera)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                escuchaEnCurso = false
                intentosReconexion = 0
                intentosSinReconocer = 0
                val alternativas = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val texto = alternativas.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                if (texto.isNotEmpty()) {
                    procesarComandoVoz(texto)
                } else {
                    reintentarEscucha()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val texto = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!texto.isNullOrBlank()) tvZoeTranscripcionRef?.text = "\"$texto\""
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            reintentarEscucha()
        }
    }

    private fun reintentarEscucha() {
        if (voiceState == VoiceStep.OFF) return
        voiceHandler.postDelayed({ if (voiceState != VoiceStep.OFF) escucharVoz() }, 500)
    }

    private fun limpiarTexto(texto: String?): String {
        if (texto.isNullOrBlank()) return ""
        val normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return normalizado.replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT).trim()
    }

    private fun crearAdapterBusquedaParcial(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items.toMutableList()) {
            private val original = items.toList()

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val query = limpiarTexto(constraint?.toString())
                        val resultados = if (query.isBlank()) {
                            original
                        } else {
                            original.filter { limpiarTexto(it).contains(query) }
                        }
                        return FilterResults().apply {
                            values = resultados
                            count = resultados.size
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        val nuevos = results?.values as? List<String>
                        if (nuevos != null) addAll(nuevos)
                        notifyDataSetChanged()
                    }

                    override fun convertResultToString(resultValue: Any?): CharSequence {
                        return resultValue as? String ?: ""
                    }
                }
            }
        }
    }

    private fun crearAdapterBusquedaProducto(items: List<ProductoResponseDto>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items.map { it.nombre ?: "" }.toMutableList()) {
            private val original = items.toList()

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val query = limpiarTexto(constraint?.toString())
                        val resultados = if (query.isBlank()) {
                            original
                        } else {
                            original.filter { p ->
                                val nombre = limpiarTexto(p.nombre)
                                val codigo = limpiarTexto(p.codigoPrincipal)
                                nombre.contains(query) || codigo.contains(query)
                            }
                        }.map { it.nombre ?: "" }
                        return FilterResults().apply {
                            values = resultados
                            count = resultados.size
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        clear()
                        val nuevos = results?.values as? List<String>
                        if (nuevos != null) addAll(nuevos)
                        notifyDataSetChanged()
                    }

                    override fun convertResultToString(resultValue: Any?): CharSequence {
                        return resultValue as? String ?: ""
                    }
                }
            }
        }
    }

    private fun avanzarPaso(nuevoPaso: VoiceStep, mensaje: String) {
        voiceState = nuevoPaso
        hablar(mensaje) { escucharVoz() }
    }

    private fun repetirPaso(mensaje: String) {
        hablar(mensaje) { escucharVoz() }
    }

    private fun procesarComandoVoz(transcriptOriginal: String) {
        val transcript = limpiarTexto(transcriptOriginal)

        // Fix 3: Asegurar que si estamos en SELECCIONAR_OPCION, procesemos únicamente la selección
        // y no reinterpretemos el comando de forma general o repetida.
        if (voiceState == VoiceStep.SELECCIONAR_OPCION && productosOpcionesPendientes.isNotEmpty()) {
            procesarSeleccionOpcionPorVoz(transcript)
            return
        }

        if (voiceState == VoiceStep.CONFIRMAR_VACIAR_CARRITO) {
            val afirmativas = listOf("si", "claro", "borra", "vaciar", "hazlo", "de acuerdo", "limpiar", "confirmar")
            if (afirmativas.any { transcript.contains(it) }) {
                resetearEstadoFactura()
                spClienteRef?.setText("", false)
                actualizarUiCarrito()
                val (paso, mensaje) = calcularPasoYMensaje()
                voiceState = paso
                hablar("Listo, vacié la factura por completo. $mensaje") { escucharVoz() }
            } else {
                val (paso, mensaje) = calcularPasoYMensaje()
                voiceState = paso
                hablar("Entendido, mantenemos los productos. $mensaje") { escucharVoz() }
            }
            return
        }

        val comandosLimpiar = listOf("borra todo", "borrar todo", "limpiar carrito", "reiniciar", "vaciar ticket", "cancela todo", "vaciar carrito", "limpiar ticket")
        if (comandosLimpiar.any { transcript.contains(it) }) {
            voiceState = VoiceStep.CONFIRMAR_VACIAR_CARRITO
            hablar("¿Estás seguro de que deseas vaciar el ticket actual?") { escucharVoz() }
            return
        }

        val palabrasEmitir = listOf("emite", "emitir", "factura ya", "cobra ya", "guarda la factura", "guardar factura", "todo bien", "listo", "cobra", "cobrar", "ya esta", "ya está", "nada mas", "nada más")
        val quiereEmitirPalabra = palabrasEmitir.any { transcript.contains(it) }

        if (voiceState == VoiceStep.CONFIRMAR) {
            if (esRespuestaAfirmativa(transcript) || quiereEmitirPalabra) {
                voiceState = VoiceStep.OFF
                hablar("¡Listo! Emitiendo factura por un total de ${String.format(Locale.US, "%.2f", totalCarritoFinal())} dólares.")
                voiceHandler.postDelayed({ emitirFactura(confirmadaPorVoz = true) }, 800)
                return
            }
            if (esRespuestaNegativa(transcript)) {
                voiceState = VoiceStep.ESCUCHANDO
                hablar("De acuerdo, no emitimos aún. ¿Qué deseas cambiar o agregar?") { escucharVoz() }
                return
            }
        }

        consultarIaYAplicar(transcriptOriginal, quiereEmitirPalabra)
    }

    private fun esRespuestaAfirmativa(t: String): Boolean {
        if (t.isBlank()) return false
        val exactas = listOf("si", "ok", "okay", "dale", "claro", "listo", "confirmo", "confirmado", "de acuerdo", "afirmativo", "hazlo", "emite", "emitir", "cobra", "cobrar")
        if (exactas.any { t == it || t == "$it por favor" || t == "$it gracias" }) return true
        val frases = listOf(
            "si por favor", "si dale", "si emite", "si emitir", "si cobra",
            "de una", "de una vez", "adelante", "vamos", "esta bien", "esta bueno",
            "confirmamos", "confirma", "si confirma", "si confirmo", "si listo"
        )
        if (frases.any { t.contains(it) }) return true
        if (Regex("(^|\\s)si(\\s|$)").containsMatchIn(t) && t.length <= 40) return true
        return false
    }

    private fun esRespuestaNegativa(t: String): Boolean {
        if (t.isBlank()) return false
        val neg = listOf("no", "nop", "nel", "cancelar", "cancela", "espera", "aun no", "todavia no", "todavía no", "no emitas", "no cobrar", "revisar")
        return neg.any { t == it || t.startsWith("$it ") || t.contains(it) }
    }

    private fun procesarSeleccionOpcionPorVoz(transcript: String) {
        var seleccion: ProductoResponseDto? = null
        when {
            transcript.contains("1") || transcript.contains("uno") || transcript.contains("primera") || transcript.contains("primero") ->
                seleccion = productosOpcionesPendientes.getOrNull(0)
            transcript.contains("2") || transcript.contains("dos") || transcript.contains("segunda") || transcript.contains("segundo") ->
                seleccion = productosOpcionesPendientes.getOrNull(1)
            transcript.contains("3") || transcript.contains("tres") || transcript.contains("tercera") || transcript.contains("tercero") ->
                seleccion = productosOpcionesPendientes.getOrNull(2)
            transcript.contains("4") || transcript.contains("cuatro") || transcript.contains("cuarta") ->
                seleccion = productosOpcionesPendientes.getOrNull(3)
        }

        if (seleccion == null) {
            seleccion = productosOpcionesPendientes.find { limpiarTexto(it.nombre).contains(transcript) || transcript.contains(limpiarTexto(it.nombre)) }
        }

        if (seleccion != null) {
            dialogOpcionesAmbiguas?.dismiss()
            dialogOpcionesAmbiguas = null
            confirmarSeleccionProductoAmbiguo(seleccion)
        } else {
            repetirPaso("No entendí cuál opción elegiste. Dime por ejemplo 'la primera' o di el nombre exacto.")
        }
    }

    private fun confirmarSeleccionProductoAmbiguo(producto: ProductoResponseDto) {
        var bodegaIdActual = pendingBodegaId
        if (bodegaIdActual == null && bodegasList.size == 1) {
            bodegaIdActual = bodegasList[0].id
        }

        var mensajePrefijo = ""
        if (bodegaIdActual == null) {
            val bodegasConStock = bodegasList.filter { obtenerStock(producto.id, it.id) > 0 }
            when {
                bodegasConStock.size == 1 -> {
                    val bod = bodegasConStock[0]
                    pendingBodegaId = bod.id
                    bodegaIdActual = bod.id
                    spBodegaRef?.setText(bod.nombre, false)
                    mensajePrefijo = "Usé la bodega ${bod.nombre}, que es donde hay stock. "
                }
                bodegasConStock.size > 1 -> {
                    val nombresBod = bodegasConStock.joinToString(", ") { it.nombre }
                    avanzarPaso(VoiceStep.ESCUCHANDO, "${producto.nombre} hay en varias bodegas: $nombresBod. ¿Cuál usamos?")
                    return
                }
                else -> {
                    avanzarPaso(VoiceStep.ESCUCHANDO, "No hay stock de ${producto.nombre} en ninguna bodega.")
                    return
                }
            }
        }

        if (bodegaIdActual != null) {
            val stockActual = obtenerStock(producto.id, bodegaIdActual)
            if (stockActual <= 0) {
                avanzarPaso(VoiceStep.ESCUCHANDO, "Lamentablemente no hay stock disponible de ${producto.nombre}.")
                return
            }
            var cantFinal = cantidadPendienteOpcion
            if (cantFinal > stockActual) cantFinal = stockActual

            agregarProductoAlCarrito(producto, cantFinal, bodegaIdActual, descuentoPendienteOpcion)
            productosOpcionesPendientes = emptyList()

            val siguienteAmbiguo = colaAmbiguos.removeFirstOrNull()
            if (siguienteAmbiguo != null) {
                productosOpcionesPendientes = siguienteAmbiguo.opciones
                cantidadPendienteOpcion = siguienteAmbiguo.cantidad
                descuentoPendienteOpcion = siguienteAmbiguo.descuentoPorcentaje
                mostrarDialogoOpcionesAmbiguas(productosOpcionesPendientes, "${mensajePrefijo}Agregué ${producto.nombre}. ")
                return
            }

            val totalFmt = String.format(Locale.US, "%.2f", totalCarritoFinal())
            avanzarPaso(VoiceStep.CONFIRMAR, "${mensajePrefijo}Agregado ${producto.nombre}. El total a cobrar con descuento es $totalFmt dólares. ¿Deseas emitir ya o agregar algo más?")
        } else {
            avanzarPaso(VoiceStep.ESCUCHANDO, "Selecciona primero una bodega para agregar el producto.")
        }
    }

    private fun consultarIaYAplicar(fraseOriginal: String, quiereEmitirPalabra: Boolean) {
        tvZoeTranscripcionRef?.text = "🧠 Zoe está pensando..."

        val nombresClientes = clientesList.map { nombreClienteDto(it) }
        val nombresProductos = productosList.mapNotNull { it.nombre }
        val nombresBodegas = bodegasList.map { it.nombre }

        lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                ZoeVoiceAI.interpretar(fraseOriginal, nombresClientes, nombresProductos, nombresBodegas)
            }

            if (voiceState == VoiceStep.OFF) return@launch

            if (resultado != null) {
                aplicarResultadoIA(resultado, quiereEmitirPalabra, fraseOriginal)
                return@launch
            }

            val resultadoLocal = ZoeVoiceAI.interpretarLocal(fraseOriginal, nombresClientes, nombresProductos, nombresBodegas)
            if (!ZoeVoiceAI.estaVacio(resultadoLocal)) {
                aplicarResultadoIA(resultadoLocal, quiereEmitirPalabra, fraseOriginal)
            } else {
                val motivo = ZoeVoiceAI.ultimoError ?: "DESCONOCIDO"
                repetirPaso("Uy, no logré entender esa frase. ¿Me la repites más despacio?")
                tvZoeTranscripcionRef?.text = "⚠️ [$motivo] \"$fraseOriginal\""
            }
        }
    }

    private fun aplicarResultadoIA(datos: ResultadoVozFactura, quiereEmitirPalabra: Boolean, fraseOriginal: String = "") {
        // Fix 1: Filtrar y deduplicar estrictamente los ítems devueltos por la IA
        val itemsUnicos = datos.items
            .filter { !it.producto.isNullOrBlank() }
            .distinctBy { limpiarTexto(it.producto) }

        val alertas = mutableListOf<String>()
        val fraseLimpia = limpiarTexto(fraseOriginal)

        val dijoEliminaLiteral = Regex("\\belimin\\w*\\b").containsMatchIn(fraseLimpia)

        if (datos.eliminarProducto != null && dijoEliminaLiteral) {
            val nombreQuitar = datos.eliminarProducto
            val index = carritoTemporal.indexOfFirst {
                limpiarTexto(it.nombreProducto).contains(limpiarTexto(nombreQuitar)) ||
                        limpiarTexto(nombreQuitar).contains(limpiarTexto(it.nombreProducto))
            }
            if (index != -1) {
                val itemActual = carritoTemporal[index]
                val nombreQuitado = itemActual.nombreProducto
                val cantidadAQuitar = datos.eliminarCantidad

                val mensaje: String
                if (cantidadAQuitar != null && cantidadAQuitar > 0 && cantidadAQuitar < itemActual.cantidad) {
                    carritoTemporal[index] = itemActual.copy(cantidad = itemActual.cantidad - cantidadAQuitar)
                    mensaje = "Quité $cantidadAQuitar de $nombreQuitado. Quedan ${carritoTemporal[index].cantidad}."
                } else {
                    carritoTemporal.removeAt(index)
                    mensaje = "Quité $nombreQuitado por completo del ticket."
                }
                actualizarUiCarrito()
                val totalFmt = String.format(Locale.US, "%.2f", totalCarritoFinal())
                if (carritoTemporal.isEmpty()) {
                    avanzarPaso(VoiceStep.ESCUCHANDO, "$mensaje El ticket quedó vacío. ¿Qué agregamos?")
                } else {
                    avanzarPaso(VoiceStep.CONFIRMAR, "$mensaje El nuevo total es $totalFmt dólares. ¿Qué más hacemos?")
                }
                return
            } else {
                alertas.add("no encontré \"$nombreQuitar\" en el ticket para eliminarlo")
            }
        }

        var pedirCedula = false
        val textoClienteBuscado = datos.cliente ?: extractDigits(fraseOriginal)
        if (!textoClienteBuscado.isNullOrBlank() && (facturaClienteId == null || datos.cliente?.isNotBlank() == true)) {
            if (textoClienteBuscado.equals("CONSUMIDOR_FINAL", ignoreCase = true) || textoClienteBuscado.contains("consumidor", ignoreCase = true)) {
                setConsumidorFinalUi()
            } else {
                val matches = buscarClientesUniversales(textoClienteBuscado)
                when {
                    matches.size == 1 -> seleccionarClienteUi(matches[0])
                    matches.size > 1 -> pedirCedula = true
                    else -> alertas.add("no encontré al cliente relacionado con \"$textoClienteBuscado\"")
                }
            }
        }

        datos.metodoPago?.let { metodo ->
            if (metodo == "TARJETA_CREDITO" && facturaEsConsumidorFinal) {
                actualizarMetodoPagoUi("EFECTIVO")
                metodoPagoConfirmadoPorVoz = true
                alertas.add("con Consumidor Final no se puede pagar con tarjeta; usé efectivo")
            } else {
                actualizarMetodoPagoUi(metodo)
                metodoPagoConfirmadoPorVoz = true
                alertas.add("cambié el pago a $metodo")
            }
        }
        datos.cuotas?.let { cuotas ->
            if (cuotas > 0 && !facturaEsConsumidorFinal) {
                facturaCuotas = cuotas
                etCuotasRef?.setText(cuotas.toString())
                if (facturaMetodoPago != "TARJETA_CREDITO") {
                    actualizarMetodoPagoUi("TARJETA_CREDITO")
                    metodoPagoConfirmadoPorVoz = true
                }
            } else if (cuotas > 0 && facturaEsConsumidorFinal) {
                alertas.add("tarjeta no permitida con Consumidor Final")
            }
        }

        datos.descuentoGlobalPorcentaje?.let { pct ->
            facturaDescuentoGlobalPorcentaje = pct.toDouble().coerceIn(0.0, 100.0)
            etDescuentoGlobalRef?.setText(pct.toString())
            alertas.add("apliqué $pct% de descuento global")
        }

        var bodegaIdActual = pendingBodegaId
        if (datos.bodega != null && bodegasList.isNotEmpty()) {
            val nombreBuscado = limpiarTexto(datos.bodega)
            val bodega = bodegasList.find { limpiarTexto(it.nombre) == nombreBuscado }
                ?: bodegasList.find { limpiarTexto(it.nombre).contains(nombreBuscado) || nombreBuscado.contains(limpiarTexto(it.nombre)) }
            if (bodega != null) {
                val eraOtraBodega = pendingBodegaId != null && pendingBodegaId != bodega.id
                pendingBodegaId = bodega.id
                bodegaIdActual = bodega.id
                spBodegaRef?.setText(bodega.nombre, false)
                if (eraOtraBodega) alertas.add("cambié a la bodega ${bodega.nombre}")
            } else {
                alertas.add("no encontré la bodega \"${datos.bodega}\"")
            }
        }
        if (bodegaIdActual == null && bodegasList.size == 1) {
            pendingBodegaId = bodegasList[0].id
            bodegaIdActual = bodegasList[0].id
            spBodegaRef?.setText(bodegasList[0].nombre, false)
        }

        var algoAgregado = false
        var ambiguoPendiente: AmbiguoPendiente? = null
        colaAmbiguos.clear()

        for (item in itemsUnicos) {
            val matches = resolverMatchesProductoVoz(item.producto, fraseOriginal)
            when {
                matches.size == 1 -> {
                    val producto = matches[0]
                    var bodegaParaEsteItem = bodegaIdActual

                    if (bodegaParaEsteItem == null) {
                        val bodegasConStock = bodegasList.filter { obtenerStock(producto.id, it.id) > 0 }
                        when {
                            bodegasConStock.size == 1 -> {
                                val bod = bodegasConStock[0]
                                pendingBodegaId = bod.id
                                bodegaIdActual = bod.id
                                bodegaParaEsteItem = bod.id
                                spBodegaRef?.setText(bod.nombre, false)
                                alertas.add("usé la bodega ${bod.nombre}, que es donde hay stock de ${producto.nombre}")
                            }
                            bodegasConStock.size > 1 -> {
                                alertas.add("${producto.nombre} hay en varias bodegas (${bodegasConStock.joinToString(", ") { it.nombre }}); dime cuál usamos")
                            }
                            else -> {
                                alertas.add("no hay stock de ${producto.nombre} en ninguna bodega")
                            }
                        }
                    }

                    if (bodegaParaEsteItem != null) {
                        val stockActual = obtenerStock(producto.id, bodegaParaEsteItem)
                        if (stockActual <= 0) {
                            alertas.add("no hay stock de ${producto.nombre}")
                        } else {
                            var cantidadFinal = item.cantidad?.takeIf { it > 0 } ?: 1
                            if (cantidadFinal > stockActual) {
                                cantidadFinal = stockActual
                                alertas.add("solo puse $cantidadFinal de ${producto.nombre} por stock limitado")
                            }
                            val descuentoItemPct = item.descuentoPorcentaje?.toDouble()?.coerceIn(0.0, 100.0) ?: 0.0
                            agregarProductoAlCarrito(producto, cantidadFinal, bodegaParaEsteItem, descuentoItemPct)
                            algoAgregado = true
                        }
                    }
                }
                matches.size > 1 -> {
                    val pendiente = AmbiguoPendiente(
                        opciones = matches.take(4),
                        cantidad = item.cantidad?.takeIf { it > 0 } ?: 1,
                        descuentoPorcentaje = item.descuentoPorcentaje?.toDouble()?.coerceIn(0.0, 100.0) ?: 0.0
                    )
                    if (ambiguoPendiente == null) {
                        ambiguoPendiente = pendiente
                    } else {
                        colaAmbiguos.addLast(pendiente)
                    }
                }
                else -> alertas.add("no tengo ${item.producto} en catálogo")
            }
        }

        if (ambiguoPendiente != null) {
            productosOpcionesPendientes = ambiguoPendiente.opciones
            cantidadPendienteOpcion = ambiguoPendiente.cantidad
            descuentoPendienteOpcion = ambiguoPendiente.descuentoPorcentaje
            val prefijoAmbiguo = if (alertas.isNotEmpty()) "Ya anoté el resto: ${alertas.joinToString(", y ")}. " else ""
            mostrarDialogoOpcionesAmbiguas(productosOpcionesPendientes, prefijoAmbiguo)
            return
        }

        if (pedirCedula) {
            val prefijoCedula = if (alertas.isNotEmpty()) "Entendido, ${alertas.joinToString(", y ")}. " else ""
            avanzarPaso(VoiceStep.ESCUCHANDO, "${prefijoCedula}Hay varios clientes coincidentes. Dime su cédula o RUC completo para seleccionarlo.")
            return
        }

        val faltaCliente = facturaClienteId == null && !facturaEsConsumidorFinal
        val faltaItems = carritoTemporal.isEmpty()
        val prefijo = if (alertas.isNotEmpty()) "Entendido, ${alertas.joinToString(", y ")}. " else ""
        val quiereEmitir = quiereEmitirPalabra || datos.emitirFactura

        val totalFmt = String.format(Locale.US, "%.2f", totalCarritoFinal())

        if (quiereEmitir) {
            val (pasoValidacion, mensajeValidacion) = calcularPasoYMensaje()
            if (pasoValidacion == VoiceStep.CONFIRMAR) {
                avanzarPaso(VoiceStep.CONFIRMAR, "${prefijo}Total a pagar $totalFmt dólares. ¿Confirmas que emitimos la factura? Di sí o no.")
            } else {
                avanzarPaso(pasoValidacion, "$prefijo$mensajeValidacion")
            }
            return
        }

        when {
            faltaCliente -> avanzarPaso(VoiceStep.ESCUCHANDO, "${prefijo}¿A quién le facturamos?")
            faltaItems -> avanzarPaso(VoiceStep.ESCUCHANDO, "${prefijo}El ticket está vacío. ¿Qué producto agregamos?")
            else -> {
                val mensaje = if (algoAgregado)
                    "${prefijo}El precio total a cobrar con el descuento es de $totalFmt dólares. ¿Agregamos algo más o emitimos la factura?"
                else
                    "${prefijo}El total a cobrar es $totalFmt dólares. ¿Deseas emitir ya?"
                avanzarPaso(VoiceStep.CONFIRMAR, mensaje)
            }
        }
    }

    private fun extractDigits(text: String): String? {
        val digits = text.filter { it.isDigit() }
        return digits.takeIf { it.isNotEmpty() }
    }

    private fun mostrarDialogoOpcionesAmbiguas(opciones: List<ProductoResponseDto>, prefijo: String = "") {
        dialogOpcionesAmbiguas?.dismiss()

        val nombres = opciones.mapIndexed { idx, prod ->
            val precio = obtenerPrecioFactura(prod)
            "${idx + 1}. ${prod.nombre} — $${String.format(Locale.US, "%.2f", precio)}"
        }.toTypedArray()

        val opcionesHablar = opciones.mapIndexed { idx, prod -> "Opción ${idx + 1}: ${prod.nombre}" }.joinToString(", ")
        voiceState = VoiceStep.SELECCIONAR_OPCION

        hablar("${prefijo}Encontré varias coincidencias: $opcionesHablar. ¿Cuál deseas seleccionar?") {
            escucharVoz()
        }

        dialogOpcionesAmbiguas = MaterialAlertDialogBuilder(this)
            .setTitle("🔍 Selecciona el producto exacto")
            .setIcon(R.drawable.ic_product_box)
            .setItems(nombres) { dialog, which ->
                dialog.dismiss()
                dialogOpcionesAmbiguas = null
                val seleccionado = opciones.getOrNull(which)
                if (seleccionado != null) {
                    confirmarSeleccionProductoAmbiguo(seleccionado)
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                dialogOpcionesAmbiguas = null
                productosOpcionesPendientes = emptyList()
                val siguiente = colaAmbiguos.removeFirstOrNull()
                if (siguiente != null) {
                    productosOpcionesPendientes = siguiente.opciones
                    cantidadPendienteOpcion = siguiente.cantidad
                    descuentoPendienteOpcion = siguiente.descuentoPorcentaje
                    mostrarDialogoOpcionesAmbiguas(productosOpcionesPendientes, "De acuerdo, seguimos con lo demás. ")
                } else {
                    avanzarPaso(VoiceStep.ESCUCHANDO, "Selección cancelada. ¿Qué otro producto agregamos?")
                }
            }
            .setCancelable(false)
            .create()

        dialogOpcionesAmbiguas?.show()
    }

    private fun buscarClientesUniversales(textoBuscado: String): List<ClienteResponseDto> {
        val txt = limpiarTexto(textoBuscado)
        if (txt.isBlank()) return emptyList()

        val exact = clientesList.filter { cli ->
            limpiarTexto(cli.nombreCompleto) == txt ||
                    limpiarTexto(cli.primerNombre) == txt ||
                    limpiarTexto(cli.apellidoPaterno) == txt ||
                    limpiarTexto(cli.dni) == txt ||
                    limpiarTexto(cli.email) == txt
        }
        if (exact.isNotEmpty()) return exact

        val digitos = txt.filter { it.isDigit() }
        if (digitos.isNotBlank()) {
            val porDigitos = clientesList.filter { cli ->
                val dni = cli.dni?.filter { c -> c.isDigit() }.orEmpty()
                dni.isNotBlank() && (dni == digitos || dni.endsWith(digitos) || dni.contains(digitos))
            }
            if (porDigitos.isNotEmpty()) return porDigitos
        }

        val partial = clientesList.filter { cli ->
            val nom = limpiarTexto(cli.nombreCompleto ?: "${cli.primerNombre ?: ""} ${cli.apellidoPaterno ?: ""}")
            val doc = limpiarTexto(cli.dni)
            val corr = limpiarTexto(cli.email)
            nom.contains(txt) || txt.contains(nom) || (doc.isNotBlank() && doc.contains(txt)) || (corr.isNotBlank() && corr.contains(txt))
        }
        if (partial.isNotEmpty()) return partial

        val palabras = txt.split(" ").filter { it.length > 2 }
        if (palabras.isEmpty()) return emptyList()
        return clientesList.filter { cli ->
            val nom = limpiarTexto(cli.nombreCompleto ?: "${cli.primerNombre ?: ""} ${cli.apellidoPaterno ?: ""}")
            val doc = limpiarTexto(cli.dni)
            palabras.all { nom.contains(it) || doc.contains(it) }
        }
    }

    private val stopWordsProducto = setOf(
        "agrega", "agregue", "agregar", "agregame", "añade", "añadir", "anade", "anadir",
        "pon", "ponme", "poner", "mete", "meteme", "meter", "incluye", "incluyeme", "incluir",
        "carga", "cargame", "cargar", "anota", "anotame", "anotar", "manda", "mandame",
        "quiero", "dame", "necesito", "sube", "subeme",
        "uno", "una", "unos", "unas", "dos", "tres", "cuatro", "cinco", "seis", "siete",
        "ocho", "nueve", "diez", "del", "de", "la", "el", "los", "las", "un", "al", "con",
        "por", "para", "y", "o", "que", "me", "te", "le", "se", "producto", "productos",
        "factura", "ticket", "favor", "porfa", "descuento", "porcentaje", "dolares", "dolar",
        "centavos", "tambien", "también"
    )

    private fun dedupProductos(lista: List<ProductoResponseDto>): List<ProductoResponseDto> {
        val vistos = mutableSetOf<Long>()
        val out = mutableListOf<ProductoResponseDto>()
        for (p in lista) {
            val id = p.id ?: continue
            if (vistos.add(id)) out.add(p)
        }
        return out.sortedBy { limpiarTexto(it.nombre).length }
    }

    private fun buscarProductosUniversales(textoBuscado: String): List<ProductoResponseDto> {
        val txt = limpiarTexto(textoBuscado)
        if (txt.isBlank()) return emptyList()

        val exact = productosList.filter { limpiarTexto(it.nombre) == txt }
        if (exact.isNotEmpty()) return dedupProductos(exact)

        val porCodigo = productosList.filter {
            !it.codigoPrincipal.isNullOrBlank() && limpiarTexto(it.codigoPrincipal) == txt
        }
        if (porCodigo.isNotEmpty()) return dedupProductos(porCodigo)

        val partial = productosList.filter {
            val nom = limpiarTexto(it.nombre)
            nom.contains(txt) || (txt.length >= 4 && txt.contains(nom))
        }
        val uniqPartial = dedupProductos(partial)
        if (uniqPartial.size == 1) return uniqPartial
        if (uniqPartial.size > 1) {
            val empiezanIgual = uniqPartial.filter { limpiarTexto(it.nombre).startsWith(txt) }
            return if (empiezanIgual.size > 1) empiezanIgual else uniqPartial
        }

        val palabras = txt.split(Regex("\\s+")).filter { it.length > 2 }
        if (palabras.isNotEmpty()) {
            val porPalabras = productosList.filter { p ->
                val nom = limpiarTexto(p.nombre)
                palabras.all { nom.contains(it) }
            }
            val uniq = dedupProductos(porPalabras)
            if (uniq.isNotEmpty()) return uniq
        }

        val largas = txt.split(Regex("\\s+")).filter { it.length >= 4 }
        if (largas.isNotEmpty()) {
            val suaves = productosList.filter { p ->
                val nom = limpiarTexto(p.nombre)
                largas.any { nom.contains(it) }
            }
            return dedupProductos(suaves)
        }

        return emptyList()
    }

    private fun buscarHermanosProducto(producto: ProductoResponseDto): List<ProductoResponseDto> {
        val nom = limpiarTexto(producto.nombre)

        val exactos = dedupProductos(productosList.filter { limpiarTexto(it.nombre) == nom })
        if (exactos.size > 1) return exactos

        val palabras = nom.split(Regex("\\s+")).filter { it.length >= 3 }
        val raiz = palabras.sortedByDescending { it.length }.firstOrNull()
        if (raiz == null || raiz.length < 4) return listOf(producto)

        var hermanos = dedupProductos(productosList.filter { limpiarTexto(it.nombre).contains(raiz) })
        if (hermanos.size > 12 && palabras.size >= 2) {
            val estrechos = hermanos.filter { p ->
                val n = limpiarTexto(p.nombre)
                palabras.count { n.contains(it) } >= minOf(2, palabras.size)
            }
            if (estrechos.size > 1) hermanos = estrechos
        }
        return if (hermanos.size > 1) hermanos else listOf(producto)
    }

    private fun resolverMatchesProductoVoz(nombreIa: String, fraseUsuario: String): List<ProductoResponseDto> {
        val txtIa = limpiarTexto(nombreIa)
        if (txtIa.isBlank()) return emptyList()

        // Fix 2: Priorizar coincidencias exactas (nombre o código principal) antes de evaluar genéricos o ambigüedades
        val exactos = dedupProductos(productosList.filter { limpiarTexto(it.nombre) == txtIa })
        if (exactos.size == 1) return exactos
        if (exactos.isNotEmpty()) return exactos

        val porCodigo = dedupProductos(productosList.filter { !it.codigoPrincipal.isNullOrBlank() && limpiarTexto(it.codigoPrincipal) == txtIa })
        if (porCodigo.size == 1) return porCodigo

        val tokensItem = txtIa
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWordsProducto && it.toIntOrNull() == null }

        var mejorMulti: List<ProductoResponseDto> = emptyList()
        for (tok in tokensItem) {
            val hits = dedupProductos(productosList.filter { limpiarTexto(it.nombre).contains(tok) })
            if (hits.size > mejorMulti.size) mejorMulti = hits
        }
        if (mejorMulti.size > 1) return mejorMulti

        val porIa = buscarProductosUniversales(nombreIa)

        if (porIa.size == 1) {
            val hermanos = buscarHermanosProducto(porIa[0])
            return if (hermanos.size > 1) hermanos else porIa
        }
        if (porIa.size > 1) return porIa

        val tokensIa = limpiarTexto(nombreIa).split(Regex("\\s+")).filter { it.length >= 3 && it !in stopWordsProducto }
        for (tok in tokensIa) {
            val hits = dedupProductos(productosList.filter { limpiarTexto(it.nombre).contains(tok) })
            if (hits.size > 1) return hits
            if (hits.size == 1) {
                val hermanos = buscarHermanosProducto(hits[0])
                return if (hermanos.size > 1) hermanos else hits
            }
        }

        return if (mejorMulti.size == 1) mejorMulti else emptyList()
    }

    private fun obtenerNombreProducto(item: DetalleFacturaResponseDto, posicion: Int): String {
        item.nombreProducto?.takeIf { it.isNotBlank() }?.let { return it }
        item.producto?.nombre?.takeIf { it.isNotBlank() }?.let { return it }

        val idProducto = item.productoId ?: item.producto?.id
        if (idProducto != null) {
            productosList.find { it.id == idProducto }?.nombre?.takeIf { it.isNotBlank() }?.let { return it }
        }

        item.producto?.marca?.takeIf { it.isNotBlank() }?.let { return it }

        return "Sin descripción"
    }

    private fun mostrarDetalleFacturaDialog(fac: FacturaResponseDto) {
        val detalles = fac.detalles ?: emptyList()

        val items = detalles.mapIndexed { index, item ->
            val cant = item.cantidad ?: 1
            val pUnit = item.precioUnitario ?: 0.0
            val subItem = item.subtotalItem ?: (cant * pUnit)
            DetalleFacturaDialogHelper.ItemLinea(
                nombre = obtenerNombreProducto(item, index),
                cantidad = cant,
                precioUnitario = pUnit,
                descuento = item.descuento ?: 0.0,
                subtotal = subItem
            )
        }

        val datos = DetalleFacturaDialogHelper.DatosFactura(
            numero = fac.numeroFactura ?: "S/N",
            fecha = fac.fechaFormateada,
            clienteNombre = fac.nombreClienteFormateado,
            metodoPago = fac.metodoPago ?: "EFECTIVO",
            estado = fac.estadoFormateado,
            total = fac.totalCalculado,
            descuentoGlobal = fac.totalDescuento ?: 0.0,
            porcentajeIva = 15.0,
            items = items,
            mostrarBotonImprimir = true,
            onImprimir = { prepararGeneracionPDF(fac) }
        )

        DetalleFacturaDialogHelper.mostrar(this, datos)
    }

    private fun cargarFacturas() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getFacturas(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    if (response.isSuccessful) {
                        val facturas = response.body() ?: emptyList()
                        if (facturas.isEmpty()) {
                            layoutVacio.visibility = View.VISIBLE
                            rvFacturas.visibility = View.GONE
                        } else {
                            layoutVacio.visibility = View.GONE
                            rvFacturas.visibility = View.VISIBLE
                            adapter.actualizarLista(facturas)
                        }
                    } else if (response.code() == 401) {
                        Toast.makeText(this@HistorialFacturasActivity, "Sesión expirada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HistorialFacturasActivity, "Error al obtener facturas: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@HistorialFacturasActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun prepararGeneracionPDF(fac: FacturaResponseDto) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Generando Factura...")
            .setMessage("Preparando el documento PDF.")
            .setCancelable(false)
            .show()

        rvFacturas.postDelayed({
            dialog.dismiss()
            imprimirFacturaPDF(fac)
        }, 600)
    }

    private fun imprimirFacturaPDF(fac: FacturaResponseDto) {
        val detalles = fac.detalles ?: emptyList()
        val tasaIva = 0.15
        val porcentajeIvaFmt = "15"

        var gravado = 0.0
        var exento = 0.0

        detalles.forEach { item ->
            val cant = item.cantidad ?: 1
            val pUnit = item.precioUnitario ?: 0.0
            val desc = item.descuento ?: 0.0
            val sub = item.subtotalItem ?: ((cant * pUnit) - desc)
            gravado += sub
        }

        val totalBruto = gravado + exento
        val descGlobalIn = fac.totalDescuento ?: 0.0
        val descGlobal = descGlobalIn.coerceAtMost(totalBruto)

        val baseImponible = (gravado - descGlobal).coerceAtLeast(0.0)
        val iva = baseImponible * tasaIva
        val subtotalSinIva = baseImponible
        val total = fac.totalCalculado.takeIf { it > 0 } ?: (subtotalSinIva + iva)
        val filasProductos = StringBuilder()

        if (detalles.isNotEmpty()) {
            detalles.forEachIndexed { index, item ->
                val cantidad = item.cantidad ?: 1
                val desc = obtenerNombreProducto(item, index)
                val precioUnit = item.precioUnitario ?: 0.0
                val subtotalItem = item.subtotalItem ?: (cantidad * precioUnit)
                val descuentoItem = item.descuento ?: 0.0

                val descHtml = if (descuentoItem > 0.0)
                    "<br><small style=\"color:#ea580c;font-weight:bold;\">(Desc. línea: -$${String.format(Locale.US, "%.2f", descuentoItem)})</small>"
                else ""

                filasProductos.append("""
                <tr>
                    <td class="center">$cantidad</td>
                    <td>$desc$descHtml</td>
                    <td class="text-right">$${String.format(Locale.US, "%.2f", precioUnit)}</td>
                    <td class="text-right font-bold">$${String.format(Locale.US, "%.2f", subtotalItem)}</td>
                </tr>
            """.trimIndent())
            }
        } else {
            filasProductos.append("""
            <tr>
                <td class="center">1</td>
                <td>Consumo general</td>
                <td class="text-right">$${String.format(Locale.US, "%.2f", subtotalSinIva)}</td>
                <td class="text-right font-bold">$${String.format(Locale.US, "%.2f", subtotalSinIva)}</td>
            </tr>
        """.trimIndent())
        }

        val numFactura = fac.numeroFactura ?: "S/N"
        val nombreCliente = fac.nombreClienteFormateado ?: "Consumidor Final"
        val fechaFactura = fac.fechaFormateada ?: "S/F"
        val metodoPago = fac.metodoPago ?: "EFECTIVO"

        val nombreComercialNegocio = negocioActual?.nombreComercial
            ?: negocioActual?.razonSocial
            ?: "Mi Negocio"
        val rucNegocio = negocioActual?.ruc ?: "N/D"
        val direccionNegocio = negocioActual?.direccion ?: "Sin dirección registrada"

        val htmlDescuentoGlobal = if (descGlobal > 0.0) """
            <div class="total-row">
                <span>Descuento Factura</span>
                <span class="font-bold" style="color:#ea580c;">-$${String.format(Locale.US, "%.2f", descGlobal)}</span>
            </div>
        """.trimIndent() else ""

        val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Factura_$numFactura</title>
            <style>
                @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');
                @page { margin: 8mm; size: portrait; }
                * { box-sizing: border-box; }
                body { font-family: 'Inter', sans-serif; color: #1e293b; margin: 0; padding: 0; background-color: #ffffff; }
                .invoice-container { width: 100%; max-width: 650px; margin: 0 auto; background: #fff; padding: 25px; }
                .top-bar { height: 6px; background: linear-gradient(90deg, #ed8936, #ea580c); width: 100%; margin-bottom: 20px; border-radius: 3px; }
                .header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 25px; gap: 10px; }
                .logo h2 { margin: 0 0 4px 0; font-size: 24px; font-weight: 800; color: #ed8936; letter-spacing: 0.5px; }
                .company-details { font-size: 12px; color: #64748b; line-height: 1.5; }
                .invoice-title-area { text-align: right; }
                .invoice-title-area h1 { margin: 0 0 2px 0; font-size: 24px; font-weight: 800; color: #0f172a; text-transform: uppercase; letter-spacing: 0.5px;}
                .invoice-title-area .invoice-no { font-size: 14px; color: #ed8936; font-weight: 700; }
                .info-grid { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 25px; background: #f8fafc; padding: 15px; border-radius: 10px; border: 1px solid #e2e8f0; }
                .info-block h3 { margin: 0 0 4px 0; font-size: 11px; text-transform: uppercase; color: #94a3b8; letter-spacing: 0.5px; }
                .info-block p { margin: 0 0 2px 0; font-size: 14px; font-weight: 600; color: #0f172a; }
                .info-block span { display: block; font-size: 12px; color: #475569; font-weight: 400; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 25px; table-layout: fixed; }
                th { background-color: #0f172a; color: white; padding: 10px 12px; text-align: left; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;}
                th:first-child { border-top-left-radius: 6px; border-bottom-left-radius: 6px; }
                th:last-child { border-top-right-radius: 6px; border-bottom-right-radius: 6px; }
                td { padding: 12px 10px; font-size: 13px; color: #334155; border-bottom: 1px solid #e2e8f0; word-wrap: break-word; }
                .text-right { text-align: right; }
                .center { text-align: center; }
                .font-bold { font-weight: 700; color: #0f172a; }
                .totals-wrapper { display: flex; justify-content: flex-end; margin-bottom: 30px; }
                .totals-box { width: 100%; max-width: 280px; }
                .total-row { display: flex; justify-content: space-between; padding: 10px 12px; font-size: 13px; color: #475569; border-bottom: 1px solid #f1f5f9; }
                .total-row.grand-total { background: #0f172a; color: white; border-radius: 6px; font-size: 16px; font-weight: 700; border: none; margin-top: 8px; padding: 14px 16px;}
                .total-row.grand-total span:last-child { color: #ed8936; }
                .footer { text-align: center; padding-top: 20px; border-top: 2px dashed #e2e8f0; color: #64748b; font-size: 12px; }
                .footer p { margin: 4px 0; }
                .footer-bold { font-weight: 600; color: #0f172a; }
            </style>
        </head>
        <body>
            <div class="invoice-container">
                <div class="top-bar"></div>
                <div class="header">
                    <div>
                        <div class="logo">
                            <h2>DILO</h2>
                        </div>
                        <div class="company-details">
                            <strong>$nombreComercialNegocio</strong><br>
                            RUC: $rucNegocio<br>
                            $direccionNegocio
                        </div>
                    </div>
                    <div class="invoice-title-area">
                        <h1>FACTURA</h1>
                        <div class="invoice-no">Nº $numFactura</div>
                    </div>
                </div>

                <div class="info-grid">
                    <div class="info-block">
                        <h3>Facturar a:</h3>
                        <p>$nombreCliente</p>
                        <span>Consumidor Final / Cliente</span>
                    </div>
                    <div class="info-block" style="text-align: right;">
                        <h3>Detalles del Documento:</h3>
                        <p>Fecha: <span>$fechaFactura</span></p>
                        <p>Método de Pago: <span>$metodoPago</span></p>
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th class="center" width="12%">Cant.</th>
                            <th width="48%">Descripción del Producto</th>
                            <th class="text-right" width="20%">P. Unitario</th>
                            <th class="text-right" width="20%">Total</th>
                        </tr>
                    </thead>
                    <tbody>
                        $filasProductos
                    </tbody>
                </table>

                <div class="totals-wrapper">
                    <div class="totals-box">
                        $htmlDescuentoGlobal
                        <div class="total-row">
                            <span>Subtotal (Sin IVA)</span>
                            <span class="font-bold">$${String.format(Locale.US, "%.2f", subtotalSinIva)}</span>
                        </div>
                        <div class="total-row">
                            <span>IVA (${porcentajeIvaFmt}%)</span>
                            <span class="font-bold">$${String.format(Locale.US, "%.2f", iva)}</span>
                        </div>
                        <div class="total-row grand-total">
                            <span>TOTAL</span>
                            <span>$${String.format(Locale.US, "%.2f", total)}</span>
                        </div>
                    </div>
                </div>

                <div class="footer">
                    <p class="footer-bold">¡Gracias por preferir nuestros servicios!</p>
                    <p>Documento generado electrónicamente por <strong>Dilo Sistema de Gestión</strong>.</p>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

        val webView = WebView(this)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("Factura_${fac.numeroFactura ?: "SN"}")
                printManager.print("Factura_${fac.numeroFactura ?: "SN"}", printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}