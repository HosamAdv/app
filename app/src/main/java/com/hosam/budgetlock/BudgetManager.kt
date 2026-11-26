package com.hosam.budgetlock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class BudgetManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "budget_prefs"
        const val BRIQ_PACKAGE = "com.riyad.bank.brq"
        const val RIYAD_PACKAGE = "com.riyadbank.mobile"
        val MONITORED_PACKAGES = setOf(BRIQ_PACKAGE, RIYAD_PACKAGE)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDailyLimit(): Double = prefs.getFloat("daily_limit", 0f).toDouble()
    fun setDailyLimit(limit: Double) = prefs.edit().putFloat("daily_limit", limit.toFloat()).apply()

    fun getTotalSpent(): Double {
        checkAndResetDaily()
        return prefs.getFloat("total_spent", 0f).toDouble()
    }

    fun addExpense(amount: Double) {
        val current = getTotalSpent()
        prefs.edit().putFloat("total_spent", (current + amount).toFloat()).apply()
    }

    fun isLocked(): Boolean {
        val limit = getDailyLimit()
        return limit > 0 && getTotalSpent() >= limit
    }

    fun reset() {
        prefs.edit()
            .putFloat("total_spent", 0f)
            .putString("last_reset_date", today())
            .apply()
    }

    private fun checkAndResetDaily() {
        val lastReset = prefs.getString("last_reset_date", "")
        if (lastReset != today()) reset()
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}