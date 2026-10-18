package com.mobilemotodroid.fuji.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ActivityEntity::class,
        TrackPointEntity::class,
        PhotoEntity::class,
        RouteEntity::class,
        RoutePointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun photoDao(): PhotoDao
    abstract fun routeDao(): RouteDao

    companion object {
        const val NAME = "fuji.db"

        /**
         * NFR-MNT-03：スキーマを変えたら version を上げ、ここに Migration を追加する。
         * fallbackToDestructiveMigration は使わない（記録が消えるため）。
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
