package com.s2023531050.interviewtrainer.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** DB 버전 올리기. SQL 은 app/schemas/.../N.json 의 createSql 과 같아야 Room 의 검사를 통과한다. */
object Migrations {
    /** 1 → 2: 개념 학습 테이블 3개 추가(기존 면접 기록은 그대로 둔다) */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `concept_progress` (`conceptId` TEXT NOT NULL, `level` INTEGER NOT NULL, " +
                    "`studiedAt` INTEGER, `quizAttempts` INTEGER NOT NULL, `quizCorrect` INTEGER NOT NULL, " +
                    "`nextReviewAt` INTEGER, PRIMARY KEY(`conceptId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wrong_answer` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`conceptId` TEXT NOT NULL, `quizIndex` INTEGER NOT NULL, `chosen` INTEGER NOT NULL, " +
                    "`wrongCount` INTEGER NOT NULL, `lastWrongAt` INTEGER NOT NULL, `resolved` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_wrong_answer_conceptId_quizIndex` " +
                    "ON `wrong_answer` (`conceptId`, `quizIndex`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `daily_quiz` (`date` TEXT NOT NULL, `items` TEXT NOT NULL, " +
                    "`completedAt` INTEGER, `correctCount` INTEGER NOT NULL, PRIMARY KEY(`date`))"
            )
        }
    }
}
