package com.aero.bar

import android.app.Application
import com.aero.bar.crash.CrashHandler
import com.aero.bar.security.SecurityGuard
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AeroBarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SecurityGuard.enforceIntegrity(this)
        CrashHandler.install(this)
    }
}
