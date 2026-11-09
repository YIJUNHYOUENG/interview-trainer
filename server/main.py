"""면접 트레이너 PC 서버.

앱은 이 서버와 JSON HTTP 로만 통신한다 (docs/DESIGN.md 7장).
기본은 claude CLI 로 진짜 면접관을 돌린다. TRAINER_FAKE=1 이면 CLI 없이 고정된 가짜 응답을 돌려준다.

실행:  python -m uvicorn main:app --host 0.0.0.0 --port 8787
"""
import json
import os
import tempfile
import uuid
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

import claude_ai
import fake_ai
import stt
from rules import AiError, decide

FAKE = os.environ.get("TRAINER_FAKE") == "1"
ENGINE = fake_ai.FakeEngine() if FAKE else claude_ai.ClaudeEngine()
STT = stt.FakeStt() if FAKE else None
MAX_AUDIO_BYTES = 8 * 1024 * 1024  # 16kHz 16bit 모노 약 4분
LOG_DIR = Path(__file__).parent / "logs"

app = FastAPI(title="Interview Trainer Server")


@app.on_event("startup")
def warm_up_stt():
    """음성 인식 모델을 백그라운드에서 미리 불러 둔다. 실패해도 서버는 뜨고, 음성 요청 때 오류로 알려 준다."""
    if FAKE:
        return

    def load():
        try:
            stt.warm_up()
        except Exception as e:  # 모델 내려받기 실패, 미설치 등
            print(f"[stt] 음성 인식 모델을 불러오지 못했습니다: {e}")

    import threading
    threading.Thread(target=load, daemon=True).start()


@app.exception_handler(AiError)
def ai_error_handler(_, e: AiError):
    return JSONResponse(status_code=e.status, content={"detail": e.message})


# ---------- 요청 / 응답 모양 ----------

class StartRequest(BaseModel):
    mode: str = "mixed"  # cs / cover / mixed
    company: str = ""
    role: str = ""
    coverLetter: str = ""
    questionCount: int = Field(5, ge=1, le=10)
    maxFollowups: int = Field(2, ge=0, le=3)
    topic: Optional[str] = None
    repoAnalysisId: Optional[str] = None


class StartResponse(BaseModel):
    sessionId: str
    question: str
    topic: str
    total: int


class AnswerRequest(BaseModel):
    sessionId: str
    answer: str


class AnswerResponse(BaseModel):
    type: str  # followup / next / end
    question: str  # type == end 이면 빈 문자열
    topic: str
    topicNo: int  # 지금 몇 번째 주제인지 (1부터)
    followups: int  # 이 주제에서 지금까지 한 꼬리질문 수
    score: int  # 직전 답변 점수 1~5
    comment: str  # 직전 답변 한 줄 평가


class VoiceAnswerResponse(AnswerResponse):
    text: str      # 인식한 답변 글자
    metrics: dict  # 이 답변의 음성 지표


class FinishRequest(BaseModel):
    sessionId: str


class QuestionReport(BaseModel):
    topic: str
    question: str
    answer: str
    score: int
    feedback: str
    modelAnswer: str


class FinishResponse(BaseModel):
    overallScore: int
    summary: str
    strengths: List[str]
    improvements: List[str]
    questions: List[QuestionReport]
    voice: Optional[dict] = None  # 음성으로 답한 면접이면 지표 요약


# ---------- 면접 세션 (메모리) ----------

class Turn:
    def __init__(self, topic: str, question: str):
        self.topic = topic
        self.question = question
        self.answer = ""
        self.score = 0
        self.comment = ""
        self.metrics: Optional[dict] = None  # 음성 답변일 때만


class Session:
    def __init__(self, req: StartRequest):
        self.req = req
        self.total = req.questionCount  # 전체 주제 수
        self.topic_idx = 0  # 지금 몇 번째 주제인지 (0부터)
        self.followups = 0  # 이 주제에서 쓴 꼬리질문 수
        self.turns: List[Turn] = []
        self.finished = False
        self.claude_session_id: Optional[str] = None
        self.topics: List[str] = []  # 가짜 면접관이 쓰는 주제 목록


SESSIONS: dict = {}


def get_session(session_id: str) -> Session:
    s = SESSIONS.get(session_id)
    if s is None:
        raise HTTPException(404, "세션을 찾을 수 없습니다. 서버가 재시작됐을 수 있어요.")
    return s


def save_log(session_id: str, s: Session) -> None:
    LOG_DIR.mkdir(exist_ok=True)
    data = {
        "request": s.req.model_dump(),
        "claudeSessionId": s.claude_session_id,
        "turns": [t.__dict__ for t in s.turns],
    }
    (LOG_DIR / f"{session_id}.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
    )


