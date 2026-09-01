package com.example.movildilo.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataCache(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_data_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun <T> guardarLista(key: String, datos: List<T>) {
        prefs.edit()
            .putString(key, gson.toJson(datos))
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun <T> obtenerLista(key: String, tipo: java.lang.reflect.Type): List<T>? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson<List<T>>(json, tipo)
        } catch (e: Exception) {
            null
        }
    }

    fun minutosDesdeUltimaActualizacion(key: String): Long? {
        val timestamp = prefs.getLong("${key}_timestamp", -1L)
        if (timestamp == -1L) return null
        return (System.currentTimeMillis() - timestamp) / 60000
    }

    companion object {
        fun keyFacturas(negocioId: Long) = "cache_facturas_$negocioId"
        fun keyClientes(negocioId: Long) = "cache_clientes_$negocioId"
        fun keyCuentasPorCobrar(negocioId: Long) = "cache_cuentas_por_cobrar_$negocioId"

        inline fun <reified T> tipoLista(): java.lang.reflect.Type =
            TypeToken.getParameterized(List::class.java, T::class.java).type
    }
}