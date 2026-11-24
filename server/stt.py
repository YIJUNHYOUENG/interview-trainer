"""음성 인식(faster-whisper)과 음성 지표 계산 (docs/DESIGN.md 4.3).

- 인식 모델은 PC 에서 직접 돌린다(내려받은 모델, API 아님). 처음 한 번만 불러온다.
- 지표(말 속도, 멈춤, 군말, 음량, 발음 명확도)는 여기서 계산하고, 판정(좋음/보통)은 앱이 기준값으로 한다.
- TRAINER_FAKE=1 이면 모델 없이 고정된 가짜 인식 결과를 돌려준다.
"""
import array
import math
import os
import re
import threading
import wave
from dataclasses import dataclass
from typing import List, Optional

from rules import AiError

MODEL_NAME = os.environ.get("TRAINER_STT_MODEL", "medium")   # 정확도 우선. 느리면 small 로 낮춘다
BEAM_SIZE = int(os.environ.get("TRAINER_STT_BEAM", "5"))      # 클수록 정확하고 조금 느리다(1 이 가장 빠름)
PAUSE_SECONDS = 1.0       # 단어 사이 이 시간 이상 비면 멈춤 1회
LOW_CONFIDENCE = 0.6      # 이보다 낮은 단어는 "알아듣기 어려웠던 단어"
SILENCE_DB = -50.0        # 이보다 작은 구간은 음량 계산에서 뺀다
CHUNK_SECONDS = 0.5
FILLERS = {"음", "으음", "어", "아", "그", "저", "약간", "뭐랄까", "그러니까", "그니까"}
TARGET_PEAK = 0.7         # 인식 전에 가장 큰 소리를 이 크기(최대 1)까지 키운다
MAX_GAIN = 20.0           # 아주 작은 녹음도 이 배율까지만 키운다(잡음만 커지지 않게)
# 인식 힌트로 주는 영어·기술 용어. 너무 많으면 말하지 않은 용어를 적는 일이 생겨 자주 나오는 것만 둔다.
TECH_TERMS = ("REST API, HTTP, HTTPS, TCP, UDP, IP, DNS, OS, CPU, DB, SQL, NoSQL, JOIN, 인덱스, B-Tree, 트랜잭션, "
              "프로세스, 스레드, 데드락, 컨텍스트 스위칭, 캐시, 해시, 스택, 큐, 힙, Java, Kotlin, Android, "
              "Activity, Fragment, ViewModel, Room, Spring, JWT, OAuth, Git, GitHub, MVVM, API")
# 무음·잡음 구간에서 Whisper 가 자주 지어내는 문장(유튜브 자막에서 배운 말버릇)
HALLUCINATIONS = ("감사합니다", "시청해 주셔서 감사합니다", "시청해주셔서 감사합니다", "구독과 좋아요",
                  "다음 영상에서 만나요", "MBC 뉴스", "자막 제공")
HANGUL = re.compile(r"[가-힣]")


@dataclass
class Word:
    text: str
    start: float
    end: float
    prob: float


@dataclass
class Transcript:
    text: str
    words: List[Word]
    duration: float  # 녹음 전체 길이(초)


