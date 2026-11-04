package com.s2023531050.interviewtrainer.concept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.s2023531050.interviewtrainer.ConceptItem
import com.s2023531050.interviewtrainer.R

class ConceptAdapter(
    private val items: List<ConceptItem>,
    private val onClick: (ConceptItem) -> Unit,
) : RecyclerView.Adapter<ConceptAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val review: TextView = view.findViewById(R.id.tvReview)
        val summary: TextView = view.findViewById(R.id.tvSummary)
        val stars: TextView = view.findViewById(R.id.tvStars)
        val level: TextView = view.findViewById(R.id.tvLevel)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_concept, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.title.text = item.title
        holder.summary.text = item.summary
        holder.stars.text = "★".repeat(item.level) + "☆".repeat(5 - item.level)
        holder.level.text = if (item.level == 0) "미학습" else "Lv.${item.level}"
        holder.progress.progress = item.level

        holder.review.text = item.review
        holder.review.setBackgroundResource(if (item.reviewDue) R.drawable.bg_chip_bad else 0)
        holder.review.setTextColor(
            ContextCompat.getColor(context, if (item.reviewDue) R.color.bad else R.color.mut)
        )

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
