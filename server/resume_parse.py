"""이력서·포트폴리오 PDF 에서 글자를 뽑는다 (docs/SCREENS.md 6장). TXT 는 앱이 직접 읽는다.

스캔한 이미지 PDF 는 글자가 없어 읽지 못한다(OCR 은 이번 범위 밖). 이때는 오류 문장으로 직접 입력을 안내한다.
"""
import io
import re

from rules import AiError

MAX_PAGES = 30
MAX_CHARS = 12000  # 앱에 저장해 두는 최대 글자 수. 면접관 프롬프트에는 그중 앞부분만 쓴다.


def parse_pdf(data: bytes) -> dict:
    try:
        from pypdf import PdfReader
        from pypdf.errors import PyPdfError
    except ImportError:
        raise AiError(503, "PDF 읽기(pypdf)가 설치되지 않았습니다. PC 에서 pip install pypdf 를 실행해 주세요.")

    try:
        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted and not reader.decrypt(""):
            raise AiError(422, "암호가 걸린 PDF 는 읽을 수 없어요. 암호를 풀거나 내용을 직접 붙여넣어 주세요.")
        pages = reader.pages[:MAX_PAGES]
        parts = [(page.extract_text() or "") for page in pages]
        total_pages = len(reader.pages)
    except AiError:
        raise
    except (PyPdfError, ValueError, KeyError, OSError, RecursionError):
        raise AiError(422, "PDF 파일을 읽지 못했어요. 파일이 손상되었거나 PDF 가 아닐 수 있어요.")

    text = _clean("\n\n".join(parts))
    if not text:
        raise AiError(422, "PDF 에서 글자를 읽지 못했어요. 스캔한 이미지 PDF 일 수 있어요. 내용을 직접 붙여넣어 주세요.")
    return {
        "text": text[:MAX_CHARS],
        "pages": total_pages,
        "chars": len(text),
        "truncated": len(text) > MAX_CHARS or total_pages > MAX_PAGES,
    }


def _clean(text: str) -> str:
    text = text.replace("\x00", "")
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()
