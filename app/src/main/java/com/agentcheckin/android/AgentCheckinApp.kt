package com.agentcheckin.android

import android.app.Application
import com.agentcheckin.android.notification.Notifier
import com.agentcheckin.android.work.Scheduler

class AgentCheckinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        Scheduler.ensureScheduled(this)
    }
}