def _as_samples(samples) -> array.array:
    """16bit 샘플 배열로 맞춘다. read_wav 가 이미 array('h') 를 주므로 보통은 그대로 쓴다.
    (예전에는 bytes(samples)[:len(samples)] 로 잘라서 샘플 수만큼의 "바이트", 즉 녹음 앞 절반만 보았다.)"""
    if isinstance(samples, array.array) and samples.typecode == "h":
        return samples
    raw = bytes(samples)
    a = array.array("h")
    a.frombytes(raw[: len(raw) // 2 * 2])
    return a


def level(samples) -> tuple:
    """녹음의 (최대 진폭 0~1, 평균 음량 dB). 마이크 입력이 제대로 들어왔는지 보는 데 쓴다."""
    a = _as_samples(samples)
    if not a:
        return 0.0, -120.0
    peak = max(max(a), -min(a)) / 32768
    rms = math.sqrt(sum(s * s for s in a) / len(a))
    return peak, (20 * math.log10(rms / 32768) if rms > 0 else -120.0)


def loudest_chunk_db(samples, rate: int) -> float:
    """0.5초 구간 중 가장 큰 구간의 음량(dB). 말소리가 한 번도 없었는지 보는 데 쓴다(클릭 소리 한 번은 구간 평균이 작다)."""
    a = _as_samples(samples)
    size = max(1, int(rate * CHUNK_SECONDS))
    best = -120.0
    for i in range(0, len(a), size):
        chunk = a[i:i + size]
        rms = math.sqrt(sum(s * s for s in chunk) / len(chunk))
        if rms > 0:
            best = max(best, 20 * math.log10(rms / 32768))
    return best


# ---------- 인식 ----------

_model = None
_lock = threading.Lock()


def _load_model():
    global _model
    with _lock:
        if _model is None:
            try:
                from faster_whisper import WhisperModel
            except ImportError:
                raise AiError(503, "음성 인식(faster-whisper)이 설치되지 않았습니다. PC 에서 pip install faster-whisper 를 실행해 주세요.")
            _model = WhisperModel(MODEL_NAME, device="cpu", compute_type="int8")
    return _model


def warm_up() -> None:
    """서버 시작 때 모델을 미리 불러 둔다(첫 답변이 느려지지 않게)."""
    _load_model()


def prepare_audio(samples, rate: int):
    """Whisper 에 넣을 float 배열(16kHz)을 만든다. 인식용으로만 쓰고, 음량 지표는 원래 녹음으로 계산한다.

    에뮬레이터 마이크(PC 마이크를 거쳐 들어옴)는 소리가 작게 들어오는 일이 많다. 작은 소리는 Whisper 앞단의
    음성 구간 검출(VAD)에서 말소리가 아닌 것으로 잘려 나가 "알아듣지 못했어요" 가 되거나 앞뒤가 빠진다.
    그래서 직류 성분을 빼고, 가장 큰 소리가 일정 크기가 되도록 키운다(너무 키워 잡음만 커지지 않게 최대 배율을 둔다).
    """
    import numpy as np

    audio = np.frombuffer(_as_samples(samples), dtype=np.int16).astype(np.float32) / 32768.0
    if rate != 16000:  # Whisper 는 16kHz 만 받는다
        n = int(len(audio) * 16000 / rate)
        audio = np.interp(np.linspace(0, len(audio) - 1, n), np.arange(len(audio)), audio).astype(np.float32)
    if len(audio) == 0:
        return audio
    audio = audio - float(audio.mean())
    # 클릭 한 번 같은 튀는 값에 맞추지 않도록 상위 0.1% 값을 "가장 큰 소리" 로 본다.
    peak = float(np.percentile(np.abs(audio), 99.9))
    if 0 < peak < TARGET_PEAK:
        audio = audio * min(TARGET_PEAK / peak, MAX_GAIN)
    return np.clip(audio, -1.0, 1.0).astype(np.float32)


def build_prompt(question: str = "") -> str:
    """Whisper 에 미리 보여 줄 글. 면접 질문과 자주 나오는 영어 기술 용어를 주면 "REST", "TCP" 같은 말을
    한글 소리대로("레스트") 적거나 엉뚱한 말로 바꾸는 일이 줄어든다. Whisper 는 이 글을 앞 대화로만 본다(지시가 아님)."""
    q = " ".join(question.split())[:200]
    head = f"개발자 기술 면접 답변입니다. 질문: {q}" if q else "개발자 기술 면접 답변입니다."
    return f"{head} 용어: {TECH_TERMS}."


def _is_noise_segment(seg) -> bool:
    """말소리가 없는 구간에서 Whisper 가 지어내는 문장("감사합니다", "시청해 주셔서…")을 걸러낸다."""
    text = seg.text.strip().rstrip(".!?~ ")
    if any(text == h or text.startswith(h) for h in HALLUCINATIONS) and seg.avg_logprob < -0.5:
        return True
    return seg.no_speech_prob > 0.6 and seg.avg_logprob < -1.0


def transcribe(samples, rate: int, question: str = "") -> Transcript:
    """16bit 샘플을 글자로 바꾼다. 파일을 다시 디코딩하지 않고 배열을 바로 넘긴다.
    question 은 지금 면접 질문이다(인식 힌트로만 쓴다)."""
    model = _load_model()
    audio = prepare_audio(samples, rate)
    duration = len(audio) / 16000
    try:
        segments, _ = model.transcribe(
            audio, language="ko", word_timestamps=True, beam_size=BEAM_SIZE,
            condition_on_previous_text=False,
            initial_prompt=build_prompt(question),
            vad_filter=True,
            # 기본값보다 말소리 판정을 조금 너그럽게, 말 사이 쉼(생각하는 시간)에서 끊기지 않게 한다.
            vad_parameters={"threshold": 0.35, "min_silence_duration_ms": 1000, "speech_pad_ms": 400},
        )
        words: List[Word] = []
        parts: List[str] = []
        for seg in segments:
            if _is_noise_segment(seg):
                continue
            parts.append(seg.text.strip())
            for w in seg.words or []:
                token = w.word.strip()
                if token:
                    words.append(Word(token, float(w.start), float(w.end), float(w.probability)))
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise AiError(500, f"음성 인식에 실패했습니다. ({type(e).__name__})")
    return Transcript(" ".join(p for p in parts if p).strip(), words, duration)


class FakeStt:
    """모델 없이 UI 를 시험하는 용도. 녹음 길이만 진짜로 읽는다."""
    SAMPLES = [
        "음 REST 는 자원을 URI 로 나타내고 HTTP 메서드로 동작을 표현하는 방식입니다. 상태를 서버에 두지 않아서 확장하기 쉽습니다.",
        "프로세스는 독립된 메모리 공간을 가지고 스레드는 같은 프로세스 안에서 메모리를 공유합니다. 그래서 스레드 사이 통신이 더 빠릅니다.",
        "인덱스는 검색 속도를 높이는 자료구조인데 쓰기 성능은 조금 떨어집니다. 그래서 자주 조회하는 컬럼에만 만듭니다.",
    ]

    def __init__(self):
        self.n = 0

    def transcribe(self, samples, rate: int) -> Transcript:
        duration = len(samples) / rate
        text = self.SAMPLES[self.n % len(self.SAMPLES)]
        self.n += 1
        words, t = [], 0.3
        for i, token in enumerate(text.split()):
            dur = 0.25 + 0.06 * len(token)
            words.append(Word(token, t, t + dur, 0.55 if i == 3 else 0.9))
            t += dur + (1.2 if i == 6 else 0.15)
        return Transcript(text, words, max(duration, t))


# ---------- 지표 ----------

def read_wav(path: str):
    """16bit 모노 WAV 를 (샘플 배열, 샘플레이트) 로 읽는다. 다른 형식이면 AiError."""
    try:
        with wave.open(path, "rb") as f:
            if f.getsampwidth() != 2:
                raise AiError(422, "지원하지 않는 녹음 형식입니다. (16bit WAV 만 가능)")
            rate, channels = f.getframerate(), f.getnchannels()
            raw = f.readframes(f.getnframes())
    except wave.Error:
        raise AiError(422, "녹음 파일을 읽지 못했습니다. WAV 형식이어야 합니다.")
    samples = array.array("h")
    samples.frombytes(raw)
    if channels > 1:
        samples = samples[::channels]
    return samples, rate


def volume_stats(samples, rate: int) -> dict:
    """구간별 음량(dB, 0 이 최대)의 평균과, 앞 1/3 대비 뒤 1/3 의 변화."""
    size = max(1, int(rate * CHUNK_SECONDS))
    levels = []
    for i in range(0, len(samples) - size + 1, size):
        chunk = samples[i:i + size]
        rms = math.sqrt(sum(s * s for s in chunk) / len(chunk))
        db = 20 * math.log10(rms / 32768) if rms > 0 else -120.0
        if db > SILENCE_DB:
            levels.append(db)
    if not levels:
        return {"volumeDb": None, "endDropDb": 0.0}
    third = max(1, len(levels) // 3)
    head = sum(levels[:third]) / third
    tail = sum(levels[-third:]) / third
    return {"volumeDb": round(sum(levels) / len(levels), 1), "endDropDb": round(tail - head, 1)}


def count_syllables(text: str) -> int:
    return len(HANGUL.findall(text))


def compute_metrics(tr: Transcript, samples=None, rate: int = 16000) -> dict:
    words = tr.words
    if words:
        speaking = max(0.5, words[-1].end - words[0].start)
    else:
        speaking = max(0.5, tr.duration)
    minutes = speaking / 60

    syllables = count_syllables(tr.text)
    pauses = sum(1 for a, b in zip(words, words[1:]) if b.start - a.end >= PAUSE_SECONDS)
    fillers = sum(1 for w in words if re.sub(r"[^가-힣]", "", w.text) in FILLERS)
    clarity = sum(w.prob for w in words) / len(words) if words else 0.0

    low = []
    for w in words:
        token = re.sub(r"[^\w가-힣]", "", w.text)
        if w.prob < LOW_CONFIDENCE and token and token not in FILLERS and token not in low:
            low.append(token)

    m = {
        "syllables": syllables,
        "seconds": round(speaking, 1),
        "spm": round(syllables / minutes),          # 분당 음절 수
        "pauses": pauses,
        "fillers": fillers,
        "fillersPerMin": round(fillers / minutes, 1),
        "clarity": round(clarity, 2),
        "lowWords": low[:5],
        "volumeDb": None,
        "endDropDb": 0.0,
    }
    if samples is not None:
        m.update(volume_stats(samples, rate))
    return m


def summarize(all_metrics: List[dict]) -> Optional[dict]:
    """답변별 지표를 면접 전체 요약 하나로 합친다."""
    ms = [m for m in all_metrics if m]
    if not ms:
        return None
    seconds = sum(m["seconds"] for m in ms)
    minutes = max(seconds, 0.5) / 60
    vols = [m["volumeDb"] for m in ms if m.get("volumeDb") is not None]
    low: List[str] = []
    for m in ms:
        for w in m.get("lowWords", []):
            if w not in low:
                low.append(w)
    return {
        "answers": len(ms),
        "spm": round(sum(m["syllables"] for m in ms) / minutes),
        "spmList": [m["spm"] for m in ms],           # 답변별 말 속도(그래프용)
        "pauses": sum(m["pauses"] for m in ms),
        "fillers": sum(m["fillers"] for m in ms),
        "pausesPerAnswer": round(sum(m["pauses"] for m in ms) / len(ms), 1),
        "fillersPerMin": round(sum(m["fillers"] for m in ms) / minutes, 1),
        "clarity": round(sum(m["clarity"] for m in ms) / len(ms), 2),
        "volumeDb": round(sum(vols) / len(vols), 1) if vols else None,
        "fadingAnswers": sum(1 for m in ms if m.get("endDropDb", 0) <= -4.0),
        "lowWords": low[:5],
    }
