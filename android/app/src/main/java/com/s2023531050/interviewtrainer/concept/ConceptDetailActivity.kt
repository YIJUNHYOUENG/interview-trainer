package com.s2023531050.interviewtrainer.concept

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import kotlinx.coroutines.launch

/**
 * 3. CS 개념 학습 - 상세. 요약, 키워드, 설명, 면접 단골 질문(누르면 모범 답안)을 보여 주고,
 * 같은 카테고리 안에서 이전/다음 개념으로 넘어간다. 숙련도는 퀴즈로만 바뀌고, 여기서는 읽은 시각만 기록한다.
 */
class ConceptDetailActivity : BaseActivity(R.layout.activity_concept_detail) {

    private lateinit var data: ConceptData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            data = ConceptRepository.load(this@ConceptDetailActivity)
            val id = savedInstanceState?.getString(STATE_ID) ?: intent.getStringExtra(EXTRA_CONCEPT_ID)
            // id 없이 열리는 경로(리포트의 예시 행 등)는 첫 개념을 보여 준다.
            val concept = id?.let { data.get(it) } ?: data.all.firstOrNull()
            if (concept != null) show(concept)
        }
    }

    private var currentId: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, currentId)
    }

    private fun show(concept: Concept) {
        currentId = concept.id
        findViewById<MaterialToolbar>(R.id.toolbar).title = concept.title
        findViewById<Chip>(R.id.chipCategory).text = concept.categoryName
        // 개념을 처음 열면 학습 시각만 기록한다. 숙련도·복습일은 퀴즈 결과를 읽어서 보여 준다.
        findViewById<TextView>(R.id.tvStars).text = "☆☆☆☆☆"
        findViewById<TextView>(R.id.tvLevelInfo).text = "미학습"
        lifecycleScope.launch {
            val repo = LearningRepository(this@ConceptDetailActivity, data)
            repo.markStudied(concept.id)
            val p = repo.progressMap()[concept.id]
            if (currentId == concept.id && p != null && p.level > 0) {
                findViewById<TextView>(R.id.tvStars).text = "★".repeat(p.level) + "☆".repeat(5 - p.level)
                findViewById<TextView>(R.id.tvLevelInfo).text =
                    "Lv.${p.level} · 다음 복습 ${LearningRules.reviewLabel(p.nextReviewAt, System.currentTimeMillis()).removePrefix("복습 ")}"
            }
        }
        findViewById<TextView>(R.id.tvSummary).text = concept.summary
        findViewById<TextView>(R.id.tvExplanation).text = concept.explanation

        val keywords = findViewById<ChipGroup>(R.id.chipKeywords)
        keywords.removeAllViews()
        concept.keywords.forEach { word ->
            val chip = layoutInflater.inflate(R.layout.chip_keyword, keywords, false) as Chip
            chip.text = word
            keywords.addView(chip)
        }

        // 단골 질문: 처음에는 첫 질문만 펼치고, 하나를 펼치면 나머지는 접는다.
        val box = findViewById<LinearLayout>(R.id.llQa)
        box.removeAllViews()
        val cards = concept.interviewQA.mapIndexed { i, qa ->
            val card = layoutInflater.inflate(R.layout.item_concept_qa, box, false) as MaterialCardView
            card.findViewById<TextView>(R.id.tvQuestion).text = "Q${i + 1}. ${qa.question}"
            card.findViewById<TextView>(R.id.tvAnswer).text = qa.answer
            box.addView(card)
            card
        }
        fun open(target: MaterialCardView?) {
            cards.forEach { c ->
                val open = c === target
                c.findViewById<View>(R.id.cardAnswer).visibility = if (open) View.VISIBLE else View.GONE
                c.findViewById<TextView>(R.id.tvArrow).text = if (open) "⌃" else "⌄"
            }
        }
        cards.forEach { c -> c.setOnClickListener { open(if (isOpen(c)) null else c) } }
        open(cards.firstOrNull())

        val prev = data.neighbor(concept, -1)
        val next = data.neighbor(concept, 1)
        findViewById<MaterialButton>(R.id.btnPrev).apply {
            isEnabled = prev != null
            setOnClickListener { prev?.let { show(it) } }
        }
        findViewById<MaterialButton>(R.id.btnNext).apply {
            isEnabled = next != null
            setOnClickListener { next?.let { show(it) } }
        }
        findViewById<ScrollView>(R.id.scroll).scrollTo(0, 0)
    }

    private fun isOpen(card: MaterialCardView) = card.findViewById<View>(R.id.cardAnswer).visibility == View.VISIBLE

    companion object {
        const val EXTRA_CONCEPT_ID = "conceptId"
        private const val STATE_ID = "currentId"
    }
}
