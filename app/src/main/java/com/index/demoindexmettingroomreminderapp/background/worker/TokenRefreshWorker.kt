package com.index.demoindexmettingroomreminderapp.background.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.index.demoindexmettingroomreminderapp.data.PreferenceHelper
import com.index.demoindexmettingroomreminderapp.web.repository.MeetingAppRepo
import kotlinx.coroutines.flow.first

class TokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = MeetingAppRepo(applicationContext)

        return try {
            // Use .first() to get the first emission from the Flow
            val result = repository.getAccessToken().first()
            result.fold(
                onSuccess = { tokenResponse ->
                    // If successful, save the new token
                    PreferenceHelper.saveTokenResponse(applicationContext, tokenResponse)
                    Result.success()
                },
                onFailure = {
                    // If the API call fails, retry the work later
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            // If an unexpected exception occurs, retry
            Result.retry()
        }
    }
}
