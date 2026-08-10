package com.example.movildilo.utils

import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import com.example.movildilo.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.text.Normalizer

/**
 * 🛡️ Validador central de formularios de la app.
 *
 * Objetivo: que TODOS los formularios (login, registro, productos, clientes, proveedores,
 * categorías, bodegas, compras, facturas, perfil, negocio, equipo, cuentas por cobrar...)
 * usen las MISMAS reglas y los MISMOS mensajes de error, claros, exactos y bonitos, en vez de
 * que cada pantalla invente su propio "Toast.makeText(this, "Error", ...)" genérico.
 *
 * Cómo se usa (caso típico, con TextInputLayout de Material, que ya usan la mayoría de
 * formularios de la app):
 *
 * ```
 * FormValidator.limpiar(tilNombre, tilPrecio)
 * val errores = mutableListOf<String>()
 * FormValidator.requerido(etNombre.text?.toString(), "El nombre del producto")
 *     ?.let { FormValidator.marcarError(tilNombre, it); errores.add(it) }
 * FormValidator.decimalPositivo(etPrecio.text?.toString(), "El precio")
 *     ?.let { FormValidator.marcarError(tilPrecio, it); errores.add(it) }
 * if (errores.isNotEmpty()) {
 *     FormValidator.enfocarPrimerError(tilNombre, tilPrecio)
 *     return
 * }
 * ```
 *
 * O, de forma más compacta, con [validar]:
 * ```
 * val ok = FormValidator.validar(
 *     FormValidator.Campo(tilNombre) { FormValidator.requerido(etNombre.text?.toString(), "El nombre") },
 *     FormValidator.Campo(tilPrecio) { FormValidator.decimalPositivo(etPrecio.text?.toString(), "El precio") }
 * )
 * if (!ok) return
 * ```
 */
object FormValidator {

    // ------------------------------------------------------------------
    // Reglas de validación: cada función devuelve null si es válido, o un
    // mensaje de error EXACTO y entendible si no lo es.
    // ------------------------------------------------------------------

    /** Campo de texto obligatorio (no vacío, no solo espacios). */
    fun requerido(valor: String?, nombreCampo: String): String? {
        if (valor.isNullOrBlank()) return "$nombreCampo es obligatorio."
        return null
    }

    /** Longitud mínima de un texto ya no-vacío. */
    fun longitudMinima(valor: String?, minimo: Int, nombreCampo: String): String? {
        val v = valor?.trim().orEmpty()
        if (v.isNotEmpty() && v.length < minimo) {
            return "$nombreCampo debe tener al menos $minimo caracteres (tiene ${v.length})."
        }
        return null
    }

    /** Longitud máxima de un texto. */
    fun longitudMaxima(valor: String?, maximo: Int, nombreCampo: String): String? {
        val v = valor?.trim().orEmpty()
        if (v.length > maximo) {
            return "$nombreCampo no puede superar los $maximo caracteres (tiene ${v.length})."
        }
        return null
    }

