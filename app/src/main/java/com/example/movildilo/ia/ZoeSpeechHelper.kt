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

class ZoeSpeechHelper(private val context: Context) {

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var reconocedorComandos: SpeechRecognizer? = null
    private var ttsListo = false
    private var escuchandoComandosDeParada = false
    private val manejadorPrincipal = Handler(Looper.getMainLooper())

    companion object {
        private val PALABRAS_DE_PARADA = listOf(
            "detente", "detén", "para", "cállate", "callate", "silencio",
            "basta", "alto", "stop", "quieto", "quieta", "termina", "suficiente"
        )

        fun esComandoDeParada(texto: String): Boolean {
            val normalizado = " ${texto.trim().lowercase()} "
            return PALABRAS_DE_PARADA.any { palabra -> normalizado.contains(" $palabra ") }
        }

        private val ACENTOS_DISPONIBLES = listOf(
            AcentoVoz("es", "AR", "acento argentino", listOf(
                "acento argentino", "habla como argentino", "hablame como argentino",
                "con acento argentino", "voz argentina", "che", "vuelve a tu acento original"
            )),
            AcentoVoz("es", "MX", "acento mexicano", listOf("acento mexicano", "voz mexicana")),
            AcentoVoz("es", "ES", "acento español", listOf("acento español", "voz española")),
            AcentoVoz("es", "US", "acento neutro", listOf("acento neutro", "voz neutra")),
            AcentoVoz("es", "EC", "acento ecuatoriano", listOf("acento ecuatoriano"))
        )

        fun detectarAcentoPedido(texto: String): AcentoVoz? {
            val normalizado = java.text.Normalizer.normalize(texto.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return ACENTOS_DISPONIBLES.firstOrNull { acento ->
                acento.frases.any { frase ->
                    val fraseNorm = java.text.Normalizer.normalize(frase, java.text.Normalizer.Form.NFD)
                        .replace(Regex("\\p{Mn}+"), "")
                    normalizado.contains(fraseNorm)
                }
            }
        }
    }

    data class AcentoVoz(val idioma: String, val pais: String, val nombre: String, val frases: List<String>)

    private var acentoActual: AcentoVoz = ACENTOS_DISPONIBLES.first { it.pais == "AR" }
        private set

    fun activarAcento(acento: AcentoVoz): String {
        val tts = textToSpeech ?: return "Che, todavía no tengo la voz lista."
        val locale = Locale(acento.idioma, acento.pais)
        tts.setLanguage(locale)
        acentoActual = acento
        return "Listo, che. Te hablo con ${acento.nombre}."
    }

    fun inicializar(onListo: (() -> Unit)? = null, onFallo: ((String) -> Unit)? = null) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ttsListo = false
                onFallo?.invoke("No se pudo iniciar el motor de voz.")
                return@TextToSpeech
            }
            val tts = textToSpeech
            tts?.setLanguage(Locale("es", "AR"))
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.05f)
            ttsListo = true
            onListo?.invoke()
        }
    }

    fun listoParaHablar(): Boolean = ttsListo

    fun hablar(texto: String, alTerminar: (() -> Unit)? = null) {
        val limpio = texto
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("[#_`|]"), "")
            .replace(Regex("id\\s*:\\s*\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("[⚠✖•]"), "")
            .replace("$", " pesos ")
            .replace(Regex("-{2,}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val tts = textToSpeech
        if (tts == null || !ttsListo || limpio.isBlank()) {
            alTerminar?.invoke()
            return
        }
        val utteranceId = "zoe_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { manejadorPrincipal.post { alTerminar?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { manejadorPrincipal.post { alTerminar?.invoke() } }
        })
        tts.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun detenerHabla() {
        textToSpeech?.stop()
    }

    fun escuchar(onResultado: (String) -> Unit, onError: (String) -> Unit, onEmpezoAEscuchar: (() -> Unit)? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible.")
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            onError("No se pudo iniciar el micrófono.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L) // 10 segundos mínimos
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5s de silencio completo
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onEmpezoAEscuchar?.invoke() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onError("No te escuché bien, che. ¿Repetís?") }
            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (texto.isNullOrBlank()) onError("No entendí, che.") else onResultado(texto)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            onError("Error al iniciar el micrófono.")
        }
    }

    fun detenerEscucha() {
        speechRecognizer?.cancel()
    }

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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            reconocedor.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onResults(results: Bundle?) {
                    val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
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
                    try { lanzarCicloInternoDeComandosDeGuia(onTexto) } catch (_: Exception) { escuchandoComandosDeParada = false }
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