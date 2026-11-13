package com.s2023531050.interviewtrainer.concept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.s2023531050.interviewtrainer.R

/** 목록 한 줄. 숙련도·복습 정보는 학습 진도(Room)가 붙으면 채운다. 지금은 모두 "아직 안 봄". */
data class ConceptRow(
    val concept: Concept,
    val level: Int = 0,               // 0 = 미학습, 1~5
    val review: String = "아직 안 봄",  // "복습 오늘", "3일 뒤", "아직 안 봄"
    val reviewDue: Boolean = false,
)

class ConceptAdapter(
    private val onClick: (Concept) -> Unit,
) : RecyclerView.Adapter<ConceptAdapter.ViewHolder>() {

    private var items: List<ConceptRow> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val review: TextView = view.findViewById(R.id.tvReview)
        val summary: TextView = view.findViewById(R.id.tvSummary)
        val stars: TextView = view.findViewById(R.id.tvStars)
        val level: TextView = view.findViewById(R.id.tvLevel)
        val progress: LinearProgressIndicator = view.findViewById(R.id.progress)
    }

    fun submit(list: List<ConceptRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_concept, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = items[position]
        val context = holder.itemView.context

        holder.title.text = row.concept.title
        holder.summary.text = row.concept.summary
        holder.stars.text = "★".repeat(row.level) + "☆".repeat(5 - row.level)
        holder.level.text = if (row.level == 0) "미학습" else "Lv.${row.level}"
        holder.progress.progress = row.level

        holder.review.text = row.review
        holder.review.setBackgroundResource(if (row.reviewDue) R.drawable.bg_chip_bad else 0)
        holder.review.setTextColor(
            ContextCompat.getColor(context, if (row.reviewDue) R.color.bad else R.color.mut)
        )

        holder.itemView.setOnClickListener { onClick(row.concept) }
    }
}
