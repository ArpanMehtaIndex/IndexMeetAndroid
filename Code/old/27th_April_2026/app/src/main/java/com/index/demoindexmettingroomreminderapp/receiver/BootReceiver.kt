package com.index.demoindexmettingroomreminderapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.service.MeetingSyncService
import com.index.demoindexmettingroomreminderapp.worker.recovery.RecoveryScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RecoveryScheduler.schedulePeriodicRecovery(context)
                if (PreferenceHelper.getSelectedEmirate(context) != null) {
                    MeetingSyncService.start(context)
                }
            }
        }
    }
}
