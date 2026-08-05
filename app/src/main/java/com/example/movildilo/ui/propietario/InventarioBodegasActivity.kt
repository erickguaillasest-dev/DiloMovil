package com.example.movildilo.ui.propietario

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.InventarioResponseDto
import com.example.movildilo.ui.adapters.InventarioAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InventarioBodegasActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "InventarioBodegasAct"
    }
    private lateinit var btnRegresar: View
    private lateinit var btnIrKardex: MaterialButton
    private lateinit var tvKpiTotalUnidades: TextView
    private lateinit var tvKpiCapitalInvertido: TextView
    private lateinit var etBuscarInventario: EditText
    private lateinit var spinnerFiltroBodega: AutoCompleteTextView
    private lateinit var rvInventario: RecyclerView
    private lateinit var layoutLoading: View

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: InventarioAdapter

    private var negocioId: Long = -1L
    private val listaOriginal = mutableListOf<InventarioResponseDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventario_bodegas)


        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        setupFiltros()

        btnRegresar.setOnClickListener { finish() }
        btnIrKardex.setOnClickListener {
            startActivity(Intent(this, KardexActivity::class.java))
        }

        if (negocioId > 0L) {
            cargarInventarioDesdeApi()
        } else {
            Toast.makeText(this, "No se encontró un negocio activo en la sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        btnIrKardex = findViewById(R.id.btnIrKardex)
        tvKpiTotalUnidades = findViewById(R.id.tvKpiTotalUnidades)
        tvKpiCapitalInvertido = findViewById(R.id.tvKpiCapitalInvertido)
        etBuscarInventario = findViewById(R.id.etBuscarInventario)
        spinnerFiltroBodega = findViewById(R.id.spinnerFiltroBodega)
        rvInventario = findViewById(R.id.rvInventario)
        layoutLoading = findViewById(R.id.layoutLoading)
    }

    private fun setupRecyclerView() {
        adapter = InventarioAdapter(
            listaInventario = mutableListOf(),
            onEditarStockMinClick = { item -> mostrarDialogoStockMinimo(item) },
            onVerLotesClick = { item -> abrirModalLotes(item) }
        )
        rvInventario.layoutManager = LinearLayoutManager(this)
        rvInventario.adapter = adapter
    }

    private fun setupFiltros() {
        // Listener cuando el usuario selecciona un ítem del menú desplegable
        spinnerFiltroBodega.setOnItemClickListener { _, _, _, _ -> aplicarFiltros() }

        // Listener para detectar cambios manuales o borrado de texto en la bodega
        spinnerFiltroBodega.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para el buscador por texto de productos/códigos
        etBuscarInventario.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun cargarInventarioDesdeApi() {
        val authHeader = sessionManager.getAuthHeader()

        if (authHeader.isNullOrEmpty()) {
            Toast.makeText(this, "Sesión no válida o token ausente", Toast.LENGTH_LONG).show()
            mostrarAlertaSesionExpirada()
            return
        }

        mostrarCargando(true)
        Log.d(TAG, "Solicitando inventario para negocio ID: $negocioId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getInventario(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)

                    if (response.isSuccessful) {
                        listaOriginal.clear()
                        val items = response.body() ?: emptyList()
                        listaOriginal.addAll(items)

                        Log.d(TAG, "Inventario obtenido: ${items.size} elementos")

                        if (items.isEmpty()) {
                            Toast.makeText(this@InventarioBodegasActivity, "No hay productos en inventario", Toast.LENGTH_SHORT).show()
                        }

                        poblarSpinnerBodegas()
                        aplicarFiltros()
                    } else if (response.code() == 401) {
                        Log.e(TAG, "Error 401: No Autorizado")
                        mostrarAlertaSesionExpirada()
                    } else {
                        Log.e(TAG, "Error backend: ${response.code()}")
                        Toast.makeText(this@InventarioBodegasActivity, "Error del servidor (${response.code()})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    Toast.makeText(this@InventarioBodegasActivity, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun poblarSpinnerBodegas() {
        val bodegas = mutableListOf("Todas las ubicaciones")
        val nombresUnicos = listaOriginal
            .mapNotNull { it.bodegaNombre?.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        bodegas.addAll(nombresUnicos)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bodegas)
        spinnerFiltroBodega.setAdapter(spinnerAdapter)

        if (spinnerFiltroBodega.text.isNullOrEmpty()) {
            spinnerFiltroBodega.setText("Todas las ubicaciones", false)
        }
    }

    private fun aplicarFiltros() {
        val textoBusqueda = etBuscarInventario.text.toString().trim().lowercase(Locale.ROOT)
        val bodegaSeleccionada = spinnerFiltroBodega.text.toString().trim()

        val resultado = listaOriginal.filter { item ->
            val nombre = item.productoNombre?.lowercase(Locale.ROOT) ?: ""
            val cod1 = item.codigoPrincipal?.lowercase(Locale.ROOT) ?: ""
            val cod2 = item.productoCodigo?.lowercase(Locale.ROOT) ?: ""
            val bodega = item.bodegaNombre?.trim() ?: ""

            // 1. Filtro de búsqueda por nombre o código
            val coincideTexto = textoBusqueda.isEmpty() ||
                    nombre.contains(textoBusqueda) ||
                    cod1.contains(textoBusqueda) ||
                    cod2.contains(textoBusqueda)

            // 2. Filtro de bodega activa (tolera diferencias de minúsculas/mayúsculas)
            val coincideBodega = bodegaSeleccionada.isEmpty() ||
                    bodegaSeleccionada.equals("Todas las ubicaciones", ignoreCase = true) ||
                    bodega.equals(bodegaSeleccionada, ignoreCase = true)

            coincideTexto && coincideBodega
        }

        // Actualizamos la lista del adapter y recalculamos los KPIs en tiempo real
        adapter.actualizarLista(resultado.toList())
        calcularResumenKpis(resultado)
    }

    private fun calcularResumenKpis(lista: List<InventarioResponseDto>) {
        var totalUnidades = 0
        var totalCapital = 0.0

        for (item in lista) {
            val cant = item.cantidadActual ?: 0
            val costo = item.costoPromedio ?: 0.0
            val valor = item.valorInventario ?: (cant * costo)

            totalUnidades += cant
            totalCapital += valor
        }

        tvKpiTotalUnidades.text = "$totalUnidades uds."
        tvKpiCapitalInvertido.text = String.format(Locale.US, "$%.2f", totalCapital)
    }

    private fun mostrarDialogoStockMinimo(item: InventarioResponseDto) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_editar_stock_minimo, null)
        val tvPregunta = view.findViewById<TextView>(R.id.tvPreguntaStockMin)
        val etNuevoStock = view.findViewById<EditText>(R.id.etNuevoStockMinimo)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardarStockMin)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarStockMin)

        tvPregunta.text = "¿Cuál es el mínimo permitido para \"${item.productoNombre ?: "este producto"}\" en ${item.bodegaNombre ?: "la bodega"}?"
        etNuevoStock.setText((item.stockMinimo ?: 5).toString())

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        btnCancelar.setOnClickListener { alertDialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nuevoMin = etNuevoStock.text.toString().toIntOrNull()
            if (nuevoMin != null && nuevoMin >= 0) {
                guardarStockMinimoEnApi(item, nuevoMin, alertDialog)
            } else {
                etNuevoStock.error = "Ingresa un número válido"
            }
        }

        alertDialog.show()
    }

    private fun guardarStockMinimoEnApi(item: InventarioResponseDto, nuevoMinimo: Int, dialog: AlertDialog) {
        val authHeader = sessionManager.getAuthHeader() ?: return
        val inventarioId = item.id ?: return

        mostrarCargando(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.actualizarStockMinimo(
                    authHeader = authHeader,
                    negocioId = negocioId,
                    inventarioId = inventarioId,
                    valor = nuevoMinimo
                )

                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    if (response.isSuccessful) {
                        item.stockMinimo = nuevoMinimo
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this@InventarioBodegasActivity, "Stock mínimo actualizado", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else if (response.code() == 401) {
                        dialog.dismiss()
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(this@InventarioBodegasActivity, "No se pudo actualizar el stock mínimo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    Toast.makeText(this@InventarioBodegasActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun abrirModalLotes(item: InventarioResponseDto) {
        val bodegaId = item.bodegaId ?: return
        val productoId = item.productoId ?: return
        val authHeader = sessionManager.getAuthHeader() ?: return

        val dialogCargando = LotesBottomSheetDialog(item.productoNombre ?: "Producto", emptyList(), true)
        dialogCargando.show(supportFragmentManager, "LotesBottomSheetDialog")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getLotesPorProducto(
                    authHeader = authHeader,
                    negocioId = negocioId,
                    bodegaId = bodegaId,
                    productoId = productoId
                )

                withContext(Dispatchers.Main) {
                    dialogCargando.dismiss()
                    if (response.isSuccessful) {
                        val lotes = response.body() ?: emptyList()
                        val dialogConLotes = LotesBottomSheetDialog(item.productoNombre ?: "Producto", lotes, false)
                        dialogConLotes.show(supportFragmentManager, "LotesBottomSheetDialog")
                    } else {
                        Toast.makeText(this@InventarioBodegasActivity, "Error al consultar lotes del producto", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialogCargando.dismiss()
                    Toast.makeText(this@InventarioBodegasActivity, "Error de red al consultar lotes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        layoutLoading.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarAlertaSesionExpirada() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Sesión Expirada")
            .setMessage("Tu sesión ha caducado. Vuelve a iniciar sesión.")
            .setPositiveButton("Aceptar") { _, _ ->
                sessionManager.clearSession()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}