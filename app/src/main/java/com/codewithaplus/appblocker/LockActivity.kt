package com.codewithaplus.appblocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

class LockActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun launch(context: Context, packageName: String) {
            val intent = Intent(context, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Non-dismissible via back button: swallow the press instead of finishing.
        onBackPressedDispatcher.addCallback(this) { /* no-op: consume back press */ }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return finish()
        val (appName, appIcon) = loadAppInfo(packageName)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    LockScreenContent(appName = appName, appIcon = appIcon)
                }
            }
        }
    }

    private fun loadAppInfo(packageName: String): Pair<String, Drawable?> {
        val pm = packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString() to pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            packageName to null
        }
    }
}

@Composable
private fun LockScreenContent(appName: String, appIcon: Drawable?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        appIcon?.let {
            Image(
                bitmap = it.toBitmap().asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.size(96.dp)
            )
        }
        Text(
            text = appName,
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Daily limit reached",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "Resets in ${rememberCountdownToMidnight()}",
            color = Color.Gray,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun rememberCountdownToMidnight(): String {
    var remaining by remember { mutableStateOf(millisUntilNextMidnight()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remaining = millisUntilNextMidnight()
        }
    }
    val totalSeconds = remaining / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun millisUntilNextMidnight(): Long {
    val now = Calendar.getInstance()
    val midnight = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0)
}
