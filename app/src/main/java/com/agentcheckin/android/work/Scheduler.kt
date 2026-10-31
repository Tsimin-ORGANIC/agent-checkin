package com.agentcheckin.android.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.agentcheckin.android.data.AppSettings
import com.agentcheckin.android.data.Platform
import com.agentcheckin.android.data.SecureStore
import com.agentcheckin.android.util.TimeUtils
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val DEFAULT_WB_TIME = "09:00"
    private const val DEFAULT_QODER_TIME = "10:05"

    fun ensureScheduled(context: Context) {
        val settings = AppSettings(context)
        if (!settings.autoCheckin) {
            cancelAll(context)
            return
        }
        Platform.entries.forEach { platform ->
            val delay = TimeUtils.nextDelayMillis(
                settings.timeFor(platform),
                defaultTime(platform)
            )
            val request = PeriodicWorkRequestBuilder<CheckinWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(CheckinWorker.KEY_PLATFORM to platform.name)
                )
                .addTag(tag(platform))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueName(platform),
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun reschedule(context: Context) {
        val settings = AppSettings(context)
        if (!settings.autoCheckin) {
            cancelAll(context)
            return
        }
        Platform.entries.forEach { platform ->
            val delay = TimeUtils.nextDelayMillis(
                settings.timeFor(platform),
                defaultTime(platform)
            )
            val request = PeriodicWorkRequestBuilder<CheckinWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(CheckinWorker.KEY_PLATFORM to platform.name)
                )
                .addTag(tag(platform))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueName(platform),
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }
    }

    fun maybeCatchUp(context: Context) {
        val settings = AppSettings(context)
        if (!settings.autoCheckin) return
        val store = SecureStore(context)
        Platform.entries.forEach { platform ->
            if (!store.isLoggedIn(platform)) return@forEach
            val currentKey = if (platform == Platform.QODER) {
                TimeUtils.qoderWindowKey()
            } else {
                TimeUtils.todayKey()
            }
            if (settings.lastSuccessDay(platform) != currentKey) {
                scheduleCatchUp(context, platform)
            }
        }
    }

    fun scheduleCatchUp(context: Context, platform: Platform) {
        val store = SecureStore(context)
        if (!store.isLoggedIn(platform)) return
        val request = OneTimeWorkRequestBuilder<CheckinWorker>()
            .setInputData(
                workDataOf(CheckinWorker.KEY_PLATFORM to platform.name)
            )
            .addTag("catchup_${platform.storageKey}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            catchUpName(platform),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(context: Context) {
        Platform.entries.forEach { platform ->
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(platform))
            WorkManager.getInstance(context).cancelUniqueWork(catchUpName(platform))
        }
    }

    private fun uniqueName(platform: Platform): String = "daily_${platform.storageKey}"

    private fun catchUpName(platform: Platform): String = "catchup_${platform.storageKey}"

    private fun tag(platform: Platform): String = "auto_${platform.storageKey}"

    private fun defaultTime(platform: Platform): String {
        return if (platform == Platform.WORKBUDDY) DEFAULT_WB_TIME else DEFAULT_QODER_TIME
    }
}
