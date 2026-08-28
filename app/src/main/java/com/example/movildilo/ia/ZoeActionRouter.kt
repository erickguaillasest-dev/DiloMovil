package com.example.movildilo.ia

import android.app.Activity
import android.content.Intent
import com.example.movildilo.ui.Bodegas.BodegasActivity
import com.example.movildilo.ui.propietario.CatalogoProductosActivity
import com.example.movildilo.ui.propietario.CategoriasActivity
import com.example.movildilo.ui.propietario.ClientesActivity
import com.example.movildilo.ui.propietario.ComprasActivity
import com.example.movildilo.ui.propietario.ConfiguracionNegocioActivity
import com.example.movildilo.ui.propietario.CuentasPorCobrarActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ui.propietario.Mi_equipo
import com.example.movildilo.ui.propietario.Perfil
import com.example.movildilo.ui.propietario.ProveedoresActivity

/**
 * 🧭 Router de acciones de Zoe.
 *
 * Traduce lo que el usuario dice o escribe ("cómo creo un producto", "quiero cambiar mi
 * contraseña") en una navegación real dentro de la app: a qué [Activity] llevarlo y qué acción
 * debe ejecutar esa pantalla apenas se abra (abrir el modal de creación correspondiente, activar
 * el modo edición del perfil, etc).
 *
 * Se deja aislado (mismo criterio que ZoeKnowledgeBase/ZoeVoiceAI) para que:
 *   1) ZoeBottomSheetDialog no se llene de listas de palabras clave ni de lógica de navegación.
 *   2) Agregar un nuevo tipo de "crear X" o "cambiar X" sea una sola línea en este archivo.
 *   3) Cada Activity destino solo necesite leer el extra [EXTRA_ACCION] en su onCreate y
 *      ejecutar la acción correspondiente (sin acoplarse al chat de Zoe).
 */
object ZoeActionRouter {

    /** Nombre del extra de Intent que cada Activity destino debe revisar en su onCreate. */
    const val EXTRA_ACCION = "zoe_accion"

    /** Valores posibles del extra [EXTRA_ACCION]. */
    object Accion {
        const val CREAR_PRODUCTO = "crear_producto"
        const val CREAR_CATEGORIA = "crear_categoria"
        const val CREAR_CLIENTE = "crear_cliente"
        const val CREAR_PROVEEDOR = "crear_proveedor"
        const val CREAR_BODEGA = "crear_bodega"
        const val CREAR_COMPRA = "crear_compra"
        const val CREAR_FACTURA = "crear_factura"
        const val REGISTRAR_PAGO = "registrar_pago"
        const val EDITAR_PERFIL = "editar_perfil"
        const val CAMBIAR_PASSWORD = "cambiar_password"
        const val EDITAR_NEGOCIO = "editar_negocio"
        const val VER_EQUIPO = "ver_equipo"
    }

    /** Una regla de "crear algo": qué palabras lo identifican, a qué pantalla va y qué acción dispara. */
    private data class ReglaCreacion(
        val palabrasClave: List<String>,
        val destino: Class<out Activity>,
        val accion: String,
        /** Nombre en español de lo que se va a crear, para el mensaje de confirmación de Zoe. */
        val nombreLegible: String
    )

    private val reglasCreacion = listOf(
        ReglaCreacion(listOf("producto"), CatalogoProductosActivity::class.java, Accion.CREAR_PRODUCTO, "un producto"),
        ReglaCreacion(listOf("categoria", "categoría"), CategoriasActivity::class.java, Accion.CREAR_CATEGORIA, "una categoría"),
        ReglaCreacion(listOf("cliente"), ClientesActivity::class.java, Accion.CREAR_CLIENTE, "un cliente"),
        ReglaCreacion(listOf("proveedor"), ProveedoresActivity::class.java, Accion.CREAR_PROVEEDOR, "un proveedor"),
        ReglaCreacion(listOf("bodega", "almacen", "almacén"), BodegasActivity::class.java, Accion.CREAR_BODEGA, "una bodega"),
        ReglaCreacion(listOf("compra"), ComprasActivity::class.java, Accion.CREAR_COMPRA, "una compra"),
        ReglaCreacion(listOf("factura", "venta", "comprobante"), HistorialFacturasActivity::class.java, Accion.CREAR_FACTURA, "una factura")
    )

    private val verbosCreacion = listOf(
        "crear", "crea", "crees", "agregar", "agrega", "añadir", "añade", "registrar", "registra",
        "nuevo", "nueva", "cómo creo", "como creo", "cómo agrego", "como agrego",
        "cómo registro", "como registro", "cómo hago para crear", "como hago para crear"
    )

