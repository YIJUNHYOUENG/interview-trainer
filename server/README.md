# PC 브릿지 서버

앱은 이 서버 하나만 부른다. 서버가 PC 에서 `claude` CLI(AI 면접관), faster-whisper(음성 인식), git(GitHub 분석)을 직접 실행한다.
API 는 [docs/DESIGN.md](../docs/DESIGN.md) 7장을 본다.

## 준비 (처음 한 번)

1. Python 3.10 이상 (개발은 3.13).
2. Claude Code CLI 설치와 로그인: `npm install -g @anthropic-ai/claude-code` 후 터미널에서 `claude` 를 실행해 로그인.
   AI 호출은 로그인한 구독을 쓴다(API 키 없음).
3. git (GitHub 분석용).
4. `run.bat` 을 실행하면 `requirements.txt` 의 라이브러리를 자동으로 설치한다.

음성 인식 모델(faster-whisper `small`, 약 460MB)은 서버를 처음 켤 때 한 번 내려받는다. 받는 동안에도 서버는 뜨고, 음성 답변만 모델이 준비된 뒤에 된다.

## 켜기

| 파일 | 설명 |
| --- | --- |
| `run.bat` | 진짜 AI 모드. 앱의 모든 기능이 동작한다 |
| `run_fake.bat` | 가짜 응답 모드(`TRAINER_FAKE=1`). claude·음성 모델 없이 앱 화면과 연결만 시험할 때 |

명령으로 직접 켜려면 `server` 폴더에서:

```bash
python -m uvicorn main:app --host 0.0.0.0 --port 8787
```

켜졌는지 확인: 브라우저에서 `http://localhost:8787/health` 를 열면 `{"ok":true,"claude":"...","model":"sonnet"}` 가 나온다.

## 앱에서 연결하기

앱 홈 위쪽의 `● 서버 연결됨` 표시를 누르면 서버 주소를 바꿀 수 있다.

- **에뮬레이터**: `10.0.2.2:8787` (기본값). 에뮬레이터가 PC 를 부르는 주소다.
- **실제 폰**: PC 와 같은 와이파이에 연결하고, PC 의 IP 를 `192.168.0.12:8787` 처럼 입력한다. IP 는 `run.bat` 이 켜질 때 보여 주는 `IPv4` 주소다.
  - 처음 켤 때 Windows 방화벽 창이 뜨면 **개인 네트워크 허용**을 누른다. 안 뜨거나 막혔다면 Windows Defender 방화벽에서 `python.exe` 의 인바운드를 허용한다.
  - 표시가 `● 서버 연결 안 됨` 이면: 같은 와이파이인지, 서버가 켜져 있는지, 방화벽, 주소 오타를 차례로 확인한다. 일부 공용·학교 와이파이는 기기끼리의 통신을 막으므로 휴대폰 핫스팟에 PC 를 연결해 시험한다.
- Android 17(API 37) 이상은 처음 연결할 때 "로컬 네트워크" 권한을 묻는다. 허용해야 서버에 붙는다.

표시 옆의 괄호: `(가짜 모드)` 는 `run_fake.bat` 으로 켠 것, `(claude 없음)` 은 서버가 `claude` 를 못 찾은 것이다.

## 폴더

| 파일 | 역할 |
| --- | --- |
| `main.py` | FastAPI 앱, 면접 세션과 API |
| `rules.py` | 꼬리질문·주제 수 규칙(질문 수는 AI 가 아니라 서버가 센다) |
| `claude_ai.py` | `claude` CLI 호출, 면접관 프롬프트, 리포트 |
| `fake_ai.py` | 가짜 면접관 |
| `stt.py` | 음성 인식(faster-whisper)과 음성 지표 |
| `repo_ai.py` | GitHub 레포 분석(보안 규칙 포함, DESIGN.md 4.4) |
| `resume_parse.py` | 이력서 PDF 글자 추출 |
| `tools/gen_concepts.py` | CS 개념 학습 콘텐츠(`android/.../assets/concepts.json`)를 claude 로 만드는 개발용 스크립트. 서버와 별개로 한 번 돌려 결과를 앱에 넣는다 |

실행 중 생기는 폴더(`logs/`, `claude_work/`, `repos/`, `repo_cache/`)는 git 에 올리지 않는다. `logs/` 에는 면접 기록이, `repo_cache/` 에는 GitHub 분석 결과가 남는다.

## 환경 변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `TRAINER_FAKE` | (없음) | `1` 이면 가짜 응답 모드 |
| `TRAINER_MODEL` | `sonnet` | 면접관이 쓰는 Claude 모델 |
| `TRAINER_TIMEOUT` | `80` | claude 한 번 호출의 제한 시간(초). 앱의 읽기 제한(90초)보다 짧게 |
| `TRAINER_STT_MODEL` | `small` | Whisper 모델 크기(`tiny`/`base`/`small`/`medium`). 느리면 줄인다 |
| `TRAINER_REPO_TIMEOUT` | `300` | GitHub 분석 전체 제한 시간(초) |
| `CLAUDE_BIN` | (자동) | `claude` 실행 파일 경로를 직접 지정 |
