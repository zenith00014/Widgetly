package com.widgetly.app

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object NotificationReplyStore {
    data class ReplyAction(val pendingIntent:PendingIntent,val remoteInput:RemoteInput)
    private val actions=ConcurrentHashMap<String,ReplyAction>()
    fun put(key:String,action:ReplyAction){actions[key]=action}
    fun has(key:String)=actions.containsKey(key)
    fun reply(context:Context,key:String,text:String) {
        val action=actions[key]?:return
        if(text.isBlank())return
        val fillIn=Intent()
        val results=android.os.Bundle().apply{putCharSequence(action.remoteInput.resultKey,text)}
        RemoteInput.addResultsToIntent(arrayOf(action.remoteInput),fillIn,results)
        runCatching{action.pendingIntent.send(context,0,fillIn)}
    }
}

class WidgetlyNotificationListener:NotificationListenerService() {
    override fun onNotificationPosted(sbn:StatusBarNotification) {
        val source=when(sbn.packageName){
            "com.whatsapp"->"WhatsApp"
            "org.telegram.messenger"->"Telegram"
            "com.instagram.android"->"Instagram"
            "com.facebook.orca"->"Messenger"
            "com.facebook.katana"->"Facebook"
            else->return
        }
        val extras=sbn.notification.extras
        val title=extras.getCharSequence("android.title")?.toString()?.take(100)?:source
        val message=extras.getCharSequence("android.text")?.toString()?.take(500)?:return
        if(message.isBlank())return
        val key=sbn.key
        sbn.notification.actions?.forEach{action->
            val remote=action.remoteInputs?.firstOrNull()
            if(remote!=null)NotificationReplyStore.put(key,NotificationReplyStore.ReplyAction(action.actionIntent,remote))
        }
        val prefs=getSharedPreferences("nexus_prefs",MODE_PRIVATE)
        val old=runCatching{JSONArray(prefs.getString("social_notifications","[]"))}.getOrDefault(JSONArray())
        val next=JSONArray()
        next.put(JSONObject().put("source",source).put("title",title).put("message",message).put("package",sbn.packageName).put("key",key).put("time",System.currentTimeMillis()))
        for(i in 0 until minOf(old.length(),59))next.put(old.getJSONObject(i))
        prefs.edit().putString("social_notifications",next.toString()).apply()
    }
}