    /**
     * Si [texto] pide crear algo que Zoe reconoce (ej. "cómo creo un producto", "agrégame un
     * cliente nuevo"), devuelve la Activity destino, la acción a ejecutar ahí y el nombre legible
     * de lo que se va a crear. Si no reconoce el pedido, devuelve null.
     */
    fun detectarCreacion(texto: String): Triple<Class<out Activity>, String, String>? {
        val t = " ${texto.trim().lowercase()} "
        val pideCrear = verbosCreacion.any { t.contains(it) }
        if (!pideCrear) return null
        for (regla in reglasCreacion) {
            if (regla.palabrasClave.any { t.contains(it) }) {
                return Triple(regla.destino, regla.accion, regla.nombreLegible)
            }
        }
        return null
    }

    private val verbosCambio = listOf(
        "cambiar", "cambia", "cambio", "editar", "edita", "actualizar", "actualiza",
        "actualicemos", "modificar", "modifica", "cómo cambio", "como cambio", "cómo edito",
        "como edito", "cómo actualizo", "como actualizo"
    )

    /**
     * Si [texto] pide cambiar/editar algo de Perfil, Mi Equipo o Configuración del Negocio,
     * devuelve la Activity destino y la acción a ejecutar ahí. Si no reconoce el pedido, null.
     */
    fun detectarCambio(texto: String): Pair<Class<out Activity>, String>? {
        val t = " ${texto.trim().lowercase()} "
        val pideCambiar = verbosCambio.any { t.contains(it) }
        if (!pideCambiar) return null
        return when {
            listOf("contraseña", "clave", "password").any { t.contains(it) } ->
                Perfil::class.java to Accion.CAMBIAR_PASSWORD
            listOf("perfil", "mi foto", "mi nombre", "mis datos", "foto de perfil").any { t.contains(it) } ->
                Perfil::class.java to Accion.EDITAR_PERFIL
            listOf(
                "negocio", "empresa", "ruc", "razón social", "razon social", "logo",
                "dirección", "direccion", "configuración", "configuracion", "costeo"
            ).any { t.contains(it) } -> ConfiguracionNegocioActivity::class.java to Accion.EDITAR_NEGOCIO
            listOf("equipo", "rol", "miembro", "invitar", "invitación", "invitacion", "código de invitación", "codigo de invitacion")
                .any { t.contains(it) } -> Mi_equipo::class.java to Accion.VER_EQUIPO
            listOf("cuota", "abono", "pago", "cuenta por cobrar", "crédito", "credito").any { t.contains(it) } ->
                CuentasPorCobrarActivity::class.java to Accion.REGISTRAR_PAGO
            else -> null
        }
    }

    /**
     * Qué acciones puede disparar cada rol. Refleja exactamente los módulos a los que ese rol
     * ya tiene acceso desde su propio dashboard (PropietarioActivity, BodegueroActivity,
     * VendedorActivity): si el rol no ve la tarjeta de ese módulo en su panel, Zoe tampoco debe
     * llevarlo ahí a crear o cambiar algo.
     */
    private val accionesPorRol: Map<String, Set<String>> = mapOf(
        "PROPIETARIO" to setOf(
            Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_CLIENTE, Accion.CREAR_PROVEEDOR,
            Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.CREAR_FACTURA, Accion.REGISTRAR_PAGO,
            Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD, Accion.EDITAR_NEGOCIO, Accion.VER_EQUIPO
        ),
        "ADMINISTRADOR" to setOf(
            Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_CLIENTE, Accion.CREAR_PROVEEDOR,
            Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.CREAR_FACTURA, Accion.REGISTRAR_PAGO,
            Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD, Accion.EDITAR_NEGOCIO, Accion.VER_EQUIPO
        ),
        // Bodeguero: solo lo relacionado a catálogo/inventario/abastecimiento, más su propio perfil.
        // No tiene acceso a Facturas, Clientes, Configuración del negocio ni Equipo.
        "BODEGUERO" to setOf(
            Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_PROVEEDOR,
            Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD
        ),
        "INVENTARIO" to setOf(
            Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_PROVEEDOR,
            Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD
        ),
        // Vendedor: solo lo comercial (facturar, clientes, cobros), más su propio perfil.
        // No tiene acceso a Catálogo, Inventario, Bodegas, Proveedores, Configuración ni Equipo.
        "VENDEDOR" to setOf(
            Accion.CREAR_FACTURA, Accion.CREAR_CLIENTE, Accion.REGISTRAR_PAGO,
            Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD
        ),
        "CAJERO" to setOf(
            Accion.CREAR_FACTURA, Accion.CREAR_CLIENTE, Accion.REGISTRAR_PAGO,
            Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD
        )
    )

    /** true si el rol [rolUsuario] tiene permitido disparar [accion]. Rol desconocido → solo su propio perfil. */
    fun permitidaParaRol(rolUsuario: String, accion: String): Boolean {
        val permitidas = accionesPorRol[rolUsuario.uppercase()]
            ?: setOf(Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD)
        return accion in permitidas
    }

    /** Navega hacia [destino] pasando la [accion] que esa pantalla debe ejecutar apenas se abra. */
    fun navegar(activity: Activity, destino: Class<out Activity>, accion: String) {
        val intent = Intent(activity, destino)
        intent.putExtra(EXTRA_ACCION, accion)
        activity.startActivity(intent)
    }
}