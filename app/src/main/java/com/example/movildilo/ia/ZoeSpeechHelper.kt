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
import android.speech.tts.Voice
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
            AcentoVoz("es", "AR", "acento argentino", listOf("acento argentino", "habla como argentino")),
            AcentoVoz("es", "MX", "acento mexicano", listOf("acento mexicano", "voz mexicana")),
            AcentoVoz("es", "ES", "acento español", listOf("acento español", "voz española")),
            AcentoVoz("es", "US", "acento neutro", listOf("acento neutro", "voz neutra")),
            AcentoVoz("es", "EC", "acento ecuatoriano", listOf("acento ecuatoriano"))
        )

        fun detectarAcentoPedido(texto: String): AcentoVoz? {
            val normalizado = java.text.Normalizer.normalize(texto.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            return ACENTOS_DISPONIBLES.firstOrNull { acento ->
                acento.frases.any { frase ->
                    val fraseNorm = java.text.Normalizer.normalize(frase, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
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
            val vocesDisponibles = try { tts?.voices } catch (e: Exception) { null }
            val vozFemenina = seleccionarVozFemenina(vocesDisponibles)

            if (vozFemenina != null) {
                tts?.voice = vozFemenina
            } else {
                tts?.setLanguage(Locale("es", "AR"))
            }

            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.08f)
            ttsListo = true
            onListo?.invoke()
        }
    }

    /**
     * Misma lógica de selección de voz que la versión web (seleccionarVozFemenina):
     * prioriza voces es-AR, descarta nombres típicamente masculinos, y entre las que
     * quedan prefiere las que suenan más naturales (Natural/Neural/Online/Premium/etc.)
     * o nombres femeninos conocidos, antes de caer a Microsoft/Google o la primera disponible.
     */
    private fun seleccionarVozFemenina(voces: Set<Voice>?): Voice? {
        if (voces.isNullOrEmpty()) return null
        val excluirMasculinas = Regex("pablo|jorge|diego|carlos|juan|pedro|antonio|male|hombre|var[oó]n|david|boy", RegexOption.IGNORE_CASE)

        val vocesArgentinas = voces.filter {
            it.locale.language == "es" && it.locale.country == "AR" && !excluirMasculinas.containsMatchIn(it.name)
        }
        val vocesEspanol = vocesArgentinas.ifEmpty {
            voces.filter { it.locale.language == "es" && !excluirMasculinas.containsMatchIn(it.name) }
        }

        if (vocesEspanol.isEmpty()) {
            return voces.firstOrNull { Regex("female|mujer|woman|femenina", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
                ?: voces.firstOrNull()
        }

        val prioridadAlta = listOf(
            "natural", "neural", "online", "premium", "enhanced", "wavenet", "studio",
            "elena", "sof(i|í)a", "m(i|í)a", "victoria", "paulina", "helena",
            "m(o|ó)nica", "luc(i|í)a", "camila", "valentina", "isabela", "esperanza"
        )
        for (patron in prioridadAlta) {
            val regex = Regex(patron, RegexOption.IGNORE_CASE)
            vocesEspanol.firstOrNull { regex.containsMatchIn(it.name) }?.let { return it }
        }

        vocesEspanol.firstOrNull { it.name.contains("microsoft", true) }?.let { return it }
        vocesEspanol.firstOrNull { it.name.contains("google", true) }?.let { return it }

        return vocesEspanol.firstOrNull()
    }

    fun listoParaHablar(): Boolean = ttsListo

    /**
     * Parte el texto en frases cortas (misma lógica que dividirEnFrases en la web):
     * protege los números decimales para que el punto no corte la cifra a la mitad,
     * y agrupa oraciones hasta ~180 caracteres por trozo. Esto evita superar el límite
     * de caracteres del motor de TextToSpeech en respuestas largas, y permite detectar
     * con precisión cuándo terminó de hablar TODO el texto (no solo el primer trozo).
     */
    private fun dividirEnFrases(texto: String): List<String> {
        val marcadorDecimal = "§DEC§"
        val textoProtegido = texto.replace(Regex("(\\d)\\.(\\d)"), "$1${marcadorDecimal}$2")

        val frases = Regex("[^.!?…]+[.!?…]*(\\s|$)").findAll(textoProtegido)
            .map { it.value }.toList()
            .ifEmpty { listOf(textoProtegido) }

        val trozos = mutableListOf<String>()
        var actual = ""
        for (fraseCruda in frases) {
            val frase = fraseCruda.trim()
            if (frase.isEmpty()) continue
            if ((actual + " " + frase).trim().length > 180) {
                if (actual.isNotEmpty()) trozos.add(actual.trim())
                actual = frase
            } else {
                actual = if (actual.isEmpty()) frase else "$actual $frase"
            }
        }
        if (actual.isNotEmpty()) trozos.add(actual.trim())

        val trozosFinales = trozos.ifEmpty { listOf(textoProtegido) }
        return trozosFinales.map { it.replace(marcadorDecimal, ".") }
    }

    fun hablar(texto: String, alTerminar: (() -> Unit)? = null) {
        // Bloqueo estricto del micrófono antes de comenzar a hablar para no escucharse a sí misma.
        detenerEscucha()
        detenerEscuchaDeComandos()

        val limpio = texto
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("[#_`|~]"), "")
            .replace(Regex("id\\s*:\\s*\\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("[⚠✖•]"), "")
            .replace("$", " dólares ")
            .replace("%", " por ciento ")
            .replace(Regex("-{2,}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val tts = textToSpeech
        if (tts == null || !ttsListo || limpio.isBlank()) {
            manejadorPrincipal.postDelayed({ alTerminar?.invoke() }, 100)
            return
        }

        val frases = dividirEnFrases(limpio)
        var completadas = 0
        val idBase = "zoe_${System.currentTimeMillis()}"

        fun marcarCompletada() {
            completadas++
            if (completadas >= frases.size) {
                // Genera un delay real antes de reactivar el mic, asegurando que el altavoz se detuvo
                manejadorPrincipal.postDelayed({ alTerminar?.invoke() }, 600)
            }
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { marcarCompletada() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { marcarCompletada() }
        })

        frases.forEachIndexed { index, frase ->
            val modoEncolado = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(frase, modoEncolado, null, "${idBase}_$index")
        }
    }

    fun detenerHabla() {
        textToSpeech?.stop()
    }

    fun escuchar(
        onResultado: (String) -> Unit,
        onError: (mensaje: String, codigoError: Int) -> Unit,
        onEmpezoAEscuchar: (() -> Unit)? = null,
        onParcial: ((String) -> Unit)? = null
    ) {
        detenerHabla()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible.", SpeechRecognizer.ERROR_CLIENT)
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        } catch (_: Exception) {
            onError("No se pudo iniciar el micrófono.", SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            // Habilitado para poder mostrar en pantalla lo que se va transcribiendo en vivo,
            // igual que hace la versión web con interimResults.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onEmpezoAEscuchar?.invoke() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Distinguimos el permiso denegado de un error recuperable (sin esto, un
                // ciclo de "escucha continua" reintentaría en loop aunque el permiso esté negado).
                val mensaje = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                    "Necesito permiso de micrófono para poder escucharte."
                else
                    "No te escuché bien, che. ¿Repetís?"
                onError(mensaje, error)
            }
            override fun onResults(results: Bundle?) {
                val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (texto.isNullOrBlank()) onError("No entendí, che.", SpeechRecognizer.ERROR_NO_MATCH) else onResultado(texto)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val parcial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!parcial.isNullOrBlank()) onParcial?.invoke(parcial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            onError("Error al iniciar el micrófono.", SpeechRecognizer.ERROR_CLIENT)
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
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