package com.example.movildilo.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R

class NegociosAdapter : RecyclerView.Adapter<NegociosAdapter.NegocioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NegocioViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_negocio, parent, false)
        return NegocioViewHolder(view)
    }

    override fun onBindViewHolder(holder: NegocioViewHolder, position: Int) {

    }

    override fun getItemCount(): Int {
        return 0
    }

    class NegocioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }
}