package com.example.movildilo.ia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.movildilo.ui.bodega.BodegasActivity
import com.example.movildilo.ui.dashboard.BodegueroActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.example.movildilo.ui.dashboard.VendedorActivity
import com.example.movildilo.ui.productos.CatalogoProductosActivity
import com.example.movildilo.ui.propietario.CategoriasActivity
import com.example.movildilo.ui.propietario.ClientesActivity
import com.example.movildilo.ui.abastecimiento.ComprasActivity
import com.example.movildilo.ui.facturas.HistorialFacturasActivity
import com.example.movildilo.ui.proveedores.ProveedoresActivity

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

    private fun pasosFacturacionPorVoz() = listOf(
        GuiaPaso("Bodegas", "Che, arranquemos por las bodegas. Acá creás tus puntos de almacenamiento físico para organizar dónde guardás la mercadería y controlar el stock.", BodegasActivity::class.java),
        GuiaPaso("Proveedores", "Acá está el directorio de tus proveedores, quienes te proveen los productos que necesitás.", ProveedoresActivity::class.java),
        GuiaPaso("Compras (Abastecimiento)", "En esta sección registrás las compras a tus proveedores para ingresar stock nuevo a tus bodegas.", ComprasActivity::class.java),
        GuiaPaso("Categorías", "Acá organizás tu catálogo en categorías para encontrar todo mucho más rápido.", CategoriasActivity::class.java),
        GuiaPaso("Catálogo de Productos", "Acá registrás los productos con su precio, marca e IVA para poder venderlos.", CatalogoProductosActivity::class.java),
        GuiaPaso("Clientes", "Acá guardás los datos de tus clientes para asociarlos de forma directa a las facturas.", ClientesActivity::class.java),
        GuiaPaso("Facturas y Ventas", "¡Listo, che! Ya tenés todo lo necesario. Acá podés emitir facturas o usar mi voz: decime el cliente, la bodega y los productos juntos, y yo armo el ticket por vos.", HistorialFacturasActivity::class.java)
    )

    fun iniciar(activity: AppCompatActivity, rol: String) {
        detener(activity, decirDespedida = false)

        pasos = pasosFacturacionPorVoz()
        pasoActual = 0
        activa = true
        rolActual = rol

        ZoeGuideOverlay.mostrar(activity, pasos[0], esUltimo = pasos.size == 1)

        voz = ZoeSpeechHelper(activity.applicationContext).also { helper ->
            helper.inicializar(
                onListo = {
                    if (activa && !activity.isFinishing && !activity.isDestroyed) {
                        // Empieza a escuchar los comandos de guía SOLO DESPUÉS de que termine de hablar
                        helper.hablar(pasos[0].explicacion) {
                            if (activa && !activity.isFinishing && !activity.isDestroyed) {
                                escucharComandosDeLaGuia(activity)
                            }
                        }
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

    fun siguiente(activity: AppCompatActivity) {
        if (!activa || activity.isFinishing || activity.isDestroyed) return
        voz?.detenerHabla()
        voz?.detenerEscuchaDeComandos()

        if (pasoActual >= pasos.size - 1) {
            val destinoInicio = when (rolActual.uppercase()) {
                "VENDEDOR", "CAJERO" -> VendedorActivity::class.java
                "BODEGUERO", "INVENTARIO" -> BodegueroActivity::class.java
                else -> PropietarioActivity::class.java
            }
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

            if (activity !is PropietarioActivity && activity !is BodegueroActivity && activity !is VendedorActivity) {
                activity.finish()
            }
        }


        voz?.hablar(paso.explicacion) {
            if (activa && !activity.isFinishing && !activity.isDestroyed) {
                escucharComandosDeLaGuia(activity)
            }
        }
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
                    voz?.hablar("Dale, te llevo a ${creacion.third}. Decime \"siguiente\" cuando estés.") {
                        if (activa) escucharComandosDeLaGuia(activity)
                    }
                    ZoeActionRouter.navegar(activity, creacion.first, creacion.second)
                }
            }
        }
    }

    private fun esOrdenDeAvanzar(texto: String): Boolean {
        val t = texto.lowercase()
        return listOf("siguiente", "avanza", "continua", "sigamos").any { t.contains(it) }
    }

    fun detener(activity: Activity?, decirDespedida: Boolean = true) {
        activa = false
        pasoActual = -1
        pasos = emptyList()
        escuchandoManualmente = false

        voz?.liberar()
        voz = null

        if (activity != null) {
            ZoeGuideOverlay.ocultar(activity)
        }
    }

    fun activarMicrofono(activity: AppCompatActivity) {
        if (!activa || escuchandoManualmente || activity.isFinishing || activity.isDestroyed) return

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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