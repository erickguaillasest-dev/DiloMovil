package com.example.movildilo.ui.propietario

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
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
import com.example.movildilo.data.model.dto.CategoriaDto
import com.example.movildilo.data.model.dto.ProductoDto
import com.example.movildilo.utils.FormValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProductoDialog(
    private val productoEditar: ProductoDto? = null,
    private val listaCategoriasBD: List<CategoriaDto> = emptyList(), // 🔥 Categorías reales desde public.categorias
    private val listaUnidades: List<String> = listOf("UNIDADES", "KILOGRAMOS", "LITROS", "METROS", "SERVICIOS"), // 🔥 Unidades VARCHAR
    private val onGuardarListener: (ProductoDto, Long?) -> Unit // Retorna el producto y el categoriaId
) : DialogFragment() {

    private lateinit var tvTitulo: TextView
    private lateinit var btnCerrar: ImageButton
    private lateinit var imgPreview: ImageView
    private lateinit var btnSubirFoto: View
    private lateinit var etNombre: TextInputEditText
    private lateinit var etCodigo: TextInputEditText
    private lateinit var etMarca: TextInputEditText
    private lateinit var etPrecioPvp: TextInputEditText
    private lateinit var spinnerCategoria: AutoCompleteTextView
    private lateinit var spinnerUnidad: AutoCompleteTextView
    private lateinit var tilNombre: TextInputLayout
    private lateinit var tilCodigo: TextInputLayout
    private lateinit var tilMarca: TextInputLayout
    private lateinit var tilPrecioPvp: TextInputLayout
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
    ) { uri: Uri? ->
        uri?.let { convertirUriABase64(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_producto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupDropdowns()
        cargarDatosSiEsEdicion()

        val abrirGaleria = View.OnClickListener { seleccionarImagenLauncher.launch("image/*") }
        btnSubirFoto.setOnClickListener(abrirGaleria)
        imgPreview.setOnClickListener(abrirGaleria)

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
        etPrecioPvp = v.findViewById(R.id.etPrecioPvp)
        spinnerCategoria = v.findViewById(R.id.spinnerModalCategoria)
        spinnerUnidad = v.findViewById(R.id.spinnerUnidadMedida)
        cbGrabaIva = v.findViewById(R.id.cbGrabaIva)
        cbTieneCaducidad = v.findViewById(R.id.cbTieneCaducidad)
        btnCancelar = v.findViewById(R.id.btnCancelar)
        btnGuardar = v.findViewById(R.id.btnGuardarProducto)
        tilNombre = v.findViewById(R.id.tilNombreProducto)
        tilCodigo = v.findViewById(R.id.tilCodigoPrincipal)
        tilMarca = v.findViewById(R.id.tilMarca)
        tilPrecioPvp = v.findViewById(R.id.tilPrecioPvp)
        tilCategoria = v.findViewById(R.id.tilCategoria)
        tilUnidad = v.findViewById(R.id.tilUnidadMedida)

        // Limpia el error del campo apenas el usuario empieza a corregirlo
        etNombre.doAfterTextChangedClearError(tilNombre)
        etCodigo.doAfterTextChangedClearError(tilCodigo)
        etMarca.doAfterTextChangedClearError(tilMarca)
        etPrecioPvp.doAfterTextChangedClearError(tilPrecioPvp)
    }

    /** Limpia el error de [til] apenas el usuario modifica el texto de este campo. */
    private fun TextInputEditText.doAfterTextChangedClearError(til: TextInputLayout) {
        this.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { FormValidator.marcarError(til, null) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupDropdowns() {
        // 1. Adapter para Categorías reales de la BD
        val adapterCat = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listaCategoriasBD)
        spinnerCategoria.setAdapter(adapterCat)

        spinnerCategoria.setOnItemClickListener { parent, _, position, _ ->
            categoriaSeleccionada = parent.getItemAtPosition(position) as CategoriaDto
        }

        // 2. Adapter para Unidades de Medida
        val adapterUni = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listaUnidades)
        spinnerUnidad.setAdapter(adapterUni)
    }

    private fun cargarDatosSiEsEdicion() {
        if (productoEditar != null) {
            tvTitulo.text = "Editar Producto"
            etNombre.setText(productoEditar.nombre)
            etCodigo.setText(productoEditar.codigoPrincipal)
            etMarca.setText(productoEditar.marca)
            etPrecioPvp.setText(productoEditar.precioUnitario?.toString() ?: "0.0")


            val catCoincidente = listaCategoriasBD.find { it.nombre.equals(productoEditar.categoria, ignoreCase = true) }
            if (catCoincidente != null) {
                categoriaSeleccionada = catCoincidente
                spinnerCategoria.setText(catCoincidente.nombre, false)
            } else if (productoEditar.categoria != null) {
                spinnerCategoria.setText(productoEditar.categoria, false)
            }

            spinnerUnidad.setText(productoEditar.unidadMedida ?: "UNIDADES", false)
            cbGrabaIva.isChecked = productoEditar.grabaIva ?: true
            cbTieneCaducidad.isChecked = productoEditar.tieneCaducidad ?: false

            imagenBase64Seleccionada = productoEditar.imagen
            cargarImagenEnPreview(productoEditar.imagen)
        } else {
            tvTitulo.text = "Nuevo Producto"
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

    private fun convertirUriABase64(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()

            imagenBase64Seleccionada = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
            cargarImagenEnPreview(imagenBase64Seleccionada)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarProducto() {
        val nombre = etNombre.text.toString().trim()
        val codigo = etCodigo.text.toString().trim()
        val precioStr = etPrecioPvp.text.toString().trim()
        val nombreCategoria = spinnerCategoria.text.toString().trim()
        val unidad = spinnerUnidad.text.toString().trim()

        val ok = FormValidator.validar(
            FormValidator.Campo(tilNombre) {
                FormValidator.requerido(nombre, "El nombre del producto")
                    ?: FormValidator.longitudMinima(nombre, 2, "El nombre del producto")
                    ?: FormValidator.longitudMaxima(nombre, 100, "El nombre del producto")
            },
            FormValidator.Campo(tilCodigo) {
                if (codigo.isNotBlank()) FormValidator.longitudMinima(codigo, 2, "El código principal") else null
            },
            FormValidator.Campo(tilPrecioPvp) {
                FormValidator.montoMayorACero(precioStr, "El precio (PVP)")
            },
            FormValidator.Campo(tilCategoria) {
                FormValidator.requerido(nombreCategoria, "La categoría")
            },
            FormValidator.Campo(tilUnidad) {
                FormValidator.requerido(unidad, "La unidad de medida")
            }
        )
        if (!ok) return

        val precio = precioStr.toDoubleOrNull() ?: 0.0

        val productoGuardado = ProductoDto(
            id = productoEditar?.id,
            codigoPrincipal = etCodigo.text.toString().trim(),
            nombre = nombre,
            marca = etMarca.text.toString().trim(),
            precioUnitario = precio,
            costoPromedio = productoEditar?.costoPromedio ?: 0.0,
            categoria = nombreCategoria,
            unidadMedida = unidad,
            grabaIva = cbGrabaIva.isChecked,
            tieneCaducidad = cbTieneCaducidad.isChecked,
            imagen = imagenBase64Seleccionada
        )

        // Retornamos el producto y el ID de la categoría (categoria_id para PostgreSQL)
        onGuardarListener(productoGuardado, categoriaSeleccionada?.id)
        dismiss()
    }
}