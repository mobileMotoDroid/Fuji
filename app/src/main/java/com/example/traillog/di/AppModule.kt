package com.example.traillog.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.example.traillog.data.db.ActivityDao
import com.example.traillog.data.db.AppDatabase
import com.example.traillog.data.db.PhotoDao
import com.example.traillog.data.db.RouteDao
import com.example.traillog.data.db.TrackPointDao
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // 活動の削除で軌跡・写真も消えるよう、外部キー制約を有効にする
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()

    @Provides fun provideActivityDao(db: AppDatabase): ActivityDao = db.activityDao()
    @Provides fun provideTrackPointDao(db: AppDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun providePhotoDao(db: AppDatabase): PhotoDao = db.photoDao()
    @Provides fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
