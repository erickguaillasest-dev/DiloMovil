package com.example.movildilo.ia

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 🎙️ Voz de Zoe reutilizable para CUALQUIER pantalla de chat (no solo Facturas).
 *
 * Concentra en un solo lugar el manejo de TextToSpeech (hablar) y SpeechRecognizer (escuchar),
 * para que pantallas como ZoeBottomSheetDialog (chat general) puedan agregar voz sin duplicar
 * toda la lógica que ya existe en HistorialFacturasActivity para la facturación por voz.
 *
 * Uso típico:
 *   val voz = ZoeSpeechHelper(requireContext())
 *   voz.hablar("Hola, ¿en qué te ayudo?")
 *   voz.escuchar(onResultado = { texto -> ... }, onError = { ... })
 *   ...
 *   voz.liberar() // en onDestroy/onDestroyView
 */
class ZoeSpeechHelper(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var reconocedorComandos: SpeechRecognizer? = null
    private var ttsListo = false
    private var escuchandoComandosDeParada = false
    private val manejadorPrincipal = Handler(Looper.getMainLooper())

    companion object {
        /**
         * Palabras que el usuario puede decir en cualquier momento (mientras Zoe habla o guía)
         * para que se detenga. Se comparan como palabra completa contra lo reconocido por el mic.
         */
        private val PALABRAS_DE_PARADA = listOf(
            "detente", "detén", "para", "cállate", "callate", "silencio",
            "basta", "alto", "stop", "quieto", "quieta", "termina", "suficiente"
        )

        /** true si [texto] contiene, como palabra completa, alguna orden de parada. */
        fun esComandoDeParada(texto: String): Boolean {
            val normalizado = " ${texto.trim().lowercase()} "
            return PALABRAS_DE_PARADA.any { palabra -> normalizado.contains(" $palabra ") }
        }

        /** Frases con las que el usuario le pide a Zoe que cambie su voz (tono/velocidad). */
        private val FRASES_CAMBIAR_VOZ = listOf(
            "cambia la voz", "cambia tu voz", "cambiar la voz", "cambiar de voz",
            "otra voz", "quiero otra voz", "suena distinto", "cambia de tono", "cambia el tono"
        )

        /** true si [texto] le pide a Zoe que cambie su voz. */
        fun esComandoCambiarVoz(texto: String): Boolean {
            val normalizado = texto.trim().lowercase()
            return FRASES_CAMBIAR_VOZ.any { normalizado.contains(it) }
        }
    }

    /**
     * Perfiles de voz disponibles para Zoe: cada uno combina una velocidad de habla y un tono
     * distintos, para que el usuario pueda elegir la que más le acomode. Se guarda como estado de
     * la instancia porque cada pantalla (chat general, guía de bienvenida, facturación por voz)
     * crea su propio [ZoeSpeechHelper].
     */
    private val perfilesDeVoz = listOf(
        Triple(1.05f, 1.05f, "Voz estándar"),
        Triple(1.25f, 1.3f, "Voz aguda y rápida"),
        Triple(0.85f, 0.8f, "Voz grave y pausada")
    )
    private var indicePerfilVoz = 0

    /** Cambia al siguiente perfil de voz disponible (rota entre ellos) y devuelve su nombre. */
    fun cambiarVoz(): String {
        indicePerfilVoz = (indicePerfilVoz + 1) % perfilesDeVoz.size
        val (velocidad, tono, nombre) = perfilesDeVoz[indicePerfilVoz]
        textToSpeech?.setSpeechRate(velocidad)
        textToSpeech?.setPitch(tono)
        return nombre
    }

    fun inicializar(onListo: (() -> Unit)? = null, onFallo: ((String) -> Unit)? = null) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ttsListo = false
                onFallo?.invoke("No se pudo iniciar el motor de voz del dispositivo.")
                return@TextToSpeech
            }
            val tts = textToSpeech
            var resultado = tts?.setLanguage(Locale("es", "EC"))
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                resultado = tts?.setLanguage(Locale("es", "ES"))
            }
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Último intento: cualquier español instalado, o el idioma por defecto del equipo.
                resultado = tts?.setLanguage(Locale("es"))
            }
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED || resultado == null) {
                ttsListo = false
                onFallo?.invoke(
                    "Este dispositivo no tiene instalada la voz en español. Ve a Ajustes > " +
                            "Accesibilidad > Salida de texto a voz e instala los datos de voz en español."
                )
                return@TextToSpeech
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.05f)
            ttsListo = true
            onListo?.invoke()
        }
    }

    /** true si el motor de voz quedó listo para hablar (idioma español disponible). */
    fun listoParaHablar(): Boolean = ttsListo

    /** Lee en voz alta [texto]. Si viene con markdown (**negrita**) lo limpia antes de hablar. */
    fun hablar(texto: String, alTerminar: (() -> Unit)? = null) {
        val limpio = texto.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1").replace(Regex("[#_`]"), "")
        val tts = textToSpeech
        if (tts == null || !ttsListo || limpio.isBlank()) {
            alTerminar?.invoke()
            return
        }
        val utteranceId = "zoe_chat_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            // onDone/onError pueden llegar en un hilo que no es el principal: los pasamos al
            // hilo principal porque el callback puede, a su vez, usar el SpeechRecognizer.
            override fun onDone(utteranceId: String?) { manejadorPrincipal.post { alTerminar?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { manejadorPrincipal.post { alTerminar?.invoke() } }
        })
        val resultado = tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (resultado == TextToSpeech.ERROR) {
            alTerminar?.invoke()
        }
    }

    fun detenerHabla() {
        textToSpeech?.stop()
    }

    /**
     * Escucha una sola frase del usuario y la entrega en [onResultado]. Cualquier error de
     * reconocimiento (permiso denegado, sin conexión, silencio) se reporta en [onError] con un
     * mensaje ya listo para mostrar al usuario, para no repetir ese mapeo en cada pantalla.
     */
    fun escuchar(onResultado: (String) -> Unit, onError: (String) -> Unit, onEmpezoAEscuchar: (() -> Unit)? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        val reconocedorCreado = try {
            speechRecognizer?.destroy()
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            onError("No se pudo iniciar el micrófono. Intenta de nuevo.")
            return
        }
        speechRecognizer = reconocedorCreado

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onEmpezoAEscuchar?.invoke() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val mensaje = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No te escuché bien, ¿puedes repetirlo?"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Necesito permiso de micrófono para escucharte."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Necesito conexión a internet para escucharte."
                    else -> "Hubo un problema con el micrófono, intenta de nuevo."
                }
                onError(mensaje)
            }

            override fun onResults(results: Bundle?) {
                val texto = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (texto.isNullOrBlank()) {
                    onError("No entendí lo que dijiste, ¿lo repites?")
                } else {
                    onResultado(texto)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            onError("No se pudo iniciar el micrófono. Intenta de nuevo.")
        }
    }

    fun detenerEscucha() {
        speechRecognizer?.cancel()
    }

    /**
     * Escucha en segundo plano, en bucle, esperando SOLO una orden de parada ("detente",
     * "cállate", "para", etc.). Se usa mientras Zoe está hablando o guiando al usuario por la
     * app, para poder interrumpirla en cualquier momento. No interfiere con [escuchar] porque
     * usa su propia instancia de [SpeechRecognizer].
     *
     * Llamar a [detenerEscuchaDeComandos] cuando ya no se necesite (Zoe terminó de hablar/guiar).
     */
    fun escucharComandosDeParada(onDetectado: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        escuchandoComandosDeParada = true
        lanzarCicloDeEscuchaDeComandos(onDetectado)
    }

    private fun lanzarCicloDeEscuchaDeComandos(onDetectado: () -> Unit, conRespiro: Boolean = false) {
        if (!escuchandoComandosDeParada) return

        // Tras un error (silencio, timeout) esperamos un instante antes de recrear el
        // reconocedor: relanzarlo sin pausa alguna martillea el servicio de reconocimiento del
        // sistema y puede volverlo inestable en algunos dispositivos.
        if (conRespiro) {
            manejadorPrincipal.postDelayed({ lanzarCicloInternoDeEscuchaDeComandos(onDetectado) }, 400)
        } else {
            lanzarCicloInternoDeEscuchaDeComandos(onDetectado)
        }
    }

    private fun lanzarCicloInternoDeEscuchaDeComandos(onDetectado: () -> Unit) {
        if (!escuchandoComandosDeParada) return
        try {
            reconocedorComandos?.destroy()
            val reconocedor = SpeechRecognizer.createSpeechRecognizer(context)
            reconocedorComandos = reconocedor

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            reconocedor.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    val texto = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!texto.isNullOrBlank() && esComandoDeParada(texto)) {
                        escuchandoComandosDeParada = false
                        onDetectado()
                    } else {
                        lanzarCicloDeEscuchaDeComandos(onDetectado, conRespiro = true)
                    }
                }

                override fun onError(error: Int) {
                    lanzarCicloDeEscuchaDeComandos(onDetectado, conRespiro = true)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            reconocedor.startListening(intent)
        } catch (_: Exception) {
            // Cualquier fallo al crear/usar el reconocedor (servicio no disponible, permiso
            // revocado a mitad de camino, etc.) se ignora en vez de tumbar la app: reintentamos
            // una vez más con respiro, y si vuelve a fallar, simplemente se deja de escuchar.
            manejadorPrincipal.postDelayed({
                if (escuchandoComandosDeParada) {
                    try {
                        lanzarCicloInternoDeEscuchaDeComandos(onDetectado)
                    } catch (_: Exception) {
                        escuchandoComandosDeParada = false
                    }
                }
            }, 800)
        }
    }

    /**
     * Escucha en segundo plano, en bucle, entregando en [onTexto] TODO lo que el usuario diga
     * (a diferencia de [escucharComandosDeParada], que solo dispara ante una orden de parar).
     * Quien la use decide qué hacer con cada frase reconocida y cuándo llamar a
     * [detenerEscuchaDeComandos] para cortar el bucle (por ejemplo, al detectar una orden de
     * parada o al navegar a otra acción). Se usa en la guía de bienvenida de Zoe para poder,
     * mientras te va guiando pantalla por pantalla, decirle "siguiente" o "agrega una bodega"
     * y que lo ejecute de una vez, sin tener que tocar nada en pantalla.
     */
    fun escucharComandosDeGuia(onTexto: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        escuchandoComandosDeParada = true
        lanzarCicloDeComandosDeGuia(onTexto)
    }

    private fun lanzarCicloDeComandosDeGuia(onTexto: (String) -> Unit, conRespiro: Boolean = false) {
        if (!escuchandoComandosDeParada) return
        if (conRespiro) {
            manejadorPrincipal.postDelayed({ lanzarCicloInternoDeComandosDeGuia(onTexto) }, 400)
        } else {
            lanzarCicloInternoDeComandosDeGuia(onTexto)
        }
    }

    private fun lanzarCicloInternoDeComandosDeGuia(onTexto: (String) -> Unit) {
        if (!escuchandoComandosDeParada) return
        try {
            reconocedorComandos?.destroy()
            val reconocedor = SpeechRecognizer.createSpeechRecognizer(context)
            reconocedorComandos = reconocedor

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            reconocedor.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onResults(results: Bundle?) {
                    val texto = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!texto.isNullOrBlank()) onTexto(texto)
                    lanzarCicloDeComandosDeGuia(onTexto, conRespiro = true)
                }

                override fun onError(error: Int) {
                    lanzarCicloDeComandosDeGuia(onTexto, conRespiro = true)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            reconocedor.startListening(intent)
        } catch (_: Exception) {
            manejadorPrincipal.postDelayed({
                if (escuchandoComandosDeParada) {
                    try {
                        lanzarCicloInternoDeComandosDeGuia(onTexto)
                    } catch (_: Exception) {
                        escuchandoComandosDeParada = false
                    }
                }
            }, 800)
        }
    }

    fun detenerEscuchaDeComandos() {
        escuchandoComandosDeParada = false
        reconocedorComandos?.cancel()
        reconocedorComandos?.destroy()
        reconocedorComandos = null
    }

    fun liberar() {
        escuchandoComandosDeParada = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
        reconocedorComandos?.destroy()
        reconocedorComandos = null
        textToSpeech = null
    }
}