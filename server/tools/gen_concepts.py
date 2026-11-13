"""CS 개념 학습 콘텐츠(assets/concepts.json)를 claude CLI 로 만든다 (docs/DESIGN.md 4.1).

사용법 (server 폴더에서):
    python tools/gen_concepts.py                # 아직 없는 개념만 만들고 합친다 (끊겨도 이어서 가능)
    python tools/gen_concepts.py os-1 db-3      # 지정한 개념만 (다시) 만든다
    python tools/gen_concepts.py --assemble     # 만들어 둔 것만 합쳐 assets 에 쓴다
    python tools/gen_concepts.py --workers 3    # 동시에 부르는 수 (기본 3)

- 개념 하나마다 claude 를 한 번 부르고 결과를 tools/concepts_out/<id>.json 에 저장한다.
- AI 는 정답을 앞쪽 보기에 두는 경향이 있어, 정답과 오답을 따로 받아 서버(이 스크립트)가 보기 순서를 섞는다.
- 결과는 사람이 꼭 검수해야 한다. 이 스크립트는 형식만 검사하고 내용의 사실 여부는 확인하지 못한다.
"""
import argparse
import json
import random
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))

import claude_ai  # noqa: E402
from concept_list import CATEGORIES, all_concepts  # noqa: E402
from rules import AiError  # noqa: E402

OUT_DIR = HERE / "concepts_out"
ASSET = HERE.parent.parent / "android" / "app" / "src" / "main" / "assets" / "concepts.json"
VERSION = 1

claude_ai.TIMEOUT = 170  # 개념 하나가 길어서 면접용 제한(80초)보다 넉넉히

SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "string", "description": "한 줄 요약. 40자 안팎"},
        "keywords": {"type": "array", "items": {"type": "string"}, "description": "핵심 키워드 4~5개"},
        "explanation": {"type": "string", "description": "본문 설명 600~900자. 문단은 빈 줄로 구분"},
        "interviewQA": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"question": {"type": "string"}, "answer": {"type": "string"}},
                "required": ["question", "answer"],
            },
        },
        "quizzes": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "question": {"type": "string"},
                    "correct": {"type": "string", "description": "정답 보기 하나"},
                    "wrong": {"type": "array", "items": {"type": "string"}, "description": "그럴듯한 오답 보기 정확히 3개"},
                    "explanation": {"type": "string", "description": "왜 정답인지, 오답은 무엇이 틀렸는지 2~3문장"},
                },
                "required": ["question", "correct", "wrong", "explanation"],
            },
        },
    },
    "required": ["summary", "keywords", "explanation", "interviewQA", "quizzes"],
}

SYSTEM = (
    "당신은 개발자 취업 면접을 준비하는 학생을 위한 CS 학습 자료를 쓰는 시니어 개발자입니다. 한국어로 씁니다.\n"
    "- 정확성이 가장 중요합니다. 확실하지 않은 수치·버전·내용은 쓰지 않고, 일반적으로 합의된 설명만 씁니다.\n"
    "- 대학 3~4학년이 이해할 수 있게 정의, 동작 원리, 장단점이나 비교, 면접에서 자주 나오는 포인트 순서로 설명합니다.\n"
    "- 퀴즈는 개념 이해를 확인하는 4지선다입니다. 정답은 하나이고, 오답 보기는 흔한 오해에서 나온 그럴듯한 것으로 만듭니다. "
    "'모두 맞다/모두 틀리다' 같은 보기는 쓰지 않습니다. 세 문제는 서로 다른 측면을 묻습니다.\n"
    "- 중요: 정답 보기가 오답보다 길거나 더 자세하면 길이만 보고 맞힐 수 있습니다. 네 보기의 길이와 구체성을 비슷하게 맞추고, "
    "오답 보기도 정답만큼(또는 더) 길고 구체적으로 쓰세요. 세 문제 중 정답이 가장 긴 보기인 문제는 많아야 한 문제입니다.\n"
    "- 면접 단골 질문은 실제 면접에서 나올 법한 질문이고, 모범 답안은 3~5문장으로 결론부터 말하는 구어체가 아닌 서술체로 씁니다."
)


