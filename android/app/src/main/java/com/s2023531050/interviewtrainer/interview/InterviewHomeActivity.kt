package com.s2023531050.interviewtrainer.interview

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.s2023531050.interviewtrainer.net.ApiException
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.AppDatabase
import kotlinx.coroutines.launch

/** 7. AI 모의 면접 - 홈 */
class InterviewHomeActivity : BaseActivity(R.layout.activity_interview_home) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        open(R.id.cardInfo, InterviewSettingsActivity::class.java)
        findViewById<View>(R.id.btnStart).setOnClickListener { startOrAskAboutAnalysis() }
        open(R.id.rowSettings, InterviewSettingsActivity::class.java)
        open(R.id.rowHistory, HistoryActivity::class.java)
    }

    /** 가장 최근 면접을 "최근 리포트" 카드에 보여 준다. 기록이 없으면 카드를 숨긴다. */
    override fun onResume() {
        super.onResume()
        renderGithub()
        val card = findViewById<View>(R.id.cardRecent)
        val title = findViewById<View>(R.id.tvRecentTitle)
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@InterviewHomeActivity).interviewRecords()
            findViewById<TextView>(R.id.tvHistoryCount).text = "${dao.count()}회 ›"
            val record = dao.latest()
            val visibility = if (record == null) View.GONE else View.VISIBLE
            card.visibility = visibility
            title.visibility = visibility
            if (record == null) return@launch

            findViewById<TextView>(R.id.tvRecentScore).text = record.overallScore.toString()
            findViewById<TextView>(R.id.tvRecentTitle2).text = record.title
            findViewById<TextView>(R.id.tvRecentMeta).text = record.meta()
            card.setOnClickListener {
                startActivity(
                    Intent(this@InterviewHomeActivity, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_RECORD_ID, record.id)
                )
            }
        }
    }

    /** 지원 정보 카드의 GitHub 줄. 분석 중이면 서버에 한 번 물어 최신 상태로 바꾼다. */
    private fun renderGithub() {
        val settings = InterviewSettings(this)
        val view = findViewById<TextView>(R.id.tvGithubStatus)
        fun bind() {
            view.text = RepoAnalysis.summaryLine(settings)
            view.setTextColor(
                getColor(
                    when (settings.repoState) {
                        "done" -> R.color.ok
                        "failed" -> R.color.bad
                        "running" -> R.color.acc
                        else -> R.color.mut
                    }
                )
            )
        }
        bind()
        if (settings.repoState == "running") {
            lifecycleScope.launch {
                runCatching { RepoAnalysis.refresh(settings) }
                bind()
            }
        }
    }

    /** 분석이 아직 끝나지 않았으면 기다릴지 분석 없이 시작할지 묻는다. */
    private fun startOrAskAboutAnalysis() {
        val settings = InterviewSettings(this)
        if (settings.repoState != "running") {
            startActivity(Intent(this, InterviewActivity::class.java))
            return
        }
        lifecycleScope.launch {
            runCatching { RepoAnalysis.refresh(settings) } // 그새 끝났을 수 있다
            renderGithub()
            if (settings.repoState != "running") {
                startActivity(Intent(this@InterviewHomeActivity, InterviewActivity::class.java))
                return@launch
            }
            AlertDialog.Builder(this@InterviewHomeActivity)
                .setTitle("분석이 끝나지 않았어요")
                .setMessage("GitHub 분석이 아직 진행 중이에요. 기다릴까요, 분석 없이 시작할까요?")
                .setPositiveButton("기다리기", null)
                .setNegativeButton("분석 없이 시작") { _, _ ->
                    // 이번 면접은 분석 없이 간다(설정의 분석 작업은 그대로 둔다).
                    startActivity(
                        Intent(this@InterviewHomeActivity, InterviewActivity::class.java)
                            .putExtra(InterviewActivity.EXTRA_SKIP_REPO, true)
                    )
                }
                .show()
        }
    }
}
