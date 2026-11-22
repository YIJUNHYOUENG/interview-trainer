package com.s2023531050.interviewtrainer.concept

import android.content.Intent
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
 * 개념 목록에서 들어왔을 때만 같은 카테고리 안의 이전/다음 개념으로 넘어갈 수 있다(오답노트·퀴즈 결과·리포트에서
 * 들어온 경우에는 목록 순서와 상관이 없으므로 이 버튼을 숨긴다). "이 개념 퀴즈 풀기"로 그 개념의 퀴즈를 바로 푼다.
 * 숙련도는 퀴즈로만 바뀌고, 여기서는 읽은 시각만 기록한다.
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

    /** 개념 목록에서 들어왔는가: true 일 때만 이전/다음 개념 버튼을 보인다 */
    private val showNeighbors by lazy { intent.getBooleanExtra(EXTRA_SHOW_NEIGHBORS, false) }

    /** 퀴즈를 풀고 돌아오면 숙련도·복습일이 바뀌어 있으므로 다시 읽어 보여 준다. */
    override fun onResume() {
        super.onResume()
        val id = currentId ?: return
        if (::data.isInitialized) data.get(id)?.let { bindProgress(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, currentId)
    }

    private fun show(concept: Concept) {
        currentId = concept.id
        findViewById<MaterialToolbar>(R.id.toolbar).title = concept.title
        findViewById<Chip>(R.id.chipCategory).text = concept.categoryName
        bindProgress(concept)
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

        // 이 개념의 퀴즈를 바로 푼다. 끝나면 이 화면으로 돌아와 숙련도가 바뀐 것을 본다.
        findViewById<MaterialButton>(R.id.btnConceptQuiz).apply {
            text = "이 개념 퀴즈 풀기 (${concept.quizzes.size}문제)"
            setOnClickListener {
                startActivity(
                    Intent(this@ConceptDetailActivity, QuizActivity::class.java)
                        .putExtra(QuizActivity.EXTRA_CONCEPT_ID, concept.id)
                )
            }
        }

        findViewById<View>(R.id.rowNav).visibility = if (showNeighbors) View.VISIBLE else View.GONE
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

    /**
     * 별·Lv·다음 복습일을 채운다. 개념을 처음 열면 학습 시각만 기록하고(퀴즈를 풀기 전에는 숙련도가 없다),
     * 퀴즈를 푼 개념은 숙련도와 복습일을 보여 준다.
     */
    private fun bindProgress(concept: Concept) {
        findViewById<TextView>(R.id.tvStars).text = "☆☆☆☆☆"
        findViewById<TextView>(R.id.tvLevelInfo).text = "미학습"
        lifecycleScope.launch {
            val repo = LearningRepository(this@ConceptDetailActivity, data)
            repo.markStudied(concept.id)
            val p = repo.progressMap()[concept.id]
            if (currentId != concept.id || p == null) return@launch
            if (p.level > 0) {
                findViewById<TextView>(R.id.tvStars).text = "★".repeat(p.level) + "☆".repeat(5 - p.level)
                val done = if (LearningRules.isCompleted(p.correctMask, concept.quizzes.size)) " · ✓ 이수 완료" else ""
                findViewById<TextView>(R.id.tvLevelInfo).text =
                    "Lv.${p.level} · 다음 복습 ${LearningRules.reviewLabel(p.nextReviewAt, System.currentTimeMillis()).removePrefix("복습 ")}$done"
            } else {
                findViewById<TextView>(R.id.tvLevelInfo).text = "읽음 · 퀴즈 전"
            }
        }
    }

    private fun isOpen(card: MaterialCardView) = card.findViewById<View>(R.id.cardAnswer).visibility == View.VISIBLE

    companion object {
        const val EXTRA_CONCEPT_ID = "conceptId"

        /** true 면 이전/다음 개념 버튼을 보인다. 개념 목록에서 열 때만 true 로 준다. */
        const val EXTRA_SHOW_NEIGHBORS = "showNeighbors"
        private const val STATE_ID = "currentId"
    }
}
