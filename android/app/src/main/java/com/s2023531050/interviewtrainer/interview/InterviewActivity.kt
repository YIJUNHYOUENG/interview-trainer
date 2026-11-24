package com.s2023531050.interviewtrainer.interview

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.s2023531050.interviewtrainer.BaseActivity
import com.s2023531050.interviewtrainer.R
import com.s2023531050.interviewtrainer.db.AppDatabase
import com.s2023531050.interviewtrainer.db.InterviewRecord
import com.s2023531050.interviewtrainer.net.ApiClient
import com.s2023531050.interviewtrainer.net.ApiException
import com.s2023531050.interviewtrainer.voice.QuestionReader
import com.s2023531050.interviewtrainer.voice.WavRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 8. AI 모의 면접 - 진행 (텍스트 답변).
 * 시작 → 질문 표시 → 답변 전송 → 꼬리질문/다음 주제 → 끝나면 리포트.
 * 질문 수와 꼬리질문 횟수는 서버가 센다. 앱은 서버가 알려 준 대로 화면만 바꾼다.
 * 설정이 "음성"이면 질문을 소리로 읽어 주고, "탭해서 말하기"로 녹음한다(탭하면 시작, 한 번 더 탭하면 끝).
 * 서버가 글자로 바꾼 결과는 사용자가 확인·수정한 뒤 보낸다.
 * 음성 모드에서도 "글자로 답하기"로 키보드 입력을 쓸 수 있다.
 */
class InterviewActivity : BaseActivity(R.layout.activity_interview) {

    private lateinit var settings: InterviewSettings
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var etAnswer: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnEnd: MaterialButton
    private lateinit var tvProgress: TextView
    private lateinit var tvFollow: TextView

    private lateinit var rowText: View
    private lateinit var rowVoice: View
    private lateinit var btnRecord: MaterialButton
    private lateinit var tvVoiceHint: TextView
    private lateinit var btnToVoice: View
    private lateinit var btnRecordCancel: View
    private lateinit var rowReview: View
    private lateinit var etRecognized: EditText
    private var timerJob: Job? = null

    private val voiceMode by lazy { settings.answerMode == "voice" }
    private var usedVoice = false // 음성으로 한 번이라도 답했는지 (기록에 남긴다)
    private var reader: QuestionReader? = null
    private val recorder = WavRecorder()

    private var sessionId: String? = null
    private var total = 0
    private var answered = 0
    private var busy = false
    private var interviewEnded = false // 서버가 type = end 를 줬다(이제 리포트만 남음)
    private var thinking: View? = null

