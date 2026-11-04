package com.s2023531050.interviewtrainer.concept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.WrongItem

class WrongAdapter(
    private val items: List<WrongItem>,
    private val onConceptClick: (WrongItem) -> Unit,
) : RecyclerView.Adapter<WrongAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val category: TextView = view.findViewById(R.id.tvCategory)
        val count: TextView = view.findViewById(R.id.tvCount)
        val question: TextView = view.findViewById(R.id.tvQuestion)
        val myAnswer: TextView = view.findViewById(R.id.tvMyAnswer)
        val correct: TextView = view.findViewById(R.id.tvCorrect)
        val explanationCard: View = view.findViewById(R.id.cardExplanation)
        val explanation: TextView = view.findViewById(R.id.tvExplanation)
        val toggle: TextView = view.findViewById(R.id.tvToggle)
        val goConcept: TextView = view.findViewById(R.id.tvGoConcept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wrong, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.category.text = item.category
        holder.count.text = "${item.wrongCount}회 틀림"
        holder.question.text = item.question
        holder.myAnswer.text = "내 답  ${item.myAnswer}"
        holder.correct.text = "정답  ${item.correct}"

        val expanded = item.explanation != null
        holder.explanationCard.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.explanation.text = item.explanation.orEmpty()
        holder.toggle.text = if (expanded) "해설 접기 ⌃" else "해설 보기 ⌄"

        holder.goConcept.setOnClickListener { onConceptClick(item) }
    }
}
