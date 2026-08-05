package com.example.movildilo.ia

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton

/**
 * 🫧 Burbuja flotante de la guía de bienvenida de Zoe.
 *
 * Se agrega/quita directamente sobre el contenido de la Activity actual (android.R.id.content),
 * así que funciona en CUALQUIER pantalla de la app sin tener que modificar el layout de cada una.
 * [ZoeOnboardingManager] la reutiliza y actualiza su texto conforme avanza la guía.
 *
 * También incluye el botón de micrófono 🎙️ con el que el usuario activa a mano la escucha para
 * darle una orden puntual a Zoe (además de la escucha continua en segundo plano), con
 * retroalimentación visual clara de cuándo está escuchando.
 */
object ZoeGuideOverlay {

    private const val TAG_OVERLAY = "zoe_guide_overlay"
    private const val COLOR_MIC_INACTIVO = "#2B354A"
    private const val COLOR_MIC_ESCUCHANDO = "#EA580C"

    private var tvTitulo: TextView? = null
    private var tvExplicacion: TextView? = null
    private var btnSiguiente: MaterialButton? = null
    private var ivMicrofono: ImageView? = null
    private var animacionMic: ObjectAnimator? = null

    fun mostrar(activity: Activity, paso: GuiaPaso, esUltimo: Boolean) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG_OVERLAY)?.let { root.removeView(it) }

        val vista = construirVista(activity, paso, esUltimo)
        vista.tag = TAG_OVERLAY
        root.addView(vista)
    }

    /** Actualiza el texto de la burbuja ya visible (usado cuando NO cambia de Activity). */
    fun actualizarTexto(paso: GuiaPaso, esUltimo: Boolean) {
        tvTitulo?.text = paso.tituloPantalla
        tvExplicacion?.text = paso.explicacion
        btnSiguiente?.text = if (esUltimo) "Terminar" else "Siguiente"
    }

    /** Pone el botón del micrófono en estado "escuchando" (color activo + pulso). */
    fun mostrarEscuchando() {
        val mic = ivMicrofono ?: return
        (mic.background as? GradientDrawable)?.setColor(Color.parseColor(COLOR_MIC_ESCUCHANDO))
        animacionMic?.cancel()
        animacionMic = ObjectAnimator.ofFloat(mic, View.ALPHA, 1f, 0.45f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    /** Vuelve el botón del micrófono a su estado normal (dejó de escuchar). */
    fun ocultarEscuchando() {
        val mic = ivMicrofono ?: return
        animacionMic?.cancel()
        animacionMic = null
        mic.alpha = 1f
        (mic.background as? GradientDrawable)?.setColor(Color.parseColor(COLOR_MIC_INACTIVO))
    }

    fun ocultar(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG_OVERLAY)?.let { root.removeView(it) }
        animacionMic?.cancel()
        animacionMic = null
        tvTitulo = null
        tvExplicacion = null
        btnSiguiente = null
        ivMicrofono = null
    }

    private fun dp(activity: Activity, valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), activity.resources.displayMetrics).toInt()

    private fun construirVista(activity: Activity, paso: GuiaPaso, esUltimo: Boolean): View {
        val contenedorExterno = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).also { it.setMargins(dp(activity, 12), 0, dp(activity, 12), dp(activity, 16)) }
        }

        val tarjeta = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2234"))
                cornerRadius = dp(activity, 18).toFloat()
            }
            elevation = dp(activity, 8).toFloat()
        }

        val filaTitulo = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28))
            setImageResource(R.drawable.zoe)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(Color.parseColor("#2B354A")); shape = GradientDrawable.OVAL }
            clipToOutline = true
        }

        val titulo = TextView(activity).apply {
            text = "Zoe · ${paso.tituloPantalla}"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(activity, 10), 0, 0, 0)
        }
        tvTitulo = titulo

        filaTitulo.addView(avatar)
        filaTitulo.addView(titulo)

        val explicacion = TextView(activity).apply {
            text = paso.explicacion
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 13.5f
            setPadding(0, dp(activity, 10), 0, dp(activity, 14))
        }
        tvExplicacion = explicacion

        val filaBotones = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val microfono = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40))
            setImageResource(R.drawable.ic_mic)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(activity, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_MIC_INACTIVO))
                shape = GradientDrawable.OVAL
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Activar micrófono para darle una orden a Zoe"
            setOnClickListener {
                if (activity is AppCompatActivity) ZoeOnboardingManager.activarMicrofono(activity)
            }
        }
        ivMicrofono = microfono

        val espacioFlexible = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        val btnDetener = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Detener"
            setTextColor(Color.WHITE)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#4B5568"))
            setOnClickListener {
                if (activity is AppCompatActivity) ZoeOnboardingManager.detener(activity)
            }
        }

        val siguiente = MaterialButton(activity).apply {
            text = if (esUltimo) "Terminar" else "Siguiente"
            setBackgroundColor(Color.parseColor("#EA580C"))
            setTextColor(Color.WHITE)
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(activity, 8)
            setOnClickListener {
                if (activity is AppCompatActivity) ZoeOnboardingManager.siguiente(activity)
            }
        }
        btnSiguiente = siguiente

        val paramsBtnSiguiente = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.marginStart = dp(activity, 8) }

        filaBotones.addView(microfono)
        filaBotones.addView(espacioFlexible)
        filaBotones.addView(btnDetener)
        filaBotones.addView(siguiente, paramsBtnSiguiente)

        tarjeta.addView(filaTitulo)
        tarjeta.addView(explicacion)
        tarjeta.addView(filaBotones)

        contenedorExterno.addView(tarjeta)
        return contenedorExterno
    }
}