    /** Correo electrónico con formato válido. */
    fun correo(valor: String?, nombreCampo: String = "El correo electrónico"): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatorio."
        val regex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!regex.matches(v)) return "$nombreCampo no tiene un formato válido (ej: nombre@correo.com)."
        return null
    }

    /** Correo electrónico opcional: si viene vacío no marca error, si viene lo valida. */
    fun correoOpcional(valor: String?, nombreCampo: String = "El correo electrónico"): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return null
        val regex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!regex.matches(v)) return "$nombreCampo no tiene un formato válido (ej: nombre@correo.com)."
        return null
    }

    /** Teléfono ecuatoriano: 7 a 10 dígitos, puede incluir espacios/guiones que se ignoran. */
    fun telefono(valor: String?, nombreCampo: String = "El teléfono", obligatorio: Boolean = true): String? {
        val soloDigitos = valor?.filter { it.isDigit() }.orEmpty()
        if (soloDigitos.isEmpty()) {
            return if (obligatorio) "$nombreCampo es obligatorio." else null
        }
        if (soloDigitos.length !in 7..10) {
            return "$nombreCampo debe tener entre 7 y 10 dígitos (ingresaste ${soloDigitos.length})."
        }
        return null
    }

    /** Cédula ecuatoriana: 10 dígitos + validación del dígito verificador. */
    fun cedulaEcuatoriana(valor: String?, nombreCampo: String = "La cédula"): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatoria."
        if (!v.all { it.isDigit() } || v.length != 10) {
            return "$nombreCampo debe tener exactamente 10 dígitos numéricos."
        }
        val provincia = v.substring(0, 2).toInt()
        if (provincia < 1 || provincia > 24) {
            return "$nombreCampo no es válida: los dos primeros dígitos no corresponden a ninguna provincia."
        }
        val tercerDigito = v[2].digitToInt()
        if (tercerDigito > 6) {
            return "$nombreCampo no es válida: el tercer dígito debe estar entre 0 y 6."
        }
        val coeficientes = intArrayOf(2, 1, 2, 1, 2, 1, 2, 1, 2)
        var suma = 0
        for (i in 0..8) {
            var valorPos = v[i].digitToInt() * coeficientes[i]
            if (valorPos > 9) valorPos -= 9
            suma += valorPos
        }
        val digitoVerificador = (10 - (suma % 10)) % 10
        if (digitoVerificador != v[9].digitToInt()) {
            return "$nombreCampo no es válida: el dígito verificador no coincide."
        }
        return null
    }

    /** RUC ecuatoriano: 13 dígitos, empieza como una cédula válida (10 primeros dígitos) y termina en "001". */
    fun rucEcuatoriano(valor: String?, nombreCampo: String = "El RUC"): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatorio."
        if (!v.all { it.isDigit() } || v.length != 13) {
            return "$nombreCampo debe tener exactamente 13 dígitos numéricos."
        }
        if (!v.endsWith("001")) {
            return "$nombreCampo debe terminar en \"001\" (establecimiento matriz)."
        }
        val errorCedula = cedulaEcuatoriana(v.substring(0, 10), nombreCampo)
        if (errorCedula != null) {
            return "$nombreCampo no es válido: los primeros 10 dígitos no forman una cédula válida."
        }
        return null
    }

    /** Cédula o RUC: acepta 10 dígitos (cédula) o 13 dígitos (RUC). */
    fun cedulaORuc(valor: String?, nombreCampo: String = "El documento", obligatorio: Boolean = true): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return if (obligatorio) "$nombreCampo (cédula o RUC) es obligatorio." else null
        if (!v.all { it.isDigit() }) return "$nombreCampo solo debe contener números."
        return when (v.length) {
            10 -> cedulaEcuatoriana(v, nombreCampo)
            13 -> rucEcuatoriano(v, nombreCampo)
            else -> "$nombreCampo debe tener 10 dígitos (cédula) o 13 dígitos (RUC), ingresaste ${v.length}."
        }
    }

    /** Solo letras y espacios (nombres, razón social, etc). Acepta tildes y ñ. */
    fun soloTexto(valor: String?, nombreCampo: String, obligatorio: Boolean = true): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return if (obligatorio) "$nombreCampo es obligatorio." else null
        val regex = Regex("^[A-Za-zÁÉÍÓÚÑáéíóúñ .'-]+$")
        if (!regex.matches(v)) return "$nombreCampo solo puede contener letras y espacios."
        return null
    }

    /** Número entero, con rango opcional. */
    fun numeroEntero(valor: String?, nombreCampo: String, minimo: Int? = null, maximo: Int? = null, obligatorio: Boolean = true): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return if (obligatorio) "$nombreCampo es obligatorio." else null
        val num = v.toIntOrNull() ?: return "$nombreCampo debe ser un número entero (sin decimales ni letras)."
        if (minimo != null && num < minimo) return "$nombreCampo no puede ser menor a $minimo."
        if (maximo != null && num > maximo) return "$nombreCampo no puede ser mayor a $maximo."
        return null
    }

    /** Número decimal (precio, cantidad, descuento...) con rango opcional. */
    fun numeroDecimal(valor: String?, nombreCampo: String, minimo: Double? = null, maximo: Double? = null, obligatorio: Boolean = true): String? {
        val v = valor?.trim()?.replace(",", ".").orEmpty()
        if (v.isEmpty()) return if (obligatorio) "$nombreCampo es obligatorio." else null
        val num = v.toDoubleOrNull() ?: return "$nombreCampo debe ser un número válido (ej: 12.50)."
        if (minimo != null && num < minimo) return "$nombreCampo no puede ser menor a $minimo."
        if (maximo != null && num > maximo) return "$nombreCampo no puede ser mayor a $maximo."
        return null
    }

    /** Igual que [numeroDecimal] pero exige que sea estrictamente mayor a 0 (precio, cantidad a comprar, etc). */
    fun montoMayorACero(valor: String?, nombreCampo: String): String? {
        val v = valor?.trim()?.replace(",", ".").orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatorio."
        val num = v.toDoubleOrNull() ?: return "$nombreCampo debe ser un número válido (ej: 12.50)."
        if (num <= 0) return "$nombreCampo debe ser mayor a 0."
        return null
    }

    /** Porcentaje entre 0 y 100. */
    fun porcentaje(valor: String?, nombreCampo: String, obligatorio: Boolean = false): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return if (obligatorio) "$nombreCampo es obligatorio." else null
        val num = v.toDoubleOrNull() ?: return "$nombreCampo debe ser un número (ej: 10 para 10%)."
        if (num < 0 || num > 100) return "$nombreCampo debe estar entre 0 y 100."
        return null
    }

    /**
     * Contraseña: mínimo 6 caracteres, al menos una letra y un número.
     * (Se mantiene simple a propósito: son negocios pequeños/medianos, no hace falta exigir
     * símbolos especiales, pero sí una fuerza mínima razonable.)
     */
    fun password(valor: String?, nombreCampo: String = "La contraseña", minimo: Int = 6): String? {
        val v = valor.orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatoria."
        if (v.length < minimo) return "$nombreCampo debe tener al menos $minimo caracteres."
        if (!v.any { it.isDigit() }) return "$nombreCampo debe incluir al menos un número."
        if (!v.any { it.isLetter() }) return "$nombreCampo debe incluir al menos una letra."
        return null
    }

    /** Confirmación de contraseña: debe coincidir exactamente con la original. */
    fun confirmarPassword(password: String?, confirmacion: String?, nombreCampo: String = "Las contraseñas"): String? {
        if (confirmacion.isNullOrEmpty()) return "Debes confirmar la contraseña."
        if (password != confirmacion) return "$nombreCampo no coinciden."
        return null
    }

    /** Que un Long? (id seleccionado en un spinner/autocompletado) no sea null. */
    fun seleccionRequerida(valorId: Long?, nombreCampo: String): String? {
        if (valorId == null) return "Debes seleccionar $nombreCampo."
        return null
    }

    /**
     * Fecha de nacimiento en formato "yyyy-MM-dd": obligatoria, con formato válido y edad entre
     * [edadMinima] y [edadMaxima] años (por defecto 18 a 98, útil para registrar usuarios/empleados).
     */
    fun fechaNacimiento(valor: String?, nombreCampo: String = "La fecha de nacimiento", edadMinima: Int = 18, edadMaxima: Int = 98): String? {
        val v = valor?.trim().orEmpty()
        if (v.isEmpty()) return "$nombreCampo es obligatoria."
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.isLenient = false
        val fecha = try { sdf.parse(v) } catch (e: Exception) { null } ?: return "$nombreCampo no es una fecha válida."
        val nacimiento = java.util.Calendar.getInstance().apply { time = fecha }
        val hoy = java.util.Calendar.getInstance()
        if (nacimiento.after(hoy)) return "$nombreCampo no puede ser una fecha futura."
        var edad = hoy.get(java.util.Calendar.YEAR) - nacimiento.get(java.util.Calendar.YEAR)
        if (hoy.get(java.util.Calendar.DAY_OF_YEAR) < nacimiento.get(java.util.Calendar.DAY_OF_YEAR)) edad--
        if (edad < edadMinima) return "$nombreCampo indica que la persona tiene $edad años; debe tener al menos $edadMinima."
        if (edad > edadMaxima) return "$nombreCampo indica una edad mayor a $edadMaxima años; verifica el dato."
        return null
    }

    // ------------------------------------------------------------------
    // Mostrar / limpiar errores en pantalla (bonito y visible)
    // ------------------------------------------------------------------

    /** Marca (o limpia, si [mensaje] es null) el error de un TextInputLayout de Material. */
    fun marcarError(campo: TextInputLayout?, mensaje: String?) {
        campo ?: return
        campo.isErrorEnabled = mensaje != null
        campo.error = mensaje
    }

    /** Limpia el error de varios TextInputLayout de una sola vez (llamar antes de re-validar). */
    fun limpiar(vararg campos: TextInputLayout?) {
        campos.forEach { marcarError(it, null) }
    }

    /**
     * Hace scroll y enfoca el primer TextInputLayout que tenga un error visible, para que el
     * usuario vea inmediatamente qué campo debe corregir (muy útil en formularios largos donde
     * el campo con error puede estar fuera de la pantalla).
     */
    fun enfocarPrimerError(vararg campos: TextInputLayout?) {
        val primero = campos.firstOrNull { it?.error != null } ?: return
        primero.requestFocus()
        primero.editText?.let { et ->
            et.postDelayed({
                val anim = AnimationUtils.loadAnimation(et.context, android.R.anim.fade_in)
                et.startAnimation(anim)
            }, 50)
        }
    }

    /**
     * Muestra/limpia un error de texto simple debajo de un EditText que NO usa TextInputLayout
     * (por ejemplo login y registro, que tienen un diseño 100% custom). [tvError] es un TextView
     * dedicado a mostrar el mensaje, ya ubicado en el XML justo debajo del campo.
     */
    fun marcarErrorSimple(editText: EditText?, tvError: TextView?, mensaje: String?) {
        if (mensaje != null) {
            tvError?.text = mensaje
            tvError?.visibility = View.VISIBLE
            editText?.setBackgroundResource(R.drawable.bg_rounded_field_error)
        } else {
            tvError?.visibility = View.GONE
            editText?.setBackgroundResource(R.drawable.bg_rounded_field)
        }
    }

    /**
     * Muestra (o limpia) el error nativo de un EditText simple (sin TextInputLayout): subraya
     * el campo en rojo y muestra un ícono + burbuja con el mensaje exacto al tocar el campo.
     * Útil en pantallas con diseño 100% custom (registro, unirse a negocio, etc) donde agregar
     * un TextInputLayout por campo implicaría rehacer todo el layout.
     */
    fun marcarErrorEditText(editText: EditText?, mensaje: String?) {
        editText?.error = mensaje
    }

    /** Contenedor de un campo a validar: el TextInputLayout donde se muestra el error + la regla que lo valida. */
    class Campo(val campo: TextInputLayout, val regla: () -> String?)

    /**
     * Valida una lista de [Campo] de un tirón: limpia errores previos, corre cada regla, marca
     * los que fallan y enfoca el primero. Devuelve true si TODO es válido.
     */
    fun validar(vararg campos: Campo): Boolean {
        limpiar(*campos.map { it.campo }.toTypedArray())
        var todoValido = true
        for (c in campos) {
            val error = c.regla()
            if (error != null) {
                marcarError(c.campo, error)
                todoValido = false
            }
        }
        if (!todoValido) {
            enfocarPrimerError(*campos.map { it.campo }.toTypedArray())
        }
        return todoValido
    }

    /**
     * Para casos donde además de marcar el error en cada campo conviene mostrar un resumen
     * (formularios largos con varios errores a la vez). Muestra un diálogo bonito con viñetas.
     */
    fun mostrarResumenErrores(context: Context, errores: List<String>, titulo: String = "Revisa estos campos") {
        if (errores.isEmpty()) return
        val mensaje = errores.joinToString("\n") { "•  $it" }
        MaterialAlertDialogBuilder(context)
            .setTitle("⚠️ $titulo")
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show()
    }

    /** Normaliza texto (sin tildes, minúsculas) — útil para comparar duplicados al validar. */
    fun normalizar(texto: String?): String {
        if (texto.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "").lowercase().trim()
    }
}