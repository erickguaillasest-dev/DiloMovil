package com.example.movildilo.ia

import android.app.Activity
import android.content.Intent
import com.example.movildilo.ui.Bodegas.BodegasActivity
import com.example.movildilo.ui.productos.CatalogoProductosActivity
import com.example.movildilo.ui.propietario.CategoriasActivity
import com.example.movildilo.ui.propietario.ClientesActivity
import com.example.movildilo.ui.abastecimiento.ComprasActivity
import com.example.movildilo.ui.propietario.ConfiguracionNegocioActivity
import com.example.movildilo.ui.propietario.CuentasPorCobrarActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ui.propietario.Mi_equipo
import com.example.movildilo.ui.propietario.Perfil
import com.example.movildilo.ui.proveedores.ProveedoresActivity
import com.example.movildilo.ui.propietario.InventarioBodegasActivity
import com.example.movildilo.ui.Kardex.KardexActivity
import com.example.movildilo.ui.propietario.RendimientoComercialActivity

object ZoeActionRouter {

    const val EXTRA_ACCION = "zoe_accion"
    const val EXTRA_MANTENER_ZOE_ABIERTA = "zoe_mantener_abierta"

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

    private data class ReglaCreacion(
        val palabrasClave: List<String>,
        val destino: Class<out Activity>,
        val accion: String,
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

    fun detectarCreacion(texto: String): Triple<Class<out Activity>, String, String>? {
        val t = " ${texto.trim().lowercase()} "
        val pideCrear = verbosCreacion.any { t.contains(it) }
        if (!pideCrear) return null
        for (regla in reglasCreacion) {
            if (regla.palabrasClave.any { t.contains(it) }) return Triple(regla.destino, regla.accion, regla.nombreLegible)
        }
        return null
    }

    private val verbosCambio = listOf(
        "cambiar", "cambia", "cambio", "editar", "edita", "actualizar", "actualiza",
        "actualicemos", "modificar", "modifica", "cómo cambio", "como cambio", "cómo edito",
        "como edito", "cómo actualizo", "como actualizo"
    )

    fun detectarCambio(texto: String): Pair<Class<out Activity>, String>? {
        val t = " ${texto.trim().lowercase()} "
        val pideCambiar = verbosCambio.any { t.contains(it) }
        if (!pideCambiar) return null
        return when {
            listOf("contraseña", "clave", "password").any { t.contains(it) } -> Perfil::class.java to Accion.CAMBIAR_PASSWORD
            listOf("perfil", "mi foto", "mi nombre", "mis datos", "foto de perfil").any { t.contains(it) } -> Perfil::class.java to Accion.EDITAR_PERFIL
            listOf("negocio", "empresa", "ruc", "razón social", "razon social", "logo", "dirección", "direccion", "configuración", "configuracion", "costeo").any { t.contains(it) } -> ConfiguracionNegocioActivity::class.java to Accion.EDITAR_NEGOCIO
            listOf("equipo", "rol", "miembro", "invitar", "invitación", "invitacion", "código de invitación", "codigo de invitacion").any { t.contains(it) } -> Mi_equipo::class.java to Accion.VER_EQUIPO
            listOf("cuota", "abono", "pago", "cuenta por cobrar", "crédito", "credito").any { t.contains(it) } -> CuentasPorCobrarActivity::class.java to Accion.REGISTRAR_PAGO
            else -> null
        }
    }

    private data class ReglaNavegacion(
        val palabrasClave: List<String>,
        val destino: Class<out Activity>,
        val nombreLegible: String
    )

    private val reglasNavegacion = listOf(
        ReglaNavegacion(listOf("inventario", "stock", "existencia"), InventarioBodegasActivity::class.java, "Inventario y Stock"),
        ReglaNavegacion(listOf("bodega", "almacen", "almacén", "sucursal"), BodegasActivity::class.java, "Bodegas"),
        ReglaNavegacion(listOf("kardex", "movimiento"), KardexActivity::class.java, "Kardex de Movimientos"),
        ReglaNavegacion(listOf("producto", "catálogo", "catalogo", "artículo", "articulo"), CatalogoProductosActivity::class.java, "Catálogo de Productos"),
        ReglaNavegacion(listOf("categoría", "categoria"), CategoriasActivity::class.java, "Categorías"),
        ReglaNavegacion(listOf("proveedor"), ProveedoresActivity::class.java, "Proveedores"),
        ReglaNavegacion(listOf("compra", "abastecimiento"), ComprasActivity::class.java, "Compras"),
        ReglaNavegacion(listOf("cliente"), ClientesActivity::class.java, "Clientes"),
        ReglaNavegacion(listOf("factura", "venta", "comprobante", "historial"), HistorialFacturasActivity::class.java, "Facturas y Ventas"),
        ReglaNavegacion(listOf("cuenta por cobrar", "cx", "crédito", "credito", "deuda", "cobro"), CuentasPorCobrarActivity::class.java, "Cuentas por Cobrar"),
        ReglaNavegacion(listOf("rendimiento", "estadística", "estadistica", "métrica", "metrica", "reporte"), RendimientoComercialActivity::class.java, "Rendimiento Comercial"),
        ReglaNavegacion(listOf("equipo", "trabajador", "empleado", "rol"), Mi_equipo::class.java, "Equipo de Trabajo"),
        ReglaNavegacion(listOf("configuración", "configuracion", "negocio", "empresa", "ruc"), ConfiguracionNegocioActivity::class.java, "Configuración del Negocio"),
        ReglaNavegacion(listOf("perfil", "mis datos"), Perfil::class.java, "Mi Perfil")
    )

    private val verbosNavegacion = listOf(
        "ver", "llévame", "llevame", "muéstrame", "muestrame", "abrir", "abre", "ir a",
        "quiero ir", "enséñame", "enseñame", "dirígeme", "dirigeme", "ingresar a", "entra a"
    )

    fun detectarNavegacion(texto: String): Pair<Class<out Activity>, String>? {
        val t = " ${texto.trim().lowercase()} "
        val pideNavegar = verbosNavegacion.any { t.contains(it) }

        if (!pideNavegar) return null

        for (regla in reglasNavegacion) {
            if (regla.palabrasClave.any { t.contains(it) }) {
                return Pair(regla.destino, regla.nombreLegible)
            }
        }
        return null
    }

    private val accionesPorRol: Map<String, Set<String>> = mapOf(
        "PROPIETARIO" to setOf(Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_CLIENTE, Accion.CREAR_PROVEEDOR, Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.CREAR_FACTURA, Accion.REGISTRAR_PAGO, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD, Accion.EDITAR_NEGOCIO, Accion.VER_EQUIPO),
        "ADMINISTRADOR" to setOf(Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_CLIENTE, Accion.CREAR_PROVEEDOR, Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.CREAR_FACTURA, Accion.REGISTRAR_PAGO, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD, Accion.EDITAR_NEGOCIO, Accion.VER_EQUIPO),
        "BODEGUERO" to setOf(Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_PROVEEDOR, Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD),
        "INVENTARIO" to setOf(Accion.CREAR_PRODUCTO, Accion.CREAR_CATEGORIA, Accion.CREAR_PROVEEDOR, Accion.CREAR_BODEGA, Accion.CREAR_COMPRA, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD),
        "VENDEDOR" to setOf(Accion.CREAR_FACTURA, Accion.CREAR_CLIENTE, Accion.REGISTRAR_PAGO, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD),
        "CAJERO" to setOf(Accion.CREAR_FACTURA, Accion.CREAR_CLIENTE, Accion.REGISTRAR_PAGO, Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD)
    )

    fun permitidaParaRol(rolUsuario: String, accion: String): Boolean {
        val permitidas = accionesPorRol[rolUsuario.uppercase()] ?: setOf(Accion.EDITAR_PERFIL, Accion.CAMBIAR_PASSWORD)
        return accion in permitidas
    }

    fun pantallaPermitidaParaRol(rolUsuario: String, destino: Class<out Activity>): Boolean {
        val rol = rolUsuario.uppercase()
        if (rol == "PROPIETARIO" || rol == "ADMINISTRADOR") return true

        val permitidasBodeguero = listOf(
            BodegasActivity::class.java, InventarioBodegasActivity::class.java, CatalogoProductosActivity::class.java,
            CategoriasActivity::class.java, ProveedoresActivity::class.java, ComprasActivity::class.java,
            KardexActivity::class.java, Perfil::class.java
        )

        val permitidasVendedor = listOf(
            HistorialFacturasActivity::class.java, ClientesActivity::class.java,
            CuentasPorCobrarActivity::class.java, RendimientoComercialActivity::class.java,
            Perfil::class.java
        )

        return when (rol) {
            "BODEGUERO", "INVENTARIO" -> permitidasBodeguero.contains(destino)
            "VENDEDOR", "CAJERO" -> permitidasVendedor.contains(destino)
            else -> destino == Perfil::class.java
        }
    }

    fun navegar(activity: Activity, destino: Class<out Activity>, accion: String? = null) {
        val intent = Intent(activity, destino)

        if (accion != null) {
            intent.putExtra(EXTRA_ACCION, accion)
        }

        intent.putExtra(EXTRA_MANTENER_ZOE_ABIERTA, true)

        activity.startActivity(intent)
    }
}