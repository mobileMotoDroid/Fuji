package com.mobilemotodroid.fuji.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 活動の状態。 */
enum class ActivityStatus { RECORDING, PAUSED, FINISHED }

/** 1 回分の登山記録（要件定義書 7.1 ACTIVITY）。時刻はすべて epoch ミリ秒。 */
@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routeId"), Index("status")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String = "",
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: ActivityStatus,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val maxAltitudeM: Double? = null,
    val minAltitudeM: Double? = null,
    /** 一時停止中を除いた経過時間（秒）。記録中は [resumedAt] からの時間を足して求める。 */
    val movingTimeSec: Long = 0,
    /** 直近で記録を開始・再開した時刻。一時停止中と終了後は null。 */
    val resumedAt: Long? = null,
    /** 現在の区間番号。一時停止から再開するたびに 1 増える。 */
    val currentSegment: Int = 0,
    val routeId: Long? = null,
)

/** 記録された位置の 1 点（7.1 TRACK_POINT）。記録中に 1 点ずつ追加する。 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["activityId", "time"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float,
    val speed: Float?,
    val pressureHpa: Float?,
    val segment: Int,
)

/** 活動に紐づく写真（7.1 PHOTO）。画像本体はアプリ専用領域（filesDir/photos）に保存する。 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("activityId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val filePath: String,
    val takenAt: Long,
    val latitude: Double?,
    val longitude: Double?,
)

/** GPX から読み込んだ予定ルート（7.1 ROUTE）。 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val distanceM: Double,
    val importedAt: Long,
)

/** ルートを構成する点（7.1 ROUTE_POINT）。 */
@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["routeId", "seq"])],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
)
