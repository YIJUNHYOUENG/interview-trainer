package com.s2023531050.interviewtrainer.concept

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.WrongAnswer

/** 오답노트 한 줄: 저장된 오답 + 그 문제가 들어 있는 개념과 퀴즈 */
data class WrongRow(val wrong: WrongAnswer, val concept: Concept, val quiz: Quiz)

class WrongAdapter(
    private val onConceptClick: (Concept) -> Unit,
) : RecyclerView.Adapter<WrongAdapter.ViewHolder>() {

    private var items: List<WrongRow> = emptyList()
    private val expanded = HashSet<Long>() // 해설을 펼친 오답의 id

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

    fun submit(list: List<WrongRow>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wrong, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = items[position]
        val quiz = row.quiz

        holder.category.text = "${row.concept.categoryName} · ${row.concept.title}"
        holder.count.text = "${row.wrong.wrongCount}회 틀림"
        holder.question.text = quiz.question
        holder.myAnswer.text = "내 답  ${quiz.options.getOrNull(row.wrong.chosen).orEmpty()}"
        holder.correct.text = "정답  ${quiz.options[quiz.answer]}"

        fun bindExpanded() {
            val open = row.wrong.id in expanded
            holder.explanationCard.visibility = if (open) View.VISIBLE else View.GONE
            holder.explanation.text = quiz.explanation
            holder.toggle.text = if (open) "해설 접기 ⌃" else "해설 보기 ⌄"
        }
        bindExpanded()
        holder.toggle.setOnClickListener {
            if (!expanded.remove(row.wrong.id)) expanded.add(row.wrong.id)
            bindExpanded()
        }
        holder.goConcept.setOnClickListener { onConceptClick(row.concept) }
    }
}
