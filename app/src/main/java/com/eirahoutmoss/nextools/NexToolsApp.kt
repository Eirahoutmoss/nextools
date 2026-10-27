package com.eirahoutmoss.nextools

import android.app.Application
import com.eirahoutmoss.nextools.diagnostics.CrashLogger

class NexToolsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
    }
}
