package com.example.movildilo.ui.auth

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.movildilo.R
import com.google.android.material.button.MaterialButton

class CuentaSuspendidaDialog(private val context: Context) {

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_cuenta_suspendida, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<MaterialButton>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}