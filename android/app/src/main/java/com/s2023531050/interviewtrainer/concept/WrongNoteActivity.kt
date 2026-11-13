package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.AppDatabase
import com.s2023531050.interviewtrainer.db.WrongAnswer
import kotlinx.coroutines.launch

/**
 * 5. CS 개념 학습 - 오답노트. "풀어야 할 것 / 해결함" 탭과 카테고리 칩으로 걸러 보고,
 * 걸러진 오답 중 최대 10문제를 "오답 다시 풀기"로 푼다. 퀴즈를 풀고 돌아오면 목록을 다시 읽는다.
 */
class WrongNoteActivity : BaseActivity(R.layout.activity_wrong_note) {

    private lateinit var adapter: WrongAdapter
    private lateinit var data: ConceptData
    private var unresolved: List<WrongAnswer> = emptyList()
    private var resolved: List<WrongAnswer> = emptyList()
    private var chipsBuilt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = WrongAdapter { concept ->
            startActivity(
                Intent(this, ConceptDetailActivity::class.java)
                    .putExtra(ConceptDetailActivity.EXTRA_CONCEPT_ID, concept.id)
            )
        }
        findViewById<RecyclerView>(R.id.rvWrong).adapter = adapter

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = render()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        findViewById<View>(R.id.btnQuizEmpty).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { retry() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            data = ConceptRepository.load(this@WrongNoteActivity)
            val dao = AppDatabase.get(this@WrongNoteActivity).wrongAnswers()
            unresolved = dao.list(false)
            resolved = dao.list(true)
            if (!chipsBuilt) buildChips()
            render()
        }
    }

    private fun buildChips() {
        val group = findViewById<ChipGroup>(R.id.chips)
        group.removeAllViews()
        fun add(id: Int, text: String, checked: Boolean = false) {
            val chip = layoutInflater.inflate(R.layout.chip_filter, group, false) as Chip
            chip.id = id
            chip.text = text
            chip.isChecked = checked
            group.addView(chip)
        }
        add(CHIP_ALL, "전체", checked = true)
        data.categories.forEachIndexed { i, c -> add(CHIP_BASE + i, c.name) }
        group.setOnCheckedStateChangeListener { _, _ -> render() }
        chipsBuilt = true
    }

    /** 지금 탭과 카테고리 칩에 걸린 오답 */
    private fun filtered(): List<WrongRow> {
        val tab = findViewById<TabLayout>(R.id.tabs).selectedTabPosition
        val category = data.categories.getOrNull(findViewById<ChipGroup>(R.id.chips).checkedChipId - CHIP_BASE)
        return (if (tab == 0) unresolved else resolved).mapNotNull { w ->
            val concept = data.get(w.conceptId) ?: return@mapNotNull null
            val quiz = concept.quizzes.getOrNull(w.quizIndex) ?: return@mapNotNull null
            if (category != null && concept.categoryId != category.id) null else WrongRow(w, concept, quiz)
        }
    }

    private fun render() {
        if (!::data.isInitialized) return
        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.getTabAt(0)?.text = "풀어야 할 것 ${unresolved.size}"
        tabs.getTabAt(1)?.text = "해결함 ${resolved.size}"

        val rows = filtered()
        adapter.submit(rows)

        val empty = rows.isEmpty()
        findViewById<RecyclerView>(R.id.rvWrong).visibility = if (empty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.llEmpty).visibility = if (empty) View.VISIBLE else View.GONE
        val onUnresolvedTab = tabs.selectedTabPosition == 0
        findViewById<TextView>(R.id.tvEmpty).text = when {
            onUnresolvedTab && unresolved.isEmpty() -> "오답이 없어요.\n퀴즈를 풀어 볼까요?"
            onUnresolvedTab -> "이 카테고리에는 풀어야 할 오답이 없어요"
            resolved.isEmpty() -> "아직 해결한 오답이 없어요"
            else -> "이 카테고리에는 해결한 오답이 없어요"
        }
        // 오답이 하나도 없을 때만 "퀴즈 풀러 가기" 버튼을 보인다
        findViewById<View>(R.id.btnQuizEmpty).visibility =
            if (onUnresolvedTab && unresolved.isEmpty()) View.VISIBLE else View.GONE

        findViewById<MaterialButton>(R.id.btnRetry).apply {
            visibility = if (empty) View.GONE else View.VISIBLE
            text = "오답 다시 풀기 (${minOf(rows.size, RETRY_MAX)})"
        }
    }

    /** 걸러진 오답 중 앞에서부터 최대 10문제를 퀴즈 화면으로 푼다. */
    private fun retry() {
        val ids = filtered().take(RETRY_MAX).map { "${it.wrong.conceptId}:${it.wrong.quizIndex}" }
        if (ids.isEmpty()) return
        startActivity(Intent(this, QuizActivity::class.java).putStringArrayListExtra(QuizActivity.EXTRA_RETRY, ArrayList(ids)))
    }

    private companion object {
        const val CHIP_ALL = 1000
        const val CHIP_BASE = 2000
        const val RETRY_MAX = 10
    }
}
