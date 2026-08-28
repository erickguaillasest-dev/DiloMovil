package com.example.movildilo.ui.adapters

import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movildilo.R
import com.example.movildilo.data.model.dto.ia.ChatItem

class ChatAdapter(
    private val listaMensajes: MutableList<ChatItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMensajeUser: TextView = view.findViewById(R.id.tvMensajeUser)
    }

    class BotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMensajeBot: TextView = view.findViewById(R.id.tvMensajeBot)
    }

    override fun getItemViewType(position: Int): Int {
        return if (listaMensajes[position].role == "user") {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_BOT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_bot, parent, false)
            BotViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = listaMensajes[position]
        val textoFormateado = parseMarkdown(item.text)

        if (holder is UserViewHolder) {
            holder.tvMensajeUser.text = textoFormateado
        } else if (holder is BotViewHolder) {
            holder.tvMensajeBot.text = textoFormateado
        }
    }

    override fun getItemCount(): Int = listaMensajes.size

    fun agregarMensaje(item: ChatItem) {
        listaMensajes.add(item)
        notifyItemInserted(listaMensajes.size - 1)
    }

    private fun parseMarkdown(text: String): Spanned {
        val htmlFormatted = text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            .replace("\n", "<br/>")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlFormatted, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlFormatted)
        }
    }
}