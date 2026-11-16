"""앱의 CS 개념 목록(id, 카테고리, 제목). 면접 리포트가 질문마다 관련 개념을 고를 때 쓴다.

목록의 원본은 tools/concept_list.py 하나다(개념 콘텐츠를 만드는 스크립트와 같은 파일). 앱 assets/concepts.json 의
개념 id 도 거기서 나오므로, 여기서 고른 id 를 앱이 그대로 개념 상세 화면에 쓸 수 있다.
"""
import importlib.util
from pathlib import Path
from typing import List, Tuple

_path = Path(__file__).parent / "tools" / "concept_list.py"
_spec = importlib.util.spec_from_file_location("concept_list", _path)
_module = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_module)

# (개념 id, 카테고리 이름, 제목) 를 개념 id 순서대로
CONCEPTS: List[Tuple[str, str, str]] = [(cid, cat_name, title) for cid, _, cat_name, title in _module.all_concepts()]
IDS = {cid for cid, _, _ in CONCEPTS}


def prompt_list() -> str:
    """프롬프트에 넣을 "id: 카테고리 · 제목" 목록"""
    return "\n".join(f"{cid}: {cat} · {title}" for cid, cat, title in CONCEPTS)


def valid(concept_id: str) -> str:
    """목록에 있는 id 면 그대로, 아니면 빈 문자열"""
    return concept_id if concept_id in IDS else ""
