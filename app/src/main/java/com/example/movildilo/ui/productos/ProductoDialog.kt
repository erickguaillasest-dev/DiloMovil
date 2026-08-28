package com.example.movildilo.ui.productos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.inventario.CategoriaDto
import com.example.movildilo.data.model.dto.inventario.ProductoDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProductoDialog(
    private val productoEditar: ProductoDto? = null,
    private val listaCategoriasBD: List<CategoriaDto> = emptyList(),
    private val listaProductosExistentes: List<ProductoDto> = emptyList(),
    private val listaUnidades: List<Pair<String, String>> = listOf(
        "UNIDADES" to "Unidades (Cajas, Botellas, Piezas)",
        "LIBRAS" to "Libras (Peso)",
        "KILOGRAMOS" to "Kilogramos (Peso)",
        "LITROS" to "Litros (Volumen)"
    ),
    private val onGuardarListener: (ProductoDto, Long?) -> Unit
) : DialogFragment() {

    private lateinit var tvTitulo: TextView
    private lateinit var btnCerrar: ImageButton
    private lateinit var imgPreview: ImageView
    private lateinit var btnSubirFoto: View
    private lateinit var etNombre: TextInputEditText
    private lateinit var etCodigo: TextInputEditText
    private lateinit var etMarca: TextInputEditText
    private lateinit var spinnerCategoria: AutoCompleteTextView
    private lateinit var spinnerUnidad: AutoCompleteTextView
    private lateinit var tilNombre: TextInputLayout
    private lateinit var tilCodigo: TextInputLayout
    private lateinit var tilMarca: TextInputLayout
    private lateinit var tilCategoria: TextInputLayout
    private lateinit var tilUnidad: TextInputLayout
    private lateinit var cbGrabaIva: CheckBox
    private lateinit var cbTieneCaducidad: CheckBox
    private lateinit var btnCancelar: MaterialButton
    private lateinit var btnGuardar: MaterialButton

    private var categoriaSeleccionada: CategoriaDto? = null
    private var imagenBase64Seleccionada: String? = null

    private val seleccionarImagenLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { convertirUriABase64(it) } }

    private val tomarFotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? -> bitmap?.let { convertirBitmapABase64(it) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_producto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupDropdowns()
        cargarDatosSiEsEdicion()

        val abrirOpciones = View.OnClickListener { mostrarOpcionesImagen() }
        btnSubirFoto.setOnClickListener(abrirOpciones)
        imgPreview.setOnClickListener(abrirOpciones)
        btnCerrar.setOnClickListener { dismiss() }
        btnCancelar.setOnClickListener { dismiss() }
        btnGuardar.setOnClickListener { guardarProducto() }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(0, 0, 0, imeInsets.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.90).toInt()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun initViews(v: View) {
        tvTitulo = v.findViewById(R.id.tvTituloModal)
        btnCerrar = v.findViewById(R.id.btnCerrarModal)
        imgPreview = v.findViewById(R.id.imgPreview)
        btnSubirFoto = v.findViewById(R.id.btnSubirFoto)
        etNombre = v.findViewById(R.id.etNombreProducto)
        etCodigo = v.findViewById(R.id.etCodigoPrincipal)
        etMarca = v.findViewById(R.id.etMarca)
        spinnerCategoria = v.findViewById(R.id.spinnerModalCategoria)
        spinnerUnidad = v.findViewById(R.id.spinnerUnidadMedida)
        cbGrabaIva = v.findViewById(R.id.cbGrabaIva)
        cbTieneCaducidad = v.findViewById(R.id.cbTieneCaducidad)
        btnCancelar = v.findViewById(R.id.btnCancelar)
        btnGuardar = v.findViewById(R.id.btnGuardarProducto)
        tilNombre = v.findViewById(R.id.tilNombreProducto)
        tilCodigo = v.findViewById(R.id.tilCodigoPrincipal)
        tilMarca = v.findViewById(R.id.tilMarca)
        tilCategoria = v.findViewById(R.id.tilCategoria)
        tilUnidad = v.findViewById(R.id.tilUnidadMedida)

        etCodigo.isEnabled = false
        etCodigo.setBackgroundColor(Color.parseColor("#F1F5F9"))
        etCodigo.setTextColor(Color.parseColor("#475569"))

        etNombre.doAfterTextChangedClearError(tilNombre)
        etMarca.doAfterTextChangedClearError(tilMarca)
    }

    private fun TextInputEditText.doAfterTextChangedClearError(til: TextInputLayout) {
        this.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { FormValidator.marcarError(til, null) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupDropdowns() {
        val adapterCat = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listaCategoriasBD)
        spinnerCategoria.setAdapter(adapterCat)
        spinnerCategoria.setOnItemClickListener { parent, _, position, _ ->
            categoriaSeleccionada = parent.getItemAtPosition(position) as CategoriaDto
        }
        spinnerCategoria.setOnClickListener { spinnerCategoria.showDropDown() }

        val nombresUnidades = listaUnidades.map { it.second }
        val adapterUni = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombresUnidades)
        spinnerUnidad.setAdapter(adapterUni)
        spinnerUnidad.setOnClickListener { spinnerUnidad.showDropDown() }
    }

    private fun generarSiguienteCodigo(): String {
        if (listaProductosExistentes.isEmpty()) return "PROD-001"

        var maxNumber = 0
        var prefix = "PROD-"
        var hasPrefixFound = false

        listaProductosExistentes.forEach { prod ->
            val codigo = prod.codigoPrincipal
            if (!codigo.isNullOrBlank() && codigo != "S/C") {
                val match = Regex("^(.*?)(\\d+)$").find(codigo)
                if (match != null) {
                    val currentPrefix = match.groupValues[1]
                    val currentNumber = match.groupValues[2].toIntOrNull() ?: 0
                    if (currentNumber >= maxNumber) {
                        maxNumber = currentNumber
                        prefix = currentPrefix
                        hasPrefixFound = currentPrefix.isNotEmpty()
                    }
                }
            }
        }

        val nextNumber = maxNumber + 1
        val padLength = maxOf(3, maxNumber.toString().length)
        val paddedNumber = nextNumber.toString().padStart(padLength, '0')

        return if (hasPrefixFound) {
            "$prefix$paddedNumber"
        } else {
            paddedNumber
        }
    }

    private fun cargarDatosSiEsEdicion() {
        if (productoEditar != null) {
            tvTitulo.text = "Editar Producto"
            etNombre.setText(productoEditar.nombre)
            etCodigo.setText(if (productoEditar.codigoPrincipal.isNullOrBlank()) "S/C" else productoEditar.codigoPrincipal)
            etMarca.setText(if (productoEditar.marca == "Sin marca") "" else productoEditar.marca)

            val catCoincidente = listaCategoriasBD.find {
                it.id == productoEditar.categoriaId || it.nombre.equals(productoEditar.categoria, ignoreCase = true)
            }
            if (catCoincidente != null) {
                categoriaSeleccionada = catCoincidente
                spinnerCategoria.setText(catCoincidente.nombre, false)
            } else if (productoEditar.categoria != null) {
                spinnerCategoria.setText(productoEditar.categoria, false)
            }

            val unidadClave = productoEditar.unidadMedida ?: "UNIDADES"
            val parUnidad = listaUnidades.find { it.first.equals(unidadClave, ignoreCase = true) } ?: listaUnidades[0]
            spinnerUnidad.setText(parUnidad.second, false)

            cbGrabaIva.isChecked = productoEditar.grabaIva ?: false
            cbTieneCaducidad.isChecked = productoEditar.tieneCaducidad ?: false

            imagenBase64Seleccionada = productoEditar.imagen
            cargarImagenEnPreview(productoEditar.imagen)
        } else {
            tvTitulo.text = "Nuevo Producto"
            etCodigo.setText(generarSiguienteCodigo())

            if (listaCategoriasBD.isNotEmpty()) {
                categoriaSeleccionada = listaCategoriasBD[0]
                spinnerCategoria.setText(listaCategoriasBD[0].nombre, false)
            }
            spinnerUnidad.setText(listaUnidades[0].second, false)
            cbGrabaIva.isChecked = false
            cbTieneCaducidad.isChecked = false

            restablecerImagenDefecto()
        }
    }

    private fun cargarImagenEnPreview(imagenStr: String?) {
        if (!imagenStr.isNullOrEmpty()) {
            imgPreview.setPadding(0, 0, 0, 0)
            imgPreview.imageTintList = null

            if (imagenStr.startsWith("http://") || imagenStr.startsWith("https://")) {
                Glide.with(this).load(imagenStr).centerCrop().into(imgPreview)
            } else {
                try {
                    val cleanBase64 = if (imagenStr.contains(",")) imagenStr.substringAfter(",") else imagenStr
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    Glide.with(this).asBitmap().load(imageBytes).centerCrop().into(imgPreview)
                } catch (e: Exception) {
                    restablecerImagenDefecto()
                }
            }
        } else {
            restablecerImagenDefecto()
        }
    }

    private fun restablecerImagenDefecto() {
        val density = resources.displayMetrics.density
        val paddingPx = (18 * density).toInt()
        imgPreview.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        imgPreview.setImageResource(android.R.drawable.ic_menu_camera)
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Elegir de Galería", "Tomar Foto")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Seleccionar Imagen")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> seleccionarImagenLauncher.launch("image/*")
                    1 -> tomarFotoLauncher.launch(null)
                }
            }
            .show()
    }

    private fun convertirUriABase64(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            bitmap?.let { convertirBitmapABase64(it) }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertirBitmapABase64(bitmap: Bitmap) {
        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()

            imagenBase64Seleccionada = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
            cargarImagenEnPreview(imagenBase64Seleccionada)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarProducto() {
        val nombre = etNombre.text.toString().trim()
        val codigo = etCodigo.text.toString().trim()
        val marca = etMarca.text.toString().trim().ifBlank { "Sin marca" }
        val nombreCategoria = spinnerCategoria.text.toString().trim()
        val nombreUnidadVisual = spinnerUnidad.text.toString().trim()

        val unidadTecnica = listaUnidades.find { it.second.equals(nombreUnidadVisual, ignoreCase = true) }?.first ?: "UNIDADES"

        // 1. Validaciones principales mediante el FormValidator con TextInputLayout
        val ok = FormValidator.validar(
            FormValidator.Campo(tilCodigo) {
                FormValidator.requerido(codigo, "El código principal")
                    ?: FormValidator.longitudMinima(codigo, 3, "El código principal")
            },
            FormValidator.Campo(tilNombre) {
                FormValidator.requerido(nombre, "El nombre del producto")
                    ?: FormValidator.longitudMinima(nombre, 2, "El nombre del producto")
                    ?: FormValidator.longitudMaxima(nombre, 100, "El nombre del producto")
            },
            FormValidator.Campo(tilMarca) {
                if (marca != "Sin marca") {
                    FormValidator.longitudMinima(marca, 2, "La marca")
                        ?: FormValidator.longitudMaxima(marca, 50, "La marca")
                } else null
            },
            FormValidator.Campo(tilCategoria) {
                FormValidator.requerido(nombreCategoria, "La categoría")
            },
            FormValidator.Campo(tilUnidad) {
                FormValidator.requerido(nombreUnidadVisual, "La unidad de medida")
            }
        )
        if (!ok) return

        // 2. Validación de duplicidad de código (si es un producto nuevo o se modificó el código)
        if (productoEditar == null || productoEditar.codigoPrincipal != codigo) {
            val codigoExiste = listaProductosExistentes.any {
                it.codigoPrincipal.equals(codigo, ignoreCase = true) && it.id != productoEditar?.id
            }
            if (codigoExiste) {
                FormValidator.marcarError(tilCodigo, "Ya existe otro producto registrado con este código")
                etCodigo.requestFocus()
                return
            }
        }

        // 3. Validación de duplicidad de nombre de producto dentro del negocio
        val nombreExiste = listaProductosExistentes.any {
            it.nombre.equals(nombre, ignoreCase = true) && it.id != productoEditar?.id
        }
        if (nombreExiste) {
            FormValidator.marcarError(tilNombre, "Ya existe un producto registrado con este mismo nombre")
            etNombre.requestFocus()
            return
        }

        // 4. Verificación de existencia de categoría seleccionada
        val idCategoriaFinal = listaCategoriasBD.find { it.nombre.equals(nombreCategoria, ignoreCase = true) }?.id ?: categoriaSeleccionada?.id
        if (idCategoriaFinal == null) {
            Toast.makeText(requireContext(), "Selecciona una categoría válida de la lista", Toast.LENGTH_SHORT).show()
            spinnerCategoria.requestFocus()
            return
        }

        val precio = if ((productoEditar?.precioUnitario ?: 0.0) > 0) productoEditar!!.precioUnitario else 0.0

        val productoGuardado = ProductoDto(
            id = productoEditar?.id,
            codigoPrincipal = codigo,
            nombre = nombre,
            marca = marca,
            precioUnitario = precio,
            costoPromedioActual = productoEditar?.costoPromedioActual ?: 0.0,
            categoriaId = idCategoriaFinal,
            categoria = nombreCategoria,
            unidadMedida = unidadTecnica,
            grabaIva = cbGrabaIva.isChecked,
            tieneCaducidad = cbTieneCaducidad.isChecked,
            imagen = imagenBase64Seleccionada
        )

        onGuardarListener(productoGuardado, idCategoriaFinal)
        dismiss()
    }
}