package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.ConceptProgress
import kotlinx.coroutines.launch

/**
 * 2. CS 개념 학습 - 목록. 개념 데이터를 카테고리 칩("오늘 복습" 포함)으로 걸러 보여 주고,
 * 숙련도·복습일(학습 진도)을 함께 보여 준다. 화면이 다시 보일 때마다 진도를 다시 읽는다.
 */
class ConceptListActivity : BaseActivity(R.layout.activity_concept_list) {

    private enum class Sort(val label: String) {
        DEFAULT("기본순"), LEVEL("숙련도 낮은 순"), REVIEW("복습일 빠른 순")
    }

    private lateinit var adapter: ConceptAdapter
    private lateinit var data: ConceptData
    private var progress: Map<String, ConceptProgress> = emptyMap()
    private var dueCount = 0
    private var sort = Sort.DEFAULT
    private var chipsBuilt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.btnQuiz, QuizActivity::class.java)
        open(R.id.btnWrong, WrongNoteActivity::class.java)

        adapter = ConceptAdapter { concept ->
            startActivity(
                Intent(this, ConceptDetailActivity::class.java)
                    .putExtra(ConceptDetailActivity.EXTRA_CONCEPT_ID, concept.id)
            )
        }
        findViewById<RecyclerView>(R.id.rvConcepts).adapter = adapter

        findViewById<TextView>(R.id.tvSort).setOnClickListener { view ->
            PopupMenu(this, view).apply {
                Sort.values().forEachIndexed { i, s -> menu.add(0, i, i, s.label) }
                setOnMenuItemClickListener { item ->
                    sort = Sort.values()[item.itemId]
                    render()
                    true
                }
            }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            data = ConceptRepository.load(this@ConceptListActivity)
            val repo = LearningRepository(this@ConceptListActivity, data)
            progress = repo.progressMap()
            val stats = repo.stats()
            dueCount = stats.dueCount

            findViewById<MaterialButton>(R.id.btnQuiz).text =
                if (stats.dailyCompleted) "오늘의 퀴즈 ✓" else "오늘의 퀴즈 (${LearningRules.DAILY_COUNT})"
            findViewById<MaterialButton>(R.id.btnWrong).text = "오답노트 (${stats.wrongUnresolved})"

            if (!chipsBuilt) buildChips()
            findViewById<Chip>(CHIP_DUE).text = "오늘 복습 $dueCount"
            render()
        }
    }

    /** "전체", "오늘 복습 N", 10개 카테고리 칩을 만든다. 처음에는 "전체"(홈의 "오늘 복습"에서 오면 그 칩). */
    private fun buildChips() {
        val group = findViewById<ChipGroup>(R.id.chips)
        group.removeAllViews()
        val startDue = intent.getBooleanExtra(EXTRA_DUE_ONLY, false)
        fun add(id: Int, text: String, checked: Boolean = false) {
            val chip = layoutInflater.inflate(R.layout.chip_filter, group, false) as Chip
            chip.id = id
            chip.text = text
            chip.isChecked = checked
            group.addView(chip)
        }
        add(CHIP_ALL, "전체", checked = !startDue)
        add(CHIP_DUE, "오늘 복습", checked = startDue)
        data.categories.forEachIndexed { i, c -> add(CHIP_CATEGORY_BASE + i, c.name) }
        group.setOnCheckedStateChangeListener { _, _ -> render() }
        chipsBuilt = true
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val checked = findViewById<ChipGroup>(R.id.chips).checkedChipId
        val category = data.categories.getOrNull(checked - CHIP_CATEGORY_BASE)

        var rows = (category?.concepts ?: data.all).map { c ->
            val p = progress[c.id]
            ConceptRow(
                c, p?.level ?: 0,
                LearningRules.reviewLabel(p?.nextReviewAt, now),
                LearningRules.isDue(p?.nextReviewAt, now),
            )
        }
        val title = when {
            checked == CHIP_DUE -> {
                rows = rows.filter { it.reviewDue }
                "오늘 복습"
            }
            else -> category?.name ?: "전체"
        }
        rows = when (sort) {
            Sort.DEFAULT -> rows
            Sort.LEVEL -> rows.sortedBy { it.level }
            Sort.REVIEW -> rows.sortedBy { progress[it.concept.id]?.nextReviewAt ?: Long.MAX_VALUE }
        }

        adapter.submit(rows)
        findViewById<TextView>(R.id.tvCount).text = "$title ${rows.size}"
        findViewById<TextView>(R.id.tvSort).text = "정렬: ${sort.label} ▾"
        findViewById<TextView>(R.id.tvEmpty).apply {
            visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            text = "오늘 복습할 개념이 없어요"
        }
    }

    companion object {
        /** 홈의 "오늘 복습"에서 열 때 true: "오늘 복습" 칩이 선택된 채로 시작한다. */
        const val EXTRA_DUE_ONLY = "dueOnly"
        private const val CHIP_ALL = 1000
        private const val CHIP_DUE = 1001
        private const val CHIP_CATEGORY_BASE = 2000
    }
}
