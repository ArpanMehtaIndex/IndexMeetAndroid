package com.index.demoindexmettingroomreminderapp.background.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                _root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.recovery.RecoveryScheduler.schedulePeriodicRecovery(context)
                if (PreferenceHelper.getSelectedEmirate(context) != null) {
                    _root_ide_package_.com.index.demoindexmettingroomreminderapp.background.worker.sync.MeetingSyncScheduler.scheduleImmediateSync(context)
                }
            }
        }
    }
}
