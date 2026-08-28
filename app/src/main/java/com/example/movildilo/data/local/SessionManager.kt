package com.example.movildilo.data.local

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Base64
import com.example.movildilo.data.model.dto.auth.LoginResponseDto
import com.example.movildilo.ui.auth.LoginActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class SessionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun setSolicitudPendiente(pendiente: Boolean) {
        prefs.edit().putBoolean("solicitud_pendiente", pendiente).apply()
    }

    fun isSolicitudPendiente(): Boolean {
        return prefs.getBoolean("solicitud_pendiente", false)
    }

    fun setCuentaSuspendida(suspendida: Boolean) {
        prefs.edit().putBoolean("cuenta_suspendida", suspendida).apply()
    }

    fun isCuentaSuspendida(): Boolean {
        return prefs.getBoolean("cuenta_suspendida", false)
    }

    fun saveToken(token: String?, tokenType: String?) {
        val tipoLimpio = tokenType?.trim().takeIf { !it.isNullOrEmpty() } ?: "Bearer"
        prefs.edit()
            .putString("auth_token", token?.trim())
            .putString("token_type", tipoLimpio)
            .apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun getAuthHeader(): String? {
        val token = getToken()?.trim()
        val tokenType = (prefs.getString("token_type", "Bearer") ?: "Bearer").trim()
        return if (!token.isNullOrEmpty()) "$tokenType $token" else null
    }

    fun isTokenExpired(): Boolean {
        val token = getToken() ?: return true
        val parts = token.split(".")
        if (parts.size < 2) return true

        return try {
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.DEFAULT),
                Charsets.UTF_8
            )
            val jsonObject = JSONObject(payloadJson)
            if (jsonObject.has("exp")) {
                val expSeconds = jsonObject.getLong("exp")
                val currentSeconds = System.currentTimeMillis() / 1000
                currentSeconds >= expSeconds
            } else {
                false
            }
        } catch (e: Exception) {
            true
        }
    }

    fun redirectToLogin(ctx: Context = context) {
        clearSession()
        val intent = Intent(ctx, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        ctx.startActivity(intent)
    }

    fun checkAndRedirectIfExpired(ctx: Context = context): Boolean {
        if (!isLoggedIn() || isTokenExpired()) {
            redirectToLogin(ctx)
            return true
        }
        return false
    }

    fun saveUser(usuarioInfo: Any) {
        val json = gson.toJson(usuarioInfo)
        val map = getUserMapFromJson(json)
        val email = map?.get("email")?.toString() ?: map?.get("sub")?.toString()
        val role = map?.get("rol")?.toString() ?: map?.get("role")?.toString()
        val isSuper = map?.get("superAdmin") as? Boolean ?: false

        val editor = prefs.edit()
            .putString("usuario_info", json)
            .putBoolean("is_super_admin", isSuper)

        if (!email.isNullOrEmpty()) {
            editor.putString("user_email", email)
        }
        if (!role.isNullOrEmpty()) {
            editor.putString("user_role", role)
        }
        editor.apply()
    }

    fun getUserMap(): Map<String, Any?>? {
        val json = prefs.getString("usuario_info", null) ?: return null
        return getUserMapFromJson(json)
    }

    private fun getUserMapFromJson(json: String): Map<String, Any?>? {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun saveUserSession(usuarioDto: LoginResponseDto) {
        val json = gson.toJson(usuarioDto)
        val negocioId = usuarioDto.selectedBusinessId ?: usuarioDto.negocioId ?: -1L
        val email = usuarioDto.email
        val rol = usuarioDto.rol?.uppercase()?.trim() ?: ""

        val isSuperAdmin = usuarioDto.superAdmin == true ||
                rol == "SUPER_ADMIN" ||
                rol == "ADMIN" ||
                usuarioDto.roles?.any { it.uppercase().contains("ADMIN") } == true

        val editor = prefs.edit()
            .putString("usuario_dto", json)
            .putString("user_role", usuarioDto.rol)
            .putBoolean("is_super_admin", isSuperAdmin)
            .putLong("negocio_id", negocioId)

        if (!email.isNullOrEmpty()) {
            editor.putString("user_email", email)
        }

        editor.apply()
    }

    fun getUserSession(): LoginResponseDto? {
        val json = prefs.getString("usuario_dto", null) ?: return null
        return try {
            gson.fromJson(json, LoginResponseDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserEmail(): String? {
        val emailDirecto = prefs.getString("user_email", null)
        if (!emailDirecto.isNullOrEmpty()) return emailDirecto

        val session = getUserSession()
        if (!session?.email.isNullOrEmpty()) return session?.email

        val userMap = getUserMap()
        return userMap?.get("email")?.toString()
            ?: userMap?.get("sub")?.toString()
            ?: userMap?.get("username")?.toString()
    }

    fun getUserRole(): String? {
        val role = prefs.getString("user_role", null)
        if (!role.isNullOrEmpty()) return role

        val sessionRole = getUserSession()?.rol
        if (!sessionRole.isNullOrEmpty()) return sessionRole

        val userMap = getUserMap()
        return userMap?.get("rol")?.toString() ?: userMap?.get("role")?.toString()
    }

    fun isAdmin(): Boolean {
        if (prefs.getBoolean("is_super_admin", false)) return true

        val session = getUserSession()
        if (session?.superAdmin == true) return true
        if (session?.rol?.uppercase()?.trim()?.contains("ADMIN") == true) return true
        if (session?.roles?.any { it.uppercase().contains("ADMIN") } == true) return true

        val role = getUserRole()?.uppercase()?.trim() ?: ""
        if (role.contains("ADMIN")) return true

        val userMap = getUserMap()
        if (userMap?.get("superAdmin") == true) return true

        val rolesList = userMap?.get("roles") as? List<*>
        if (rolesList?.any { it.toString().uppercase().contains("ADMIN") } == true) return true

        return false
    }

    fun getNegocioId(): Long {
        val directId = prefs.getLong("negocio_id", -1L)
        if (directId != -1L) return directId

        val session = getUserSession()
        val sessionId = session?.selectedBusinessId ?: session?.negocioId
        if (sessionId != null && sessionId != -1L) return sessionId

        val userMap = getUserMap() ?: return -1L
        val negocioIdObj = userMap["negocioId"] ?: userMap["negocio_id"] ?: return -1L

        return when (negocioIdObj) {
            is Number -> negocioIdObj.toLong()
            is String -> negocioIdObj.toLongOrNull() ?: -1L
            else -> -1L
        }
    }

    fun hasNegocio(): Boolean {
        if (isAdmin()) return true
        return getNegocioId() != -1L
    }

    fun saveNegocioId(negocioId: Long) {
        prefs.edit().putLong("negocio_id", negocioId).apply()
    }

    fun saveNegocioId(negocioId: String) {
        val parsedId = negocioId.toLongOrNull() ?: -1L
        if (parsedId != -1L) {
            saveNegocioId(parsedId)
        }
    }

    fun saveNegocioId(negocioId: Any?) {
        when (negocioId) {
            is Number -> saveNegocioId(negocioId.toLong())
            is String -> saveNegocioId(negocioId)
        }
    }

    fun removeNegocioId() {
        prefs.edit().remove("negocio_id").apply()
    }

    fun isLoggedIn(): Boolean {
        val hasToken = !getToken().isNullOrEmpty()
        val hasUser = getUserMap() != null || getUserSession() != null
        return hasToken && hasUser && !isTokenExpired()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun logout(ctx: Context = context) {
        redirectToLogin(ctx)
    }
}