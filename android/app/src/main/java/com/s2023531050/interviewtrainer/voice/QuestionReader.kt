package com.s2023531050.interviewtrainer.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 면접관 질문을 한국어 음성으로 읽어 준다 (Android TextToSpeech).
 * 한국어 음성 데이터가 없으면 [available] 이 false 가 되고 [speak] 는 아무것도 하지 않는다.
 * 화면이 끝날 때 [shutdown] 을 꼭 부른다.
 */
class QuestionReader(context: Context, private val onUnavailable: () -> Unit) {
    private var ready = false
    private val pending = ArrayDeque<String>()

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS && setKorean()) {
            ready = true
            while (pending.isNotEmpty()) speak(pending.removeFirst())
        } else {
            pending.clear()
            onUnavailable()
        }
    }

    val available get() = ready

    private fun setKorean(): Boolean {
        val result = tts.setLanguage(Locale.KOREAN)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** 이전에 읽던 것은 멈추고 새 질문을 읽는다. 엔진이 아직 준비 중이면 준비되는 대로 읽는다. */
    fun speak(text: String) {
        if (!ready) {
            pending.clear()
            pending.addLast(text)
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "question")
    }

    fun stop() {
        pending.clear()
        if (ready) tts.stop()
    }

    fun shutdown() {
        pending.clear()
        tts.stop()
        tts.shutdown()
    }
}
