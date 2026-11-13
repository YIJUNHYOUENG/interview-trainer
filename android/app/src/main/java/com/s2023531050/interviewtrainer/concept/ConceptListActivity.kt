package com.s2023531050.interviewtrainer.concept

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import kotlinx.coroutines.launch

/** 2. CS 개념 학습 - 목록. 개념 데이터(assets/concepts.json)를 카테고리 칩으로 걸러 보여 준다. */
class ConceptListActivity : BaseActivity(R.layout.activity_concept_list) {

    private lateinit var adapter: ConceptAdapter
    private lateinit var data: ConceptData

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

        // 정렬과 "오늘 복습"은 학습 진도가 붙은 뒤에 쓴다.
        findViewById<View>(R.id.tvSort).visibility = View.GONE

        lifecycleScope.launch {
            data = ConceptRepository.load(this@ConceptListActivity)
            buildChips()
        }
    }

    /** "전체" + 카테고리 칩을 데이터에서 만든다. 처음에는 "전체". */
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
        data.categories.forEachIndexed { i, c -> add(CHIP_CATEGORY_BASE + i, c.name) }
        group.setOnCheckedStateChangeListener { _, _ -> render() }
        render()
    }

    private fun render() {
        val checked = findViewById<ChipGroup>(R.id.chips).checkedChipId
        val index = checked - CHIP_CATEGORY_BASE
        val category = data.categories.getOrNull(index)
        val concepts = category?.concepts ?: data.all
        adapter.submit(concepts.map { ConceptRow(it) })
        findViewById<TextView>(R.id.tvCount).text = "${category?.name ?: "전체"} ${concepts.size}"
    }

    private companion object {
        const val CHIP_ALL = 1000
        const val CHIP_CATEGORY_BASE = 2000
    }
}
