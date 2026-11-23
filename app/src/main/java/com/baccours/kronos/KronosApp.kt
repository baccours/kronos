package com.baccours.kronos

import android.app.Application
import android.content.Context
import com.baccours.kronos.audio.ClipCatalog
import com.baccours.kronos.audio.ClipLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KronosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // Loads the settings once per process, so the service never runs on default settings.
        container = AppContainer(this, scope)
        // Decode the fixed phrases early so "Get ready" starts instantly.
        scope.launch { ClipLibrary.get(this@KronosApp).preload(ClipCatalog.clipsForAll(ClipCatalog.PHRASES)) }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as KronosApp).container
