package com.example.movildilo.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Un producto que la IA entendió que el usuario quiere agregar (o modificar) en el carrito. */
data class ItemVozIA(
    val producto: String,
    val cantidad: Int?,
    val descuentoPorcentaje: Int? = null
)

/** Todo lo que la IA logró extraer de una frase libre del usuario. */
data class ResultadoVozFactura(
    val cliente: String? = null,
    val metodoPago: String? = null,
    val cuotas: Int? = null,
    val bodega: String? = null,
    val items: List<ItemVozIA> = emptyList(),
    val eliminarProducto: String? = null,
    val descuentoGlobalPorcentaje: Int? = null,
    val emitirFactura: Boolean = false
)

object ZoeVoiceAI {

    private const val GROQ_API_KEY = "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"
    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODELO = "llama-3.1-8b-instant"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Procesa la frase del usuario en un hilo secundario de I/O para evitar bloqueos en la interfaz.
     */
    suspend fun interpretar(
        fraseUsuario: String,
        nombresClientes: List<String>,
        nombresProductos: List<String>,
        nombresBodegas: List<String>
    ): ResultadoVozFactura? = withContext(Dispatchers.IO) {
        val promptSistema = construirPrompt(nombresClientes, nombresProductos, nombresBodegas)

        val mensajes = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", promptSistema))
            put(JSONObject().put("role", "user").put("content", fraseUsuario))
        }

        val cuerpo = JSONObject().apply {
            put("model", MODELO)
            put("messages", mensajes)
            put("temperature", 0.0)
            put("max_tokens", 450)
        }

        val mediaType = MediaType.parse("application/json; charset=utf-8")
        val requestBody = RequestBody.create(mediaType, cuerpo.toString())

        val request = Request.Builder()
            .url(GROQ_URL)
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val cuerpoTexto = response.body()?.string() ?: return@withContext null
                val contenido = JSONObject(cuerpoTexto)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                parsearRespuesta(contenido)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun construirPrompt(
        clientes: List<String>,
        productos: List<String>,
        bodegas: List<String>
    ): String {
        // Limitamos por cantidad de elementos en lugar de caracteres para evitar nombres cortados a la mitad
        val listaClientes = clientes.take(40).joinToString(", ")
        val listaProductos = productos.take(60).joinToString(", ")
        val listaBodegas = bodegas.take(20).joinToString(", ")

        return """
            Eres la IA de voz de un sistema de facturación (POS) llamada Zoe. El usuario habla
            en español de forma natural y puede decir varios datos en una sola frase (cliente,
            método de pago, cuotas, bodega, cantidad y producto, todo junto o por partes).
            Extrae los datos en un JSON puro, sin texto adicional ni explicaciones.

            Listas reales del negocio (compáralas con lo que dice el usuario, aunque no las
            mencione exactamente igual):
            Clientes: [$listaClientes]
            Productos: [$listaProductos]
            Bodegas: [$listaBodegas]

            Formato EXACTO de salida (JSON, nada más, sin comentarios ni bloques de código):
            {
              "cliente": "nombre o cédula extraída, o 'CONSUMIDOR_FINAL', o null",
              "metodoPago": "EFECTIVO" | "TRANSFERENCIA" | "TARJETA_CREDITO" | null,
              "cuotas": numero_entero_o_null,
              "bodega": "nombre de bodega extraído, o null",
              "items": [ { "producto": "nombre extraído", "cantidad": numero_entero_o_null, "descuentoPorcentaje": numero_entero_o_null } ],
              "eliminarProducto": "nombre del producto a quitar del carrito, o null",
              "descuentoGlobalPorcentaje": numero_entero_o_null,
              "emitirFactura": true o false
            }

            REGLAS:
            1. Si dice "consumidor final" o "sin datos", cliente es "CONSUMIDOR_FINAL".
            2. Si solo dicta números para identificar al cliente, es su cédula: ponla en "cliente".
            3. "emitirFactura": true SOLO si el usuario pide EXPLÍCITAMENTE y sin ambigüedad terminar/cerrar/emitir la factura completa como acción final (ej. "emite la factura", "ya está, emítela", "guarda la factura", "eso es todo, cóbrala"). NO actives "emitirFactura" solo porque la frase contenga palabras sueltas como "listo", "ok", "dale", "cobra" o "cobrar" si esas palabras están describiendo otra cosa (por ejemplo "cóbrale a Juan" es solo el nombre del cliente, no una orden de emitir). Ante la duda, deja "emitirFactura" en false: es preferible preguntar antes de cerrar la factura.
            4. Si dice "borra", "quita" o "elimina" un producto puntual, pon su nombre en "eliminarProducto" (y no lo repitas en "items").
            5. CUOTAS: si menciona "tarjeta" o "crédito" el método es TARJETA_CREDITO. Si dice "cuotas" o "meses", extrae el número entero en "cuotas".
            6. El usuario puede mencionar VARIOS campos en la misma frase (por ejemplo: cliente, método de pago y bodega juntos, y luego cantidad y producto). Extrae absolutamente todos los que encuentres, no solo el primero.
            7. Si no menciona algún campo, ese campo va en null (o "items": [] si no menciona ningún producto).
            8. PRODUCTOS: solo agrega un producto a "items" si el usuario lo nombró explícitamente pidiendo agregarlo (verbo de agregar + cantidad/nombre, ej. "ponme dos cocas", "agrégame una leche", "quiero tres panes"). NUNCA inventes ni supongas un producto que el usuario no mencionó. Cada frase se interpreta sola, solo con lo que esa frase dice. Si la frase solo da el cliente, la forma de pago, la bodega o una palabra de confirmación, "items" va vacío.
            9. DESCUENTOS: si el usuario pide un descuento para UN producto puntual (ej. "2 coca colas con 10% de descuento", "la leche con un 5 por ciento menos"), pon ese número entero (0-100, sin el símbolo %) en "descuentoPorcentaje" DENTRO de ese item. Si pide un descuento para TODA la factura o el ticket completo (ej. "aplícale un 15% de descuento a todo", "dale un 10 por ciento de descuento general"), pon ese número entero en "descuentoGlobalPorcentaje" (a nivel raíz, no dentro de items). Si no menciona ningún descuento, ambos van en null.
            10. NO devuelvas texto fuera del JSON. No expliques nada. No uses ```.
        """.trimIndent()
    }

