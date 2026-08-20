package com.codewithaplus.appblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codewithaplus.appblocker.data.AppDatabase
import com.codewithaplus.appblocker.data.onboardingCompleteFlow
import com.codewithaplus.appblocker.ui.AppPickerScreen
import com.codewithaplus.appblocker.ui.DashboardScreen
import com.codewithaplus.appblocker.ui.OnboardingScreen
import com.codewithaplus.appblocker.ui.SetLimitScreen
import com.codewithaplus.appblocker.ui.SettingsScreen
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppBlockerNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppBlockerNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                val complete = onboardingCompleteFlow(context).first()
                navController.navigate(if (complete) "dashboard" else "onboarding") {
                    popUpTo("splash") { inclusive = true }
                }
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onAddApp = { navController.navigate("app_picker") },
                onOpenSettings = { navController.navigate("settings") },
                onAppClick = { pkg -> navController.navigate("edit_limit/$pkg") },
                onFixPermissions = { navController.navigate("onboarding") }
            )
        }
        composable("app_picker") {
            var trackedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                trackedPackages = AppDatabase.getInstance(context)
                    .trackedAppDao().getAll().first().map { it.packageName }.toSet()
            }
            AppPickerScreen(
                alreadyTrackedPackages = trackedPackages,
                onNext = { selected -> navController.navigate("set_limit/${selected.joinToString(",")}") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "set_limit/{pkgs}",
            arguments = listOf(navArgument("pkgs") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkgs = backStackEntry.arguments?.getString("pkgs")?.split(",") ?: emptyList()
            SetLimitScreen(
                packageNames = pkgs,
                onDone = { navController.popBackStack("dashboard", inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "edit_limit/{pkg}",
            arguments = listOf(navArgument("pkg") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString("pkg") ?: ""
            SetLimitScreen(
                packageNames = listOf(pkg),
                onDone = { navController.popBackStack("dashboard", inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditApp = { pkg -> navController.navigate("edit_limit/$pkg") }
            )
        }
    }
}
