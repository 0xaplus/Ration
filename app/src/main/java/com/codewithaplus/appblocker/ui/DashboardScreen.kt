package com.codewithaplus.appblocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.codewithaplus.appblocker.data.AppDatabase
import com.codewithaplus.appblocker.data.AppPermission
import com.codewithaplus.appblocker.data.TrackedAppWithUsage
import com.codewithaplus.appblocker.data.todayDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onAppClick: (String) -> Unit,
    onFixPermissions: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    var trackedApps by remember { mutableStateOf<List<TrackedAppWithUsage>>(emptyList()) }
    var missingPermissions by remember {
        mutableStateOf(AppPermission.entries.filterNot { it.isGranted(context) })
    }

    LaunchedEffect(Unit) {
        db.trackedAppDao().getTrackedAppsWithUsage(todayDateString()).collect { trackedApps = it }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missingPermissions = AppPermission.entries.filterNot { it.isGranted(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ration") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = "Add app")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (missingPermissions.isNotEmpty()) {
                PermissionBanner(missingPermissions = missingPermissions, onClick = onFixPermissions)
            }

            if (trackedApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No apps being tracked yet.\nTap + to add one.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(trackedApps, key = { it.packageName }) { app ->
                        TrackedAppRow(app = app, onClick = { onAppClick(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(missingPermissions: List<AppPermission>, onClick: () -> Unit) {
    val message = if (missingPermissions.size == 1) {
        "${missingPermissions.first().title} permission is off — Ration may not work correctly. Tap to fix."
    } else {
        "${missingPermissions.size} permissions need attention — Ration may not work correctly. Tap to fix."
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                message,
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun TrackedAppRow(app: TrackedAppWithUsage, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try { context.packageManager.getApplicationIcon(app.packageName) } catch (e: Exception) { null }
    }
    val usedMinutes = app.secondsUsedToday / 60
    val limitMinutes = app.dailyLimitSeconds / 60
    val progress = if (app.dailyLimitSeconds > 0) {
        (app.secondsUsedToday.toFloat() / app.dailyLimitSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Image(
                        bitmap = it.toBitmap().asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(app.appName, modifier = Modifier.padding(start = 12.dp).weight(1f))
                Text("${usedMinutes}m / ${limitMinutes}m", style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = if (progress >= 1f) Color.Red else MaterialTheme.colorScheme.primary
            )
        }
    }
}
