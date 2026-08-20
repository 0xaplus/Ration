package com.codewithaplus.appblocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.codewithaplus.appblocker.data.InstalledAppInfo
import com.codewithaplus.appblocker.data.loadLaunchableApps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    alreadyTrackedPackages: Set<String>,
    onNext: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledAppInfo>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        apps = loadLaunchableApps(context).filter { it.packageName !in alreadyTrackedPackages }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose apps to track") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Button(
                    onClick = { onNext(selected.toList()) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("Next (${selected.size} selected)")
                }
            }
        }
    ) { padding ->
        val currentApps = apps
        if (currentApps == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(currentApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.icon.toBitmap().asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(app.appName, modifier = Modifier.padding(start = 16.dp).weight(1f))
                    Checkbox(
                        checked = app.packageName in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                        }
                    )
                }
            }
        }
    }
}
