package com.example.movildilo.ui.propietario

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.api.RetrofitClient
import com.example.movildilo.data.local.SessionManager
import com.example.movildilo.data.model.dto.inventario.LoteResponseDto
import com.example.movildilo.ui.adapters.KardexAdapter
import com.example.movildilo.ui.adapters.LotesAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LotesBottomSheetDialog(
    private val productoNombre: String,
    private val listaLotes: List<LoteResponseDto>,
    private val isLoading: Boolean,
    private val negocioId: Long = -1L,
    private val productoId: Long = -1L
) : BottomSheetDialogFragment() {

    private var kardexYaCargado = false

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

        val tabLayout: TabLayout = view.findViewById(R.id.tabLayoutLotesKardex)
        val layoutContenidoLotes: View = view.findViewById(R.id.layoutContenidoLotes)
        val layoutContenidoKardex: View = view.findViewById(R.id.layoutContenidoKardex)
        val progressKardex: ProgressBar = view.findViewById(R.id.progressKardexProducto)
        val tvSinKardex: TextView = view.findViewById(R.id.tvSinKardexProducto)
        val rvKardex: RecyclerView = view.findViewById(R.id.rvKardexProducto)

        tvTitulo.text = "Detalle: $productoNombre"
        btnCerrar.setOnClickListener { dismiss() }

        // Antes se descartaban lotes sin código Y sin cantidad > 0, lo que podía
        // ocultar lotes reales (agotados, o sin código de lote) que el backend
        // sí devolvió. Ahora confiamos en que si el backend mandó un id, es un
        // registro real.
        val lotesReales = listaLotes.filter { it.id != null }

        if (isLoading) {
            progress.visibility = View.VISIBLE
            rvLotes.visibility = View.GONE
            tvSinLotes.visibility = View.GONE
        } else {
            progress.visibility = View.GONE
            if (lotesReales.isEmpty()) {
                tvSinLotes.visibility = View.VISIBLE
                rvLotes.visibility = View.GONE
            } else {
                tvSinLotes.visibility = View.GONE
                rvLotes.visibility = View.VISIBLE
                rvLotes.layoutManager = LinearLayoutManager(requireContext())
                rvLotes.adapter = LotesAdapter(lotesReales)
            }
        }

        rvKardex.layoutManager = LinearLayoutManager(requireContext())
        val kardexAdapter = KardexAdapter(mutableListOf())
        rvKardex.adapter = kardexAdapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        layoutContenidoLotes.visibility = View.VISIBLE
                        layoutContenidoKardex.visibility = View.GONE
                    }
                    1 -> {
                        layoutContenidoLotes.visibility = View.GONE
                        layoutContenidoKardex.visibility = View.VISIBLE
                        if (!kardexYaCargado) {
                            cargarKardexDelProducto(progressKardex, tvSinKardex, rvKardex, kardexAdapter)
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun cargarKardexDelProducto(
        progressKardex: ProgressBar,
        tvSinKardex: TextView,
        rvKardex: RecyclerView,
        kardexAdapter: KardexAdapter
    ) {
        val sessionManager = SessionManager(requireContext())
        val authHeader = sessionManager.getAuthHeader()

        if (authHeader.isNullOrEmpty() || negocioId <= 0L || productoId <= 0L) {
            tvSinKardex.text = "No se pudo cargar el kardex de este producto."
            tvSinKardex.visibility = View.VISIBLE
            return
        }

        progressKardex.visibility = View.VISIBLE
        tvSinKardex.visibility = View.GONE
        rvKardex.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getKardexPorProducto(
                    token = authHeader,
                    negocioId = negocioId,
                    productoId = productoId
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressKardex.visibility = View.GONE
                    kardexYaCargado = true

                    if (response.isSuccessful) {
                        val movimientos = response.body() ?: emptyList()
                        if (movimientos.isEmpty()) {
                            tvSinKardex.visibility = View.VISIBLE
                            rvKardex.visibility = View.GONE
                        } else {
                            tvSinKardex.visibility = View.GONE
                            rvKardex.visibility = View.VISIBLE
                            kardexAdapter.actualizarLista(movimientos)
                        }
                    } else {
                        tvSinKardex.text = "Error al consultar el kardex (${response.code()})."
                        tvSinKardex.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    progressKardex.visibility = View.GONE
                    tvSinKardex.text = "Error de red al consultar el kardex."
                    tvSinKardex.visibility = View.VISIBLE
                }
            }
        }
    }
}