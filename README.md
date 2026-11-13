# 개발자 면접 트레이너

CS 개념 학습부터 AI 모의 면접, 음성 피드백, GitHub 프로젝트 질문까지 한 앱에서 연습하는 Android 앱 (스마트모바일프로그래밍 기말 프로젝트).

- 설계 문서: [docs/DESIGN.md](docs/DESIGN.md)

## 폴더

| 경로 | 내용 |
| --- | --- |
| `android/` | Android Studio 프로젝트 (Kotlin + XML View, minSdk 24 / targetSdk 37) |
| `server/` | PC 브릿지 서버 (FastAPI). AI 면접관·음성 인식·GitHub 분석을 PC 에서 실행 |
| `docs/` | 설계 문서와 그림 |

## 열기

Android Studio 에서 `android` 폴더를 연다.

## 면접 기능 실행

AI 모의 면접은 PC 서버가 켜져 있어야 동작한다. `server/run.bat` 을 실행하고, 앱 홈 위쪽의 서버 표시가 `● 서버 연결됨` 인지 확인한다.
준비물과 실제 폰 연결 방법은 [server/README.md](server/README.md) 를 본다.
