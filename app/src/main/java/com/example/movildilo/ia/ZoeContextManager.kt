package com.example.movildilo.ia

import com.example.movildilo.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

object ZoeContextManager {

    // Tiempo de vigencia del caché en memoria: 3 minutos
    private const val TIEMPO_CACHE_MS = 3 * 60 * 1000L
    private var ultimaActualizacion: Long = 0
    private var negocioIdCacheado: Long = -1L

    // Variables de caché de contexto ya procesado en formato Markdown
    private var contextoPropietario = ""
    private var contextoVendedor = ""
    private var contextoBodeguero = ""
    private var alertasGenerales = ""

    suspend fun obtenerContextoPorRol(
        negocioId: Long,
        authHeader: String,
        rolUsuario: String,
        forzarActualizacion: Boolean = false
    ): Pair<String, String> = withContext(Dispatchers.IO) {

        val tiempoActual = System.currentTimeMillis()
        val cacheExpirado = (tiempoActual - ultimaActualizacion) > TIEMPO_CACHE_MS
        val cambioDeNegocio = negocioIdCacheado != negocioId

        if (cacheExpirado || cambioDeNegocio || forzarActualizacion || contextoPropietario.isEmpty()) {
            sincronizarDesdeApi(negocioId, authHeader)
            ultimaActualizacion = tiempoActual
            negocioIdCacheado = negocioId
        }

        val contextoFiltrado = when (rolUsuario.uppercase()) {
            "VENDEDOR", "CAJERO" -> contextoVendedor
            "BODEGUERO", "INVENTARIO" -> contextoBodeguero
            "PROPIETARIO", "ADMINISTRADOR" -> contextoPropietario
            else -> contextoPropietario
        }

        return@withContext Pair(contextoFiltrado, alertasGenerales)
    }

    private fun fmt(valor: Double?): String = String.format(Locale.US, "%.2f", valor ?: 0.0)

