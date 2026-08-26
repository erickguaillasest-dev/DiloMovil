package com.example.movildilo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.ui.admin.AdminIvaActivity
import com.example.movildilo.ui.admin.AdminNegociosActivity
import com.example.movildilo.ui.admin.AdminParroquiasActivity
import com.example.movildilo.ui.admin.AdminUsuariosActivity
import com.example.movildilo.ui.auth.LoginActivity
import com.example.movildilo.ui.propietario.Perfil
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ShapeableImageView
    private lateinit var tvWelcome: TextView
    private lateinit var tvRoleSubtitle: TextView
    private lateinit var btnLogout: MaterialButton
    private lateinit var headerProfileClick: LinearLayout

    private lateinit var tvTotalNegocios: TextView
    private lateinit var tvTotalUsuarios: TextView
    private lateinit var tvIvaVigente: TextView

    private lateinit var cardNegocios: LinearLayout
    private lateinit var cardUsuarios: LinearLayout
    private lateinit var cardConfiguracionIva: LinearLayout
    private lateinit var cardParroquias: LinearLayout

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin)

        sessionManager = SessionManager(this)

        initViews()
        setupListeners()
        cargarDatosHeader()
        cargarMetricasGlobales()
        verificarEstadoSuspendido()
    }

    private fun initViews() {
        ivAvatar = findViewById(R.id.ivAvatar)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvRoleSubtitle = findViewById(R.id.tvRoleSubtitle)
        btnLogout = findViewById(R.id.btnLogout)
        headerProfileClick = findViewById(R.id.headerProfileClick)

        tvTotalNegocios = findViewById(R.id.tvTotalNegocios)
        tvTotalUsuarios = findViewById(R.id.tvTotalUsuarios)
        tvIvaVigente = findViewById(R.id.tvIvaVigente)

        cardNegocios = findViewById(R.id.cardNegocios)
        cardUsuarios = findViewById(R.id.cardUsuarios)
        cardConfiguracionIva = findViewById(R.id.cardConfiguracionIva)
        cardParroquias = findViewById(R.id.cardParroquias)
    }

    private fun setupListeners() {
        btnLogout.setOnClickListener { confirmarCerrarSesion() }

        headerProfileClick.setOnClickListener {
            startActivity(Intent(this, Perfil::class.java))
        }

        cardNegocios.setOnClickListener {
            startActivity(Intent(this, AdminNegociosActivity::class.java))
        }
        cardUsuarios.setOnClickListener {
            val intent = Intent(this, AdminUsuariosActivity::class.java)
            startActivity(intent)
        }
        cardConfiguracionIva.setOnClickListener {
            startActivity(Intent(this, AdminIvaActivity::class.java))
        }

        cardParroquias.setOnClickListener {
            val intent = Intent(this, AdminParroquiasActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cargarDatosHeader() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful && response.body() != null) {
                    val usuario = response.body()!!
                    val nombre = usuario.primerNombre?.takeIf { it.isNotBlank() } ?: "Administrador"
                    tvWelcome.text = "Bienvenido, $nombre 👋"
                    tvRoleSubtitle.text = "Central de Control General"

                    if (!usuario.fotoPerfil.isNullOrBlank()) {
                        Glide.with(this@AdminActivity)
                            .load(usuario.fotoPerfil)
                            .circleCrop()
                            .placeholder(R.drawable.bg_avatar_circulo)
                            .error(R.drawable.bg_avatar_circulo)
                            .into(ivAvatar)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun verificarEstadoSuspendido() {
        val authHeader = sessionManager.getAuthHeader() ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getMiPerfil(authHeader)
                if (response.isSuccessful && response.body()?.suspendido == true) {
                    sessionManager.clearSession()
                    val intent = Intent(this@AdminActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (_: Exception) {}
        }
    }

    private fun cargarMetricasGlobales() {
        val authHeader = sessionManager.getAuthHeader() ?: return

        lifecycleScope.launch {
            try {
                val respNegocios = RetrofitClient.apiService.getAllNegocios(authHeader)
                if (respNegocios.isSuccessful) {
                    val total = respNegocios.body()?.size ?: 0
                    tvTotalNegocios.text = "$total registrados"
                }
            } catch (e: Exception) {
                tvTotalNegocios.text = "-- registrados"
            }

            try {
                val respUsuarios = RetrofitClient.apiService.getAllUsuarios(authHeader)
                if (respUsuarios.isSuccessful) {
                    val total = respUsuarios.body()?.size ?: 0
                    tvTotalUsuarios.text = "$total activos"
                }
            } catch (e: Exception) {
                tvTotalUsuarios.text = "-- activos"
            }

            try {
                val respIva = RetrofitClient.apiService.getIva(authHeader)
                if (respIva.isSuccessful) {
                    val ivaTexto = respIva.body()?.get("ivaActual")
                    val ivaDecimal = ivaTexto?.toDoubleOrNull() ?: 0.15
                    val porcentaje = (ivaDecimal * 100).toInt()
                    tvIvaVigente.text = "$porcentaje%"
                }
            } catch (e: Exception) {
                tvIvaVigente.text = "--%"
            }
        }
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro de que quieres cerrar sesión?")
            .setPositiveButton("Cerrar Sesión") { d, _ ->
                d.dismiss()
                sessionManager.logout()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }
}