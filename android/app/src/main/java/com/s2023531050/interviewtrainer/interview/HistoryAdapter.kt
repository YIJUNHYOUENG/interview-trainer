package com.s2023531050.interviewtrainer.interview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.HistoryItem
import com.s2023531050.interviewtrainer.R

class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onClick: (HistoryItem) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val score: TextView = view.findViewById(R.id.tvScore)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val meta: TextView = view.findViewById(R.id.tvMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.score.text = item.score.toString()
        holder.title.text = item.title
        holder.meta.text = item.meta
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
