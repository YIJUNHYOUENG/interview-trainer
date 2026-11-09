package com.s2023531050.interviewtrainer.interview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.InterviewRecord

class HistoryAdapter(
    private val onClick: (InterviewRecord) -> Unit,
    private val onLongClick: (InterviewRecord) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items: List<InterviewRecord> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val score: TextView = view.findViewById(R.id.tvScore)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val meta: TextView = view.findViewById(R.id.tvMeta)
    }

    fun submit(list: List<InterviewRecord>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.score.text = item.overallScore.toString()
        holder.title.text = item.title
        holder.meta.text = item.meta()
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item); true }
    }
}
