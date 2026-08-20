package com.codewithaplus.appblocker.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.codewithaplus.appblocker.data.AppDatabase
import com.codewithaplus.appblocker.data.TrackedApp
import kotlinx.coroutines.launch

private const val MIN_LIMIT_MINUTES = 5
private const val MAX_LIMIT_MINUTES = 240
private const val LIMIT_STEP_MINUTES = 5
private const val DEFAULT_LIMIT_MINUTES = 60

private data class TargetApp(val packageName: String, val appName: String, val icon: Drawable?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLimitScreen(
    packageNames: List<String>,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var targets by remember { mutableStateOf<List<TargetApp>>(emptyList()) }
    var minutes by remember { mutableIntStateOf(DEFAULT_LIMIT_MINUTES) }
    var isEditMode by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(packageNames) {
        val pm = context.packageManager
        targets = packageNames.map { pkg ->
            val icon = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            TargetApp(pkg, label, icon)
        }

        if (packageNames.size == 1) {
            val existing = db.trackedAppDao().getByPackageName(packageNames.first())
            if (existing != null) {
                isEditMode = true
                minutes = (existing.dailyLimitSeconds / 60).coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
            }
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit limit" else "Set daily limit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(targets) { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        target.icon?.let {
                            Image(
                                bitmap = it.toBitmap().asImageBitmap(),
                                contentDescription = target.appName,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(target.appName, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            Text(
                text = "$minutes minutes / day",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Slider(
                value = minutes.toFloat(),
                onValueChange = { newValue ->
                    val stepped = (newValue / LIMIT_STEP_MINUTES).toInt() * LIMIT_STEP_MINUTES
                    minutes = stepped.coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
                },
                valueRange = MIN_LIMIT_MINUTES.toFloat()..MAX_LIMIT_MINUTES.toFloat(),
                steps = (MAX_LIMIT_MINUTES - MIN_LIMIT_MINUTES) / LIMIT_STEP_MINUTES - 1
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditMode) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            db.trackedAppDao().delete(packageNames.first())
                            onDone()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(" Remove", modifier = Modifier.padding(start = 4.dp))
                    }
                } else {
                    Text("")
                }

                Button(onClick = {
                    scope.launch {
                        val seconds = minutes * 60
                        if (isEditMode) {
                            db.trackedAppDao().updateLimit(packageNames.first(), seconds)
                        } else {
                            targets.forEach { target ->
                                db.trackedAppDao().insert(
                                    TrackedApp(
                                        packageName = target.packageName,
                                        appName = target.appName,
                                        dailyLimitSeconds = seconds,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        onDone()
                    }
                }) {
                    Text("Save")
                }
            }
        }
    }
}
