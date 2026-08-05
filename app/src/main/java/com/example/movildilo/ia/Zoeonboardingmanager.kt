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
import com.example.movildilo.ui.propietario.HistorialFacturasActivity
import com.example.movildilo.ui.propietario.InventarioBodegasActivity
import com.example.movildilo.ui.propietario.KardexActivity
import com.example.movildilo.ui.propietario.Mi_equipo
import com.example.movildilo.ui.propietario.Perfil
import com.example.movildilo.ui.propietario.ProveedoresActivity

/** Un paso de la guía: qué pantalla mostrar/abrir y qué le explica Zoe en voz alta al usuario. */
data class GuiaPaso(
    val tituloPantalla: String,
    val explicacion: String,
    /** Clase de la Activity que representa esta pantalla, o null si es la pantalla actual (inicio). */
    val activityDestino: Class<out Activity>? = null
)

/**
 * 🧭 Guía de bienvenida por voz de Zoe.
 *
 * Recorre, pantalla por pantalla, los módulos reales de la app (navegando de verdad entre las
 * Activities) explicando en voz alta para qué sirve cada una. Pensada para un usuario nuevo que
 * recién entra al negocio: empieza creando una bodega (todo lo demás depende de tener al menos
 * una) y termina emitiendo su primera factura de práctica.
 *
 * Cómo funciona sin tocar cada Activity una por una:
 *   - [DiloApplication] registra un ActivityLifecycleCallbacks que, mientras la guía está activa,
 *     agrega automáticamente la burbuja flotante de Zoe ([ZoeGuideOverlay]) sobre cualquier
 *     Activity que se abra o reanude.
 *   - Mientras guía, escucha en bucle por el micrófono (ver [ZoeSpeechHelper.escucharComandosDeGuia]):
 *       · "detente" / "cállate" / "para" → detiene la guía por completo.
 *       · "siguiente" / "avanza" / "continúa" (o tocar el botón) → pasa al siguiente paso.
 *       · Cualquier orden de crear algo que Zoe reconozca ("agrega una bodega", "crea un
 *         producto"...) → te lleva directo a esa pantalla con el formulario ya abierto, sin
 *         salir de la guía; cuando termines, solo dile "siguiente" para retomar el recorrido.
 */
object ZoeOnboardingManager {

    private var activa = false
    private var pasoActual = -1
    private var pasos: List<GuiaPaso> = emptyList()
    private var voz: ZoeSpeechHelper? = null
    private var rolActual: String = "PROPIETARIO"

    val enCurso: Boolean get() = activa

    /**
     * Pasos para el rol PROPIETARIO: recorre todos los módulos administrativos del negocio.
     * Empieza SIEMPRE por Bodegas (todo lo demás —productos, compras, inventario, ventas—
     * depende de tener al menos una) y termina en Facturas, emitiendo una factura de práctica
     * como cierre del recorrido.
     */
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

    /**
     * Pasos para el rol VENDEDOR: se enfoca en lo comercial (facturar, clientes, cobros).
     * Termina también en Facturas, con la emisión de una factura de práctica.
     */
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

    /** Pasos para el rol BODEGUERO: enfocado en catálogo, inventario y abastecimiento (sin Facturas). */
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

    /** Pantalla de inicio (dashboard) a la que vuelve cada rol al terminar su recorrido. */
    private fun pantallaInicioPorRol(rol: String): Class<out Activity> = when (rol.uppercase()) {
        "VENDEDOR", "CAJERO" -> VendedorActivity::class.java
        "BODEGUERO", "INVENTARIO" -> BodegueroActivity::class.java
        else -> PropietarioActivity::class.java
    }

    /** Empieza la guía desde la pantalla actual. [activity] es la Activity de inicio (el dashboard). */
    fun iniciar(activity: AppCompatActivity, rol: String) {
        detener(activity) // por si había una guía previa colgada

        pasos = pasosParaRol(rol)
        pasoActual = 0
        activa = true
        rolActual = rol

        // El primer paso se muestra YA MISMO, sin esperar a que el motor de voz esté listo:
        // así el mensaje ("Esta es tu pantalla de inicio...") se ve de inmediato en la pantalla
        // principal, en vez de aparecer recién cuando el usuario entra a otra sección.
        ZoeGuideOverlay.mostrar(activity, pasos[0], esUltimo = pasos.size == 1)

        voz = ZoeSpeechHelper(activity.applicationContext).also { helper ->
            helper.inicializar(
                onListo = {
                    escucharComandosDeLaGuia(activity)
                    helper.hablar(pasos[0].explicacion)
                },
                onFallo = {
                    // Sin voz disponible en el dispositivo: el mensaje igual queda visible en la
                    // burbuja, y dejamos escuchar comandos (avanzar, "agrega una bodega"...)
                    // aunque Zoe no pueda leerlo en voz alta.
                    escucharComandosDeLaGuia(activity)
                }
            )
        }
    }

