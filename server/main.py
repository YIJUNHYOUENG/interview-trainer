"""면접 트레이너 PC 서버.

앱은 이 서버와 JSON HTTP 로만 통신한다 (docs/DESIGN.md 7장).
기본은 claude CLI 로 진짜 면접관을 돌린다. TRAINER_FAKE=1 이면 CLI 없이 고정된 가짜 응답을 돌려준다.

실행:  python -m uvicorn main:app --host 0.0.0.0 --port 8787
"""
import json
import os
import tempfile
import threading
import uuid
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

import claude_ai
import fake_ai
import repo_ai
import resume_parse
import stt
from rules import AiError, decide

FAKE = os.environ.get("TRAINER_FAKE") == "1"
ENGINE = fake_ai.FakeEngine() if FAKE else claude_ai.ClaudeEngine()
STT = stt.FakeStt() if FAKE else None
MAX_AUDIO_BYTES = 8 * 1024 * 1024  # 16kHz 16bit 모노 약 4분
MAX_RESUME_BYTES = 5 * 1024 * 1024  # 이력서 파일 5MB
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
    coverLetter: str = Field("", max_length=20000)  # 이력서·포트폴리오·자소서 글자
    jobPosting: str = Field("", max_length=5000)    # 채용 공고 본문(붙여넣기)
    extraRequest: str = Field("", max_length=500)   # 기타 요청(면접 방식에 대한 희망사항)
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
    voice: bool = False  # 음성으로 한 답변이면 true: 직전 /voice/transcribe 의 음성 지표를 이 답변에 붙인다
    # 앱이 지금까지 받은 답변 결과 수(0부터). 응답을 못 받아 같은 답변을 다시 보내면 서버가 처리해 둔 결과를 그대로 돌려준다.
    # 없으면(옛 앱) 이 검사를 하지 않는다.
    turn: Optional[int] = None


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
    conceptId: str = ""  # 이 질문과 관련된 CS 개념 id(앱의 개념 상세로 연결). 없으면 빈 문자열


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
        self.pending_metrics: Optional[dict] = None  # /voice/transcribe 가 계산해 둔, 아직 답변에 붙지 않은 음성 지표
        # 한 세션의 답변·리포트 요청은 한 번에 하나만 처리한다(앱이 시간 초과로 다시 보내도 주제 수가 두 번 늘지 않게).
        self.lock = threading.Lock()
        self.answered = 0  # 처리를 마친 답변 수
        self.last_response: Optional["AnswerResponse"] = None  # 마지막 답변 결과(같은 답변을 다시 받으면 그대로 돌려준다)
        self.report: Optional[dict] = None  # 만들어 둔 리포트(다시 요청하면 claude 를 또 부르지 않는다)


SESSIONS: dict = {}


def get_session(session_id: str) -> Session:
    s = SESSIONS.get(session_id)
    if s is None:
        raise HTTPException(404, "세션을 찾을 수 없습니다. 서버가 재시작됐을 수 있어요.")
    return s


@contextmanager
def session_lock(s: Session):
    """다른 요청이 이 세션을 처리 중이면 기다리지 않고 409 를 돌려준다."""
    if not s.lock.acquire(blocking=False):
        raise HTTPException(409, "이전 요청을 아직 처리하고 있어요. 잠시 후 다시 시도해 주세요.")
    try:
        yield
    finally:
        s.lock.release()


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


class RepoAnalyzeRequest(BaseModel):
    url: str


# ---------- API ----------

@app.get("/health")
def health():
    if FAKE:
        return {"ok": True, "claude": "fake", "model": "fake"}
    version = claude_ai.claude_version()
    return {"ok": version != "missing", "claude": version, "model": claude_ai.MODEL}


@app.post("/interview/start", response_model=StartResponse)
def interview_start(req: StartRequest):
    if req.repoAnalysisId and repo_ai.load_result(req.repoAnalysisId) is None:
        raise HTTPException(422, "GitHub 분석 결과를 찾을 수 없어요. 설정에서 다시 분석해 주세요.")
    s = Session(req)
    topic, question = ENGINE.start(s)
    s.turns.append(Turn(topic, question))
    session_id = uuid.uuid4().hex[:12]
    SESSIONS[session_id] = s
    save_log(session_id, s)
    return StartResponse(sessionId=session_id, question=question, topic=topic, total=s.total)


@app.post("/interview/answer", response_model=AnswerResponse)
def interview_answer(req: AnswerRequest):
    # voice: 사용자가 글자를 고친 뒤 보내는 음성 답변. 인식 때 계산한 지표를 붙인다(처리가 성공했을 때만 비운다).
    return process_answer(req.sessionId, req.answer, None, turn=req.turn, use_pending=req.voice)


@app.post("/voice/transcribe")
async def voice_transcribe(request: Request, sessionId: str):
    """음성을 글자로만 바꿔 돌려준다(AI 질문 단계는 아직 하지 않는다). 앱이 글자를 보여 주고 고친 뒤 /interview/answer 로 보낸다."""
    s = get_session(sessionId)
    if s.finished:
        raise HTTPException(409, "이미 끝난 면접입니다.")
    body = await request.body()
    if len(body) < 1000:
        raise HTTPException(422, "녹음이 너무 짧습니다. 조금 더 길게 말해 주세요.")
    if len(body) > MAX_AUDIO_BYTES:
        raise HTTPException(413, "녹음이 너무 깁니다. 3분 이내로 말해 주세요.")
    text, metrics = await run_in_threadpool(recognize, body, s.turns[-1].question)
    s.pending_metrics = metrics  # 사용자가 글자를 고쳐도 말 속도·멈춤 같은 지표는 목소리에서 나온 값 그대로 쓴다
    return {"text": text, "metrics": metrics}


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

    text, metrics = await run_in_threadpool(recognize, body, s.turns[-1].question)
    res = await run_in_threadpool(process_answer, sessionId, text, metrics)
    return VoiceAnswerResponse(**res.model_dump(), text=text, metrics=metrics)


