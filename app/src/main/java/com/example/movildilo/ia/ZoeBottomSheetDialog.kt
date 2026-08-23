package com.example.movildilo.ia

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.data.api.GroqApiClient
import com.example.movildilo.data.model.dto.ChatItem
import com.example.movildilo.data.model.dto.GroqMessage
import com.example.movildilo.data.model.dto.GroqRequest
import com.example.movildilo.ui.adapters.ChatAdapter
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZoeBottomSheetDialog(
    private val usuarioNombre: String,
    private val negocioNombre: String,
    private val contextoNegocioTexto: String,
    private val alertasTexto: String,
    private val groqApiKey: String,
    private val rolUsuario: String = "PROPIETARIO"
) : BottomSheetDialogFragment() {

    private lateinit var rvChatMensajes: RecyclerView
    private lateinit var etMensajeChat: EditText
    private lateinit var btnEnviarMensaje: MaterialButton
    private lateinit var btnCerrarChat: MaterialButton
    private lateinit var btnMicChat: MaterialButton
    private lateinit var btnToggleVozChat: View
    private lateinit var ivIconoVoz: ImageView
    private lateinit var tvEstadoVoz: TextView
    private lateinit var btnIniciarGuia: View

    private val listaHistorialDto = mutableListOf<GroqMessage>()
    private val listaChatUi = mutableListOf<ChatItem>()
    private lateinit var adapter: ChatAdapter

    private lateinit var voz: ZoeSpeechHelper
    private var vozActivada: Boolean = true
    private var escuchaContinuaActiva: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            iniciarEscuchaContinua()
        } else {
            Toast.makeText(requireContext(), "Se requiere permiso de micrófono para hablarle a Zoe.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<View>(R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(com.example.movildilo.R.layout.bottom_sheet_zoe_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChatMensajes = view.findViewById(com.example.movildilo.R.id.rvChatMensajes)
        etMensajeChat = view.findViewById(com.example.movildilo.R.id.etMensajeChat)
        btnEnviarMensaje = view.findViewById(com.example.movildilo.R.id.btnEnviarMensaje)
        btnCerrarChat = view.findViewById(com.example.movildilo.R.id.btnCerrarChat)
        btnMicChat = view.findViewById(com.example.movildilo.R.id.btnMicChat)
        btnToggleVozChat = view.findViewById(com.example.movildilo.R.id.btnToggleVozChat)
        ivIconoVoz = view.findViewById(com.example.movildilo.R.id.ivIconoVoz)
        tvEstadoVoz = view.findViewById(com.example.movildilo.R.id.tvEstadoVoz)
        btnIniciarGuia = view.findViewById(com.example.movildilo.R.id.btnIniciarGuia)

        adapter = ChatAdapter(listaChatUi)
        rvChatMensajes.layoutManager = LinearLayoutManager(requireContext())
        rvChatMensajes.adapter = adapter

        voz = ZoeSpeechHelper(requireContext())

        val bienvenida = "¡Hola! Soy **Zoe**, tu asistente en **$negocioNombre**.\n\n" +
                "¿En qué te puedo ayudar hoy?\n" +
                "* **Consultar datos:** Ventas, inventario o alertas.\n" +
                "* **Acciones rápidas:** Di *\"crea un producto\"* o *\"cambia mi contraseña\"*.\n" +
                "* **Guía interactiva:** Di *\"guíame\"* para dar un recorrido por la app."

        voz.inicializar(
            onListo = {
                ZoeSpeechHelper.detectarAcentoPedido("acento argentino")?.let { acento ->
                    voz.activarAcento(acento)
                }
                if (vozActivada) voz.hablar("¡Hola! Soy Zoe, tu asistente en $negocioNombre. ¿En qué te puedo ayudar hoy?")
            },
            onFallo = { mensaje -> Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show() }
        )

        actualizarEstadoVoz()

        btnCerrarChat.setOnClickListener {
            escuchaContinuaActiva = false
            voz.detenerHabla()
            voz.detenerEscuchaDeComandos()
            voz.detenerEscucha()
            dismiss()
        }

        agregarMensajeUi("assistant", bienvenida, "¡Hola! Soy Zoe, tu asistente en $negocioNombre. ¿En qué te puedo ayudar hoy?")

        btnEnviarMensaje.setOnClickListener {
            val texto = etMensajeChat.text.toString().trim()
            if (texto.isNotEmpty()) {
                etMensajeChat.setText("")
                procesarMensajeUsuario(texto)
            }
        }

        btnToggleVozChat.setOnClickListener {
            vozActivada = !vozActivada
            if (!vozActivada) voz.detenerHabla()
            actualizarEstadoVoz()
            Toast.makeText(
                requireContext(),
                if (vozActivada) "Voz activada" else "Voz desactivada",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnMicChat.setOnClickListener {
            if (escuchaContinuaActiva) {
                detenerEscuchaContinua()
            } else {
                pedirPermisoYEscuchar()
            }
        }

        btnIniciarGuia.setOnClickListener {
            iniciarGuiaDeBienvenida()
        }
    }

    private fun actualizarEstadoVoz() {
        ivIconoVoz.setImageResource(
            if (vozActivada) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
        tvEstadoVoz.text = if (vozActivada) "Voz: ON" else "Voz: OFF"
    }

    private fun procesarMensajeUsuario(texto: String) {
        val creacion = ZoeActionRouter.detectarCreacion(texto)
        val cambio = ZoeActionRouter.detectarCambio(texto)
        val acentoPedido = ZoeSpeechHelper.detectarAcentoPedido(texto)
        when {
            ZoeSpeechHelper.esComandoDeParada(texto) -> {
                escuchaContinuaActiva = false
                actualizarIconoMic()
                voz.detenerHabla()
                agregarMensajeUi("assistant", "Listo, me detengo.")
            }
            acentoPedido != null -> {
                val respuestaAcento = voz.activarAcento(acentoPedido)
                agregarMensajeUi("assistant", respuestaAcento)
            }
            esPedidoDeGuia(texto) -> iniciarGuiaDeBienvenida()
            creacion != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, creacion.second)) {
                    irADialogoDeCreacion(creacion.first, creacion.second, creacion.third)
                } else {
                    agregarMensajeUi(
                        "assistant",
                        "Esa función no está disponible para tu rol (**$rolUsuario**)."
                    )
                }
            }
            cambio != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, cambio.second)) {
                    irAPantallaDeCambio(cambio.first, cambio.second)
                } else {
                    agregarMensajeUi(
                        "assistant",
                        "Ese cambio no está disponible para tu rol (**$rolUsuario**)."
                    )
                }
            }
            else -> enviarPreguntaAGroq(texto)
        }
    }

    private fun esPedidoDeGuia(texto: String): Boolean {
        val t = texto.lowercase()
        return listOf("guíame", "guiame", "guíame por la app", "hazme un tour", "dame un tour",
            "cómo funciona la app", "como funciona la app", "ayúdame a empezar", "ayudame a empezar",
            "muéstrame la app", "muestrame la app", "soy nuevo", "soy nueva", "explícame la app", "explicame la app"
        ).any { t.contains(it) }
    }

    private fun iniciarGuiaDeBienvenida() {
        escuchaContinuaActiva = false
        agregarMensajeUi("assistant", "¡Perfecto! Te voy guiando pantalla por pantalla.")
        val activityActual = activity as? AppCompatActivity
        if (activityActual != null) {
            ZoeOnboardingManager.iniciar(activityActual, rolUsuario)
            dismiss()
        }
    }

    private fun irADialogoDeCreacion(destino: Class<out Activity>, accion: String, nombreLegible: String) {
        agregarMensajeUi("assistant", "Te llevo al formulario de **$nombreLegible**.")
        val activityActual = activity ?: return
        cerrarConPequenaDemora { ZoeActionRouter.navegar(activityActual, destino, accion) }
    }

    private fun irAPantallaDeCambio(destino: Class<out Activity>, accion: String) {
        agregarMensajeUi("assistant", "Abriendo la pantalla de configuración.")
        val activityActual = activity ?: return
        cerrarConPequenaDemora { ZoeActionRouter.navegar(activityActual, destino, accion) }
    }

    private fun cerrarConPequenaDemora(accion: () -> Unit) {
        escuchaContinuaActiva = false
        voz.detenerEscucha()
        rvChatMensajes.postDelayed({
            if (isAdded) {
                accion()
                dismiss()
            } else {
                accion()
            }
        }, 500)
    }

    private fun pedirPermisoYEscuchar() {
        val permisoConcedido = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (permisoConcedido) {
            iniciarEscuchaContinua()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarEscuchaContinua() {
        escuchaContinuaActiva = true
        actualizarIconoMic()
        Toast.makeText(requireContext(), "Escuchando...", Toast.LENGTH_SHORT).show()
        cicloEscuchaContinua()
    }

    private fun cicloEscuchaContinua() {
        if (!escuchaContinuaActiva || !isAdded) return
        voz.escuchar(
            onResultado = { texto ->
                if (isAdded) procesarMensajeUsuario(texto)
            },
            onError = { mensaje ->
                if (!isAdded) return@escuchar
                if (escuchaContinuaActiva) {
                    cicloEscuchaContinua()
                } else {
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
                }
            },
            onEmpezoAEscuchar = { actualizarIconoMic() }
        )
    }

    private fun detenerEscuchaContinua() {
        escuchaContinuaActiva = false
        voz.detenerEscucha()
        actualizarIconoMic()
    }

    private fun actualizarIconoMic() {
        if (!::btnMicChat.isInitialized) return
        if (escuchaContinuaActiva) {
            btnMicChat.setIconResource(com.example.movildilo.R.drawable.ic_mic)
            btnMicChat.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA580C"))
            btnMicChat.contentDescription = "Detener micrófono"
        } else {
            btnMicChat.setIconResource(com.example.movildilo.R.drawable.ic_mic)
            btnMicChat.backgroundTintList = null
            btnMicChat.contentDescription = "Activar micrófono"
        }
    }

    override fun onDestroyView() {
        escuchaContinuaActiva = false
        voz.liberar()
        super.onDestroyView()
    }

    private fun agregarMensajeUi(role: String, text: String, textoVoz: String? = null) {
        adapter.agregarMensaje(ChatItem(role, text))
        if (listaChatUi.isNotEmpty()) {
            rvChatMensajes.smoothScrollToPosition(listaChatUi.size - 1)
        }
        if (role != "system") {
            listaHistorialDto.add(GroqMessage(role = role, content = text))
        }

        if (role == "assistant" && vozActivada) {
            voz.hablar(textoVoz ?: text) {
                if (escuchaContinuaActiva && isAdded) cicloEscuchaContinua()
            }
        } else if (role == "assistant" && escuchaContinuaActiva && isAdded) {
            cicloEscuchaContinua()
        }
    }

    private fun enviarPreguntaAGroq(preguntaUsuario: String) {
        agregarMensajeUi("user", preguntaUsuario)
        btnEnviarMensaje.isEnabled = false

        val instruccionEstructura = "\n\nREGLAS DE FORMATO OBLIGATORIAS:\n" +
                "1. Ve directo a la respuesta puntual, sin saludos ni introducciones tipo 'claro, con gusto...'.\n" +
                "2. Organiza la respuesta visual usando Markdown estructurado (negritas, viñetas '*' y listas) SOLO cuando aporte claridad, no por defecto.\n" +
                "3. Sé ultra conciso: cero relleno, cero repetición de lo ya dicho antes en el chat.\n" +
                "4. Incluye AL FINAL la etiqueta <voz>texto fluido y natural para hablar sin símbolos ni markdown</voz>."

        val manualDelSistema = ZoeKnowledgeBase.construirManualCompleto(
            usuarioNombre = usuarioNombre,
            negocioNombre = negocioNombre,
            rolUsuario = rolUsuario,
            contextoNegocioTexto = contextoNegocioTexto,
            alertasTexto = alertasTexto
        ) + instruccionEstructura

        val mensajesParaApi = mutableListOf<GroqMessage>()
        mensajesParaApi.add(GroqMessage(role = "system", content = manualDelSistema))
        mensajesParaApi.addAll(listaHistorialDto.takeLast(12))

        val request = GroqRequest(
            model = "openai/gpt-oss-120b",
            messages = mensajesParaApi,
            temperature = 1,
            max_tokens = 400
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = GroqApiClient.apiService.enviarMensajeChat("Bearer $groqApiKey", request)
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (!isAdded) return@withContext

                    if (response.isSuccessful) {
                        val respuestaCruda = response.body()?.choices?.firstOrNull()?.message?.content
                            ?: "No dispongo de esa información. Te sugiero consultar en la plataforma web."

                        val vozMatch = Regex("<voz>([\\s\\S]*?)</voz>", RegexOption.IGNORE_CASE).find(respuestaCruda)
                        val textoVoz = vozMatch?.groupValues?.get(1)?.trim()
                        val textoPantalla = respuestaCruda.replace(Regex("<voz>[\\s\\S]*?</voz>", RegexOption.IGNORE_CASE), "").trim()

                        agregarMensajeUi("assistant", textoPantalla.ifBlank { respuestaCruda }, textoVoz)
                    } else if (response.code() == 429) {
                        escuchaContinuaActiva = false
                        actualizarIconoMic()
                        agregarMensajeUi(
                            "assistant",
                            "Por favor espera unos segundos antes de realizar otra consulta."
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Error del servidor: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (escuchaContinuaActiva) cicloEscuchaContinua()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (!isAdded) return@withContext
                    Toast.makeText(
                        requireContext(),
                        "Fallo de conexión: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (escuchaContinuaActiva) cicloEscuchaContinua()
                }
            }
        }
    }
}