package com.codewithaplus.appblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class TrackedAppWithUsage(
    val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Int,
    val secondsUsedToday: Int
)

@Dao
interface TrackedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: TrackedApp)

    @Query("SELECT * FROM TrackedApp WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): TrackedApp?

    @Query("SELECT * FROM TrackedApp")
    fun getAll(): Flow<List<TrackedApp>>

    @Query("DELETE FROM TrackedApp WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE TrackedApp SET dailyLimitSeconds = :dailyLimitSeconds WHERE packageName = :packageName")
    suspend fun updateLimit(packageName: String, dailyLimitSeconds: Int)

    @Query(
        """
        SELECT t.packageName as packageName, t.appName as appName, t.dailyLimitSeconds as dailyLimitSeconds,
               COALESCE(d.secondsUsedToday, 0) as secondsUsedToday
        FROM TrackedApp t
        LEFT JOIN DailyUsage d ON d.packageName = t.packageName AND d.date = :today
        ORDER BY t.createdAt DESC
        """
    )
    fun getTrackedAppsWithUsage(today: String): Flow<List<TrackedAppWithUsage>>
}
