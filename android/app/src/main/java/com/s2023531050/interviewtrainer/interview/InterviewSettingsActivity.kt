package com.s2023531050.interviewtrainer.interview

import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 6. AI 모의 면접 - 설정. 저장하면 다음 면접부터 이 값으로 시작한다. */
class InterviewSettingsActivity : BaseActivity(R.layout.activity_interview_settings) {
    private var count = 5
    private var followups = 2

    private lateinit var settings: InterviewSettings
    private lateinit var etGithub: TextInputEditText
    private var pollJob: Job? = null
    private var pendingAction: (() -> Unit)? = null

    // Android 17(API 37)부터는 PC 서버 같은 로컬 네트워크에 붙으려면 사용자 허용이 필요하다.
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingAction?.let {
            pendingAction = null
            it()
        }
    }

    private val pickResume = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadResume(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = InterviewSettings(this)
        etGithub = findViewById(R.id.etGithub)
        val etCompany = findViewById<TextInputEditText>(R.id.etCompany)
        val etRole = findViewById<TextInputEditText>(R.id.etRole)
        val etResume = findViewById<TextInputEditText>(R.id.etResume)
        val toggleType = findViewById<MaterialButtonToggleGroup>(R.id.toggleType)
        val toggleMode = findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)
        val tvCount = findViewById<TextView>(R.id.tvCount)
        val tvFollow = findViewById<TextView>(R.id.tvFollow)

        // 저장된 값 불러오기
        etCompany.setText(settings.company)
        etRole.setText(settings.role)
        etResume.setText(settings.resume)
        findViewById<TextInputEditText>(R.id.etJob).setText(settings.jobPosting)
        findViewById<TextInputEditText>(R.id.etExtra).setText(settings.extraRequest)
        findViewById<View>(R.id.btnPickResume).setOnClickListener {
            pickResume.launch(arrayOf("application/pdf", "text/plain"))
        }
        findViewById<Chip>(R.id.chipResume).setOnCloseIconClickListener {
            settings.resumeFileName = ""
            settings.resumeFileText = ""
            renderResume()
        }
        renderResume()
        toggleType.check(
            when (settings.type) {
                "cs" -> R.id.btnTypeCs
                "cover" -> R.id.btnTypeCover
                else -> R.id.btnTypeMixed
            }
        )
        toggleMode.check(if (settings.answerMode == "voice") R.id.btnModeVoice else R.id.btnModeText)
        // GitHub: 분석한 주소를 보여 주고, 주소를 고치면 이전 분석은 버린다.
        etGithub.setText(settings.githubUrl)
        etGithub.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                findViewById<TextInputLayout>(R.id.tilGithub).error = null
                if (settings.repoState.isNotEmpty() && RepoAnalysis.normalize(s.toString()) != settings.githubUrl) {
                    pollJob?.cancel()
                    settings.clearRepo()
                    renderRepo()
                }
            }
        })
        findViewById<View>(R.id.btnAnalyze).setOnClickListener { withNetwork { analyze() } }
        renderRepo()
        if (settings.repoState == "running") startPolling() // 나갔다 돌아와도 이어서 확인한다
        count = settings.questionCount
        followups = settings.maxFollowups
        tvCount.text = count.toString()
        tvFollow.text = followups.toString()

        // 주제 수 1~10, 꼬리질문 0~3
        findViewById<View>(R.id.btnCountMinus).setOnClickListener {
            count = (count - 1).coerceAtLeast(1); tvCount.text = count.toString()
        }
        findViewById<View>(R.id.btnCountPlus).setOnClickListener {
            count = (count + 1).coerceAtMost(10); tvCount.text = count.toString()
        }
        findViewById<View>(R.id.btnFollowMinus).setOnClickListener {
            followups = (followups - 1).coerceAtLeast(0); tvFollow.text = followups.toString()
        }
        findViewById<View>(R.id.btnFollowPlus).setOnClickListener {
            followups = (followups + 1).coerceAtMost(3); tvFollow.text = followups.toString()
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            settings.company = etCompany.text.toString().trim()
            settings.role = etRole.text.toString().trim()
            settings.resume = etResume.text.toString().trim()
            settings.jobPosting = findViewById<TextInputEditText>(R.id.etJob).text.toString().trim()
            settings.extraRequest = findViewById<TextInputEditText>(R.id.etExtra).text.toString().trim()
            if (etGithub.text.isNullOrBlank()) settings.clearRepo() // 주소를 지웠으면 분석도 지운다
            settings.type = when (toggleType.checkedButtonId) {
                R.id.btnTypeCs -> "cs"
                R.id.btnTypeCover -> "cover"
                else -> "mixed"
            }
            settings.answerMode = if (toggleMode.checkedButtonId == R.id.btnModeVoice) "voice" else "text"
            settings.questionCount = count
            settings.maxFollowups = followups
            if (settings.type != "cs" && !settings.hasApplicationInfo) {
                Toast.makeText(this, "입력한 정보가 없어 CS 질문 위주로 진행돼요", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    // ---------- GitHub 분석 ----------

    /** PC 서버를 부르는 일은 로컬 네트워크 권한(Android 17+)을 받은 뒤에 한다. */
    private fun withNetwork(action: () -> Unit) {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val needed = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        if (needed) {
            pendingAction = action
            localNetworkPermission.launch(permission)
        } else {
            action()
        }
    }

    private fun analyze() {
        val til = findViewById<TextInputLayout>(R.id.tilGithub)
        val url = RepoAnalysis.normalize(etGithub.text.toString())
        if (url == null) {
            til.error = "https://github.com/{사용자}/{레포} 형식으로 입력해 주세요"
            return
        }
        til.error = null
        pollJob?.cancel()
        lifecycleScope.launch {
            try {
                RepoAnalysis.start(settings, url)
                renderRepo()
                if (settings.repoState == "running") startPolling()
            } catch (e: ApiException) {
                til.error = e.message // 서버가 거절한 이유(형식, 없는 레포, 비공개 등)를 그대로 보여 준다
            }
        }
    }

    /** 3초마다 진행 상황을 묻는다. 끝나거나 화면을 나가면 멈춘다(최대 약 6분). */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            var tries = 0
            while (isActive && tries++ < 120) {
                try {
                    if (RepoAnalysis.refresh(settings) != "running") break
                } catch (e: ApiException) {
                    // 잠깐 연결이 끊겨도 계속 기다린다
                }
                renderRepo()
                delay(3000)
            }
            if (settings.repoState == "running" && isActive) {
                settings.repoState = "failed"
                settings.repoError = "분석이 너무 오래 걸려요. 다시 시도해 주세요."
            }
            renderRepo()
        }
    }

    private fun renderRepo() {
        val card = findViewById<MaterialCardView>(R.id.cardRepo)
        val title = findViewById<TextView>(R.id.tvRepoTitle)
        val sub = findViewById<TextView>(R.id.tvRepoSub)
        val button = findViewById<View>(R.id.btnAnalyze)
        val state = settings.repoState
        card.visibility = if (state.isEmpty()) View.GONE else View.VISIBLE
        button.isEnabled = state != "running"

        fun paint(bg: Int, line: Int, text: Int) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, bg))
            card.strokeColor = ContextCompat.getColor(this, line)
            title.setTextColor(ContextCompat.getColor(this, text))
        }
        when (state) {
            "running" -> {
                paint(R.color.accbg, R.color.accline, R.color.acc)
                title.text = "분석 중… 몇 분 걸릴 수 있어요"
                sub.text = "다른 화면에 갔다 와도 이어서 확인해요"
            }
            "done" -> {
                paint(R.color.okbg, R.color.okline, R.color.ok)
                title.text = RepoAnalysis.summaryLine(settings)
                sub.text = "예상 프로젝트 질문 ${settings.repoQuestionCount}개"
            }
            "failed" -> {
                paint(R.color.badbg, R.color.badline, R.color.bad)
                title.text = "✗ 분석 실패"
                sub.text = settings.repoError
            }
        }
    }

    // ---------- 이력서·포트폴리오 파일 ----------

    private fun renderResume() {
        val chip = findViewById<Chip>(R.id.chipResume)
        val has = settings.resumeFileName.isNotEmpty()
        chip.visibility = if (has) View.VISIBLE else View.GONE
        chip.text = settings.resumeFileName
    }

    private fun loadResume(uri: Uri) {
        val name = displayName(uri)
        val isPdf = contentResolver.getType(uri) == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        val isTxt = contentResolver.getType(uri) == "text/plain" || name.endsWith(".txt", ignoreCase = true)
        if (!isPdf && !isTxt) {
            Toast.makeText(this, "PDF 또는 TXT 파일만 고를 수 있어요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPdf) withNetwork { readResume(uri, name, true) } else readResume(uri, name, false)
    }

    private fun readResume(uri: Uri, name: String, pdf: Boolean) {
        val button = findViewById<MaterialButton>(R.id.btnPickResume)
        button.isEnabled = false
        button.text = "읽는 중…"
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw ApiException("파일을 열 수 없어요.")
                if (bytes.size > MAX_RESUME_BYTES) throw ApiException("파일이 너무 커요. 5MB 이하만 올릴 수 있어요.")
                val text = if (pdf) {
                    ApiClient.postBytes("/resume/parse", bytes, "application/pdf").getString("text") // PDF 글자는 서버가 뽑는다
                } else {
                    decodeText(bytes)
                }
                if (text.isBlank()) throw ApiException("파일에 읽을 수 있는 글자가 없어요.")
                settings.resumeFileName = name
                settings.resumeFileText = text.trim().take(InterviewSettings.MAX_RESUME)
                renderResume()
            } catch (e: ApiException) {
                Toast.makeText(this@InterviewSettingsActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Toast.makeText(this@InterviewSettingsActivity, "파일을 읽지 못했어요.", Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                Toast.makeText(this@InterviewSettingsActivity, "파일을 열 권한이 없어요.", Toast.LENGTH_LONG).show()
            } finally {
                button.isEnabled = true
                button.text = "파일 선택 (PDF·TXT)"
            }
        }
    }

    /** TXT 는 UTF-8 로 읽고, 아니면 한글 윈도우 메모장 기본(CP949)으로 읽는다. */
    private fun decodeText(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            String(body, Charset.forName("x-windows-949"))
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "resume"

    private companion object {
        const val MAX_RESUME_BYTES = 5 * 1024 * 1024
    }
}
