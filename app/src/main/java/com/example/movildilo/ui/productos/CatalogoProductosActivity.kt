package com.example.movildilo.ui.productos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.data.model.dto.inventario.ProductoDto
import com.example.movildilo.data.model.dto.inventario.toProductoDtoList
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.adapters.ProductosAdapter
import com.example.movildilo.utils.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody

class CatalogoProductosActivity : AppCompatActivity() {

    private lateinit var btnRegresar: View
    private lateinit var etBuscarProducto: EditText
    private lateinit var spinnerCategoriaFiltro: AutoCompleteTextView
    private lateinit var rvProductos: RecyclerView
    private lateinit var fabNuevoProducto: ExtendedFloatingActionButton
    private lateinit var layoutLoading: View

    private lateinit var sessionManager: SessionManager
    private lateinit var adapter: ProductosAdapter

    private var negocioId: Long = -1L
    private var listaOriginal = mutableListOf<ProductoDto>()
    private var listaFiltrada = mutableListOf<ProductoDto>()
    private var listaCategoriasBD = mutableListOf<CategoriaDto>()

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalogo_productos)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupFiltros()
        setupRecyclerView()

        btnRegresar.setOnClickListener { finish() }

        fabNuevoProducto.setOnClickListener {
            if (negocioId == -1L) {
                Toast.makeText(this, "No se puede agregar un producto sin un negocio activo.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            abrirModalProducto(null)
        }

        if (negocioId != -1L) {
            cargarCategoriasDesdeApi()
            cargarProductosDesdeApi()
        } else {
            Toast.makeText(this, "No se encontró un negocio activo.", Toast.LENGTH_SHORT).show()
        }

        manejarAccionDeZoe()

        if (intent.getBooleanExtra(com.example.movildilo.ia.ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun abrirChatZoe() {
        val sessionManager = SessionManager(this)
        val userMap = sessionManager.getUserMap()
        val nombreUsuario = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString() ?: "Propietario"

        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = nombreUsuario,
            negocioNombre = "Mi Empresa",
            contextoNegocioTexto = "Estás visualizando el catálogo de productos.",
            alertasTexto = "Sin alertas recientes.",
            groqApiKey = Constants.GROQ_API_KEY_CHAT
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun manejarAccionDeZoe() {
        if (intent.getStringExtra(ZoeActionRouter.EXTRA_ACCION) ==
            ZoeActionRouter.Accion.CREAR_PRODUCTO
        ) {
            fabNuevoProducto.postDelayed({
                if (negocioId != -1L) abrirModalProducto(null)
            }, 700)
        }
    }

    private fun initViews() {
        btnRegresar = findViewById(R.id.btnRegresar)
        etBuscarProducto = findViewById(R.id.etBuscarProducto)
        spinnerCategoriaFiltro = findViewById(R.id.spinnerCategoriaFiltro)
        rvProductos = findViewById(R.id.rvProductos)
        fabNuevoProducto = findViewById(R.id.fabNuevoProducto)
        layoutLoading = findViewById(R.id.layoutLoading)
    }

    private fun setupRecyclerView() {
        adapter = ProductosAdapter(
            listaProductos = listaFiltrada,
            onEditClick = { producto -> abrirModalProducto(producto) },
            onDeleteClick = { producto -> confirmarEliminacion(producto) }
        )
        rvProductos.layoutManager = LinearLayoutManager(this)
        rvProductos.adapter = adapter
    }

    private fun setupFiltros() {
        actualizarSpinnerFiltroCategorias()

        spinnerCategoriaFiltro.setOnItemClickListener { _, _, _, _ ->
            aplicarFiltros()
        }

        spinnerCategoriaFiltro.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aplicarFiltros()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etBuscarProducto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    aplicarFiltros()
                }
                searchHandler.postDelayed(searchRunnable!!, 250)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun actualizarSpinnerFiltroCategorias() {
        val nombresCategorias = mutableListOf("Todas")
        nombresCategorias.addAll(listaCategoriasBD.mapNotNull { it.nombre })

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            nombresCategorias
        )
        spinnerCategoriaFiltro.setAdapter(spinnerAdapter)

        if (spinnerCategoriaFiltro.text.isNullOrBlank()) {
            spinnerCategoriaFiltro.setText("Todas", false)
        }
    }

    private fun aplicarFiltros() {
        val textoBusqueda = etBuscarProducto.text.toString().trim().lowercase()
        val categoriaSeleccionada = spinnerCategoriaFiltro.text.toString().trim()

        val categoriaDtoSeleccionada = listaCategoriasBD.find {
            it.nombre.equals(categoriaSeleccionada, ignoreCase = true)
        }

        val resultado = listaOriginal.filter { prod ->
            val coincideTexto = textoBusqueda.isEmpty() ||
                    (prod.nombre?.lowercase()?.contains(textoBusqueda) == true) ||
                    (prod.codigoPrincipal?.lowercase()?.contains(textoBusqueda) == true) ||
                    (prod.marca?.lowercase()?.contains(textoBusqueda) == true)

            val esTodas = categoriaSeleccionada.isEmpty() ||
                    categoriaSeleccionada.equals("Todas", ignoreCase = true)

            // CORRECCIÓN: Se validan de forma flexible las coincidencias de categoría por nombre o ID
            val coincideCategoria = esTodas ||
                    (prod.categoria != null && prod.categoria.trim().equals(categoriaSeleccionada, ignoreCase = true)) ||
                    (categoriaDtoSeleccionada != null && prod.categoriaId == categoriaDtoSeleccionada.id) ||
                    (categoriaDtoSeleccionada != null && prod.categoria?.trim().equals(categoriaDtoSeleccionada.id.toString()))

            coincideTexto && coincideCategoria
        }

        // IMPORTANTE: se crea una lista NUEVA (no se reutiliza/mutea la anterior) para que
        // DiffUtil pueda comparar la lista vieja del adapter contra una lista realmente distinta.
        // Si se reutiliza el mismo objeto, el adapter y la actividad terminan apuntando a la
        // misma referencia y DiffUtil no detecta cambios -> el RecyclerView nunca se actualiza.
        listaFiltrada = resultado.toMutableList()
        adapter.actualizarLista(listaFiltrada)
    }

    private fun cargarCategoriasDesdeApi() {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getCategorias(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        listaCategoriasBD.clear()
                        val categorias = response.body() ?: emptyList()
                        listaCategoriasBD.addAll(categorias)
                        actualizarSpinnerFiltroCategorias()
                        aplicarFiltros()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    }
                }
            } catch (e: Exception) {
                Log.e("CATALOGO_ERR", "Error al cargar categorías", e)
            }
        }
    }

    private fun cargarProductosDesdeApi() {
        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }
        mostrarCargando(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getCatalogo(authHeader, negocioId)
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    if (response.isSuccessful) {
                        listaOriginal.clear()
                        val productos = response.body()?.toProductoDtoList() ?: emptyList()
                        listaOriginal.addAll(productos)
                        aplicarFiltros()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(
                            this@CatalogoProductosActivity,
                            "Error al obtener catálogo (${response.code()})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    Toast.makeText(
                        this@CatalogoProductosActivity,
                        "Error de red: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun validarProducto(producto: ProductoDto): Boolean {
        if (negocioId == -1L) {
            Toast.makeText(this, "No hay un negocio seleccionado.", Toast.LENGTH_SHORT).show()
            return false
        }

        val nombre = producto.nombre?.trim().orEmpty()
        if (nombre.isEmpty()) {
            Toast.makeText(this, "El nombre del producto es obligatorio.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (nombre.length < 2) {
            Toast.makeText(this, "El nombre del producto debe tener al menos 2 caracteres.", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun guardarProductoEnBackend(producto: ProductoDto, categoriaId: Long?) {
        if (!validarProducto(producto)) {
            return
        }

        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }

        mostrarCargando(true)

        val productoAjustado = producto.copy(
            categoriaId = categoriaId ?: producto.categoriaId,
            negocioId = if (producto.negocioId == null || producto.negocioId <= 0) negocioId else producto.negocioId
        )

        val jsonProducto = Gson().toJson(productoAjustado)

        val mediaType = MediaType.parse("application/json")
        val datosPart = RequestBody.create(mediaType, jsonProducto)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (productoAjustado.id == null) {
                    RetrofitClient.apiService.crearProducto(
                        token = authHeader,
                        negocioId = negocioId,
                        datos = datosPart,
                        imagen = null
                    )
                } else {
                    RetrofitClient.apiService.actualizarProducto(
                        token = authHeader,
                        negocioId = negocioId,
                        id = productoAjustado.id,
                        datos = datosPart,
                        imagen = null
                    )
                }

                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@CatalogoProductosActivity,
                            "Guardado correctamente",
                            Toast.LENGTH_SHORT
                        ).show()
                        cargarProductosDesdeApi()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        val errorDetail = response.errorBody()?.string()
                        Log.e("PROD_UPDATE_ERR", "Error ${response.code()}: $errorDetail")

                        val mensaje = when {
                            response.code() == 400 && !errorDetail.isNullOrBlank() -> errorDetail
                            response.code() == 500 && errorDetail?.contains("Ya existe") == true -> errorDetail
                            else -> "Ocurrió un error al guardar el producto. Inténtalo nuevamente."
                        }
                        Toast.makeText(this@CatalogoProductosActivity, mensaje, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    Log.e("PROD_UPDATE_EXC", "Error de red", e)
                    Toast.makeText(
                        this@CatalogoProductosActivity,
                        "Error de red: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun eliminarProductoEnBackend(producto: ProductoDto) {
        val prodId = producto.id ?: run {
            Toast.makeText(this, "No se puede eliminar un producto sin ID.", Toast.LENGTH_SHORT).show()
            return
        }

        val authHeader = sessionManager.getAuthHeader() ?: run {
            mostrarAlertaSesionExpirada()
            return
        }

        mostrarCargando(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.eliminarProducto(authHeader, negocioId, prodId)
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@CatalogoProductosActivity,
                            "Producto eliminado",
                            Toast.LENGTH_SHORT
                        ).show()
                        cargarProductosDesdeApi()
                    } else if (response.code() == 401) {
                        mostrarAlertaSesionExpirada()
                    } else {
                        Toast.makeText(
                            this@CatalogoProductosActivity,
                            "No se pudo eliminar el producto",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarCargando(false)
                    Toast.makeText(
                        this@CatalogoProductosActivity,
                        "Error de red: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun abrirModalProducto(producto: ProductoDto?) {
        val dialog = ProductoDialog(
            productoEditar = producto,
            listaCategoriasBD = listaCategoriasBD,
            listaProductosExistentes = listaOriginal,
            onGuardarListener = { prodAGuardar, categoriaId ->
                guardarProductoEnBackend(prodAGuardar, categoriaId)
            }
        )
        dialog.show(supportFragmentManager, "ProductoDialog")
    }

    private fun confirmarEliminacion(producto: ProductoDto) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar Producto")
            .setMessage("¿Estás seguro de eliminar '${producto.nombre}' de la base de datos?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarProductoEnBackend(producto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
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