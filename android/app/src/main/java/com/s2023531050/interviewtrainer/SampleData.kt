package com.s2023531050.interviewtrainer

/** 화면 모양을 보여 주기 위한 임시 데이터. 실제 개념 JSON · Room 이 붙으면 이 파일은 지운다. */

data class ConceptItem(
    val title: String,
    val summary: String,
    val level: Int,          // 0 = 미학습, 1~5
    val review: String,      // "복습 오늘", "3일 뒤", "아직 안 봄"
    val reviewDue: Boolean,
)

data class WrongItem(
    val category: String,
    val wrongCount: Int,
    val question: String,
    val myAnswer: String,
    val correct: String,
    val explanation: String?, // null 이면 접힌 상태
)

data class HistoryItem(
    val score: Int,
    val title: String,
    val meta: String,
)

object SampleData {
    val osConcepts = listOf(
        ConceptItem("프로세스와 스레드", "실행 단위의 차이와 메모리 공유 방식", 2, "복습 오늘", true),
        ConceptItem("데드락", "발생 조건 4가지와 해결 방법", 3, "3일 뒤", false),
        ConceptItem("가상 메모리", "페이지 교체와 주소 변환", 0, "아직 안 봄", false),
        ConceptItem("컨텍스트 스위칭", "프로세스 전환 때 저장·복원되는 정보", 1, "1일 뒤", false),
        ConceptItem("CPU 스케줄링", "FCFS, SJF, 라운드 로빈 비교", 4, "7일 뒤", false),
        ConceptItem("세마포어와 뮤텍스", "동기화 도구의 차이", 2, "2일 뒤", false),
    )

    val wrongItems = listOf(
        WrongItem("네트워크", 2, "TCP 가 연결 설정에 사용하는 절차는?", "4-way handshake", "3-way handshake", null),
        WrongItem(
            "운영체제", 1, "교착 상태 발생 조건이 아닌 것은?", "점유 대기", "선점",
            "교착 상태의 네 가지 조건은 상호 배제, 점유 대기, 비선점, 환형 대기다. 자원을 빼앗을 수 있으면(선점) 교착 상태가 생기지 않는다.",
        ),
        WrongItem("DB", 1, "인덱스를 만들면 오히려 느려지는 작업은?", "조회", "삽입·수정·삭제", null),
    )

    val history = listOf(
        HistoryItem(78, "네이버 / Android", "11.01 · 혼합 · 음성"),
        HistoryItem(71, "카카오 / 서버", "10.29 · 지원서 · 텍스트"),
        HistoryItem(74, "CS 기초", "10.27 · CS · 음성"),
        HistoryItem(65, "CS 기초", "10.24 · CS · 텍스트"),
        HistoryItem(62, "토스 / Android", "10.20 · 혼합 · 텍스트"),
    )
}
