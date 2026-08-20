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
import com.example.movildilo.utils.Constants

data class ItemVozIA(
    val producto: String,
    val cantidad: Int?,
    val descuentoPorcentaje: Int? = null
)

data class ResultadoVozFactura(
    val cliente: String? = null,
    val metodoPago: String? = null,
    val cuotas: Int? = null,
    val bodega: String? = null,
    val items: List<ItemVozIA> = emptyList(),
    val eliminarProducto: String? = null,
    val eliminarCantidad: Int? = null,
    val descuentoGlobalPorcentaje: Int? = null,
    val emitirFactura: Boolean = false,
    val vaciarCarrito: Boolean = false
)

object ZoeVoiceAI {

    private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODELO = "openai/gpt-oss-120b"

    @Volatile var ultimoError: String? = null
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun interpretar(
        fraseUsuario: String,
        nombresClientes: List<String>,
        nombresProductos: List<String>,
        nombresBodegas: List<String>
    ): ResultadoVozFactura? = withContext(Dispatchers.IO) {
        ultimoError = null
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

        var exito = false
        var intentos = 0
        val maxIntentos = if (Constants.totalClavesFacturas() > 0) Constants.totalClavesFacturas() else 1
        var resultadoFinal: ResultadoVozFactura? = null

        while (!exito && intentos < maxIntentos) {
            val apiKeyActual = Constants.obtenerClaveFacturasActual()

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer $apiKeyActual")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val cuerpoTexto = response.body()?.string()
                        if (cuerpoTexto == null) {
                            ultimoError = "RESPUESTA_VACIA"
                            return@withContext null
                        }
                        val contenido = JSONObject(cuerpoTexto)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        resultadoFinal = parsearRespuesta(contenido)
                        if (resultadoFinal == null) {
                            ultimoError = "JSON_INVALIDO"
                        }
                        exito = true
                    } else if (response.code() == 429 || response.code() == 401 || response.code() == 403) {
                        Constants.rotarClaveFacturas()
                        intentos++
                    } else {
                        ultimoError = "HTTP_${response.code()}"
                        return@withContext null
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                Constants.rotarClaveFacturas()
                intentos++
                if (intentos >= maxIntentos) {
                    ultimoError = "SIN_INTERNET"
                    return@withContext null
                }
            } catch (e: java.net.SocketTimeoutException) {
                Constants.rotarClaveFacturas()
                intentos++
                if (intentos >= maxIntentos) {
                    ultimoError = "TIEMPO_AGOTADO"
                    return@withContext null
                }
            } catch (e: Exception) {
                Constants.rotarClaveFacturas()
                intentos++
                if (intentos >= maxIntentos) {
                    ultimoError = "EXCEPCION_${e.javaClass.simpleName}"
                    return@withContext null
                }
            }
        }

