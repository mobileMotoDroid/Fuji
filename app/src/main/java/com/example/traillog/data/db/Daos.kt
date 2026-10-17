package com.example.traillog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observe(id: Long): Flow<ActivityEntity?>

    /** FR-ACT-01：保存済みの活動を新しい順に。 */
    @Query("SELECT * FROM activities WHERE status = 'FINISHED' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<ActivityEntity>>

    /** FR-REC-05：記録中・一時停止中のまま残っている活動。 */
    @Query("SELECT * FROM activities WHERE status IN ('RECORDING', 'PAUSED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun findUnfinished(): ActivityEntity?

    @Query("UPDATE activities SET title = :title, memo = :memo WHERE id = :id")
    suspend fun updateTitleMemo(id: Long, title: String, memo: String)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity): Long

    @Query("SELECT * FROM track_points WHERE activityId = :activityId ORDER BY time ASC, id ASC")
    suspend fun getForActivity(activityId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE activityId = :activityId ORDER BY time DESC, id DESC LIMIT 1")
    suspend fun getLast(activityId: Long): TrackPointEntity?
}

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE activityId = :activityId ORDER BY takenAt ASC")
    fun observeForActivity(activityId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE activityId = :activityId")
    suspend fun getForActivity(activityId: Long): List<PhotoEntity>
}

@Dao
abstract class RouteDao {
    @Insert
    abstract suspend fun insertRoute(route: RouteEntity): Long

    @Insert
    abstract suspend fun insertPoints(points: List<RoutePointEntity>)

    /** ルートと点をまとめて保存する。 */
    @Transaction
    open suspend fun insertWithPoints(route: RouteEntity, points: List<RoutePointEntity>): Long {
        val id = insertRoute(route)
        insertPoints(points.map { it.copy(routeId = id) })
        return id
    }

    @Query("SELECT * FROM routes ORDER BY importedAt DESC")
    abstract fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract suspend fun get(id: Long): RouteEntity?

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY seq ASC")
    abstract suspend fun getPoints(routeId: Long): List<RoutePointEntity>

    @Query("DELETE FROM routes WHERE id = :id")
    abstract suspend fun delete(id: Long)
}