def prompt_for(cat_name: str, title: str) -> str:
    return (
        f"카테고리: {cat_name}\n개념: {title}\n\n"
        "이 개념의 학습 자료를 만드세요.\n"
        "- summary: 한 줄 요약(40자 안팎)\n"
        "- keywords: 핵심 키워드 4~5개\n"
        "- explanation: 본문 600~900자(공백 포함). 문단은 빈 줄로 구분하고, 마크다운 기호(#, *, -)는 쓰지 않습니다.\n"
        "- interviewQA: 면접 단골 질문 정확히 2개와 모범 답안\n"
        "- quizzes: 4지선다 정확히 3문제. correct 에 정답 하나, wrong 에 오답 정확히 3개를 씁니다."
    )


def length_bias(quizzes: list) -> int:
    """정답이 모든 오답보다 엄격하게 긴 문제 수. 길이만 보고 맞힐 수 있는 문제라 개념 하나(3문제)에서 1개까지만 허용한다."""
    n = 0
    for q in quizzes:
        if "wrong" in q:  # AI 원본 형식
            correct, others = q["correct"], list(q["wrong"])
        else:             # 앱 형식(보기 4개 + 정답 위치)
            correct = q["options"][q["answer"]]
            others = [o for i, o in enumerate(q["options"]) if i != q["answer"]]
        n += len(correct.strip()) > max(len(o.strip()) for o in others)
    return n


def validate(data: dict) -> list:
    """형식 문제를 모아서 돌려준다. 비어 있으면 통과."""
    problems = []
    if not str(data.get("summary", "")).strip():
        problems.append("summary 가 비었음")
    kw = data.get("keywords") or []
    if not (3 <= len(kw) <= 6) or any(not str(k).strip() for k in kw):
        problems.append(f"keywords 개수 {len(kw)}")
    n = len(str(data.get("explanation", "")).strip())
    if not (450 <= n <= 1300):
        problems.append(f"explanation 길이 {n}")
    qa = data.get("interviewQA") or []
    if len(qa) != 2 or any(not str(x.get("question", "")).strip() or not str(x.get("answer", "")).strip() for x in qa):
        problems.append("interviewQA 는 2개여야 함")
    quizzes = data.get("quizzes") or []
    if len(quizzes) != 3:
        problems.append(f"quizzes 개수 {len(quizzes)}")
    for i, q in enumerate(quizzes):
        options = [str(q.get("correct", "")).strip()] + [str(w).strip() for w in (q.get("wrong") or [])]
        if len(q.get("wrong") or []) != 3 or len(set(options)) != 4 or any(not o for o in options):
            problems.append(f"quiz {i + 1} 보기가 4개의 서로 다른 값이 아님")
        if not str(q.get("question", "")).strip() or not str(q.get("explanation", "")).strip():
            problems.append(f"quiz {i + 1} 문제/해설이 비었음")
    if len(quizzes) == 3 and not problems and length_bias(quizzes) > 1:
        problems.append("정답이 가장 긴 보기인 문제가 2개 이상(길이로 정답이 티 남)")
    return problems


def finalize(concept_id: str, title: str, data: dict) -> dict:
    """AI 결과를 앱 형식으로 바꾼다. 보기 순서는 개념 id 로 고정한 난수로 섞어 다시 만들어도 같은 결과가 나오게 한다."""
    quizzes = []
    for i, q in enumerate(data["quizzes"]):
        options = [q["correct"].strip()] + [w.strip() for w in q["wrong"]]
        rng = random.Random(f"{concept_id}:{i}")
        rng.shuffle(options)
        quizzes.append({
            "question": q["question"].strip(),
            "options": options,
            "answer": options.index(q["correct"].strip()),  # 0~3
            "explanation": q["explanation"].strip(),
        })
    return {
        "id": concept_id,
        "title": title,
        "summary": data["summary"].strip(),
        "keywords": [k.strip() for k in data["keywords"]],
        "explanation": data["explanation"].strip(),
        "interviewQA": [{"question": x["question"].strip(), "answer": x["answer"].strip()} for x in data["interviewQA"]],
        "quizzes": quizzes,
    }