    /** Avanza al siguiente paso: si requiere otra pantalla, la abre de verdad. */
    fun siguiente(activity: AppCompatActivity) {
        if (!activa) return
        voz?.detenerHabla()
        voz?.detenerEscuchaDeComandos()

        if (pasoActual >= pasos.size - 1) {
            // Recorrido terminado (último paso, siempre Facturas): cierra la guía sin la
            // despedida hablada —el propio último paso ya se despide— y manda al usuario
            // directo a la pantalla de inicio de SU rol, limpiando el historial de pantallas
            // que recorrió la guía para que no pueda "volver" con el botón atrás a mitad del tour.
            val destinoInicio = pantallaInicioPorRol(rolActual)
            detener(activity, decirDespedida = false)
            val intent = Intent(activity, destinoInicio).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            return
        }
        pasoActual++
        val paso = pasos[pasoActual]

        // Actualiza el texto ya mismo (por si no cambia de Activity); si cambia, DiloApplication
        // vuelve a mostrar la burbuja en onActivityReanudada, ya con el texto correcto.
        ZoeGuideOverlay.actualizarTexto(paso, esUltimo = pasoActual == pasos.size - 1)

        val destino = paso.activityDestino
        if (destino != null && destino != activity::class.java) {
            activity.startActivity(Intent(activity, destino))
        }
        escucharComandosDeLaGuia(activity)
        voz?.hablar(paso.explicacion)
    }

    /**
     * Escucha en bucle, mientras la guía está activa, cualquier orden que el usuario diga:
     * parar la guía, avanzar de paso, o crear algo de lo que Zoe reconozca (ver
     * [ZoeActionRouter.detectarCreacion]). Se relanza sola tras cada frase reconocida, así que
     * sigue escuchando de corrido durante todo el recorrido, aunque se cambie de pantalla.
     */
    private fun escucharComandosDeLaGuia(activity: AppCompatActivity) {
        voz?.escucharComandosDeGuia { texto -> procesarComandoDeGuia(activity, texto) }
    }

    private fun procesarComandoDeGuia(activity: AppCompatActivity, texto: String) {
        if (!activa) return
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
                // Si no reconoce nada (ruido, silencio, una frase suelta), se ignora y se sigue
                // escuchando: no interrumpimos el recorrido por algo que Zoe no entendió.
            }
        }
    }

    /** true si [texto] pide avanzar al siguiente paso de la guía, por voz. */
    private fun esOrdenDeAvanzar(texto: String): Boolean {
        val t = " ${texto.trim().lowercase()} "
        return listOf("siguiente", "avanza", "avancemos", "continua", "continúa", "continuemos", "sigamos")
            .any { t.contains(" $it ") }
    }

    /**
     * Detiene la guía por completo: deja de hablar, de escuchar y quita la burbuja.
     * [decirDespedida] se pone en `false` cuando el recorrido terminó normalmente en su último
     * paso (ese paso ya se despide por su cuenta), para no repetir el mensaje de despedida justo
     * antes de mandar al usuario a su pantalla de inicio.
     */
    fun detener(activity: Activity?, decirDespedida: Boolean = true) {
        val estabaActiva = activa
        activa = false
        pasoActual = -1
        pasos = emptyList()
        escuchandoManualmente = false
        voz?.liberar()
        voz = null
        if (activity != null) ZoeGuideOverlay.ocultar(activity)
        if (estabaActiva && activity != null && decirDespedida) {
            // Aviso corto de que se detuvo, sin volver a escuchar después.
            val despedida = ZoeSpeechHelper(activity.applicationContext)
            despedida.inicializar { despedida.hablar("Listo, dejo de guiarte.") { despedida.liberar() } }
        }
    }

    /**
     * Código de solicitud usado al pedir el permiso de micrófono desde el botón 🎙️ de la guía.
     * La Activity destino no necesita manejar el resultado: si el usuario concede el permiso,
     * simplemente vuelve a tocar el botón para activar el micrófono.
     */
    private const val CODIGO_PERMISO_MICROFONO = 9821

    /** true mientras se está escuchando una orden puntual pedida a mano con el botón del micrófono. */
    private var escuchandoManualmente = false

    /**
     * Activa el micrófono a pedido del usuario (botón 🎙️ de la burbuja de la guía), para que
     * pueda darle una orden puntual a Zoe de forma explícita —mejor experiencia que depender
     * solo de la escucha continua en segundo plano—, con retroalimentación visual clara de
     * cuándo Zoe está escuchando (el botón se pone naranja y pulsa) y de cuándo terminó.
     */
    fun activarMicrofono(activity: AppCompatActivity) {
        if (!activa || escuchandoManualmente) return

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

    /** Llamado por [DiloApplication] cada vez que se reanuda una Activity mientras la guía está activa. */
    fun onActivityReanudada(activity: Activity) {
        if (!activa) return
        val paso = pasos.getOrNull(pasoActual) ?: return
        ZoeGuideOverlay.mostrar(activity, paso, esUltimo = pasoActual == pasos.size - 1)
    }

    fun onActivityPausada(activity: Activity) {
        ZoeGuideOverlay.ocultar(activity)
    }
}