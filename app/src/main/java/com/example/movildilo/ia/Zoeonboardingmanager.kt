package com.example.movildilo.ia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.movildilo.ui.bodegas.BodegasActivity
import com.example.movildilo.ui.dashboard.BodegueroActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.example.movildilo.ui.dashboard.VendedorActivity
import com.example.movildilo.ui.propietario.CatalogoProductosActivity
import com.example.movildilo.ui.propietario.CategoriasActivity
import com.example.movildilo.ui.propietario.ClientesActivity
import com.example.movildilo.ui.propietario.ComprasActivity
import com.example.movildilo.ui.propietario.ConfiguracionNegocioActivity
import com.example.movildilo.ui.propietario.CuentasPorCobrarActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ui.propietario.InventarioBodegasActivity
import com.example.movildilo.ui.Kardex.KardexActivity
import com.example.movildilo.ui.propietario.Mi_equipo
import com.example.movildilo.ui.propietario.Perfil
import com.example.movildilo.ui.propietario.ProveedoresActivity

data class GuiaPaso(
    val tituloPantalla: String,
    val explicacion: String,
    val activityDestino: Class<out Activity>? = null
)

object ZoeOnboardingManager {

    private const val CODIGO_PERMISO_MICROFONO = 9821

    private var activa = false
    private var pasoActual = -1
    private var pasos: List<GuiaPaso> = emptyList()
    private var voz: ZoeSpeechHelper? = null
    private var rolActual: String = "PROPIETARIO"
    private var escuchandoManualmente = false

    val enCurso: Boolean get() = activa

    private fun pasosPropietario() = listOf(
        GuiaPaso(
            "Panel de Control",
            "Esta es tu pantalla de inicio. Aquí ves de un vistazo cómo va tu negocio: las ventas del mes, cuántas facturas has emitido, tus clientes activos y las alertas de stock bajo."
        ),
        GuiaPaso(
            "Bodegas",
            "Empecemos por aquí: en esta pantalla creas tus bodegas o puntos de almacenamiento físico, por ejemplo tu local principal o una bodega secundaria. Es el primer paso de todos, porque tus productos, tus compras y tus ventas siempre están ligados a una bodega. Puedes decirme \"agrega una bodega\" y te abro el formulario de una vez.",
            BodegasActivity::class.java
        ),
        GuiaPaso(
            "Catálogo de Productos",
            "Con al menos una bodega creada, ya puedes registrar tus productos: nombre, precio, marca, si graban IVA y si tienen fecha de caducidad.",
            CatalogoProductosActivity::class.java
        ),
        GuiaPaso(
            "Categorías",
            "Desde aquí organizas tu catálogo en categorías, para encontrar tus productos más rápido cuando factures o revises el inventario.",
            CategoriasActivity::class.java
        ),
        GuiaPaso(
            "Inventario y Bodegas",
            "Aquí ves las existencias en tiempo real de cada producto por bodega, el valor total de tu stock, las alertas de stock mínimo, y puedes hacer ajustes manuales cuando algo no cuadre.",
            InventarioBodegasActivity::class.java
        ),
        GuiaPaso(
            "Kardex de Movimientos",
            "Esta pantalla es el historial: todas las entradas, salidas y transferencias de cada producto entre tus bodegas.",
            KardexActivity::class.java
        ),
        GuiaPaso(
            "Compras",
            "Aquí registras las compras que le haces a tus proveedores. Cada compra que registres aumenta el stock de la bodega que elijas.",
            ComprasActivity::class.java
        ),
        GuiaPaso(
            "Proveedores",
            "Este es tu directorio de proveedores, con las categorías de productos que te suministra cada uno.",
            ProveedoresActivity::class.java
        ),
        GuiaPaso(
            "Clientes",
            "Aquí administras tu directorio de clientes, para poder facturarles después de forma rápida.",
            ClientesActivity::class.java
        ),
        GuiaPaso(
            "Cuentas por Cobrar",
            "Esta pantalla controla tus ventas a crédito: las cuotas, los saldos pendientes y el registro de pagos y abonos de tus clientes.",
            CuentasPorCobrarActivity::class.java
        ),
        GuiaPaso(
            "Mi Equipo",
            "En esta pantalla ves quién trabaja en tu negocio, sus roles, y apruebas las solicitudes de las personas nuevas que pidan unirse con tu código de invitación.",
            Mi_equipo::class.java
        ),
        GuiaPaso(
            "Configuración del Negocio",
            "Aquí configuras la razón social, el nombre comercial, el RUC, la dirección y el método de costeo de tu negocio.",
            ConfiguracionNegocioActivity::class.java
        ),
        GuiaPaso(
            "Perfil",
            "En tu perfil puedes cambiar tu foto, tus datos personales y tu contraseña.",
            Perfil::class.java
        ),
        GuiaPaso(
            "Facturas y Ventas",
            "Y para cerrar el recorrido, vamos a emitir tu primera factura de práctica. Aquí emites tus comprobantes de venta: lo puedes hacer manualmente o hablándome a mí, dime el cliente, la forma de pago, la bodega y los productos, todo junto en una sola frase, y yo armo el ticket. Con esto ya conoces toda la aplicación. Cualquier duda, solo pregúntame.",
            HistorialFacturasActivity::class.java
        )
    )

