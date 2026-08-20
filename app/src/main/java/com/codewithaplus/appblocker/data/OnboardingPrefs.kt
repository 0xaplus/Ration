package com.codewithaplus.appblocker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_blocker_prefs")
private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")

fun onboardingCompleteFlow(context: Context): Flow<Boolean> =
    context.dataStore.data.map { it[ONBOARDING_COMPLETE_KEY] ?: false }

suspend fun setOnboardingComplete(context: Context, complete: Boolean) {
    context.dataStore.edit { it[ONBOARDING_COMPLETE_KEY] = complete }
}