    private fun parsearRespuesta(contenidoCrudo: String): ResultadoVozFactura? {
        return try {
            var jsonStr = contenidoCrudo.trim()
            val match = Regex("\\{[\\s\\S]*\\}").find(jsonStr)
            if (match != null) jsonStr = match.value
            jsonStr = jsonStr.replace("```json", "", ignoreCase = true).replace("```", "").trim()

            val obj = JSONObject(jsonStr)

            val items = mutableListOf<ItemVozIA>()
            val itemsJson = obj.optJSONArray("items")
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val itemObj = itemsJson.optJSONObject(i) ?: continue
                    val nombreProd = itemObj.optString("producto", "")
                        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    val cantidad = if (itemObj.isNull("cantidad")) null else itemObj.optInt("cantidad", 1)
                    val descuentoItem = if (itemObj.isNull("descuentoPorcentaje")) null
                    else itemObj.optInt("descuentoPorcentaje", -1).takeIf { it in 0..100 }
                    if (nombreProd != null) items.add(ItemVozIA(nombreProd, cantidad, descuentoItem))
                }
            }

            fun campoTexto(nombre: String): String? =
                obj.optString(nombre, "").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            ResultadoVozFactura(
                cliente = campoTexto("cliente"),
                metodoPago = campoTexto("metodoPago"),
                cuotas = if (obj.isNull("cuotas")) null else obj.optInt("cuotas", -1).takeIf { it > 0 },
                bodega = campoTexto("bodega"),
                items = items,
                eliminarProducto = campoTexto("eliminarProducto"),
                descuentoGlobalPorcentaje = if (obj.isNull("descuentoGlobalPorcentaje")) null
                else obj.optInt("descuentoGlobalPorcentaje", -1).takeIf { it in 0..100 },
                emitirFactura = obj.optBoolean("emitirFactura", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}