def generate_one(concept_id: str, cat_name: str, title: str) -> str:
    """개념 하나를 만들어 저장한다. 형식이 틀리거나 호출이 실패하면 최대 3번 시도. 결과 문장을 돌려준다."""
    last = ""
    for attempt in range(1, 4):
        try:
            started = time.time()
            data, _ = claude_ai.call_claude(prompt_for(cat_name, title), SYSTEM, SCHEMA)
            problems = validate(data)
            if problems:
                last = "; ".join(problems)
                continue
            (OUT_DIR / f"{concept_id}.json").write_text(
                json.dumps(finalize(concept_id, title, data), ensure_ascii=False, indent=2), encoding="utf-8"
            )
            return f"OK   {concept_id} {title} ({time.time() - started:.0f}s, {attempt}회째)"
        except AiError as e:
            last = e.message
        except Exception as e:  # noqa: BLE001
            last = f"{type(e).__name__}: {e}"
    return f"FAIL {concept_id} {title}: {last}"


def assemble() -> int:
    """만들어 둔 개념을 카테고리 순서대로 합쳐 assets/concepts.json 으로 쓴다. 빠진 개념 수를 돌려준다."""
    categories, missing = [], 0
    for cat_id, cat_name, titles in CATEGORIES:
        concepts = []
        for i, title in enumerate(titles, 1):
            f = OUT_DIR / f"{cat_id}-{i}.json"
            if f.is_file():
                concepts.append(json.loads(f.read_text(encoding="utf-8")))
            else:
                missing += 1
        categories.append({"id": cat_id, "name": cat_name, "concepts": concepts})
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(
        json.dumps({"version": VERSION, "categories": categories}, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    total = sum(len(c["concepts"]) for c in categories)
    print(f"합침: {total}개 -> {ASSET} (빠진 개념 {missing}개)")
    return missing


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*", help="만들 개념 id (비우면 아직 없는 것 전부)")
    ap.add_argument("--assemble", action="store_true", help="새로 만들지 않고 합치기만 한다")
    ap.add_argument("--redo-biased", action="store_true",
                    help="이미 만든 것 중 정답이 가장 긴 보기인 문제가 2개 이상인 개념을 다시 만든다")
    ap.add_argument("--workers", type=int, default=3)
    args = ap.parse_args()

    OUT_DIR.mkdir(exist_ok=True)
    if args.assemble:
        sys.exit(1 if assemble() else 0)

    def biased(cid):
        f = OUT_DIR / f"{cid}.json"
        return f.is_file() and length_bias(json.loads(f.read_text(encoding="utf-8"))["quizzes"]) > 1

    if args.redo_biased:
        targets = [c for c in all_concepts() if biased(c[0])]
    else:
        targets = [c for c in all_concepts() if (c[0] in args.ids if args.ids else not (OUT_DIR / f"{c[0]}.json").is_file())]
    print(f"만들 개념 {len(targets)}개, 동시 {args.workers}개")
    failed = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(generate_one, cid, cat_name, title): cid for cid, _, cat_name, title in targets}
        for fut in as_completed(futures):
            line = fut.result()
            print(line, flush=True)
            if line.startswith("FAIL"):
                failed.append(futures[fut])
    missing = assemble()
    if failed:
        print("실패:", " ".join(failed), "-> 같은 명령을 다시 실행하면 실패한 것만 다시 만듭니다.")
    sys.exit(1 if failed or missing else 0)


if __name__ == "__main__":
    main()
