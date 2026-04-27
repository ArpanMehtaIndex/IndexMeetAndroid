package com.index.demoindexmettingroomreminderapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.worker.recovery.RecoveryScheduler
import com.index.demoindexmettingroomreminderapp.worker.sync.MeetingSyncScheduler

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                RecoveryScheduler.schedulePeriodicRecovery(context)
                if (PreferenceHelper.getSelectedEmirate(context) != null) {
                    MeetingSyncScheduler.scheduleImmediateSync(context)
                }
            }
        }
    }
}
