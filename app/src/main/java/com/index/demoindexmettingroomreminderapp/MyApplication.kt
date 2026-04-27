package com.index.demoindexmettingroomreminderapp

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.index.demoindexmettingroomreminderapp.background.lifecycle.AppLifecycleObserver
import com.index.demoindexmettingroomreminderapp.background.worker.recovery.RecoveryScheduler
import com.index.demoindexmettingroomreminderapp.web.client.ApiClient

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver)
        ApiClient.initialize(this)
       RecoveryScheduler.schedulePeriodicRecovery(this)
    }
}
