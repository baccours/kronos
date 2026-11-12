package com.baccours.kronos

import android.app.Application
import com.baccours.kronos.repository.SettingsRepository
import com.baccours.kronos.repository.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KronosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load once per process, so the service never runs on default settings.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            SettingsStore.load(SettingsRepository(this@KronosApp).settings.first())
        }
    }
}
