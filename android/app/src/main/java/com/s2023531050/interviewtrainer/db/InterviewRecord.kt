package com.s2023531050.interviewtrainer.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** 지난 면접 한 건. 리포트 전체(질문, 답변, 피드백)는 서버가 준 JSON 그대로 reportJson 에 둔다. */
@Entity(tableName = "interview_record")
data class InterviewRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,        // 회사 / 직무 스냅샷. 비어 있으면 면접 종류
    val mode: String,         // cs / cover / mixed
    val answerMode: String,   // text / voice
    val startedAt: Long,      // 면접 종료(리포트 생성) 시각, epoch millis
    val overallScore: Int,    // 0~100
    val reportJson: String,
)

@Dao
interface InterviewRecordDao {
    @Insert
    suspend fun insert(record: InterviewRecord): Long

    @Query("SELECT * FROM interview_record ORDER BY startedAt DESC")
    suspend fun all(): List<InterviewRecord>

    @Query("SELECT * FROM interview_record WHERE id = :id")
    suspend fun get(id: Long): InterviewRecord?

    @Query("SELECT * FROM interview_record ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): InterviewRecord?

    @Query("SELECT COUNT(*) FROM interview_record")
    suspend fun count(): Int

    @Query("DELETE FROM interview_record WHERE id = :id")
    suspend fun delete(id: Long)
}
