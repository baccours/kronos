package com.baccours.kronos

import android.app.Application
import com.baccours.kronos.audio.ClipCatalog
import com.baccours.kronos.audio.ClipLibrary
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
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Load once per process, so the service never runs on default settings.
        scope.launch { SettingsStore.load(SettingsRepository(this@KronosApp).settings.first()) }
        // Decode the fixed phrases early so "Get ready" starts instantly.
        scope.launch { ClipLibrary.get(this@KronosApp).preload(ClipCatalog.clipsForAll(ClipCatalog.PHRASES)) }
    }
}
