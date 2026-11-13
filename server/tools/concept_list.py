"""CS 개념 학습 콘텐츠의 범위: 10개 카테고리 x 6개 = 60개 (docs/DESIGN.md 4.1).

개념 id(os-1 등)는 아래 목록 순서로 정해지고 앱의 학습 진도(Room)에 그대로 쓰인다.
그래서 이미 앱에 배포한 뒤에는 중간에 끼워 넣거나 순서를 바꾸지 말고, 새 개념은 항상 해당 카테고리 끝에 추가한다.
"""

CATEGORIES = [
    ("ds", "자료구조", [
        "배열과 연결 리스트",
        "스택과 큐",
        "해시 테이블",
        "트리와 이진 탐색 트리",
        "힙과 우선순위 큐",
        "그래프 표현과 BFS·DFS 탐색",
    ]),
    ("algo", "알고리즘", [
        "시간 복잡도와 Big-O",
        "정렬 알고리즘 비교",
        "이진 탐색",
        "재귀와 동적 계획법(DP)",
        "그리디 알고리즘",
        "최단 경로와 다익스트라",
    ]),
    ("os", "운영체제", [
        "프로세스와 스레드",
        "컨텍스트 스위칭",
        "CPU 스케줄링",
        "데드락",
        "세마포어와 뮤텍스",
        "가상 메모리와 페이징",
    ]),
    ("net", "네트워크", [
        "OSI 7계층과 TCP/IP 모델",
        "TCP와 UDP",
        "3-way handshake와 4-way handshake",
        "HTTP 버전별 차이(1.1, 2, 3)",
        "HTTPS와 TLS",
        "DNS 동작 과정",
    ]),
    ("db", "DB", [
        "인덱스",
        "트랜잭션과 ACID",
        "트랜잭션 격리 수준",
        "정규화와 반정규화",
        "JOIN의 종류",
        "SQL과 NoSQL",
    ]),
    ("lang", "Java/Kotlin", [
        "JVM 메모리 구조와 가비지 컬렉션",
        "equals와 hashCode",
        "컬렉션 프레임워크",
        "제네릭",
        "Kotlin 코루틴",
        "Kotlin의 null 안전성과 확장 함수",
    ]),
    ("web", "웹/백엔드", [
        "REST API",
        "쿠키, 세션, JWT",
        "CORS와 동일 출처 정책",
        "캐싱 전략",
        "동기·비동기와 블로킹·논블로킹",
        "로드 밸런싱과 스케일 업·스케일 아웃",
    ]),
    ("sec", "보안", [
        "대칭키와 비대칭키 암호",
        "해시와 솔트(비밀번호 저장)",
        "SQL 인젝션",
        "XSS와 CSRF",
        "OAuth 2.0",
        "인증과 인가",
    ]),
    ("design", "설계/패턴", [
        "객체 지향 4대 특징",
        "SOLID 원칙",
        "싱글톤과 팩토리 패턴",
        "옵저버 패턴",
        "의존성 주입(DI)",
        "MVC, MVP, MVVM",
    ]),
    ("android", "안드로이드", [
        "액티비티 생명주기",
        "ViewModel과 상태 관리",
        "RecyclerView 동작 원리",
        "메인 스레드와 ANR",
        "Room과 데이터 저장",
        "4대 컴포넌트와 Intent",
    ]),
]


def all_concepts():
    """(개념 id, 카테고리 id, 카테고리 이름, 제목) 를 순서대로 돌려준다."""
    for cat_id, cat_name, titles in CATEGORIES:
        for i, title in enumerate(titles, 1):
            yield f"{cat_id}-{i}", cat_id, cat_name, title
