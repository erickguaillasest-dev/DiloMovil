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
import com.example.movildilo.data.model.dto.ia.ChatItem
import com.example.movildilo.data.model.dto.ia.GroqMessage
import com.example.movildilo.data.model.dto.ia.GroqRequest
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

    private val listaHistorialDto = ZoeSession.historialDto
    private val listaChatUi = ZoeSession.historialUi
    private lateinit var adapter: ChatAdapter

    private lateinit var voz: ZoeSpeechHelper
    private var vozActivada: Boolean = false
    private var escuchaContinuaActiva: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) iniciarEscuchaContinua()
        else Toast.makeText(requireContext(), "Se requiere permiso de micrófono.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(R.id.design_bottom_sheet)
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
        dialog?.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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

        voz.inicializar(
            onListo = { ZoeSpeechHelper.detectarAcentoPedido("acento argentino")?.let { voz.activarAcento(it) } }
        )
        actualizarEstadoVoz()

        if (listaChatUi.isEmpty()) {
            val bienvenida = "¡Hola! Soy **Zoe**, tu asistente en **$negocioNombre**.\n\n" +
                    "¿En qué te puedo ayudar hoy?\n" +
                    "* **Consultar datos:** Ventas, inventario o alertas.\n" +
                    "* **Acciones rápidas:** Di *\"crea un producto\"*.\n" +
                    "* **Navegación:** Di *\"llévame a mis bodegas\"* o *\"llévame a productos\"*.\n" +
                    "* **Guía interactiva:** Di *\"guíame\"*."
            agregarMensajeUi("assistant", bienvenida, null)
        } else {
            rvChatMensajes.scrollToPosition(listaChatUi.size - 1)
        }

        btnCerrarChat.setOnClickListener {
            escuchaContinuaActiva = false
            voz.detenerHabla()
            voz.detenerEscuchaDeComandos()
            voz.detenerEscucha()
            dismiss()
        }

        btnEnviarMensaje.setOnClickListener {
            val texto = etMensajeChat.text.toString().trim()
            if (texto.isNotEmpty()) {
                etMensajeChat.setText("")
                voz.detenerEscucha()
                procesarMensajeUsuario(texto)
            }
        }

        btnToggleVozChat.setOnClickListener {
            vozActivada = !vozActivada
            if (!vozActivada) voz.detenerHabla()
            actualizarEstadoVoz()
        }

        btnMicChat.setOnClickListener {
            if (escuchaContinuaActiva) detenerEscuchaContinua()
            else {
                vozActivada = true
                actualizarEstadoVoz()
                voz.detenerHabla()
                pedirPermisoYEscuchar()
            }
        }

        btnIniciarGuia.setOnClickListener { iniciarGuiaDeBienvenida() }
    }

    private fun actualizarEstadoVoz() {
        ivIconoVoz.setImageResource(if (vozActivada) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        tvEstadoVoz.text = if (vozActivada) "Voz: ON" else "Voz: OFF"
    }

    private fun procesarMensajeUsuario(texto: String) {
        val creacion = ZoeActionRouter.detectarCreacion(texto)
        val cambio = ZoeActionRouter.detectarCambio(texto)
        val navegacion = ZoeActionRouter.detectarNavegacion(texto)
        val acentoPedido = ZoeSpeechHelper.detectarAcentoPedido(texto)

        when {
            ZoeSpeechHelper.esComandoDeParada(texto) -> {
                escuchaContinuaActiva = false
                actualizarIconoMic()
                voz.detenerHabla()
                agregarMensajeUi("assistant", "Listo, me detengo.")
            }
            acentoPedido != null -> agregarMensajeUi("assistant", voz.activarAcento(acentoPedido))
            esPedidoDeGuia(texto) -> iniciarGuiaDeBienvenida()
            creacion != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, creacion.second)) irADialogoDeCreacion(creacion.first, creacion.second, creacion.third)
                else agregarMensajeUi("assistant", "Esa función no está disponible para tu rol (**$rolUsuario**).")
            }
            cambio != null -> {
                if (ZoeActionRouter.permitidaParaRol(rolUsuario, cambio.second)) irAPantallaDeCambio(cambio.first, cambio.second)
                else agregarMensajeUi("assistant", "Ese cambio no está disponible para tu rol (**$rolUsuario**).")
            }
            navegacion != null -> {
                if (ZoeActionRouter.pantallaPermitidaParaRol(rolUsuario, navegacion.first)) irAVista(navegacion.first, navegacion.second)
                else agregarMensajeUi("assistant", "La pantalla de **${navegacion.second}** no está habilitada para tu rol (**$rolUsuario**).")
            }
            else -> enviarPreguntaAGroq(texto)
        }
    }

    private fun esPedidoDeGuia(texto: String): Boolean = listOf("guíame", "hazme un tour", "dame un tour", "cómo funciona", "explícame").any { texto.lowercase().contains(it) }

    private fun iniciarGuiaDeBienvenida() {
        escuchaContinuaActiva = false
        agregarMensajeUi("assistant", "¡Perfecto! Te voy guiando pantalla por pantalla.")
        (activity as? AppCompatActivity)?.let { ZoeOnboardingManager.iniciar(it, rolUsuario); dismiss() }
    }

    private fun irADialogoDeCreacion(destino: Class<out Activity>, accion: String, nombreLegible: String) {
        agregarMensajeUi("assistant", "Te llevo al formulario de **$nombreLegible**.")
        cerrarConPequenaDemora { activity?.let { ZoeActionRouter.navegar(it, destino, accion) } }
    }

    private fun irAPantallaDeCambio(destino: Class<out Activity>, accion: String) {
        agregarMensajeUi("assistant", "Abriendo la configuración.")
        cerrarConPequenaDemora { activity?.let { ZoeActionRouter.navegar(it, destino, accion) } }
    }

    // Navega a la vista solicitada y pasa el parámetro para auto-abrir a Zoe allí
    private fun irAVista(destino: Class<out Activity>, nombreLegible: String) {
        agregarMensajeUi("assistant", "¡Claro! Te llevo a **$nombreLegible**.")
        cerrarConPequenaDemora {
            activity?.let {
                ZoeActionRouter.navegar(it, destino, null)
            }
        }
    }

    private fun cerrarConPequenaDemora(accion: () -> Unit) {
        escuchaContinuaActiva = false
        voz.detenerEscucha()
        rvChatMensajes.postDelayed({
            accion()
            if (isAdded) dismiss()
        }, 500)
    }

    private fun pedirPermisoYEscuchar() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) iniciarEscuchaContinua()
        else requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun iniciarEscuchaContinua() {
        escuchaContinuaActiva = true
        actualizarIconoMic()
        cicloEscuchaContinua()
    }

    private fun cicloEscuchaContinua() {
        if (!escuchaContinuaActiva || !isAdded) return
        voz.escuchar(
            onResultado = { if (isAdded) procesarMensajeUsuario(it) },
            onError = { if (isAdded && escuchaContinuaActiva) rvChatMensajes.postDelayed({ cicloEscuchaContinua() }, 800) },
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
        btnMicChat.setIconResource(com.example.movildilo.R.drawable.ic_mic)
        btnMicChat.backgroundTintList = if (escuchaContinuaActiva) ColorStateList.valueOf(Color.parseColor("#EA580C")) else null
    }

    override fun onDestroyView() {
        escuchaContinuaActiva = false
        voz.liberar()
        super.onDestroyView()
    }

    private fun agregarMensajeUi(role: String, text: String, textoVoz: String? = null) {
        adapter.agregarMensaje(ChatItem(role, text))
        rvChatMensajes.smoothScrollToPosition(listaChatUi.size - 1)
        if (role != "system") listaHistorialDto.add(GroqMessage(role = role, content = text))

        if (role == "assistant" && vozActivada && textoVoz != null) {
            voz.hablar(textoVoz) { if (escuchaContinuaActiva && isAdded) cicloEscuchaContinua() }
        } else if (role == "assistant" && escuchaContinuaActiva && isAdded) {
            cicloEscuchaContinua()
        }
    }

    private val MAX_REINTENTOS_429 = 3

    private fun enviarPreguntaAGroq(preguntaUsuario: String) {
        agregarMensajeUi("user", preguntaUsuario)
        btnEnviarMensaje.isEnabled = false

        val pantallasNavegables = ZoeActionRouter.pantallasNavegablesParaRol(rolUsuario)
        val manualDelSistema = ZoeKnowledgeBase.construirManualCompleto(usuarioNombre, negocioNombre, rolUsuario, contextoNegocioTexto, alertasTexto, pantallasNavegables) +
                "\n\nREGLAS: Ve directo al punto, usa Markdown solo si es necesario, cero relleno. Incluye AL FINAL <voz>texto fluido</voz>."

        val mensajesParaApi = mutableListOf(GroqMessage(role = "system", content = manualDelSistema))
        mensajesParaApi.addAll(listaHistorialDto.takeLast(12))

        lifecycleScope.launch(Dispatchers.IO) {
            ejecutarPeticionGroqConReintentos(mensajesParaApi, intento = 0)
        }
    }

    /**
     * Igual que la versión web (ejecutarPeticionGroq): ante un 429 reintenta en silencio
     * con backoff exponencial (o respetando el header Retry-After) antes de rendirse,
     * y solo entonces le muestra un mensaje de error al usuario.
     */
    private suspend fun ejecutarPeticionGroqConReintentos(mensajes: List<GroqMessage>, intento: Int) {
        try {
            val response = GroqApiClient.apiService.enviarMensajeChat(
                "Bearer $groqApiKey",
                GroqRequest(model = "openai/gpt-oss-120b", messages = mensajes, temperature = 1, max_tokens = 700)
            )

            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (!isAdded) return@withContext
                    val respuestaCruda = response.body()?.choices?.firstOrNull()?.message?.content ?: "Sin respuesta."
                    procesarRespuestaAsistente(respuestaCruda)
                }
                return
            }

            if (response.code() == 429 && intento < MAX_REINTENTOS_429) {
                val retryAfterSeg = response.headers()["retry-after"]?.toLongOrNull()
                val esperaMs = retryAfterSeg?.times(1000) ?: (2000L * (1 shl intento))
                kotlinx.coroutines.delay(esperaMs)
                ejecutarPeticionGroqConReintentos(mensajes, intento + 1)
                return
            }

            withContext(Dispatchers.Main) {
                btnEnviarMensaje.isEnabled = true
                if (!isAdded) return@withContext
                when (response.code()) {
                    429 -> {
                        escuchaContinuaActiva = false
                        agregarMensajeUi("assistant", "Espera unos segundos, me estás hablando muy rápido.", "Espera unos segundos, me estás hablando muy rápido.")
                    }
                    400, 413 -> agregarMensajeUi("assistant", "Veníamos hablando tanto que se me llenó la cabeza. ¿Puedes repetirlo más corto?", "Veníamos hablando tanto que se me llenó la cabeza. ¿Puedes repetirlo más corto?")
                    else -> agregarMensajeUi("assistant", "Tuve un problema para responder. Intenta de nuevo en un momento.", "Tuve un problema para responder. Intenta de nuevo en un momento.")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                btnEnviarMensaje.isEnabled = true
                if (escuchaContinuaActiva && isAdded) rvChatMensajes.postDelayed({ cicloEscuchaContinua() }, 1000)
            }
        }
    }

    /**
     * Extrae <voz>, limpia la respuesta y — igual que la web con [[NAVEGAR:/ruta]] — detecta
     * si el modelo pidió navegar con [[NAVEGAR:id]] para llevarlo a esa pantalla.
     */
    private fun procesarRespuestaAsistente(respuestaCruda: String) {
        var texto = respuestaCruda

        val navMatch = Regex("\\[\\[NAVEGAR:\\s*([a-zA-Z0-9_]+)\\s*\\]\\]", RegexOption.IGNORE_CASE).find(texto)
        var navegacionSolicitada: Pair<Class<out Activity>, String>? = null
        if (navMatch != null) {
            val id = navMatch.groupValues[1]
            texto = texto.replace(navMatch.value, "").trim()
            val destino = ZoeActionRouter.resolverNavegacionPorId(id)
            if (destino != null && ZoeActionRouter.pantallaPermitidaParaRol(rolUsuario, destino.first)) {
                navegacionSolicitada = destino
            }
        }

        val vozMatch = Regex("<voz>([\\s\\S]*?)</voz>", RegexOption.IGNORE_CASE).find(texto)
        val textoPantalla = texto.replace(Regex("<voz>[\\s\\S]*?</voz>", RegexOption.IGNORE_CASE), "").trim().ifBlank { texto }

        agregarMensajeUi("assistant", textoPantalla, vozMatch?.groupValues?.get(1)?.trim())

        if (navegacionSolicitada != null) {
            val (destino, _) = navegacionSolicitada
            escuchaContinuaActiva = false
            voz.detenerEscucha()
            rvChatMensajes.postDelayed({
                activity?.let { ZoeActionRouter.navegar(it, destino, null) }
                if (isAdded) dismiss()
            }, 900)
        }
    }
}