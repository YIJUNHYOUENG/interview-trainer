package com.s2023531050.interviewtrainer.interview

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.AppDatabase
import com.s2023531050.interviewtrainer.db.InterviewRecord
import kotlinx.coroutines.launch

/** 10. AI 모의 면접 - 히스토리. 저장된 면접 기록을 최신순으로 보여 주고, 종류별로 걸러 볼 수 있다. */
class HistoryActivity : BaseActivity(R.layout.activity_history) {

    private lateinit var adapter: HistoryAdapter
    private var all: List<InterviewRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = HistoryAdapter(
            onClick = { record ->
                startActivity(
                    Intent(this, ReportActivity::class.java).putExtra(ReportActivity.EXTRA_RECORD_ID, record.id)
                )
            },
            onLongClick = ::confirmDelete,
        )
        findViewById<RecyclerView>(R.id.rvHistory).adapter = adapter
        findViewById<ChipGroup>(R.id.chips).setOnCheckedStateChangeListener { _, _ -> render() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            all = AppDatabase.get(this@HistoryActivity).interviewRecords().all()
            render()
        }
    }

    private fun render() {
        val mode = when (findViewById<ChipGroup>(R.id.chips).checkedChipId) {
            R.id.chipCs -> "cs"
            R.id.chipCover -> "cover"
            R.id.chipMixed -> "mixed"
            else -> null
        }
        val shown = if (mode == null) all else all.filter { it.mode == mode }
        adapter.submit(shown)

        val empty = findViewById<View>(R.id.tvEmpty)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        (empty as android.widget.TextView).text =
            if (all.isEmpty()) "아직 면접 기록이 없어요.\n면접을 끝내면 여기에 쌓입니다." else "이 종류의 면접 기록이 없어요."
    }

    private fun confirmDelete(record: InterviewRecord) {
        AlertDialog.Builder(this)
            .setTitle("기록을 삭제할까요?")
            .setMessage("${record.title} · ${record.meta()}\n삭제하면 되돌릴 수 없어요.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.get(this@HistoryActivity).interviewRecords().delete(record.id)
                    all = all.filter { it.id != record.id }
                    render()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
