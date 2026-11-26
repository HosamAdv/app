package com.hosam.budgetlock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * يحسب الحد اليومي من الرصيد المتبقي وعدد الأيام الباقية لنزول الراتب.
 *
 * في بداية كل يوم: حد اليوم = رصيد بداية اليوم ÷ الأيام الباقية (مع اليوم الحالي).
 * إذا وفّرت اليوم، يرتفع حد الأيام الجاية تلقائياً، وإذا تجاوزت ينخفض.
 */
class BudgetManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "budget_prefs"
        private const val KEY_BALANCE = "balance"
        private const val KEY_DAY_START_BALANCE = "day_start_balance"
        private const val KEY_SPENT_TODAY = "spent_today"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_PAYDAY = "payday"
        private const val KEY_HAS_PLAN = "has_plan"
        private const val KEY_PACKAGES = "monitored_packages"
        private const val KEY_PLAN_END = "plan_end"
        private const val KEY_STRICT = "strict_mode"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_EMERGENCY_DATE = "emergency_date"
        const val DEFAULT_PAYDAY = 27

        /** في القفل الصارم: الإعدادات وشاشات إلغاء التثبيت تنقفل بعد. */
        val STRICT_BLOCKED_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- الإعداد ----------

    /** يبدأ خطة جديدة: الرصيد الحالي ويوم نزول الراتب (1-31). */
    fun startPlan(balance: Double, payday: Int) {
        prefs.edit()
            .putBoolean(KEY_HAS_PLAN, true)
            .putInt(KEY_PAYDAY, payday.coerceIn(1, 31))
            .putLong(KEY_BALANCE, balance.toBits())
            .putLong(KEY_DAY_START_BALANCE, balance.toBits())
            .putLong(KEY_SPENT_TODAY, 0.0.toBits())
            .putString(KEY_LAST_DATE, LocalDate.now().toString())
            .putString(KEY_PLAN_END, nextPayday(LocalDate.now(), payday).toString())
            .apply()
    }

    /**
     * ما تقدر تعدل الخطة وأنت مقفل (عشان ما ترفع الرصيد وتفك القفل)،
     * إلا إذا نزل الراتب.
     */
    fun canEditPlan(): Boolean {
        if (!hasPlan() || !isLocked()) return true
        val end = prefs.getString(KEY_PLAN_END, null) ?: return true
        return !LocalDate.now().isBefore(LocalDate.parse(end))
    }

    /** true = القفل ينفك بكرة، false = الرصيد خلص وينفك يوم الراتب. */
    fun unlocksTomorrow(): Boolean = getBalance() > 0.005

    fun isStrict(): Boolean = prefs.getBoolean(KEY_STRICT, false)

    /** يتفعّل بأي وقت، لكن ما ينطفي وأنت مقفل. يرجع القيمة النهائية. */
    fun setStrict(on: Boolean, force: Boolean = false): Boolean {
        if (!on && !force && isLocked()) return true
        prefs.edit().putBoolean(KEY_STRICT, on).apply()
        return on
    }

    /** هل لازم نقفل هذا التطبيق الحين؟ */
    fun shouldBlock(pkg: String): Boolean {
        if (!isLocked()) return false
        return pkg in getMonitoredPackages() || (isStrict() && pkg in STRICT_BLOCKED_PACKAGES)
    }

    // ---------- الرمز السري ----------

    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash(salt, pin))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return prefs.getString(KEY_PIN_HASH, null) == hash(salt, pin)
    }

    /** فك القفل لباقي اليوم بس (للطوارئ، يحتاج الرمز). */
    fun emergencyUnlockToday() {
        prefs.edit().putString(KEY_EMERGENCY_DATE, LocalDate.now().toString()).apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun hasPlan(): Boolean = prefs.getBoolean(KEY_HAS_PLAN, false)

    fun getPayday(): Int = prefs.getInt(KEY_PAYDAY, DEFAULT_PAYDAY)

    // ---------- المصاريف ----------

    fun addExpense(amount: Double) {
        if (amount <= 0) return
        rollOverIfNewDay()
        prefs.edit()
            .putLong(KEY_BALANCE, (getDouble(KEY_BALANCE) - amount).toBits())
            .putLong(KEY_SPENT_TODAY, (getDouble(KEY_SPENT_TODAY) + amount).toBits())
            .apply()
    }

    // ---------- الحسابات ----------

    fun getBalance(): Double {
        rollOverIfNewDay()
        return getDouble(KEY_BALANCE)
    }

    fun getSpentToday(): Double {
        rollOverIfNewDay()
        return getDouble(KEY_SPENT_TODAY)
    }

    /** عدد الأيام الباقية حتى الراتب، شاملة اليوم الحالي (أقل شي 1). */
    fun getDaysLeft(today: LocalDate = LocalDate.now()): Int {
        val next = nextPayday(today, getPayday())
        return ChronoUnit.DAYS.between(today, next).toInt().coerceAtLeast(1)
    }

    fun getDailyLimit(): Double {
        rollOverIfNewDay()
        val start = getDouble(KEY_DAY_START_BALANCE)
        if (start <= 0) return 0.0
        return start / getDaysLeft()
    }

    fun getRemainingToday(): Double = (getDailyLimit() - getSpentToday()).coerceAtLeast(0.0)

    fun isLocked(): Boolean {
        if (!hasPlan()) return false
        if (prefs.getString(KEY_EMERGENCY_DATE, null) == LocalDate.now().toString()) return false
        val limit = getDailyLimit()
        // رصيد خلص = قفل، أو صرفت حد اليوم كامل = قفل
        return limit <= 0.0 || getSpentToday() >= limit - 0.005
    }

    // ---------- تطبيقات البنوك المراقبة ----------

    fun getMonitoredPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun setMonitoredPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    // ---------- داخلي ----------

    /** أول ما يبدأ يوم جديد: رصيد بداية اليوم = الرصيد الحالي، والمصروف يرجع صفر. */
    private fun rollOverIfNewDay() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_DATE, null) == today) return
        prefs.edit()
            .putLong(KEY_DAY_START_BALANCE, getDouble(KEY_BALANCE).toBits())
            .putLong(KEY_SPENT_TODAY, 0.0.toBits())
            .putString(KEY_LAST_DATE, today)
            .apply()
    }

    private fun getDouble(key: String): Double =
        Double.fromBits(prefs.getLong(key, 0.0.toBits()))

    private fun nextPayday(today: LocalDate, payday: Int): LocalDate {
        val thisMonth = today.withDayOfMonth(payday.coerceAtMost(today.lengthOfMonth()))
        if (thisMonth.isAfter(today)) return thisMonth
        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
        return nextMonth.withDayOfMonth(payday.coerceAtMost(nextMonth.lengthOfMonth()))
    }
}