    // Android 17(API 37)부터는 PC 서버 같은 로컬 네트워크에 붙으려면 사용자 허용이 필요하다.
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { afterNetworkPermission() }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "마이크 권한이 없어 글자로 답해야 해요.", Toast.LENGTH_LONG).show()
            showVoiceInput(false)
        }
        startInterview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = InterviewSettings(this)
        chat = findViewById(R.id.chat)
        scroll = findViewById(R.id.scroll)
        etAnswer = findViewById(R.id.etAnswer)
        btnSend = findViewById(R.id.btnSend)
        btnEnd = findViewById(R.id.btnEnd)
        tvProgress = findViewById(R.id.tvProgress)
        tvFollow = findViewById(R.id.tvFollow)

        rowText = findViewById(R.id.rowText)
        rowVoice = findViewById(R.id.rowVoice)
        btnRecord = findViewById(R.id.btnRecord)
        tvVoiceHint = findViewById(R.id.tvVoiceHint)
        btnToVoice = findViewById(R.id.btnToVoice)
        btnRecordCancel = findViewById(R.id.btnRecordCancel)
        rowReview = findViewById(R.id.rowReview)
        etRecognized = findViewById(R.id.etRecognized)
        findViewById<View>(R.id.btnConfirm).setOnClickListener { sendRecognized() }
        findViewById<View>(R.id.btnRedo).setOnClickListener { hideReview() }

        btnSend.setOnClickListener { sendAnswer() }
        btnEnd.setOnClickListener { confirmExit() }

        // 면접 중에는 화면이 꺼지지 않게 한다. 꺼지면 onStop 에서 녹음이 취소돼 길게 말한 답변이 사라진다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 뒤로가기도 [종료] 와 같은 확인을 연다. 그냥 닫으면 진행 중인 면접이 모두 사라진다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        if (voiceMode) {
            reader = QuestionReader(this) {
                Toast.makeText(this, "질문을 읽어 줄 수 없어요. (한국어 음성 데이터가 없음) 글자로 확인해 주세요.", Toast.LENGTH_LONG).show()
            }
            showVoiceInput(true)
            setupRecordButton()
            btnToKeyboard().setOnClickListener { showVoiceInput(false) }
            btnToVoice.setOnClickListener { showVoiceInput(true) }
        }

        if (savedInstanceState == null) requestPermissionThenStart()
    }

    private fun requestPermissionThenStart() {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val needed = Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        if (needed) localNetworkPermission.launch(permission) else afterNetworkPermission()
    }

    /** 음성 모드면 마이크 권한도 받는다. 거절하면 글자 입력으로 진행한다. */
    private fun afterNetworkPermission() {
        val mic = android.Manifest.permission.RECORD_AUDIO
        if (voiceMode && ContextCompat.checkSelfPermission(this, mic) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(mic)
        } else {
            startInterview()
        }
    }

    // ---------- 서버 호출 ----------

    private fun startInterview() {
        val body = JSONObject()
            .put("mode", settings.type)
            .put("company", settings.company)
            .put("role", settings.role)
            .put("coverLetter", settings.effectiveResume) // 직접 입력 > 파일에서 읽은 글자
            .put("jobPosting", settings.jobPosting)
            .put("extraRequest", settings.extraRequest)
            .put("questionCount", settings.questionCount)
            .put("maxFollowups", settings.maxFollowups)
        // 분석이 끝난 GitHub 프로젝트가 있으면 서버가 그 내용으로 프로젝트 질문을 섞는다.
        if (settings.repoState == "done" && settings.repoJobId.isNotEmpty() && !intent.getBooleanExtra(EXTRA_SKIP_REPO, false)) {
            body.put("repoAnalysisId", settings.repoJobId)
        }

        call(
            request = { ApiClient.post("/interview/start", body) },
            onError = { msg -> showError(msg, retry = ::startInterview, closeOnCancel = true) },
        ) { r ->
            sessionId = r.getString("sessionId")
            total = r.getInt("total")
            updateHeader(1, r.getString("topic"), 0)
            addQuestion(r.getString("question"))
            setInputEnabled(true)
        }
    }

    private fun sendAnswer() {
        val id = sessionId ?: return
        val text = etAnswer.text.toString().trim()
        if (text.isEmpty() || busy) return

        etAnswer.text.clear()
        val bubble = addMine(text)
        // turn: 서버가 같은 답변을 두 번 처리하지 않게, 지금까지 결과를 받은 답변 수를 함께 보낸다.
        val body = JSONObject().put("sessionId", id).put("answer", text).put("turn", answered)
        answered++

        call(
            request = { ApiClient.post("/interview/answer", body) },
            onError = { msg ->
                answered--
                chat.removeView(bubble) // 다시 보낼 때 같은 말풍선이 두 번 생기지 않게
                etAnswer.setText(text) // 보낸 답변을 되살려서 다시 보낼 수 있게 한다
                showError(msg, retry = null, closeOnCancel = false)
            },
        ) { r -> onAnswerResult(r) }
    }

    /** 녹음(WAV)을 서버에 보내 글자로만 바꾼다. 결과는 바로 보내지 않고 사용자가 확인·수정하게 한다. */
    private fun transcribe(wav: ByteArray) {
        val id = sessionId ?: return
        if (busy) return
        call(
            // 긴 녹음(최대 2분 30초)은 인식에 1분 넘게 걸릴 수 있어 보통 요청보다 오래 기다린다.
            request = { ApiClient.postBytes("/voice/transcribe?sessionId=$id", wav, "audio/wav", TRANSCRIBE_TIMEOUT_MS) },
            onError = { msg ->
                AlertDialog.Builder(this)
                    .setTitle("문제가 생겼어요")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("다시 말하기") { _, _ -> setInputEnabled(true) }
                    .setNeutralButton("같은 녹음 다시 보내기") { _, _ -> transcribe(wav) }
                    .show()
            },
            thinkingText = "목소리를 글자로 바꾸는 중…",
        ) { r -> showReview(r.getString("text")) }
    }

    /** 인식한 글자를 고칠 수 있는 칸에 보여 준다. */
    private fun showReview(text: String) {
        rowVoice.visibility = View.GONE
        rowText.visibility = View.GONE
        btnToVoice.visibility = View.GONE
        rowReview.visibility = View.VISIBLE
        etRecognized.setText(text)
        etRecognized.setSelection(text.length)
        etRecognized.requestFocus()
    }

    /** "다시 말하기": 인식 결과를 버리고 녹음 화면으로 돌아간다. */
    private fun hideReview() {
        showVoiceInput(true)
        setInputEnabled(true)
    }

    /** 확인한(고친) 글자를 답변으로 보낸다. voice=true 라서 서버가 인식 때 계산해 둔 음성 지표를 이 답변에 붙인다. */
    private fun sendRecognized() {
        val id = sessionId ?: return
        val text = etRecognized.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "답변이 비어 있어요. 다시 말하거나 글자를 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (busy) return
        showVoiceInput(true) // 다음 질문을 읽어 주려면 음성 입력 상태로 먼저 돌려 둔다
        val body = JSONObject().put("sessionId", id).put("answer", text).put("voice", true).put("turn", answered)
        call(
            request = { ApiClient.post("/interview/answer", body) },
            onError = { msg ->
                AlertDialog.Builder(this)
                    .setTitle("문제가 생겼어요")
                    .setMessage(msg)
                    .setCancelable(false)
                    .setPositiveButton("확인") { _, _ -> showReview(text) } // 고친 글자를 잃지 않게 다시 보여 준다
                    .show()
            },
        ) { r ->
            usedVoice = true
            answered++
            addMine(text)
            onAnswerResult(r)
        }
    }

    /** /interview/answer 와 /voice/answer 가 같은 모양으로 돌려주는 결과를 화면에 반영한다. */
    private fun onAnswerResult(r: JSONObject) {
        addEval(r.getInt("score"), r.getString("comment"))
        val type = r.getString("type")
        if (type == "end") {
            interviewEnded = true
            addAi("면접이 끝났습니다. 결과 리포트를 만들고 있어요.")
            finishInterview()
        } else {
            updateHeader(r.getInt("topicNo"), r.getString("topic"), r.getInt("followups"))
            addQuestion(r.getString("question"))
            setInputEnabled(true)
        }
    }

    // ---------- 음성 입력 ----------

    private fun btnToKeyboard() = findViewById<View>(R.id.btnToKeyboard)

    /** true 면 "누르고 말하기", false 면 키보드 입력. 음성 모드가 아니면 늘 키보드다. */
    private fun showVoiceInput(voice: Boolean) {
        val useVoice = voice && voiceMode
        rowReview.visibility = View.GONE
        rowVoice.visibility = if (useVoice) View.VISIBLE else View.GONE
        rowText.visibility = if (useVoice) View.GONE else View.VISIBLE
        btnToVoice.visibility = if (!useVoice && voiceMode) View.VISIBLE else View.GONE
        if (!useVoice) reader?.stop()
    }

    private val voiceInputShown get() = rowVoice.visibility == View.VISIBLE

    /** 탭하면 녹음을 시작하고, 한 번 더 탭하면 끝내서 글자로 바꾼다(1~2분 답변을 계속 누르고 있을 필요가 없다). */
    private fun setupRecordButton() {
        btnRecord.setOnClickListener { if (recorder.isRecording) finishRecording() else startRecording() }
        btnRecordCancel.setOnClickListener { cancelRecording() }
    }

    private fun startRecording() {
        reader?.stop() // 질문 읽는 소리가 녹음에 섞이지 않게
        val started = recorder.start {
            // 최대 길이에 닿으면 자동으로 끝낸다(녹음 스레드에서 불리므로 화면 스레드로 옮긴다)
            runOnUiThread {
                if (recorder.isRecording) {
                    Toast.makeText(this, "최대 길이에 닿아 녹음을 끝냈어요.", Toast.LENGTH_SHORT).show()
                    finishRecording()
                }
            }
        }
        if (!started) {
            Toast.makeText(this, "마이크를 사용할 수 없어요.", Toast.LENGTH_SHORT).show()
            return
        }
        btnRecord.text = "녹음 끝내기"
        btnRecordCancel.visibility = View.VISIBLE
        val begin = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            val max = WavRecorder.MAX_SECONDS
            while (isActive) {
                val ms = SystemClock.elapsedRealtime() - begin
                val sec = (ms / 1000).toInt()
                // 에뮬레이터는 마이크가 열린 뒤 소리가 들어오기까지 2초쯤 걸려 앞부분이 잘린다. 잠깐 기다렸다 말하게 한다.
                tvVoiceHint.text = if (ms < MIC_WARMUP_MS) "마이크 준비 중… 잠시 후 말해 주세요"
                else "● 녹음 중 %d:%02d (최대 %d분 %d초)".format(sec / 60, sec % 60, max / 60, max % 60)
                delay(300)
            }
        }
    }

    private fun resetRecordUi() {
        timerJob?.cancel()
        btnRecord.text = "탭해서 말하기"
        btnRecordCancel.visibility = View.GONE
    }

    private var finishing = false

    /** 끝내기를 누른 뒤 잠깐 더 녹음해, 말끝("…습니다")이 잘리지 않게 한다. */
    private fun finishRecording() {
        if (finishing) return
        finishing = true
        btnRecord.isEnabled = false
        btnRecordCancel.visibility = View.GONE
        tvVoiceHint.text = "마무리 중…"
        lifecycleScope.launch {
            delay(RECORD_TAIL_MS)
            if (!recorder.isRecording) { // 그 사이 화면을 떠나 이미 취소됐다
                finishing = false
                btnRecord.isEnabled = true
                return@launch
            }
            val wav = recorder.stop()
            finishing = false
            btnRecord.isEnabled = true
            resetRecordUi()
            if (wav == null) {
                Toast.makeText(this@InterviewActivity, "너무 짧아요. 조금 더 길게 말해 주세요.", Toast.LENGTH_SHORT).show()
                tvVoiceHint.text = VOICE_HINT
                return@launch
            }
            tvVoiceHint.text = ""
            transcribe(wav)
        }
    }

    private fun cancelRecording() {
        recorder.cancel()
        resetRecordUi()
        tvVoiceHint.text = VOICE_HINT
    }

    /** 면접관 질문을 말풍선으로 보이고, 음성 모드에서 음성 입력 중이면 소리로도 읽어 준다. */
    private fun addQuestion(text: String) {
        addAi(text)
        if (voiceMode && voiceInputShown) reader?.speak(text)
    }

    override fun onStop() {
        super.onStop()
        if (recorder.isRecording) cancelRecording()
        reader?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder.cancel()
        reader?.shutdown()
    }

    /** [종료] 와 뒤로가기가 함께 쓰는 확인 창: 계속하기 / 종료하고 리포트 보기 / 기록 없이 나가기 (SCREENS 10장). */
    private fun confirmExit() {
        if (busy) {
            // 서버 응답을 기다리는 중. 나가면 이번 면접은 남지 않는다는 것을 알리고 고르게 한다.
            AlertDialog.Builder(this)
                .setTitle("응답을 기다리는 중이에요")
                .setMessage("지금 나가면 이번 면접 기록이 남지 않아요.")
                .setPositiveButton("계속 기다리기", null)
                .setNegativeButton("기록 없이 나가기") { _, _ -> finish() }
                .show()
            return
        }
        if (answered == 0) {
            Toast.makeText(this, "답변한 내용이 없어 리포트 없이 종료합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        reader?.stop() // 녹음 중이면 "계속하기" 를 고를 수 있으니 녹음은 그대로 둔다
        val builder = AlertDialog.Builder(this)
        if (interviewEnded) {
            // 면접은 끝났고 리포트 만들기만 실패한 상태
            builder.setTitle("리포트를 다시 만들까요?")
                .setMessage("지금 나가면 답변 기록이 남지 않아요.")
                .setPositiveButton("다시 만들기") { _, _ -> finishInterview() }
        } else {
            builder.setTitle("면접을 종료할까요?")
                .setMessage("지금까지 답변한 내용으로 리포트를 만듭니다.")
                .setPositiveButton("종료하고 리포트 보기") { _, _ ->
                    if (recorder.isRecording) cancelRecording()
                    finishInterview()
                }
        }
        builder.setNegativeButton("계속하기", null)
            .setNeutralButton("기록 없이 나가기") { _, _ -> confirmDiscard() }
            .show()
    }

    /** "기록 없이 나가기" 는 되돌릴 수 없으니 한 번 더 묻는다. */
    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("기록 없이 나갈까요?")
            .setMessage("지금까지의 질문과 답변이 저장되지 않고 사라져요.")
            .setPositiveButton("나가기") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun finishInterview() {
        val id = sessionId ?: return
        setInputEnabled(false)
        val body = JSONObject().put("sessionId", id)
        call(
            request = { ApiClient.post("/interview/finish", body) },
            onError = { msg -> showReportError(msg) },
        ) { report -> saveAndShowReport(report) }
    }

    /**
     * 리포트를 못 만들었을 때. 화면을 닫지 않아 답변이 그대로 남고, 바로 또는 나중에([종료]) 다시 만들 수 있다.
     * 서버가 세션을 들고 있는 동안(서버를 끄기 전까지)은 다시 시도하면 같은 답변으로 리포트를 만든다.
     */
    private fun showReportError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("리포트를 만들지 못했어요")
            .setMessage("$message\n\n답변은 아직 이 화면에 남아 있어요.")
            .setCancelable(false)
            .setPositiveButton("다시 시도") { _, _ -> finishInterview() }
            .setNegativeButton("나중에") { _, _ ->
                addAi("리포트를 만들지 못했어요. 위쪽 [종료] 를 누르면 다시 만들 수 있어요.")
                if (!interviewEnded) setInputEnabled(true) // 면접 중간에 종료했다면 이어서 답할 수도 있다
            }
            .setNeutralButton("기록 없이 나가기") { _, _ -> confirmDiscard() }
            .show()
    }

    /** 리포트를 기기에 저장하고(히스토리용) 리포트 화면을 연다. 저장이 실패해도 방금 리포트는 보여 준다. */
    private fun saveAndShowReport(report: JSONObject) {
        lifecycleScope.launch {
            val intent = Intent(this@InterviewActivity, ReportActivity::class.java)
            try {
                val title = listOf(settings.company, settings.role).filter { it.isNotBlank() }
                    .joinToString(" / ").ifEmpty { "${settings.typeLabel} 면접" }
                val id = AppDatabase.get(this@InterviewActivity).interviewRecords().insert(
                    InterviewRecord(
                        title = title,
                        mode = settings.type,
                        answerMode = if (usedVoice) "voice" else "text",
                        startedAt = System.currentTimeMillis(),
                        overallScore = report.getInt("overallScore"),
                        reportJson = report.toString(),
                    )
                )
                intent.putExtra(ReportActivity.EXTRA_RECORD_ID, id)
            } catch (e: Exception) {
                Toast.makeText(this@InterviewActivity, "기록을 저장하지 못했어요.", Toast.LENGTH_SHORT).show()
                intent.putExtra(ReportActivity.EXTRA_REPORT, report.toString())
                    .putExtra(ReportActivity.EXTRA_MODE_LABEL, settings.typeLabel)
            }
            startActivity(intent)
            finish()
        }
    }

    /** 서버 호출 한 번. 기다리는 동안 "생각 중" 표시를 보여 주고, 성공/실패를 나눠 처리한다. */
    private fun call(
        request: suspend () -> JSONObject,
        onError: (String) -> Unit,
        thinkingText: String = "면접관이 생각 중…",
        onSuccess: (JSONObject) -> Unit,
    ) {
        setBusy(true, thinkingText)
        lifecycleScope.launch {
            try {
                val result = request()
                setBusy(false)
                onSuccess(result)
            } catch (e: ApiException) {
                setBusy(false)
                onError(e.message.orEmpty())
            } catch (e: org.json.JSONException) {
                setBusy(false)
                onError("서버 응답을 읽지 못했습니다.")
            }
        }
    }

    // ---------- 화면 ----------

    private fun setBusy(value: Boolean, text: String = "면접관이 생각 중…") {
        busy = value
        btnEnd.isEnabled = !value
        if (value) {
            setInputEnabled(false)
            thinking = addBubble(R.layout.item_bubble_ai, text)
        } else {
            thinking?.let { chat.removeView(it) }
            thinking = null
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        etAnswer.isEnabled = enabled
        btnSend.isEnabled = enabled
        btnRecord.isEnabled = enabled
        if (voiceInputShown) tvVoiceHint.text = if (enabled) VOICE_HINT else ""
        if (enabled && !voiceInputShown) etAnswer.requestFocus()
    }

    private fun updateHeader(topicNo: Int, topic: String, followups: Int) {
        tvProgress.text = "주제 $topicNo/$total"
        tvFollow.text = "· $topic · 꼬리질문 $followups/${settings.maxFollowups}"
    }

    private fun addAi(text: String) = addBubble(R.layout.item_bubble_ai, text)

    private fun addMine(text: String) = addBubble(R.layout.item_bubble_me, text)

    private fun addEval(score: Int, comment: String) {
        val stars = "★".repeat(score.coerceIn(0, 5)) + "☆".repeat(5 - score.coerceIn(0, 5))
        addBubble(R.layout.item_bubble_eval, "$stars  $comment")
    }

    private fun addBubble(layout: Int, text: String): View {
        val view = LayoutInflater.from(this).inflate(layout, chat, false) as TextView
        view.text = text
        chat.addView(view)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return view
    }

    private fun showError(message: String, retry: (() -> Unit)?, closeOnCancel: Boolean) {
        val builder = AlertDialog.Builder(this)
            .setTitle("문제가 생겼어요")
            .setMessage(message)
            .setCancelable(false)
        if (retry != null) builder.setPositiveButton("다시 시도") { _, _ -> retry() }
        builder.setNegativeButton(if (closeOnCancel) "닫기" else "확인") { _, _ ->
            if (closeOnCancel) finish() else setInputEnabled(true)
        }
        builder.show()
    }

    companion object {
        /** 면접 홈에서 "분석 없이 시작"을 고르면 true. GitHub 분석 결과를 이번 면접에만 쓰지 않는다. */
        const val EXTRA_SKIP_REPO = "skipRepo"
        private const val MIC_WARMUP_MS = 2000L   // 녹음을 시작하고 이 시간 동안은 "준비 중"으로 알린다
        private const val RECORD_TAIL_MS = 700L   // 끝내기를 눌러도 이 시간만큼 더 녹음한다
        private const val TRANSCRIBE_TIMEOUT_MS = 180_000 // 음성 인식은 녹음 길이에 따라 오래 걸린다(서버 CPU 인식)
        private const val VOICE_HINT = "버튼을 누르면 녹음이 시작돼요. 말이 끝나면 한 번 더 누르세요"
    }
}
