package com.example.movildilo.ui.auth

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton

class TerminosActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_terminos)

        val btnRegresar: MaterialButton? = findViewById(R.id.btnRegresar)
        btnRegresar?.setOnClickListener {
            finish()
        }

        val btnEntendido: MaterialButton? = findViewById(R.id.btnEntendido)
        btnEntendido?.setOnClickListener {
            finish()
        }
    }
}