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

    /**
     * 🎙️ Escucha continua "manos libres", igual que `keepListeningActive` en la web: un solo
     * toque en el micrófono la activa, y desde ahí el ciclo es solo: escuchar → procesar →
     * Zoe responde por voz → en cuanto termina de hablar, vuelve a escuchar sola, sin que el
     * usuario tenga que tocar el botón cada vez. Un segundo toque (o decir "detente") la apaga.
     */
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


        val bienvenida = "¡Hola!  Soy **Zoe**, tu asistente inteligente en **Dilo Móvil**. Tengo acceso a la información en tiempo real de **$negocioNombre**. Pregúntame lo que sea sobre la app, dime \"crea un producto\"  y te llevo directo al formulario, o \"cambia mi contraseña\" para ir a tu perfil. También puedo guiarte por toda la app: toca la brújula de arriba o dime \"guíame\"."


        voz.inicializar(
            onListo = { if (vozActivada) voz.hablar(bienvenida) },
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


        agregarMensajeUi("assistant", bienvenida)

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
                if (vozActivada) "Zoe leerá sus respuestas en voz alta" else "Zoe ya no leerá sus respuestas",
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

    /**
     * Procesa lo que dijo o escribió el usuario. Puede ser, en este orden de prioridad:
     * 1) Una orden de parar la voz.
     * 2) Un pedido de guía de bienvenida.
     * 3) Un pedido de CREAR algo (producto, cliente, factura, etc.) → la lleva directo al
     *    diálogo de creación correspondiente.
     * 4) Un pedido de CAMBIAR algo de perfil, equipo o configuración → la lleva a esa pantalla.
     * 5) Cualquier otra pregunta → se la manda a la IA con el manual completo de la app.
     */
    private fun procesarMensajeUsuario(texto: String) {
        val creacion = ZoeActionRouter.detectarCreacion(texto)
        val cambio = ZoeActionRouter.detectarCambio(texto)
        when {
            ZoeSpeechHelper.esComandoDeParada(texto) -> {
                escuchaContinuaActiva = false
                actualizarIconoMic()
                voz.detenerHabla()
                agregarMensajeUi("assistant", "Listo, me detengo. 🙂")
            }
            esPedidoDeGuia(texto) -> iniciarGuiaDeBienvenida()
            creacion != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, creacion.second)) {
                    irADialogoDeCreacion(creacion.first, creacion.second, creacion.third)
                } else {
                    agregarMensajeUi(
                        "assistant",
                        "Esa función no está disponible para tu rol (**$rolUsuario**). Consulta con el propietario del negocio si la necesitas."
                    )
                }
            }
            cambio != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, cambio.second)) {
                    irAPantallaDeCambio(cambio.first, cambio.second)
                } else {
                    agregarMensajeUi(
                        "assistant",
                        "Ese cambio no está disponible para tu rol (**$rolUsuario**). Consulta con el propietario del negocio si lo necesitas."
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
        agregarMensajeUi("assistant", "¡Vamos! Te voy guiando pantalla por pantalla. Di \"detente\" cuando quieras que pare.")
        val activityActual = activity as? AppCompatActivity
        if (activityActual != null) {
            ZoeOnboardingManager.iniciar(activityActual, rolUsuario)
            dismiss()
        }
    }

    private fun irADialogoDeCreacion(destino: Class<out Activity>, accion: String, nombreLegible: String) {
        agregarMensajeUi("assistant", "¡Vamos! Te llevo a la pantalla para crear $nombreLegible ahora mismo.")
        val activityActual = activity ?: return
        cerrarConPequenaDemora { ZoeActionRouter.navegar(activityActual, destino, accion) }
    }

    private fun irAPantallaDeCambio(destino: Class<out Activity>, accion: String) {
        agregarMensajeUi("assistant", "Claro, te llevo a esa pantalla para que hagas el cambio.")
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
        Toast.makeText(requireContext(), "Te escucho...", Toast.LENGTH_SHORT).show()
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
            btnMicChat.contentDescription = "Detener la escucha de Zoe"
        } else {
            btnMicChat.setIconResource(com.example.movildilo.R.drawable.ic_mic)
            btnMicChat.backgroundTintList = null
            btnMicChat.contentDescription = "Hablarle a Zoe"
        }
    }

    override fun onDestroyView() {
        escuchaContinuaActiva = false
        voz.liberar()
        super.onDestroyView()
    }

    private fun agregarMensajeUi(role: String, text: String) {
        adapter.agregarMensaje(ChatItem(role, text))
        if (listaChatUi.isNotEmpty()) {
            rvChatMensajes.smoothScrollToPosition(listaChatUi.size - 1)
        }
        if (role != "system") {
            listaHistorialDto.add(GroqMessage(role = role, content = text))
        }

        if (role == "assistant" && vozActivada) {
            voz.hablar(text) {
                if (escuchaContinuaActiva && isAdded) cicloEscuchaContinua()
            }
        } else if (role == "assistant" && escuchaContinuaActiva && isAdded) {

            cicloEscuchaContinua()
        }
    }

    private fun enviarPreguntaAGroq(preguntaUsuario: String) {
        agregarMensajeUi("user", preguntaUsuario)
        btnEnviarMensaje.isEnabled = false

        val manualDelSistema = ZoeKnowledgeBase.construirManualCompleto(
            usuarioNombre = usuarioNombre,
            negocioNombre = negocioNombre,
            rolUsuario = rolUsuario,
            contextoNegocioTexto = contextoNegocioTexto,
            alertasTexto = alertasTexto
        )

        val mensajesParaApi = mutableListOf<GroqMessage>()
        mensajesParaApi.add(GroqMessage(role = "system", content = manualDelSistema))
        mensajesParaApi.addAll(listaHistorialDto)

        val request = GroqRequest(
            model = "openai/gpt-oss-120b",
            messages = mensajesParaApi,
            temperature = 1,
            max_tokens = 1000
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = GroqApiClient.apiService.enviarMensajeChat("Bearer $groqApiKey", request)
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (!isAdded) return@withContext

                    if (response.isSuccessful) {
                        val respuestaBot = response.body()?.choices?.firstOrNull()?.message?.content
                            ?: "No dispongo de esa información en este momento. Te sugiero consultar en la **plataforma Web de Dilo**."
                        agregarMensajeUi("assistant", respuestaBot)
                    } else if (response.code() == 429) {

                        escuchaContinuaActiva = false
                        actualizarIconoMic()
                        agregarMensajeUi(
                            "assistant",
                            "Uy, me hiciste pensar demasiado rápido y me quedé sin aire. Espera unos segundos, por favor. 😅"
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Error en el servidor de IA: ${response.code()}",
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