    private fun pasosVendedor() = listOf(
        GuiaPaso(
            "Panel de Vendedor",
            "Esta es tu pantalla principal. Aquí ves tus ventas al contado y a crédito, el total de facturas emitidas, y si hay cuentas por cobrar pendientes."
        ),
        GuiaPaso(
            "Clientes",
            "En esta pantalla puedes ver y registrar los clientes del negocio, para facturarles rápido después.",
            ClientesActivity::class.java
        ),
        GuiaPaso(
            "Cuentas por Cobrar",
            "Aquí controlas las ventas a crédito: cuotas, saldos pendientes y el registro de pagos y abonos.",
            CuentasPorCobrarActivity::class.java
        ),
        GuiaPaso(
            "Facturas y Ventas",
            "Y para cerrar el recorrido, vamos a emitir tu primera factura de práctica. Aquí la emites, ya sea manualmente o hablándome a mí, y también puedes consultar el historial de tus ventas. Con esto ya conoces todo lo que tienes disponible. Cualquier duda, solo pregúntame.",
            HistorialFacturasActivity::class.java
        )
    )

    private fun pasosBodeguero() = listOf(
        GuiaPaso(
            "Panel de Bodeguero",
            "Esta es tu pantalla principal, enfocada en el control de productos, stock y bodegas del negocio."
        ),
        GuiaPaso(
            "Bodegas",
            "Empecemos por aquí: en esta pantalla creas las bodegas o puntos de almacenamiento físico del negocio. Es el primer paso, porque los productos, las compras y el inventario siempre están ligados a una bodega. Puedes decirme \"agrega una bodega\" y te abro el formulario de una vez.",
            BodegasActivity::class.java
        ),
        GuiaPaso(
            "Catálogo de Productos",
            "Con al menos una bodega creada, aquí consultas y registras productos: nombre, precio, marca, si graban IVA y si tienen fecha de caducidad.",
            CatalogoProductosActivity::class.java
        ),
        GuiaPaso(
            "Categorías",
            "Desde aquí organizas el catálogo en categorías, para encontrar productos más rápido.",
            CategoriasActivity::class.java
        ),
        GuiaPaso(
            "Inventario y Bodegas",
            "Aquí ves las existencias en tiempo real de cada producto por bodega, el valor total del stock y las alertas de stock mínimo.",
            InventarioBodegasActivity::class.java
        ),
        GuiaPaso(
            "Compras",
            "Aquí registras las compras que se le hacen a los proveedores. Cada compra aumenta el stock de la bodega que elijas.",
            ComprasActivity::class.java
        ),
        GuiaPaso(
            "Proveedores",
            "Y este es el directorio de proveedores del negocio. Con esto ya conoces todo lo que tienes disponible. Cualquier duda, solo pregúntame.",
            ProveedoresActivity::class.java
        )
    )

    private fun pasosParaRol(rol: String): List<GuiaPaso> = when (rol.uppercase()) {
        "VENDEDOR", "CAJERO" -> pasosVendedor()
        "BODEGUERO", "INVENTARIO" -> pasosBodeguero()
        else -> pasosPropietario()
    }

    private fun pantallaInicioPorRol(rol: String): Class<out Activity> = when (rol.uppercase()) {
        "VENDEDOR", "CAJERO" -> VendedorActivity::class.java
        "BODEGUERO", "INVENTARIO" -> BodegueroActivity::class.java
        else -> PropietarioActivity::class.java
    }

    fun iniciar(activity: AppCompatActivity, rol: String) {
        detener(activity, decirDespedida = false)

        pasos = pasosParaRol(rol)
        pasoActual = 0
        activa = true
        rolActual = rol

        ZoeGuideOverlay.mostrar(activity, pasos[0], esUltimo = pasos.size == 1)

        voz = ZoeSpeechHelper(activity.applicationContext).also { helper ->
            helper.inicializar(
                onListo = {
                    if (activa && !activity.isFinishing && !activity.isDestroyed) {
                        escucharComandosDeLaGuia(activity)
                        helper.hablar(pasos[0].explicacion)
                    }
                },
                onFallo = {
                    if (activa && !activity.isFinishing && !activity.isDestroyed) {
                        escucharComandosDeLaGuia(activity)
                    }
                }
            )
        }
    }

