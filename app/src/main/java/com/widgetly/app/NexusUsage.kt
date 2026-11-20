package com.widgetly.app

import android.content.Context
import java.time.LocalDate

object NexusUsage {
    private fun day(): String = LocalDate.now().toString()
    private fun itemKey() = "nexus_daily_items_${day()}"
    private fun secondsKey() = "nexus_discover_seconds_${day()}"
    fun used(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(itemKey(), 0)
    fun budget(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("daily_item_budget", 20)
    fun seconds(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(secondsKey(), 0)
    fun timeBudgetSeconds(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt("daily_time_budget_minutes", 30) * 60
    fun canOpen(context: Context): Boolean = used(context) < budget(context) && seconds(context) < timeBudgetSeconds(context)
    fun addDiscoverSecond(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(secondsKey(), prefs.getInt(secondsKey(), 0) + 1).apply()
    }
    fun recordOpen(context: Context): Boolean {
        if (!canOpen(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(itemKey(), used(context) + 1).apply()
        return true
    }
}
