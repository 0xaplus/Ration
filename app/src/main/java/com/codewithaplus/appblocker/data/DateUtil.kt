package com.codewithaplus.appblocker.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
