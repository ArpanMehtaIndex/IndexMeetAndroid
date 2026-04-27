package com.index.demoindexmettingroomreminderapp.background.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                _root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.recovery.RecoveryScheduler.schedulePeriodicRecovery(context)
                if (PreferenceHelper.getSelectedEmirate(context) != null) {
                    _root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.sync.MeetingSyncScheduler.scheduleImmediateSync(context)
                }
            }
        }
    }
}