# ---------- API ----------

@app.get("/health")
def health():
    if FAKE:
        return {"ok": True, "claude": "fake", "model": "fake"}
    version = claude_ai.claude_version()
    return {"ok": version != "missing", "claude": version, "model": claude_ai.MODEL}


@app.post("/interview/start", response_model=StartResponse)
def interview_start(req: StartRequest):
    s = Session(req)
    topic, question = ENGINE.start(s)
    s.turns.append(Turn(topic, question))
    session_id = uuid.uuid4().hex[:12]
    SESSIONS[session_id] = s
    save_log(session_id, s)
    return StartResponse(sessionId=session_id, question=question, topic=topic, total=s.total)


@app.post("/interview/answer", response_model=AnswerResponse)
def interview_answer(req: AnswerRequest):
    return process_answer(req.sessionId, req.answer, None)


@app.post("/voice/answer", response_model=VoiceAnswerResponse)
async def voice_answer(request: Request, sessionId: str):
    """음성 답변. 본문은 16kHz 모노 WAV 원본. 글자로 바꾼 뒤 /interview/answer 와 같은 처리를 한다."""
    s = get_session(sessionId)
    if s.finished:
        raise HTTPException(409, "이미 끝난 면접입니다.")
    body = await request.body()
    if len(body) < 1000:
        raise HTTPException(422, "녹음이 너무 짧습니다. 버튼을 누른 채로 말해 주세요.")
    if len(body) > MAX_AUDIO_BYTES:
        raise HTTPException(413, "녹음이 너무 깁니다. 3분 이내로 말해 주세요.")

    text, metrics = await run_in_threadpool(recognize, body)
    res = await run_in_threadpool(process_answer, sessionId, text, metrics)
    return VoiceAnswerResponse(**res.model_dump(), text=text, metrics=metrics)


def recognize(wav_bytes: bytes):
    """WAV 를 글자와 지표로 바꾼다. 알아듣지 못했으면 422."""
    fd, path = tempfile.mkstemp(suffix=".wav")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(wav_bytes)
        samples, rate = stt.read_wav(path)
        tr = STT.transcribe(samples, rate) if FAKE else stt.transcribe(samples, rate)
        if not tr.text.strip():
            raise HTTPException(422, "목소리를 알아듣지 못했어요. 조용한 곳에서 조금 더 크게 말해 주세요.")
        return tr.text.strip(), stt.compute_metrics(tr, samples, rate)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def process_answer(session_id: str, answer: str, metrics: Optional[dict]) -> AnswerResponse:
    s = get_session(session_id)
    if s.finished:
        raise HTTPException(409, "이미 끝난 면접입니다.")
    if not answer.strip():
        raise HTTPException(422, "답변이 비어 있습니다.")

    cur = s.turns[-1]
    left = s.req.maxFollowups - s.followups
    is_last = s.topic_idx >= s.total - 1

    # 면접관(가짜/진짜)이 먼저 답하고, 서버가 규칙에 맞는지 마지막으로 확인한다.
    res = ENGINE.answer(s, answer.strip(), left, is_last)
    kind = decide(res.type, left, is_last)

    cur.answer = answer.strip()
    cur.metrics = metrics
    cur.score, cur.comment = res.score, res.comment

    question = res.question
    if kind == "end":
        question = ""
        topic = cur.topic
        s.finished = True
    elif kind == "followup":
        question = question or "방금 답변에 대해 조금 더 설명해 주시겠어요?"
        topic = cur.topic
        s.followups += 1
    else:  # next
        question = question or "다음 주제로 넘어가겠습니다. 가장 자신 있는 기술 하나를 설명해 주세요."
        topic = res.topic
        s.topic_idx += 1
        s.followups = 0

    if question:
        s.turns.append(Turn(topic, question))
    save_log(session_id, s)

    return AnswerResponse(
        type=kind,
        question=question,
        topic=topic,
        topicNo=s.topic_idx + 1,
        followups=s.followups,
        score=cur.score,
        comment=cur.comment,
    )


@app.post("/interview/finish", response_model=FinishResponse)
def interview_finish(req: FinishRequest):
    s = get_session(req.sessionId)
    answered = [t for t in s.turns if t.answer]
    if not answered:
        raise HTTPException(409, "답변한 내용이 없어 리포트를 만들 수 없습니다.")
    report = ENGINE.report(s, answered)
    report["voice"] = stt.summarize([t.metrics for t in answered])
    if report["voice"] is not None:
        report["voice"]["comment"] = report.pop("voiceComment", "")
    else:
        report.pop("voiceComment", None)
    s.finished = True
    save_log(req.sessionId, s)
    return report
