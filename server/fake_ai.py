"""claude CLI 없이 쓰는 가짜 면접관 (TRAINER_FAKE=1). 앱과 서버의 약속(JSON 모양)을 맞추기 위한 것이다."""
from typing import List, Tuple

from rules import Result, decide

CS_TOPICS = [
    "REST API", "프로세스와 스레드", "TCP 와 UDP", "DB 인덱스", "HTTP 와 HTTPS",
    "해시 테이블", "트랜잭션과 ACID", "OOP 4대 특징", "캐시", "JWT 인증",
]
COVER_TOPICS = [
    "지원 동기", "프로젝트 경험", "협업 경험", "문제 해결 경험", "성장 계획",
    "강점과 약점", "실패 경험", "기술 선택 이유", "코드 리뷰 경험", "입사 후 포부",
]

FOLLOWUPS = [
    "방금 답변에서 한 가지를 더 깊이 설명해 주시겠어요? 실제 사용 예시를 들어 주세요.",
    "그 방식의 단점이나 한계는 무엇이라고 생각하세요?",
    "다른 대안과 비교하면 어떤 점이 달라지나요?",
]

MODEL_ANSWER = "핵심 정의를 한 문장으로 말하고, 동작 원리, 장단점, 실제 사용 사례 순서로 답하면 좋습니다."


def pick_topics(mode: str, count: int) -> List[str]:
    if mode == "cs":
        pool = CS_TOPICS
    elif mode == "cover":
        pool = COVER_TOPICS
    else:  # mixed: 번갈아 섞는다
        pool = [t for pair in zip(CS_TOPICS, COVER_TOPICS) for t in pair]
    return pool[:count]


def first_question(req, topic: str) -> str:
    who = f"{req.company} {req.role}".strip()
    head = f"[{who}] " if who else ""
    return f"{head}'{topic}'에 대해 설명해 주세요."


def evaluate(answer: str) -> Tuple[int, str]:
    n = len(answer)
    if n < 20:
        return 2, "너무 짧습니다. 정의와 이유를 함께 말해 보세요."
    if n < 60:
        return 3, "핵심은 맞지만 예시나 근거가 더 필요합니다."
    if n < 120:
        return 4, "구조가 잘 잡힌 답변입니다. 사례를 더하면 더 좋아요."
    return 5, "정의, 원리, 사례까지 충실하게 답했습니다."


class FakeEngine:
    def start(self, s) -> Tuple[str, str]:
        s.topics = pick_topics(s.req.mode, s.total)
        s.total = len(s.topics)
        return s.topics[0], first_question(s.req, s.topics[0])

    def answer(self, s, answer: str, followups_left: int, is_last: bool) -> Result:
        score, comment = evaluate(answer)
        kind = decide("followup", followups_left, is_last)
        topic = s.topics[s.topic_idx]
        question = ""
        if kind == "followup":
            question = FOLLOWUPS[s.followups % len(FOLLOWUPS)]
        elif kind == "next":
            topic = s.topics[s.topic_idx + 1]
            question = first_question(s.req, topic)
        return Result(kind, topic, question, score, comment)

    def report(self, s, answered) -> dict:
        scores = [t.score for t in answered]
        avg = sum(scores) / len(scores)
        return {
            "overallScore": round(avg * 20),
            "summary": f"{len(answered)}개 질문에 답했고 평균 {avg:.1f}점입니다. (가짜 리포트)",
            "strengths": ["질문 의도를 파악하고 차분히 답했습니다.", "핵심 용어를 정확히 사용했습니다."],
            "improvements": ["실제 사용 예시를 더 들어 보세요.", "결론을 먼저 말하는 두괄식으로 연습해 보세요."],
            "questions": [
                {
                    "topic": t.topic,
                    "question": t.question,
                    "answer": t.answer,
                    "score": t.score,
                    "feedback": t.comment,
                    "modelAnswer": MODEL_ANSWER,
                }
                for t in answered
            ],
        }