    private suspend fun sincronizarDesdeApi(negocioId: Long, authHeader: String) = coroutineScope {
        val api = RetrofitClient.apiService

        val negocioDef = async { runCatching { api.getNegocio(authHeader, negocioId) }.getOrNull()?.body() }
        val productosDef = async { runCatching { api.getCatalogo(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val categoriasDef = async { runCatching { api.getCategorias(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val inventarioDef = async { runCatching { api.getInventario(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val bodegasDef = async { runCatching { api.getBodegas(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val proveedoresDef = async { runCatching { api.getProveedores(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val comprasDef = async { runCatching { api.getCompras(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val clientesDef = async { runCatching { api.getClientes(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val facturasDef = async { runCatching { api.getFacturas(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val cuentasDef = async { runCatching { api.getCuentasPorCobrar(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val equipoDef = async { runCatching { api.getEquipo(authHeader, negocioId) }.getOrNull()?.body() ?: emptyList() }
        val alertasDef = async { runCatching { api.getAlertasCaducidad(authHeader, negocioId, 30) }.getOrNull()?.body() ?: emptyList() }

        val negocio = negocioDef.await()
        val productos = productosDef.await()
        val categorias = categoriasDef.await()
        val inventario = inventarioDef.await()
        val bodegas = bodegasDef.await()
        val proveedores = proveedoresDef.await()
        val compras = comprasDef.await()
        val clientes = clientesDef.await()
        val facturas = facturasDef.await()
        val cuentas = cuentasDef.await()
        val equipo = equipoDef.await()
        val alertasCaducidad = alertasDef.await()

        // --- Configuración del negocio ---
        val textoConfiguracion = """
            - Razón social: ${negocio?.razonSocial ?: "No configurada"}.
            - Nombre comercial: ${negocio?.nombreComercial ?: "No configurado"}.
            - RUC: ${negocio?.ruc ?: "No configurado"}.
            - Dirección: ${negocio?.direccion ?: "No configurada"}.
            - Obligado a llevar contabilidad: ${if (negocio?.obligadoContabilidad == true) "Sí" else "No"}.
            - Método de costeo: ${negocio?.metodoCosteo ?: "No definido"}.
        """.trimIndent()

        // --- Catálogo de productos y categorías ---
        val nombresCategorias = categorias.mapNotNull { it.nombre }.filter { it.isNotBlank() }
        val listaProductos = productos.take(40).joinToString("; ") { p ->
            "${p.nombre ?: "S/N"} (cod: ${p.codigoPrincipal ?: "S/C"}, marca: ${p.marca ?: "-"}, categoría: ${p.categoria ?: "-"}, PVP: $${fmt(p.precioUnitario)})"
        }.ifEmpty { "Aún no hay productos registrados." }

        val textoCatalogo = """
            - Categorías registradas (${categorias.size}): ${nombresCategorias.joinToString(", ").ifEmpty { "ninguna aún" }}.
            - Total de productos en catálogo: ${productos.size}.
            - Detalle de productos: $listaProductos.
        """.trimIndent()

        // --- Inventario y bodegas ---
        val nombresBodegas = bodegas.map { it.nombre }.filter { it.isNotBlank() }
        val valorTotalInventario = inventario.sumOf { it.valorInventario ?: 0.0 }
        val stockBajo = inventario
            .filter { (it.cantidadActual ?: 0) <= (it.stockMinimo ?: 0) }
            .take(20)
            .joinToString("; ") { i -> "${i.productoNombre ?: "Producto"} en ${i.bodegaNombre ?: "bodega"} (quedan ${i.cantidadActual ?: 0}, mínimo ${i.stockMinimo ?: 0})" }
            .ifEmpty { "Ningún producto en stock bajo por el momento." }

        val inventarioPorBodega = inventario
            .groupBy { it.bodegaNombre ?: "Bodega sin nombre" }
            .toSortedMap()
            .entries
            .joinToString("\n            ") { (bodega, items) ->
                val valorBodega = items.sumOf { it.valorInventario ?: 0.0 }
                val detalleProductos = items
                    .sortedByDescending { it.cantidadActual ?: 0 }
                    .take(15)
                    .joinToString(", ") { i -> "${i.productoNombre ?: "Producto"} (${i.cantidadActual ?: 0} uds)" }
                "  · $bodega: ${items.size} productos distintos, valor $${fmt(valorBodega)}. Detalle: $detalleProductos"
            }.ifEmpty { "Aún no hay inventario registrado en ninguna bodega." }

        val textoInventario = """
            - Bodegas registradas (${bodegas.size}): ${nombresBodegas.joinToString(", ").ifEmpty { "ninguna aún" }}.
            - Valor total actual del inventario: $${fmt(valorTotalInventario)}.
            - Inventario detallado por bodega:
            $inventarioPorBodega
            - Productos con stock bajo o crítico: $stockBajo.
        """.trimIndent()

        // --- Proveedores y compras ---
        val nombresProveedores = proveedores.filter { it.estado != false }.mapNotNull { it.nombreComercial }
        val ultimasCompras = compras.takeLast(10).joinToString("; ") { c ->
            "N° ${c.numeroComprobante ?: "S/N"} a ${c.proveedorNombre ?: "proveedor"} en ${c.bodegaIngresoNombre ?: "bodega"} por $${fmt(c.totalCompra)}"
        }.ifEmpty { "Aún no hay compras registradas." }

        val textoCompras = """
            - Proveedores activos (${nombresProveedores.size} de ${proveedores.size} registrados): ${nombresProveedores.joinToString(", ").ifEmpty { "ninguno aún" }}.
            - Total de compras registradas: ${compras.size}.
            - Últimas compras: $ultimasCompras.
        """.trimIndent()

        // --- Clientes ---
        val nombresClientes = clientes.take(30).mapNotNull { c ->
            c.nombreCompleto?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(c.primerNombre, c.apellidoPaterno).joinToString(" ").ifBlank { null }
        }

        val textoClientes = """
            - Total de clientes registrados: ${clientes.size}. Algunos: ${nombresClientes.joinToString(", ").ifEmpty { "ninguno aún" }}.
        """.trimIndent()

        // --- Facturas y ventas ---
        val totalVentas = facturas.sumOf { it.totalCalculado }
        val ultimasFacturas = facturas.takeLast(10).joinToString("; ") { f ->
            "#${f.numeroFactura ?: "S/N"} - ${f.nombreClienteFormateado} - $${fmt(f.totalCalculado)} (${f.metodoPago ?: "?"})"
        }.ifEmpty { "Aún no hay facturas emitidas." }

        val textoVentas = """
            - Total de facturas emitidas: ${facturas.size}, con ventas acumuladas por $${fmt(totalVentas)}.
            - Últimas facturas: $ultimasFacturas.
        """.trimIndent()

        // --- Cuentas por cobrar ---
        val cuentasPendientes = cuentas.filter { !it.estado.equals("PAGADO", ignoreCase = true) }
        val totalPendiente = cuentasPendientes.sumOf { it.saldoPendiente ?: 0.0 }
        val detalleCobros = cuentasPendientes.take(15).joinToString("; ") { c ->
            "${c.clienteNombre ?: "Cliente"} debe $${fmt(c.saldoPendiente)} (factura ${c.numeroFactura ?: "S/N"}, vence ${c.fechaVencimiento ?: "N/D"})"
        }.ifEmpty { "No hay cuentas por cobrar pendientes." }

        val textoCobros = """
            - Cuentas por cobrar pendientes: ${cuentasPendientes.size}, por un total de $${fmt(totalPendiente)}.
            - Detalle: $detalleCobros.
        """.trimIndent()

        // --- Equipo de trabajo ---
        val equipoActivo = equipo.filter { miembro ->
            val estadoInvitacion = miembro.estadoInvitacion?.uppercase(Locale.ROOT) ?: ""
            val estadoLaboral = miembro.estadoLaboral?.uppercase(Locale.ROOT) ?: ""
            estadoInvitacion != "PENDIENTE" && estadoLaboral != "PENDIENTE"
        }
        val listaEquipo = equipoActivo.joinToString("; ") { m -> "${m.nombreUsuario ?: "Usuario"} (${m.rol ?: "Sin rol"})" }
            .ifEmpty { "Aún no hay miembros en el equipo." }

        val textoEquipo = """
            - Miembros activos del equipo (${equipoActivo.size}): $listaEquipo.
        """.trimIndent()

        // --- Ensamblado final por rol ---
        contextoBodeguero = """
            **Catálogo de Productos y Categorías:**
            $textoCatalogo

            **Inventario, Bodegas y Movimientos:**
            $textoInventario

            **Proveedores y Abastecimiento:**
            $textoCompras
        """.trimIndent()

        contextoVendedor = """
            **Catálogo disponible (Para ventas):**
            $textoCatalogo

            **Directorio de Clientes:**
            $textoClientes

            **Facturas y Ventas:**
            $textoVentas

            **Cuentas por Cobrar (Créditos):**
            $textoCobros
        """.trimIndent()

        contextoPropietario = """
            **Configuración del Negocio:**
            $textoConfiguracion

            $contextoBodeguero

            $contextoVendedor

            **Mi Equipo:**
            $textoEquipo
        """.trimIndent()

        alertasGenerales = if (alertasCaducidad.isNotEmpty()) {
            alertasCaducidad.take(15).joinToString("; ") { a ->
                "${a.productoNombre ?: "Producto"} caduca el ${a.fechaCaducidad ?: "N/D"} (quedan ${a.diasParaCaducar ?: "?"} días)"
            }
        } else {
            "No hay productos próximos a caducar en los siguientes 30 días."
        }
    }

    fun invalidarCache() {
        ultimaActualizacion = 0
        negocioIdCacheado = -1L
        contextoPropietario = ""
        contextoVendedor = ""
        contextoBodeguero = ""
        alertasGenerales = ""
    }
}
