package com.hosam.budgetlock

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class LockActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        findViewById<MaterialButton>(R.id.btnClose).setOnClickListener { goHome() }
        findViewById<MaterialButton>(R.id.btnEmergency).setOnClickListener {
            val m = BudgetManager(applicationContext)
            PinDialog.require(this, m, "فك القفل لباقي اليوم") {
                m.emergencyUnlockToday()
                android.widget.Toast.makeText(this, "انفك القفل لين نهاية اليوم", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val m = BudgetManager(applicationContext)
        if (!m.isLocked()) { finish(); return }
        // زر الطوارئ يظهر بس إذا فيه رمز
        findViewById<MaterialButton>(R.id.btnEmergency).visibility =
            if (m.hasPin()) android.view.View.VISIBLE else android.view.View.GONE

        val spent = money(m.getSpentToday())
        val limit = money(m.getDailyLimit())

        if (m.unlocksTomorrow()) {
            findViewById<TextView>(R.id.tvLockTitle).text = "خلصت مصروف اليوم"
            findViewById<TextView>(R.id.tvLockMessage).text =
                "صرفت $spent من $limit ريال.\nاللي وفرته يرجع لك في الأيام الجاية."
            val left = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
            val h = left.toHours()
            val min = left.toMinutes() % 60
            findViewById<TextView>(R.id.tvUnlockAt).text = "ينفتح بعد $h ساعة و $min دقيقة"
        } else {
            findViewById<TextView>(R.id.tvLockTitle).text = "خلص الرصيد"
            findViewById<TextView>(R.id.tvLockMessage).text =
                "ما باقي شي لين الراتب.\nتطبيقات البنك مقفلة لين ينزل."
            findViewById<TextView>(R.id.tvUnlockAt).text = "ينفتح يوم ${m.getPayday()}"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v).removeSuffix(".00")
}
