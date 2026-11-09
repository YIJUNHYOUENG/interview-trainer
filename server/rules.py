"""가짜/진짜 면접관이 함께 쓰는 규칙. 질문 수와 꼬리질문 횟수는 AI 가 아니라 서버가 정한다."""
from dataclasses import dataclass


class AiError(Exception):
    """AI 호출 실패. status 는 앱에 돌려줄 HTTP 상태 코드, message 는 사용자에게 보여 줄 문장."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


@dataclass
class Result:
    """면접관이 직전 답변에 대해 돌려준 결과."""
    type: str      # followup / next / end
    topic: str     # 다음 질문의 주제 (end 이면 현재 주제)
    question: str  # end 이면 빈 문자열
    score: int     # 직전 답변 점수 1~5
    comment: str   # 직전 답변 한 줄 평가


def decide(ai_type: str, followups_left: int, is_last_topic: bool) -> str:
    """AI 가 고른 type 을 서버 규칙에 맞게 바로잡아 최종 type 을 돌려준다.

    - 꼬리질문을 다 썼으면: 마지막 주제면 end, 아니면 next
    - 꼬리질문이 남았으면: AI 가 followup 이면 그대로,
      next / end 로 넘어가려 하면 마지막 주제는 end, 아니면 next
    """
    if followups_left <= 0:
        return "end" if is_last_topic else "next"
    if ai_type == "followup":
        return "followup"
    return "end" if is_last_topic else "next"
