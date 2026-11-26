package com.hosam.budgetlock

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var manager: BudgetManager

    private lateinit var etExpense: TextInputEditText
    private lateinit var etBalance: TextInputEditText
    private lateinit var etPayday: TextInputEditText
    private lateinit var swStrict: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = BudgetManager(applicationContext)

        etExpense = findViewById(R.id.etExpense)
        etBalance = findViewById(R.id.etBalance)
        etPayday = findViewById(R.id.etPayday)
        swStrict = findViewById(R.id.swStrict)

        etPayday.setText(manager.getPayday().toString())

        // مبالغ سريعة
        listOf(R.id.chip5, R.id.chip10, R.id.chip20, R.id.chip50).forEach { id ->
            findViewById<Chip>(id).setOnClickListener { etExpense.setText((it as Chip).text) }
        }

        findViewById<MaterialButton>(R.id.btnAddExpense).setOnClickListener { addExpense() }
        findViewById<MaterialButton>(R.id.btnStartPlan).setOnClickListener { startPlan() }
        findViewById<MaterialButton>(R.id.btnPickApps).setOnClickListener { showAppPicker() }
        findViewById<MaterialButton>(R.id.btnPin).setOnClickListener {
            if (manager.hasPin()) {
                PinDialog.require(this, manager, "تغيير الرمز") { PinDialog.setNew(this, manager) { updateUI() } }
            } else {
                PinDialog.setNew(this, manager) { updateUI() }
            }
        }
        findViewById<MaterialButton>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        swStrict.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (checked) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("تفعيل القفل الصارم؟")
                    .setMessage("وقت ما تكون مقفل، الإعدادات وإلغاء التثبيت ينقفلون بعد، وما تقدر تطفي القفل الصارم لين ينفك القفل.")
                    .setPositiveButton("فعّله") { _, _ -> manager.setStrict(true); updateUI() }
                    .setNegativeButton("لا") { _, _ -> updateUI() }
                    .setOnCancelListener { updateUI() }
                    .show()
            } else if (manager.hasPin()) {
                updateUI() // يرجع مفعّل لين يتأكد الرمز
                PinDialog.require(this, manager, "إطفاء القفل الصارم") {
                    manager.setStrict(false, force = true); updateUI()
                }
            } else {
                if (manager.setStrict(false)) toast("ما تقدر تطفيه وأنت مقفل")
                updateUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    // ---------------- الأفعال ----------------

    private fun addExpense() {
        val amount = etExpense.text?.toString()?.toDoubleOrNull()
        val til = findViewById<TextInputLayout>(R.id.tilExpense)
        if (!manager.hasPlan()) {
            toast("حدد رصيدك من كرت الخطة أول")
            return
        }
        if (amount == null || amount <= 0) {
            til.error = "اكتب المبلغ"
            return
        }
        til.error = null
        manager.addExpense(amount)
        etExpense.text?.clear()
        updateUI()
        if (manager.isLocked()) toast("خلصت حد اليوم 🔒 تطبيقات البنك انقفلت لين بكرة")
    }

    private fun startPlan() {
        if (!manager.hasPin() && !manager.canEditPlan()) {
            toast("ما تقدر تعدل الخطة وأنت مقفل. تقدر بعد نزول الراتب.")
            return
        }
        val tilBalance = findViewById<TextInputLayout>(R.id.tilBalance)
        val tilPayday = findViewById<TextInputLayout>(R.id.tilPayday)
        val balance = etBalance.text?.toString()?.toDoubleOrNull()
        val payday = etPayday.text?.toString()?.toIntOrNull()

        tilBalance.error = if (balance == null || balance < 0) "اكتب الرصيد" else null
        tilPayday.error = if (payday == null || payday !in 1..31) "من 1 إلى 31" else null
        if (tilBalance.error != null || tilPayday.error != null) return

        val apply = {
            manager.startPlan(balance!!, payday!!)
            etBalance.text?.clear()
            updateUI()
            toast("تمام! حدك اليوم ${money(manager.getDailyLimit())} ريال")
        }
        if (manager.hasPlan() && manager.hasPin()) {
            PinDialog.require(this, manager, "تعديل الخطة") { apply() }
        } else {
            apply()
        }
    }

    private fun showAppPicker() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }

        val current = manager.getMonitoredPackages()
        val selected = current.toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in selected }
        val locked = manager.isLocked()

        MaterialAlertDialogBuilder(this)
            .setTitle("وش التطبيقات اللي تنقفل؟")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) selected.add(apps[which].first) else selected.remove(apps[which].first)
            }
            .setPositiveButton("حفظ") { _, _ ->
                val removing = (current - selected).isNotEmpty()
                when {
                    // شيل تطبيق من القائمة يحتاج الرمز
                    removing && manager.hasPin() ->
                        PinDialog.require(this, manager, "شيل تطبيق من القفل") {
                            manager.setMonitoredPackages(selected); updateUI()
                        }
                    // بدون رمز: وأنت مقفل تقدر تضيف بس
                    removing && locked -> {
                        manager.setMonitoredPackages(current + selected)
                        toast("وأنت مقفل ما تقدر تشيل تطبيق من القائمة")
                    }
                    else -> manager.setMonitoredPackages(selected)
                }
                updateUI()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------------- الواجهة ----------------

    private fun updateUI() {
        val hasPlan = manager.hasPlan()
        val locked = manager.isLocked()
        val limit = manager.getDailyLimit()
        val spent = manager.getSpentToday()
        val remaining = manager.getRemainingToday()

        // الحالة واللون
        val ratio = if (limit > 0) (spent / limit).coerceIn(0.0, 1.0) else if (hasPlan) 1.0 else 0.0
        val stateColor = color(
            when {
                !hasPlan -> R.color.text_secondary
                locked -> R.color.danger
                ratio >= 0.75 -> R.color.warn
                else -> R.color.good
            }
        )

        val badge = findViewById<TextView>(R.id.tvBadge)
        badge.text = when {
            !hasPlan -> "بدون خطة"
            locked -> "🔒 مقفل"
            ratio >= 0.75 -> "انتبه"
            else -> "مفتوح"
        }
        (badge.background.mutate() as? GradientDrawable)?.setColor(stateColor)

        val ring = findViewById<CircularProgressIndicator>(R.id.ring)
        ring.setIndicatorColor(stateColor)
        ring.setProgressCompat((ratio * 100).toInt(), true)

        findViewById<TextView>(R.id.tvRemaining).apply {
            text = if (hasPlan) money(remaining) else "—"
            setTextColor(if (locked) stateColor else color(R.color.text_primary))
        }
        findViewById<TextView>(R.id.tvSpentLine).text = when {
            !hasPlan -> "ابدأ من كرت «الخطة» تحت 👇"
            locked && !manager.unlocksTomorrow() -> "خلص الرصيد، القفل ينفك يوم الراتب"
            locked -> "صرفت ${money(spent)} من ${money(limit)} — ينفك بكرة"
            else -> "صرفت ${money(spent)} من ${money(limit)} ريال"
        }
        findViewById<TextView>(R.id.tvLimit).text = if (hasPlan) money(limit) else "—"
        findViewById<TextView>(R.id.tvDays).text = if (hasPlan) manager.getDaysLeft().toString() else "—"
        findViewById<TextView>(R.id.tvBalance).text = if (hasPlan) money(manager.getBalance()) else "—"

        // الخطة
        val canEdit = manager.canEditPlan() || manager.hasPin()
        listOf(R.id.tilBalance, R.id.tilPayday).forEach { findViewById<TextInputLayout>(it).isEnabled = canEdit }
        findViewById<MaterialButton>(R.id.btnStartPlan).apply {
            isEnabled = canEdit
            text = if (hasPlan) "حدّث الرصيد وأعد التقسيم" else "قسّم الرصيد على الأيام"
        }
        findViewById<TextView>(R.id.tvPlanHint).text =
            if (canEdit) "اكتب كم باقي في حسابك ومتى ينزل الراتب، وأنا أقسمه على الأيام."
            else "🔒 مقفل: ما تقدر تعدل الرصيد لين ينزل الراتب."

        // الحماية
        val serviceOn = isAccessibilityEnabled()
        findViewById<TextView>(R.id.tvStepService).text =
            if (serviceOn) "١) ✅ خدمة القفل شغالة" else "١) ⚠️ خدمة القفل مطفية"
        findViewById<MaterialButton>(R.id.btnAccessibility).text =
            if (serviceOn) "إعدادات خدمة القفل" else "فعّل خدمة القفل"

        val apps = manager.getMonitoredPackages()
        findViewById<TextView>(R.id.tvStepApps).text =
            if (apps.isEmpty()) "٢) ⚠️ ما اخترت تطبيقات بنوك"
            else "٢) ✅ " + apps.joinToString("، ") { appLabel(it) }

        swStrict.isChecked = manager.isStrict()

        val hasPin = manager.hasPin()
        findViewById<TextView>(R.id.tvStepPin).text =
            if (hasPin) "٤) ✅ الرمز السري محطوط" else "٤) ⚠️ بدون رمز سري"
        findViewById<MaterialButton>(R.id.btnPin).text =
            if (hasPin) "تغيير الرمز" else "حط رمز سري"

        // تحذير واضح لو الحماية ناقصة
        if (hasPlan && (!serviceOn || apps.isEmpty())) {
            badge.text = "الحماية ناقصة"
            (badge.background.mutate() as? GradientDrawable)?.setColor(color(R.color.warn))
        }
    }

    // ---------------- مساعدات ----------------

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, LockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v).removeSuffix(".00")

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
