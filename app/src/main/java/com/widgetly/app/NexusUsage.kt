package com.widgetly.app

import android.content.Context
import java.time.LocalDate

object NexusUsage {
    private fun key(): String = "nexus_daily_items_${LocalDate.now()}"
    fun used(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key(), 0)
    fun budget(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("daily_item_budget", 20)
    fun canOpen(context: Context): Boolean = used(context) < budget(context)
    fun recordOpen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(key(), 0)
        val limit = prefs.getInt("daily_item_budget", 20)
        if (current >= limit) return false
        prefs.edit().putInt(key(), current + 1).apply()
        return true
    }
}