    /** Avanza al siguiente paso: abre la nueva pantalla y cierra la anterior si no es el inicio. */
    fun siguiente(activity: AppCompatActivity) {
        if (!activa || activity.isFinishing || activity.isDestroyed) return
        voz?.detenerHabla()
        voz?.detenerEscuchaDeComandos()

        if (pasoActual >= pasos.size - 1) {
            val destinoInicio = pantallaInicioPorRol(rolActual)
            detener(activity, decirDespedida = false)
            val intent = Intent(activity, destinoInicio).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            activity.finish()
            return
        }

        pasoActual++
        val paso = pasos[pasoActual]

        ZoeGuideOverlay.actualizarTexto(paso, esUltimo = pasoActual == pasos.size - 1)

        val destino = paso.activityDestino
        if (destino != null && destino != activity::class.java) {
            val intent = Intent(activity, destino)
            activity.startActivity(intent)

            val inicioClass = pantallaInicioPorRol(rolActual)
            if (activity::class.java != inicioClass) {
                activity.finish()
            }
        }

        escucharComandosDeLaGuia(activity)
        voz?.hablar(paso.explicacion)
    }

    private fun escucharComandosDeLaGuia(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        voz?.escucharComandosDeGuia { texto -> procesarComandoDeGuia(activity, texto) }
    }

    private fun procesarComandoDeGuia(activity: AppCompatActivity, texto: String) {
        if (!activa || activity.isFinishing || activity.isDestroyed) return
        when {
            ZoeSpeechHelper.esComandoDeParada(texto) -> detener(activity)
            esOrdenDeAvanzar(texto) -> siguiente(activity)
            else -> {
                val creacion = ZoeActionRouter.detectarCreacion(texto)
                if (creacion != null) {
                    if (ZoeActionRouter.permitidaParaRol(rolActual, creacion.second)) {
                        voz?.hablar("¡Vamos! Te llevo a crear ${creacion.third}. Cuando termines, dime \"siguiente\" para seguir el recorrido.")
                        ZoeActionRouter.navegar(activity, creacion.first, creacion.second)
                    } else {
                        voz?.hablar("Esa función no está disponible para tu rol. Sigamos con el recorrido.")
                    }
                }
            }
        }
    }

    private fun esOrdenDeAvanzar(texto: String): Boolean {
        val textoLimpio = texto.lowercase().replace(Regex("[^a-záéíóúñ0-9\\s]"), " ")
        val palabras = textoLimpio.split("\\s+".toRegex())
        val comandos = setOf("siguiente", "avanza", "avancemos", "continua", "continúa", "continuemos", "sigamos")
        return palabras.any { it in comandos }
    }

    fun detener(activity: Activity?, decirDespedida: Boolean = true) {
        val estabaActiva = activa
        activa = false
        pasoActual = -1
        pasos = emptyList()
        escuchandoManualmente = false

        voz?.liberar()
        voz = null

        if (activity != null) {
            ZoeGuideOverlay.ocultar(activity)
        }

        if (estabaActiva && activity != null && decirDespedida && !activity.isFinishing && !activity.isDestroyed) {
            val despedida = ZoeSpeechHelper(activity.applicationContext)
            despedida.inicializar(
                onListo = {
                    despedida.hablar("Listo, dejo de guiarte.")
                },
                onFallo = {
                    despedida.liberar()
                }
            )
        }
    }

    fun activarMicrofono(activity: AppCompatActivity) {
        if (!activa || escuchandoManualmente || activity.isFinishing || activity.isDestroyed) return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), CODIGO_PERMISO_MICROFONO)
            return
        }

        escuchandoManualmente = true
        voz?.detenerHabla()
        voz?.detenerEscuchaDeComandos()
        ZoeGuideOverlay.mostrarEscuchando()

        voz?.escuchar(
            onResultado = { texto ->
                escuchandoManualmente = false
                ZoeGuideOverlay.ocultarEscuchando()
                procesarComandoDeGuia(activity, texto)
                if (activa) escucharComandosDeLaGuia(activity)
            },
            onError = { _ ->
                escuchandoManualmente = false
                ZoeGuideOverlay.ocultarEscuchando()
                if (activa) escucharComandosDeLaGuia(activity)
            }
        )
    }

    fun onActivityReanudada(activity: Activity) {
        if (!activa || activity.isFinishing || activity.isDestroyed) return
        val paso = pasos.getOrNull(pasoActual) ?: return
        ZoeGuideOverlay.mostrar(activity, paso, esUltimo = pasoActual == pasos.size - 1)
    }

    fun onActivityPausada(activity: Activity) {
        ZoeGuideOverlay.ocultar(activity)
    }
}