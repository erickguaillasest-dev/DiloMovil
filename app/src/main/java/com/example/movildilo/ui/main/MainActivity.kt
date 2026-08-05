package com.example.movildilo.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.auth.RegistroNegocioActivity
import com.example.movildilo.ui.auth.SelectRoleActivity
import com.example.movildilo.ui.dashboard.BodegueroActivity
import com.example.movildilo.ui.dashboard.PropietarioActivity
import com.example.movildilo.ui.dashboard.VendedorActivity

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Evaluar sesión inmediatamente
        evaluarSesion()
    }

    private fun evaluarSesion() {
        if (!sessionManager.isLoggedIn()) {
            irAlLogin()
            return
        }

        val userMap = sessionManager.getUserMap()
        if (userMap == null) {
            // Si la sesión no se deserializó bien, limpiamos e ir a Login
            sessionManager.logout()
            irAlLogin()
            return
        }

        val negocioId = sessionManager.getNegocioId()
        val tieneNegocio = negocioId != -1L && negocioId != 0L
        val needsRoleSelection = userMap["needsRoleSelection"] as? Boolean ?: false
        val rol = userMap["rol"]?.toString()

        val intent = when {
            !tieneNegocio -> Intent(this, RegistroNegocioActivity::class.java)
            needsRoleSelection -> Intent(this, SelectRoleActivity::class.java)
            else -> when (rol) {
                "PROPIETARIO" -> Intent(this, PropietarioActivity::class.java)
                "VENDEDOR" -> Intent(this, VendedorActivity::class.java)
                "BODEGUERO" -> Intent(this, BodegueroActivity::class.java)
                else -> Intent(this, LoginActivity::class.java)
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun irAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}