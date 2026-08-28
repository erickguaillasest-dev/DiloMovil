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

object ZoeGuideOverlay {


    private const val TAG_OVERLAY = "zoe_guide_overlay"
    // Colores renovados, el microfono ahora usa el color naranja principal al escuchar
    private const val COLOR_MIC_INACTIVO = "#F1F5F9"
    private const val COLOR_MIC_ESCUCHANDO = "#EA580C"

    private var tvTitulo: TextView? = null
    private var tvExplicacion: TextView? = null
    private var btnSiguiente: MaterialButton? = null
    private var ivMicrofono: ImageView? = null
    private var animacionMic: ObjectAnimator? = null

    private var contenidoPlegable: LinearLayout? = null
    private var btnPlegar: ImageView? = null
    private var estaPlegado = false

    fun mostrar(activity: Activity, paso: GuiaPaso, esUltimo: Boolean) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG_OVERLAY)?.let { root.removeView(it) }

        estaPlegado = false
        val vista = construirVista(activity, paso, esUltimo)
        vista.tag = TAG_OVERLAY
        root.addView(vista)
    }

    fun actualizarTexto(paso: GuiaPaso, esUltimo: Boolean) {
        tvTitulo?.text = paso.tituloPantalla
        tvExplicacion?.text = paso.explicacion
        btnSiguiente?.text = if (esUltimo) "Terminar" else "Siguiente"
    }

    fun mostrarEscuchando() {
        val mic = ivMicrofono ?: return
        (mic.background as? GradientDrawable)?.setColor(Color.parseColor(COLOR_MIC_ESCUCHANDO))
        mic.setColorFilter(Color.WHITE) // El icono del mic se pone blanco sobre fondo naranja
        animacionMic?.cancel()
        animacionMic = ObjectAnimator.ofFloat(mic, View.ALPHA, 1f, 0.45f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun ocultarEscuchando() {
        val mic = ivMicrofono ?: return
        animacionMic?.cancel()
        animacionMic = null
        mic.alpha = 1f
        (mic.background as? GradientDrawable)?.setColor(Color.parseColor(COLOR_MIC_INACTIVO))
        mic.setColorFilter(Color.parseColor("#475569")) // Gris para el icono inactivo
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
        contenidoPlegable = null
        btnPlegar = null
    }

    private fun dp(activity: Activity, valor: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor.toFloat(), activity.resources.displayMetrics).toInt()

    private fun construirVista(activity: Activity, paso: GuiaPaso, esUltimo: Boolean): View {
        val anchoMaximo = minOf(dp(activity, 300), (activity.resources.displayMetrics.widthPixels * 0.88f).toInt())

        val contenedorExterno = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).also { it.setMargins(dp(activity, 16), 0, dp(activity, 16), dp(activity, 20)) }
        }

        // Diseño mucho más hermoso y elegante
        val tarjeta = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18))
            layoutParams = FrameLayout.LayoutParams(anchoMaximo, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(activity, 24).toFloat()
                // Borde ligero gris claro para definir
                setStroke(dp(activity, 1), Color.parseColor("#E2E8F0"))
            }
            elevation = dp(activity, 12).toFloat()
        }

        val filaTitulo = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32))
            setImageResource(R.drawable.zoe) // Asume que tienes un drawable zoe
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(Color.parseColor("#F8FAFC")); shape = GradientDrawable.OVAL }
            clipToOutline = true
        }

        val titulo = TextView(activity).apply {
            text = "Zoe · ${paso.tituloPantalla}"
            setTextColor(Color.parseColor("#0F172A")) // Letra oscura
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(activity, 10), 0, 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvTitulo = titulo

        val plegar = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)).also {
                it.marginStart = dp(activity, 6)
            }
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(Color.parseColor("#94A3B8"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            contentDescription = "Minimizar la guía de Zoe"
        }
        btnPlegar = plegar

        filaTitulo.addView(avatar)
        filaTitulo.addView(titulo)
        filaTitulo.addView(plegar)

        val explicacion = TextView(activity).apply {
            text = paso.explicacion
            setTextColor(Color.parseColor("#475569")) // Gris oscuro legible
            textSize = 14f
            setPadding(0, dp(activity, 10), 0, dp(activity, 16))
        }
        tvExplicacion = explicacion

        val filaBotones = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val microfono = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44))
            setImageResource(R.drawable.ic_mic)
            setColorFilter(Color.parseColor("#475569")) // Inicial gris oscuro
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(activity, 10))
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
            text = "Cerrar"
            setTextColor(Color.parseColor("#64748B"))
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
            setOnClickListener {
                if (activity is AppCompatActivity) ZoeOnboardingManager.detener(activity)
            }
        }

        val siguiente = MaterialButton(activity).apply {
            text = if (esUltimo) "Terminar" else "Siguiente"
            setBackgroundColor(Color.parseColor("#EA580C")) // Naranja vibrante
            setTextColor(Color.WHITE)
            cornerRadius = dp(activity, 12)
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

        val plegable = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(explicacion)
            addView(filaBotones)
        }
        contenidoPlegable = plegable

        plegar.setOnClickListener {
            estaPlegado = !estaPlegado
            plegable.visibility = if (estaPlegado) View.GONE else View.VISIBLE
            plegar.setImageResource(
                if (estaPlegado) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float
            )
            plegar.contentDescription = if (estaPlegado) "Expandir la guía de Zoe" else "Minimizar la guía de Zoe"
        }

        tarjeta.addView(filaTitulo)
        tarjeta.addView(plegable)

        contenedorExterno.addView(tarjeta)
        return contenedorExterno
    }
}