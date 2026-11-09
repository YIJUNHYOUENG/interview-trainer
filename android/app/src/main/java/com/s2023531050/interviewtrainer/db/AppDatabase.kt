package com.s2023531050.interviewtrainer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 Room DB (study.db). 테이블을 추가할 때는 entities 에 클래스를 넣고 version 을 올린다.
 * 아직 배포 전이라 스키마가 바뀌면 DB 를 지우고 새로 만든다(fallbackToDestructiveMigration).
 */
@Database(entities = [InterviewRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun interviewRecords(): InterviewRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "study.db")
                .fallbackToDestructiveMigration(true)
                .build()
                .also { instance = it }
        }
    }
}
