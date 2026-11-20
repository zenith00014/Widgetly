package com.widgetly.app

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification

object NexusNotificationBridge {
    private val actions = mutableMapOf<String, Pair<StatusBarNotification, Notification.Action>>()

    @Synchronized fun register(sbn: StatusBarNotification) {
        val action = sbn.notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        if (action != null) actions[sbn.key] = sbn to action
        while (actions.size > 80) actions.remove(actions.keys.first())
    }

    @Synchronized fun canReply(key: String): Boolean = actions.containsKey(key)

    fun reply(key: String, message: String): Boolean {
        val pair = synchronized(this) { actions[key] } ?: return false
        return runCatching {
            val (sbn, action) = pair
            val remoteInput = action.remoteInputs?.firstOrNull() ?: return false
            val fillIn = Intent()
            val results = android.os.Bundle().apply { putCharSequence(remoteInput.resultKey, message) }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, results)
            action.actionIntent.send(null, 0, fillIn)
            true
        }.getOrDefault(false)
    }

    fun openOriginal(context: Context, packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
