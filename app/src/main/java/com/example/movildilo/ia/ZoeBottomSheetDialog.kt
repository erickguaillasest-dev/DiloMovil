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
import com.example.movildilo.R
import com.example.movildilo.data.api.GroqApiClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.ia.ChatItem
import com.example.movildilo.data.model.dto.ia.GroqMessage
import com.example.movildilo.data.model.dto.ia.GroqRequest
import com.example.movildilo.ui.adapters.ChatAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZoeBottomSheetDialog(
    private val usuarioNombre: String,
    private val negocioNombre: String,
    private val negocioId: String,
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
    private var datosCargados: Boolean = false

    private var contextoNegocioTexto: String = ""
    private var alertasTexto: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) iniciarEscuchaContinua()
        else Toast.makeText(requireContext(), "Se requiere permiso de micrófono.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
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
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_zoe_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inicializarVistas(view)
        configurarVoz()
        cargarContextoInicial()
        configurarListeners()
    }

    private fun inicializarVistas(view: View) {
        rvChatMensajes = view.findViewById(R.id.rvChatMensajes)
        etMensajeChat = view.findViewById(R.id.etMensajeChat)
        btnEnviarMensaje = view.findViewById(R.id.btnEnviarMensaje)
        btnCerrarChat = view.findViewById(R.id.btnCerrarChat)
        btnMicChat = view.findViewById(R.id.btnMicChat)
        btnToggleVozChat = view.findViewById(R.id.btnToggleVozChat)
        ivIconoVoz = view.findViewById(R.id.ivIconoVoz)
        tvEstadoVoz = view.findViewById(R.id.tvEstadoVoz)
        btnIniciarGuia = view.findViewById(R.id.btnIniciarGuia)

        adapter = ChatAdapter(listaChatUi)
        rvChatMensajes.layoutManager = LinearLayoutManager(requireContext())
        rvChatMensajes.adapter = adapter
    }

    private fun configurarVoz() {
        voz = ZoeSpeechHelper(requireContext())
        voz.inicializar(
            onListo = { ZoeSpeechHelper.detectarAcentoPedido("acento argentino")?.let { voz.activarAcento(it) } }
        )
        actualizarEstadoVoz()
    }

    private fun cargarContextoInicial() {
        bloquearInterfazCargando(true)

        if (listaChatUi.isEmpty()) {
            val bienvenida = "¡Hola! Soy **Zoe**. Cuentame, $usuarioNombre, ¿qué revisamos hoy?"
            agregarMensajeUi("assistant", bienvenida, null)
            agregarMensajeUi("assistant", "Sincronizando datos de tu negocio...", null)
        }

        val negocioIdLong = negocioId.toLongOrNull()
        val authHeader = SessionManager(requireContext()).getAuthHeader()

        if (negocioIdLong == null || authHeader == null) {
            bloquearInterfazCargando(false)
            agregarMensajeUi("assistant", "Hubo un error cargando los datos. Por favor, reintenta más tarde.", null)
            return
        }

        lifecycleScope.launch {
            try {
                val (contexto, alertas) = ZoeContextManager.obtenerContextoPorRol(negocioIdLong, authHeader, rolUsuario)
                contextoNegocioTexto = contexto
                alertasTexto = alertas
                datosCargados = true
                bloquearInterfazCargando(false)

                if (listaChatUi.size <= 2) {
                    val mensajeListo = "¡Listo, che! Datos sincronizados. Ya podés consultarme lo que quieras de tu negocio."
                    agregarMensajeUi("assistant", mensajeListo, mensajeListo)
                } else {
                    rvChatMensajes.scrollToPosition(listaChatUi.size - 1)
                }
            } catch (e: Exception) {
                bloquearInterfazCargando(false)
                agregarMensajeUi("assistant", "Hubo un error cargando los datos. Por favor, reintenta más tarde.", null)
            }
        }
    }

    private fun configurarListeners() {
        btnCerrarChat.setOnClickListener {
            escuchaContinuaActiva = false
            voz.liberar()
            dismiss()
        }

        btnEnviarMensaje.setOnClickListener {
            if (!datosCargados) return@setOnClickListener
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
            if (!datosCargados) return@setOnClickListener
            if (escuchaContinuaActiva) detenerEscuchaContinua()
            else {
                vozActivada = true
                actualizarEstadoVoz()
                voz.detenerHabla()
                pedirPermisoYEscuchar()
            }
        }

        btnIniciarGuia.setOnClickListener {
            if (datosCargados) iniciarGuiaDeBienvenida()
        }
    }

    private fun bloquearInterfazCargando(cargando: Boolean) {
        btnEnviarMensaje.isEnabled = !cargando
        btnMicChat.isEnabled = !cargando
        etMensajeChat.isEnabled = !cargando
        etMensajeChat.hint = if (cargando) "Cargando datos..." else "Escribe un mensaje..."
        btnEnviarMensaje.alpha = if (cargando) 0.5f else 1.0f
        btnMicChat.alpha = if (cargando) 0.5f else 1.0f
    }

    private fun actualizarEstadoVoz() {
        ivIconoVoz.setImageResource(if (vozActivada) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        tvEstadoVoz.text = if (vozActivada) "Voz: ON" else "Voz: OFF"
    }

    private fun procesarMensajeUsuario(texto: String) {
        if (!datosCargados) return
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

    private fun irAVista(destino: Class<out Activity>, nombreLegible: String) {
        agregarMensajeUi("assistant", "¡Claro! Te llevo a **$nombreLegible**.")
        cerrarConPequenaDemora { activity?.let { ZoeActionRouter.navegar(it, destino, null) } }
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
        if (!datosCargados) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) iniciarEscuchaContinua()
        else requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun iniciarEscuchaContinua() {
        if (!datosCargados) return
        escuchaContinuaActiva = true
        actualizarIconoMic()
        cicloEscuchaContinua()
    }

    private fun cicloEscuchaContinua() {
        if (!escuchaContinuaActiva || !isAdded || !datosCargados) return
        voz.escuchar(
            onResultado = {
                if (isAdded) {
                    etMensajeChat.setText("")
                    procesarMensajeUsuario(it)
                }
            },
            onError = { mensaje, codigoError ->
                if (codigoError == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    escuchaContinuaActiva = false
                    actualizarIconoMic()
                    if (isAdded) Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
                } else if (isAdded && escuchaContinuaActiva) {
                    rvChatMensajes.postDelayed({ cicloEscuchaContinua() }, 800)
                }
            },
            onEmpezoAEscuchar = {
                etMensajeChat.setText("")
                actualizarIconoMic()
            },
            onParcial = { texto ->
                if (isAdded) {
                    etMensajeChat.setText(texto)
                    etMensajeChat.setSelection(texto.length)
                }
            }
        )
    }

    private fun detenerEscuchaContinua() {
        escuchaContinuaActiva = false
        voz.detenerEscucha()
        actualizarIconoMic()
    }

    private fun actualizarIconoMic() {
        if (!::btnMicChat.isInitialized) return
        btnMicChat.setIconResource(R.drawable.ic_mic)
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

    private fun enviarPreguntaAGroq(preguntaUsuario: String) {
        if (!datosCargados) return
        agregarMensajeUi("user", preguntaUsuario)
        bloquearInterfazCargando(true)

        val pantallasNavegables = ZoeActionRouter.pantallasNavegablesParaRol(rolUsuario)
        val manualDelSistema = ZoeKnowledgeBase.construirManualCompleto(
            usuarioNombre, negocioNombre, rolUsuario, contextoNegocioTexto, alertasTexto, pantallasNavegables
        )

        val mensajesParaApi = mutableListOf(GroqMessage(role = "system", content = manualDelSistema))
        mensajesParaApi.addAll(listaHistorialDto.takeLast(12))

        lifecycleScope.launch(Dispatchers.IO) {
            ejecutarPeticionGroqConReintentos(mensajesParaApi, intento = 0)
        }
    }

    private suspend fun ejecutarPeticionGroqConReintentos(mensajes: List<GroqMessage>, intento: Int) {
        try {
            val response = GroqApiClient.apiService.enviarMensajeChat(
                "Bearer $groqApiKey",
                GroqRequest(model = "openai/gpt-oss-120b", messages = mensajes, temperature = 0.3, max_tokens = 900)
            )

            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    bloquearInterfazCargando(false)
                    if (!isAdded) return@withContext
                    val respuestaCruda = response.body()?.choices?.firstOrNull()?.message?.content ?: "Sin respuesta."
                    procesarRespuestaAsistente(respuestaCruda)
                }
                return
            }

            if (response.code() == 429 && intento < 3) {
                val retryAfterSeg = response.headers()["retry-after"]?.toLongOrNull()
                val esperaMs = retryAfterSeg?.times(1000) ?: (2000L * (1 shl intento))
                delay(esperaMs)
                ejecutarPeticionGroqConReintentos(mensajes, intento + 1)
                return
            }

            withContext(Dispatchers.Main) {
                bloquearInterfazCargando(false)
                if (!isAdded) return@withContext
                val msjError = when (response.code()) {
                    429 -> "Bancame un segundito. Me estás hablando muy rápido, volvé a hablarme en un ratito."
                    400, 413 -> "Uy corazón, veníamos hablando tanto que se me llenó la cabeza. ¿Podés repetirme más cortito?"
                    else -> "Perdoname, parece que hay un problemita con internet."
                }
                if (response.code() == 429) escuchaContinuaActiva = false
                agregarMensajeUi("assistant", msjError, msjError)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                bloquearInterfazCargando(false)
                if (!isAdded) return@withContext
                val msj = "Perdoname, parece que hay un problemita con internet."
                agregarMensajeUi("assistant", msj, msj)
            }
        }
    }

    private fun limpiarTextoParaVozFallback(texto: String): String {
        return texto
            .replace(Regex("[*#|>_~]"), "")
            .replace(Regex("id\\s*:\\s*\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[⚠✖•–—]"), "")
            .replace("$", " dólares ")
            .replace("%", " por ciento ")
            .replace(Regex("(\\d+)\\s*uds?", RegexOption.IGNORE_CASE), "$1 unidades")
            .replace(Regex("(\\d+)\\s*mín", RegexOption.IGNORE_CASE), "mínimo $1")
            .replace(Regex("^-\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\n+"), ". ")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\.\\s*\\."), ".")
            .replace(Regex("\\s+,"), ",")
            .trim()
    }

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
        var textoPantalla = texto.replace(Regex("<voz>[\\s\\S]*?</voz>", RegexOption.IGNORE_CASE), "").trim().ifBlank { texto }

        val textoVoz = if (vozMatch != null) {
            vozMatch.groupValues[1].trim()
        } else {
            textoPantalla = texto.replace(Regex("<voz>|</voz>", RegexOption.IGNORE_CASE), "").trim()
            limpiarTextoParaVozFallback(textoPantalla)
        }

        agregarMensajeUi("assistant", textoPantalla, textoVoz)

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