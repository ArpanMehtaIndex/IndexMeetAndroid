package com.index.demoindexmettingroomreminderapp

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.index.demoindexmettingroomreminderapp.lifecycle.AppLifecycleObserver
import com.index.demoindexmettingroomreminderapp.web.client.ApiClient
import com.index.demoindexmettingroomreminderapp.worker.recovery.RecoveryScheduler

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver)
        ApiClient.initialize(this)
        RecoveryScheduler.schedulePeriodicRecovery(this)
    }
}
