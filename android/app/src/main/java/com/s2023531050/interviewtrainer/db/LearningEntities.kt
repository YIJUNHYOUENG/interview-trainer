package com.s2023531050.interviewtrainer.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 개념별 숙련도와 다음 복습일 (docs/DESIGN.md 6장). 행이 없는 개념은 Lv.0(미학습)이다.
 * 시각은 모두 epoch millis 이고, 복습일은 "그날 0시"로 맞춰 둔다([LearningRules.reviewAt]).
 */
@Entity(tableName = "concept_progress")
data class ConceptProgress(
    @PrimaryKey val conceptId: String,
    val level: Int,              // 1~5 (행이 있으면 1 이상)
    val studiedAt: Long?,        // 개념 상세를 처음 연 시각
    val quizAttempts: Int,       // 이 개념에서 출제된 횟수. 다음에 낼 문제 번호를 돌려 가며 고르는 데 쓴다
    val quizCorrect: Int,
    val nextReviewAt: Long?,     // 퀴즈를 푼 적이 없으면 null
)

/** 오답노트. (conceptId, quizIndex) 한 문제에 한 행. */
@Entity(
    tableName = "wrong_answer",
    indices = [Index(value = ["conceptId", "quizIndex"], unique = true)],
)
data class WrongAnswer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conceptId: String,
    val quizIndex: Int,          // 개념의 퀴즈 3개 중 몇 번째(0~2)
    val chosen: Int,             // 가장 최근에 고른 보기(0~3)
    val wrongCount: Int,         // 틀린 횟수
    val lastWrongAt: Long,
    val resolved: Boolean,       // 다시 맞혀서 해결했는지
)

/**
 * 오늘의 퀴즈. 날짜(yyyy-MM-dd)가 키이고, 하루에 한 세트만 있다.
 * [items] 는 문제 3개의 JSON 배열이다([DailyItem]). 중간에 나갔다 와도 같은 문제를 이어 풀기 위해 저장한다.
 */
@Entity(tableName = "daily_quiz")
data class DailyQuiz(
    @PrimaryKey val date: String,
    val items: String,
    val completedAt: Long?,
    val correctCount: Int,
)

@Dao
interface ConceptProgressDao {
    @Query("SELECT * FROM concept_progress WHERE conceptId = :id")
    suspend fun get(id: String): ConceptProgress?

    @Query("SELECT * FROM concept_progress")
    suspend fun all(): List<ConceptProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ConceptProgress)

    /** 복습일이 [before] 보다 이른(= 오늘 포함 지난) 개념 수 */
    @Query("SELECT COUNT(*) FROM concept_progress WHERE nextReviewAt IS NOT NULL AND nextReviewAt < :before")
    suspend fun countDue(before: Long): Int
}

@Dao
interface WrongAnswerDao {
    @Query("SELECT * FROM wrong_answer WHERE conceptId = :conceptId AND quizIndex = :quizIndex")
    suspend fun find(conceptId: String, quizIndex: Int): WrongAnswer?

    @Query("SELECT * FROM wrong_answer WHERE resolved = :resolved ORDER BY lastWrongAt DESC")
    suspend fun list(resolved: Boolean): List<WrongAnswer>

    @Query("SELECT COUNT(*) FROM wrong_answer WHERE resolved = 0")
    suspend fun countUnresolved(): Int

    @Query("SELECT conceptId FROM wrong_answer WHERE resolved = 0")
    suspend fun unresolvedConceptIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wrong: WrongAnswer)
}

@Dao
interface DailyQuizDao {
    @Query("SELECT * FROM daily_quiz WHERE date = :date")
    suspend fun get(date: String): DailyQuiz?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quiz: DailyQuiz)
}