        resultadoFinal
    }

    private fun construirPrompt(
        clientes: List<String>,
        productos: List<String>,
        bodegas: List<String>
    ): String {
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
              "eliminarCantidad": numero_entero_o_null,
              "descuentoGlobalPorcentaje": numero_entero_o_null,
              "emitirFactura": true o false,
              "vaciarCarrito": true o false
            }

            REGLAS:
            1. Si dice "consumidor final" o "sin datos", cliente es "CONSUMIDOR_FINAL".
            2. Si solo dicta números para identificar al cliente, es su cédula: ponla en "cliente".
            3. "emitirFactura": true SOLO si el usuario pide EXPLÍCITAMENTE y sin ambigüedad terminar/cerrar/emitir la factura completa como acción final (ej. "emite la factura", "ya está, emítela", "guarda la factura", "eso es todo, cóbrala"). NO actives "emitirFactura" solo porque la frase contenga palabras sueltas como "listo", "ok", "dale", "cobra" o "cobrar" si esas palabras están describiendo otra cosa (por ejemplo "cóbrale a Juan" es solo el nombre del cliente, no una orden de emitir). Ante la duda, deja "emitirFactura" en false: es preferible preguntar antes de cerrar la factura.
            4. "eliminarProducto" SOLO se activa si el usuario usa literalmente la palabra "elimina" o "eliminar" (ej. "elimina el pan", "elimina 2 panes", "eliminar la coca cola"). Palabras como "quita", "borra", "saca" o un descuento NO activan "eliminarProducto"; en esos casos déjalo en null. Cuando sí aplica, pon el nombre del producto en "eliminarProducto" y NO lo repitas en "items".
               - "eliminarCantidad": si el usuario dio un número puntual a eliminar (ej. "elimina 2 panes" -> eliminarCantidad: 2, "elimina una coca cola" -> eliminarCantidad: 1), ponlo aquí.
               - Si el usuario NO dio cantidad, o pidió eliminar "todo el producto" / "todos los panes" / el producto sin número (ej. "elimina el pan", "elimina la coca cola"), deja "eliminarCantidad": null (esto significa eliminar el producto completo del ticket).
            5. CUOTAS: si menciona "tarjeta" o "crédito" el método es TARJETA_CREDITO. Si dice "cuotas" o "meses", extrae el número entero en "cuotas".
            5b. REGLA DE NEGOCIO OBLIGATORIA: Consumidor Final NUNCA puede pagar con TARJETA_CREDITO (es una regla del negocio, no una preferencia). Si la frase pide "consumidor final" (o "cliente" ya es Consumidor Final) Y AL MISMO TIEMPO pide tarjeta/crédito, pon "metodoPago": "EFECTIVO" (nunca TARJETA_CREDITO) y "cuotas": null. No expliques la regla en el JSON, solo aplícala.
            6. El usuario puede mencionar VARIOS campos en la misma frase (por ejemplo: cliente, método de pago y bodega juntos, y luego cantidad y producto). Extrae absolutamente todos los que encuentres, no solo el primero.
            7. Si no menciona algún campo, ese campo va en null (o "items": [] si no menciona ningún producto).
            8. PRODUCTOS: solo agrega un producto a "items" si el usuario lo nombró explílicamente pidiendo agregarlo. Reconoce CUALQUIER verbo o frase que signifique "agregar al ticket/factura/carrito", entre otros: "agrégame", "agrega", "ponme", "pon en el ticket", "mete", "métele", "incluye", "carga", "anota", "manda", "quiero", "dame", "necesito", "sube" (ej. "ponme dos cocas", "agrégame una leche", "mete tres panes al ticket", "incluye una máscara", "quiero tres panes", "cambia a la bodega norte y agrégame dos panes"). NUNCA inventes ni supongas un producto que el usuario no mencionó. Cada frase se interpreta sola, solo con lo que esa frase dice. Si la frase solo da el cliente, la forma de pago, la bodega o una palabra de confirmación, "items" va vacío.
            8b. NOMBRE DEL PRODUCTO: en "producto" pon el nombre o palabra clave del producto tal como lo dijo el usuario, SIN el verbo ni palabras sueltas como "el", "la", "producto", "al ticket", "por favor" (ej. si dice "agrégame el producto máscara" pon "producto": "mascara", no "el producto mascara"). El usuario puede decir solo una PARTE del nombre real del producto (ej. dice "máscara" y el catálogo tiene "Zen Máscara Facial 50ml"): eso es válido y esperado, no hace falta que coincida exacto ni completo con la lista de Productos — el sistema se encarga de buscar la coincidencia más parecida.
            9. DESCUENTOS: si el usuario pide un descuento para UN producto puntual (ej. "2 coca colas con 10% de descuento", "la leche con un 5 por ciento menos"), pon ese número entero (0-100, sin el símbolo %) en "descuentoPorcentaje" DENTRO de ese item. Si pide un descuento para TODA la factura o el ticket completo (ej. "aplícale un 15% de descuento a todo", "dale un 10 por ciento de descuento general"), pon ese número entero en "descuentoGlobalPorcentaje" (a nivel raíz, no dentro de items). Si no menciona ningún descuento, ambos van en null.
            10. NO devuelvas texto fuera del JSON. No expliques nada. No uses ```.
            11. Si pide vaciar todo el ticket o borrar todos los productos (ej. "borra todo", "elimina los productos del ticket", "vaciar carrito", "limpiar ticket"), pon "vaciarCarrito": true.
            12. BODEGA: si el usuario pide cambiar de bodega/almacén (ej. "cambia a la bodega norte", "usa la bodega centro", "de la bodega dos"), extrae el nombre en "bodega" aunque no agregue ningún producto en esa misma frase.
            13. Si el usuario NO menciona ninguna bodega en la frase, deja "bodega": null (NO inventes ni asumas una bodega). El sistema ya se encarga de elegir automáticamente la bodega correcta según el stock disponible del producto cuando el usuario no la dice.
            14. ROBUSTEZ CON FRASES LARGAS: el usuario suele decir varios datos seguidos en una sola frase (cliente, pago, cuotas, bodega, y uno o varios productos con cantidad). Extrae CADA dato que sí reconozcas con confianza, aunque otra parte de la frase sea confusa, esté mal dicha o no la entiendas del todo. Nunca dejes de extraer un dato claro solo porque otro dato de la misma frase sea dudoso: cada campo del JSON se decide de forma independiente con lo que sí quedó claro. Si una palabra suelta no encaja en ningún campo, simplemente ignórala.
        """.trimIndent()
    }

    private val numeroPalabras = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11,
        "doce" to 12, "docena" to 12, "media docena" to 6
    )

    private fun normalizar(t: String): String {
        val nfd = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{Mn}+"), "").lowercase().trim()
    }

    fun interpretarLocal(
        fraseUsuario: String,
        nombresClientes: List<String>,
        nombresProductos: List<String>,
        nombresBodegas: List<String>
    ): ResultadoVozFactura {
        val f = normalizar(fraseUsuario)

        val dijoElimina = Regex("\\belimin\\w*\\b").containsMatchIn(f)

        fun extraerCantidadCerca(nombreEncontrado: String): Int? {
            val idx = f.indexOf(nombreEncontrado)
            if (idx <= 0) return null
            val antes = f.substring(0, idx).trim().split(Regex("\\s+")).takeLast(3)
            for (palabra in antes.asReversed()) {
                palabra.toIntOrNull()?.let { return it }
                numeroPalabras[palabra]?.let { return it }
            }
            return null
        }

        var cliente: String? = null
        if (f.contains("consumidor final") || f.contains("sin datos")) {
            cliente = "CONSUMIDOR_FINAL"
        } else {
            cliente = nombresClientes.filter { it.isNotBlank() }
                .map { normalizar(it) to it }
                .filter { (n, _) -> n.length >= 3 && f.contains(n) }
                .maxByOrNull { (n, _) -> n.length }?.second
        }

        val metodoPago = when {
            f.contains("tarjeta") || f.contains("credito") -> "TARJETA_CREDITO"
            f.contains("transferencia") -> "TRANSFERENCIA"
            f.contains("efectivo") -> "EFECTIVO"
            else -> null
        }.let { metodo ->
            val pideConsumidorFinal = cliente == "CONSUMIDOR_FINAL"
            if (metodo == "TARJETA_CREDITO" && pideConsumidorFinal) "EFECTIVO" else metodo
        }

        val cuotas = Regex("(\\d+)\\s*(cuotas|meses)").find(f)?.groupValues?.get(1)?.toIntOrNull()

        val bodega = nombresBodegas.filter { it.isNotBlank() }
            .map { normalizar(it) to it }
            .filter { (n, _) -> f.contains(n) }
            .maxByOrNull { (n, _) -> n.length }?.second

        var eliminarProducto: String? = null
        var eliminarCantidad: Int? = null
        val items = mutableListOf<ItemVozIA>()

        val stopWordsProducto = setOf(
            "agrega", "agregue", "agregar", "agregame", "anade", "anadir",
            "pon", "ponme", "poner", "mete", "meteme", "meter", "incluye", "incluyeme", "incluir",
            "carga", "cargame", "cargar", "anota", "anotame", "anotar", "manda", "mandame",
            "quiero", "dame", "necesito", "sube", "subeme",
            "un", "una", "unos", "unas", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete",
            "ocho", "nueve", "diez", "del", "de", "la", "el", "los", "las", "al", "con",
            "por", "para", "y", "o", "que", "me", "te", "le", "se", "producto", "productos",
            "factura", "ticket", "favor", "porfa", "descuento", "porcentaje", "dolares", "dolar",
            "centavos", "tambien"
        )

        val nombresOrdenados = nombresProductos.filter { it.isNotBlank() }
            .map { normalizar(it) to it }
            .filter { (n, _) -> n.length >= 3 && f.contains(n) }
            .toMutableList()

        val tokensFrase = f.split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWordsProducto && it.toIntOrNull() == null }
        for (tok in tokensFrase) {
            nombresProductos.filter { it.isNotBlank() }
                .map { normalizar(it) to it }
                .filter { (n, _) -> n.split(Regex("\\s+")).any { palabra -> palabra.length >= 3 && (palabra == tok || palabra.contains(tok) || tok.contains(palabra)) } }
                .forEach { par -> if (nombresOrdenados.none { it.second == par.second }) nombresOrdenados.add(par) }
        }

        val nombresOrdenadosFinal = nombresOrdenados.sortedByDescending { (n, _) -> n.length }

        val yaUsados = mutableSetOf<String>()
        for ((n, original) in nombresOrdenadosFinal) {
            if (yaUsados.any { it.contains(n) || n.contains(it) }) continue
            yaUsados.add(n)
            val cantidad = extraerCantidadCerca(n)
            if (dijoElimina && eliminarProducto == null) {
                eliminarProducto = original
                eliminarCantidad = cantidad
            } else if (!dijoElimina) {
                items.add(ItemVozIA(original, cantidad))
            }
        }

        val descuentoGlobal = if (f.contains("descuento") && (f.contains("todo") || f.contains("general") || f.contains("factura") || f.contains("ticket"))) {
            Regex("(\\d+)\\s*(%|por ciento)").find(f)?.groupValues?.get(1)?.toIntOrNull()
        } else null

        val palabrasEmitir = listOf("emite", "emitir", "guarda la factura", "guardar factura", "cobra ya", "factura ya")
        val emitirFactura = palabrasEmitir.any { f.contains(it) }

        val vaciarCarrito = listOf("borra todo", "vaciar carrito", "vacia el carrito", "limpiar ticket", "limpia el ticket").any { f.contains(it) }

        return ResultadoVozFactura(
            cliente = cliente,
            metodoPago = metodoPago,
            cuotas = cuotas,
            bodega = bodega,
            items = items,
            eliminarProducto = eliminarProducto,
            eliminarCantidad = eliminarCantidad,
            descuentoGlobalPorcentaje = descuentoGlobal,
            emitirFactura = emitirFactura,
            vaciarCarrito = vaciarCarrito
        )
    }

    fun estaVacio(r: ResultadoVozFactura): Boolean {
        return r.cliente == null && r.metodoPago == null && r.cuotas == null && r.bodega == null &&
                r.items.isEmpty() && r.eliminarProducto == null && r.descuentoGlobalPorcentaje == null &&
                !r.emitirFactura && !r.vaciarCarrito
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
                eliminarCantidad = if (obj.isNull("eliminarCantidad")) null else obj.optInt("eliminarCantidad", -1).takeIf { it > 0 },
                descuentoGlobalPorcentaje = if (obj.isNull("descuentoGlobalPorcentaje")) null
                else obj.optInt("descuentoGlobalPorcentaje", -1).takeIf { it in 0..100 },
                emitirFactura = obj.optBoolean("emitirFactura", false),
                vaciarCarrito = obj.optBoolean("vaciarCarrito", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}