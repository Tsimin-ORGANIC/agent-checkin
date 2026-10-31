package com.agentcheckin.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.agentcheckin.android.checkin.CheckinRepository
import com.agentcheckin.android.data.AppSettings
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.notification.Notifier

class CheckinWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val platform = runCatching {
            Platform.valueOf(inputData.getString(KEY_PLATFORM).orEmpty())
        }.getOrNull() ?: return Result.success()

        val settings = AppSettings(applicationContext)
        if (!settings.autoCheckin) return Result.success()

        val repository = CheckinRepository(applicationContext)
        if (!repository.isLoggedIn(platform)) return Result.success()

        val result = repository.checkin(platform)
        Notifier.showResult(applicationContext, result)
        return Result.success()
    }

    companion object {
        const val KEY_PLATFORM = "platform"
    }
}
