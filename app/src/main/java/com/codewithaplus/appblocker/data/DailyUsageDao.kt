package com.codewithaplus.appblocker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DailyUsageDao {
    @Insert
    suspend fun insert(usage: DailyUsage): Long

    @Query("SELECT * FROM DailyUsage WHERE packageName = :packageName AND date = :date LIMIT 1")
    suspend fun getForPackageAndDate(packageName: String, date: String): DailyUsage?

    @Query("UPDATE DailyUsage SET secondsUsedToday = secondsUsedToday + :seconds WHERE packageName = :packageName AND date = :date")
    suspend fun addSeconds(packageName: String, date: String, seconds: Int)

    @Query("UPDATE DailyUsage SET isLockedToday = :locked WHERE packageName = :packageName AND date = :date")
    suspend fun setLocked(packageName: String, date: String, locked: Boolean)
}
