package com.example.movildilo.ui.propietario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.LoteResponseDto
import com.example.movildilo.ui.adapters.LotesAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LotesBottomSheetDialog(
    private val productoNombre: String,
    private val listaLotes: List<LoteResponseDto>,
    private val isLoading: Boolean
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_lotes_producto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitulo: TextView = view.findViewById(R.id.tvTituloLotes)
        val btnCerrar: ImageButton = view.findViewById(R.id.btnCerrarLotes)
        val progress: ProgressBar = view.findViewById(R.id.progressLotes)
        val tvSinLotes: TextView = view.findViewById(R.id.tvSinLotes)
        val rvLotes: RecyclerView = view.findViewById(R.id.rvLotes)

        tvTitulo.text = "Lotes: $productoNombre"
        btnCerrar.setOnClickListener { dismiss() }

        if (isLoading) {
            progress.visibility = View.VISIBLE
            rvLotes.visibility = View.GONE
            tvSinLotes.visibility = View.GONE
        } else {
            progress.visibility = View.GONE
            if (listaLotes.isEmpty()) {
                tvSinLotes.visibility = View.VISIBLE
                rvLotes.visibility = View.GONE
            } else {
                tvSinLotes.visibility = View.GONE
                rvLotes.visibility = View.VISIBLE
                rvLotes.layoutManager = LinearLayoutManager(requireContext())
                rvLotes.adapter = LotesAdapter(listaLotes)
            }
        }
    }
}