package com.example.movildilo.ui.propietario

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.ia.ZoeActionRouter
import com.example.movildilo.ia.ZoeBottomSheetDialog
import com.example.movildilo.ui.adapters.CategoriasAdapter
import com.example.movildilo.utils.Constants
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoriasActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var rvCategorias: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var tvListaVacia: TextView
    private lateinit var layoutLoading: FrameLayout
    private lateinit var btnNuevaCategoria: MaterialButton
    private lateinit var btnRegresar: ImageButton
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val listaOriginal = mutableListOf<CategoriaDto>()
    private val listaFiltrada = mutableListOf<CategoriaDto>()
    private lateinit var adapter: CategoriasAdapter

    private var negocioId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categorias)

        sessionManager = SessionManager(this)
        negocioId = sessionManager.getNegocioId()

        initViews()
        setupRecyclerView()
        setupSearch()

        btnRegresar.setOnClickListener { finish() }
        btnNuevaCategoria.setOnClickListener { abrirModalDialog(null) }

        swipeRefreshLayout.setOnRefreshListener {
            if (negocioId > 0) {
                cargarCategorias {
                    swipeRefreshLayout.isRefreshing = false
                }
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }

        if (intent.getStringExtra(ZoeActionRouter.EXTRA_ACCION) ==
            ZoeActionRouter.Accion.CREAR_CATEGORIA
        ) {
            btnNuevaCategoria.postDelayed({ abrirModalDialog(null) }, 500)
        }

        if (negocioId > 0) {
            cargarCategorias()
        } else {
            Toast.makeText(this, "No se encontró el ID del negocio", Toast.LENGTH_SHORT).show()
        }

        if (intent.getBooleanExtra(ZoeActionRouter.EXTRA_MANTENER_ZOE_ABIERTA, false)) {
            abrirChatZoe()
        }
    }

    private fun abrirChatZoe() {
        val userMap = sessionManager.getUserMap()
        val nombreUsuario = userMap?.get("primerNombre")?.toString() ?: userMap?.get("nombre")?.toString() ?: "Usuario"
        val rolUsuario = sessionManager.getUserRole() ?: "PROPIETARIO"
        val negocioNombre = userMap?.get("negocioNombre")?.toString() ?: userMap?.get("nombreNegocio")?.toString() ?: "Tu Negocio"

        val dialogZoe = ZoeBottomSheetDialog(
            usuarioNombre = nombreUsuario,
            negocioNombre = negocioNombre,
            negocioId = negocioId.toString(),
            groqApiKey = Constants.GROQ_API_KEY_CHAT,
            rolUsuario = rolUsuario
        )
        dialogZoe.show(supportFragmentManager, "ZoeChatBottomSheet")
    }

    private fun initViews() {
        rvCategorias = findViewById(R.id.rvCategorias)
        etBuscar = findViewById(R.id.etBuscar)
        tvListaVacia = findViewById(R.id.tvListaVacia)
        layoutLoading = findViewById(R.id.layoutLoading)
        btnNuevaCategoria = findViewById(R.id.btnNuevaCategoria)
        btnRegresar = findViewById(R.id.btnRegresar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupRecyclerView() {
        adapter = CategoriasAdapter(
            listaFiltrada,
            onEditar = { cat -> abrirModalDialog(cat) },
            onEliminar = { cat -> confirmarEliminación(cat) }
        )
        rvCategorias.layoutManager = LinearLayoutManager(this)
        rvCategorias.adapter = adapter
    }

    private fun setupSearch() {
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarCategorias(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun cargarCategorias(onComplete: (() -> Unit)? = null) {
        if (!swipeRefreshLayout.isRefreshing) {
            mostrarLoading(true)
        }
        val token = sessionManager.getAuthHeader() ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getCategorias(token, negocioId)
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    if (response.isSuccessful && response.body() != null) {
                        listaOriginal.clear()
                        listaOriginal.addAll(response.body()!!)
                        filtrarCategorias(etBuscar.text.toString())
                    } else {
                        Toast.makeText(this@CategoriasActivity, "Error al cargar categorías", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    Toast.makeText(this@CategoriasActivity, "Fallo de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    private fun filtrarCategorias(query: String) {
        val term = query.trim().lowercase()
        listaFiltrada.clear()

        if (term.isEmpty()) {
            listaFiltrada.addAll(listaOriginal)
        } else {
            listaFiltrada.addAll(listaOriginal.filter { cat ->
                cat.nombre.lowercase().contains(term) ||
                        (cat.descripcion?.lowercase()?.contains(term) == true)
            })
        }

        adapter.actualizarLista(listaFiltrada)
        tvListaVacia.visibility = if (listaFiltrada.isEmpty()) View.VISIBLE else View.GONE
    }

    // DIÁLOGO FLOATING DE CREACIÓN Y EDICIÓN
    private fun abrirModalDialog(categoriaExistente: CategoriaDto?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_categoria, null)
        val tvTitulo = dialogView.findViewById<TextView>(R.id.tvTituloDialog)
        val etNombre = dialogView.findViewById<EditText>(R.id.etNombreCategoria)
        val etDescripcion = dialogView.findViewById<EditText>(R.id.etDescripcionCategoria)
        val btnCerrar = dialogView.findViewById<ImageButton>(R.id.btnCerrarDialog)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarDialog)
        val btnGuardar = dialogView.findViewById<MaterialButton>(R.id.btnGuardarDialog)

        val esEdicion = categoriaExistente != null
        tvTitulo.text = if (esEdicion) "Editar Categoría" else "Nueva Categoría"

        if (esEdicion) {
            etNombre.setText(categoriaExistente?.nombre)
            etDescripcion.setText(categoriaExistente?.descripcion ?: "")
        }

        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCerrar.setOnClickListener { alertDialog.dismiss() }
        btnCancelar.setOnClickListener { alertDialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val descripcion = etDescripcion.text.toString().trim()

            val errorNombre = FormValidator.requerido(nombre, "El nombre de la categoría")
                ?: FormValidator.longitudMinima(nombre, 2, "El nombre de la categoría")
                ?: FormValidator.longitudMaxima(nombre, 60, "El nombre de la categoría")
                ?: run {
                    val yaExiste = listaOriginal.any {
                        FormValidator.normalizar(it.nombre) == FormValidator.normalizar(nombre) &&
                                it.id != categoriaExistente?.id
                    }
                    if (yaExiste) "Ya existe una categoría con ese nombre." else null
                }

            val errorDescripcion = FormValidator.longitudMaxima(descripcion, 250, "La descripción")

            if (errorNombre != null) {
                etNombre.error = errorNombre
                etNombre.requestFocus()
                return@setOnClickListener
            } else {
                etNombre.error = null
            }

            if (errorDescripcion != null) {
                etDescripcion.error = errorDescripcion
                etDescripcion.requestFocus()
                return@setOnClickListener
            } else {
                etDescripcion.error = null
            }

            val requestDto = CategoriaDto(
                id = categoriaExistente?.id,
                nombre = nombre,
                descripcion = if (descripcion.isEmpty()) null else descripcion
            )

            alertDialog.dismiss()
            guardarCategoria(requestDto, esEdicion)
        }

        alertDialog.show()

        alertDialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun guardarCategoria(dto: CategoriaDto, esEdicion: Boolean) {
        mostrarLoading(true)
        val token = sessionManager.getAuthHeader() ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (esEdicion) {
                    RetrofitClient.apiService.actualizarCategoria(token, negocioId, dto.id!!, dto)
                } else {
                    RetrofitClient.apiService.crearCategoria(token, negocioId, dto)
                }

                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    if (response.isSuccessful) {
                        val msg = if (esEdicion) "¡Categoría actualizada!" else "¡Categoría creada exitosamente!"
                        Toast.makeText(this@CategoriasActivity, msg, Toast.LENGTH_SHORT).show()
                        cargarCategorias()
                    } else {
                        Toast.makeText(this@CategoriasActivity, "Error al guardar categoría", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    Toast.makeText(this@CategoriasActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarEliminación(cat: CategoriaDto) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar categoría?")
            .setMessage("Se borrará permanentemente '${cat.nombre}'.")
            .setPositiveButton("Sí, eliminar") { _, _ -> eliminarCategoria(cat.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarCategoria(id: Long?) {
        if (id == null) return
        mostrarLoading(true)
        val token = sessionManager.getAuthHeader() ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.eliminarCategoria(token, negocioId, id)
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    if (response.isSuccessful) {
                        Toast.makeText(this@CategoriasActivity, "Categoría borrada", Toast.LENGTH_SHORT).show()
                        cargarCategorias()
                    } else {
                        Toast.makeText(this@CategoriasActivity, "No se pudo eliminar la categoría", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarLoading(false)
                    Toast.makeText(this@CategoriasActivity, "Fallo al eliminar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarLoading(visible: Boolean) {
        layoutLoading.visibility = if (visible) View.VISIBLE else View.GONE
    }
}