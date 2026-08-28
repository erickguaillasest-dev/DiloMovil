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

    private fun enviarPreguntaAGroq(preguntaUsuario: String) {
        agregarMensajeUi("user", preguntaUsuario)
        btnEnviarMensaje.isEnabled = false

        val manualDelSistema = ZoeKnowledgeBase.construirManualCompleto(usuarioNombre, negocioNombre, rolUsuario, contextoNegocioTexto, alertasTexto) +
                "\n\nREGLAS: Ve directo al punto, usa Markdown solo si es necesario, cero relleno. Incluye AL FINAL <voz>texto fluido</voz>."

        val mensajesParaApi = mutableListOf(GroqMessage(role = "system", content = manualDelSistema))
        mensajesParaApi.addAll(listaHistorialDto.takeLast(12))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = GroqApiClient.apiService.enviarMensajeChat("Bearer $groqApiKey", GroqRequest(model = "openai/gpt-oss-120b", messages = mensajesParaApi, temperature = 1, max_tokens = 700))
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (!isAdded) return@withContext

                    if (response.isSuccessful) {
                        val respuestaCruda = response.body()?.choices?.firstOrNull()?.message?.content ?: "Sin respuesta."
                        val vozMatch = Regex("<voz>([\\s\\S]*?)</voz>", RegexOption.IGNORE_CASE).find(respuestaCruda)
                        agregarMensajeUi("assistant", respuestaCruda.replace(Regex("<voz>[\\s\\S]*?</voz>", RegexOption.IGNORE_CASE), "").trim().ifBlank { respuestaCruda }, vozMatch?.groupValues?.get(1)?.trim())
                    } else if (response.code() == 429) {
                        agregarMensajeUi("assistant", "Espera unos segundos.", "Espera unos segundos.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnEnviarMensaje.isEnabled = true
                    if (escuchaContinuaActiva && isAdded) rvChatMensajes.postDelayed({ cicloEscuchaContinua() }, 1000)
                }
            }
        }
    }
}