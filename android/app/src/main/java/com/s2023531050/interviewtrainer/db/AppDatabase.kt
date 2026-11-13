package com.s2023531050.interviewtrainer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 Room DB (study.db). 테이블을 추가하면 entities 에 넣고 version 을 올린 뒤 [Migrations] 에 마이그레이션을 더한다.
 * 스키마는 app/schemas 에 파일로 내보내진다(빌드 때 자동 생성).
 */
@Database(
    entities = [InterviewRecord::class, ConceptProgress::class, WrongAnswer::class, DailyQuiz::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun interviewRecords(): InterviewRecordDao
    abstract fun conceptProgress(): ConceptProgressDao
    abstract fun wrongAnswers(): WrongAnswerDao
    abstract fun dailyQuiz(): DailyQuizDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "study.db")
                .addMigrations(Migrations.MIGRATION_1_2)
                .fallbackToDestructiveMigration(true) // 마이그레이션이 없는 버전 차이만 DB 를 새로 만든다
                .build()
                .also { instance = it }
        }
    }
}