AUDIO_DIR = LOG_DIR / "audio"
KEEP_RECORDINGS = 30  # 마이크·인식 문제를 직접 들어 보려고 남겨 두는 최근 녹음 수


def save_recording(wav_bytes: bytes) -> str:
    """받은 녹음을 logs/audio/ 에 저장한다(인식 전에 저장하므로 인식이 실패해도 남는다). 오래된 것은 지운다."""
    try:
        AUDIO_DIR.mkdir(parents=True, exist_ok=True)
        path = AUDIO_DIR / f"{datetime.now().strftime('%Y%m%d-%H%M%S-%f')[:-3]}.wav"
        path.write_bytes(wav_bytes)
        for old in sorted(AUDIO_DIR.glob("*.wav"))[:-KEEP_RECORDINGS]:
            old.unlink(missing_ok=True)
        return str(path)
    except OSError as e:  # 저장이 안 돼도 인식은 계속한다
        return f"저장 실패({e})"


def recognize(wav_bytes: bytes, question: str = ""):
    """WAV 를 글자와 지표로 바꾼다. 알아듣지 못했으면 422. question 은 지금 면접 질문(인식 힌트)."""
    fd, path = tempfile.mkstemp(suffix=".wav")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(wav_bytes)
        saved = save_recording(wav_bytes)
        samples, rate = stt.read_wav(path)
        peak, db = stt.level(samples)
        loud = stt.loudest_chunk_db(samples, rate)
        print(f"[stt] 녹음 {len(samples) / rate:.1f}초, 최대 {peak:.3f}, 평균 {db:.0f}dB, 가장 큰 구간 {loud:.0f}dB, 저장: {saved}", flush=True)
        # 말소리가 한 번도 없었다: 마이크 입력이 안 들어온 경우(에뮬레이터 마이크 설정 등).
        # 이대로 인식하면 Whisper 가 "감사합니다" 같은 없는 말을 지어낸다.
        if not FAKE and loud < -45:
            raise HTTPException(422, "녹음에 소리가 거의 들어오지 않았어요. 마이크가 켜져 있는지 확인해 주세요. "
                                     "에뮬레이터는 켤 때마다 확장 설정(…) > Microphone 의 "
                                     "'Virtual microphone uses host audio input' 을 켜야 PC 마이크 소리가 들어와요.")
        tr = STT.transcribe(samples, rate) if FAKE else stt.transcribe(samples, rate, question)
        if not tr.text.strip():
            raise HTTPException(422, "목소리를 알아듣지 못했어요. 조용한 곳에서 조금 더 크게 말해 주세요.")
        return tr.text.strip(), stt.compute_metrics(tr, samples, rate)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def process_answer(session_id: str, answer: str, metrics: Optional[dict],
                   turn: Optional[int] = None, use_pending: bool = False) -> AnswerResponse:
    s = get_session(session_id)
    with session_lock(s):
        if turn is not None:
            if turn == s.answered - 1 and s.last_response is not None:
                return s.last_response  # 이미 처리한 답변을 다시 보냈다(앱이 응답을 받기 전에 시간 초과)
            if turn != s.answered:
                raise HTTPException(409, "면접 진행 순서가 맞지 않아요. 면접을 다시 시작해 주세요.")
        if use_pending:
            metrics = s.pending_metrics
        res = _process_answer(session_id, s, answer, metrics)
        if use_pending:
            s.pending_metrics = None
        s.answered += 1
        s.last_response = res
        return res


def _process_answer(session_id: str, s: Session, answer: str, metrics: Optional[dict]) -> AnswerResponse:
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
    if kind != res.type:
        # AI 가 끝까지 규칙을 어겼다. 그 질문은 다른 종류(예: 꼬리질문)로 쓴 것이라 버리고 기본 질문을 쓴다.
        question = ""
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
        topic = res.topic if res.topic and res.topic != cur.topic else "자유 주제"
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
    with session_lock(s):
        if s.report is not None:
            return s.report  # 앱이 응답을 못 받고 다시 요청했다: 만들어 둔 리포트를 그대로 준다
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
        s.report = report
        save_log(req.sessionId, s)
        return report


@app.post("/repo/analyze")
def repo_analyze(req: RepoAnalyzeRequest):
    """공개 GitHub 레포 분석을 시작한다. 몇 분 걸릴 수 있어 jobId 를 돌려주고, 앱이 GET /repo/{jobId} 로 다시 묻는다."""
    return repo_ai.start(req.url)


@app.get("/repo/{job_id}")
def repo_status(job_id: str):
    st = repo_ai.status(job_id)
    if st is None:
        raise HTTPException(404, "분석 작업을 찾을 수 없어요.")
    return st


@app.post("/resume/parse")
async def resume_parse_endpoint(request: Request):
    """이력서·포트폴리오 PDF(본문 원본)에서 글자를 뽑아 돌려준다. 앱이 면접 설정에 저장해 두고 /interview/start 로 보낸다."""
    data = await request.body()
    if not data:
        raise HTTPException(422, "파일이 비어 있어요.")
    if len(data) > MAX_RESUME_BYTES:
        raise HTTPException(413, "파일이 너무 커요. 5MB 이하만 올릴 수 있어요.")
    if not data.startswith(b"%PDF"):
        raise HTTPException(422, "PDF 파일이 아니에요.")
    return await run_in_threadpool(resume_parse.parse_pdf, data)
