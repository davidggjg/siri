package com.groqvoice.assistant.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.groqvoice.assistant.R
import com.groqvoice.assistant.data.ChatMessage

class ChatAdapter(private val messages: MutableList<ChatMessage> = mutableListOf()) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    inner class VH(val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
        val tv: TextView = layout.getChildAt(0) as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 15f
            setPadding(28, 18, 28, 18)
            maxWidth = (parent.width * 0.82).toInt()
            setLineSpacing(4f, 1f)
        }
        val ll = LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 4, 0, 4) }
            addView(tv)
        }
        return VH(ll)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        val isUser = msg.role == "user"
        holder.tv.text = msg.content

        if (isUser) {
            holder.tv.setBackgroundResource(R.drawable.bg_bubble_user)
            holder.tv.setTextColor(0xFFFFFFFF.toInt())
            holder.layout.gravity = Gravity.END
        } else {
            holder.tv.setBackgroundResource(R.drawable.bg_bubble_assistant)
            holder.tv.setTextColor(0xFFFFFFFF.toInt())
            holder.layout.gravity = Gravity.START
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }
}
