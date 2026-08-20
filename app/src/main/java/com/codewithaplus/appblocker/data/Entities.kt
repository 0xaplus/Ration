package com.codewithaplus.appblocker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class TrackedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Int,
    val createdAt: Long
)

@Entity(
    indices = [Index(value = ["packageName", "date"], unique = true)]
)
data class DailyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val date: String, // "yyyy-MM-dd", local timezone
    val secondsUsedToday: Int,
    val isLockedToday: Boolean
)
