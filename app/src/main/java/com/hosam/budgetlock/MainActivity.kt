package com.hosam.budgetlock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var manager: BudgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = BudgetManager(applicationContext)
        updateUI()

        findViewById<Button>(R.id.btnSetLimit).setOnClickListener {
            val input = findViewById<EditText>(R.id.etLimit).text.toString()
            val limit = input.toDoubleOrNull() ?: return@setOnClickListener
            manager.setDailyLimit(limit)
            updateUI()
        }

        findViewById<Button>(R.id.btnAddExpense).setOnClickListener {
            val input = findViewById<EditText>(R.id.etExpense).text.toString()
            val amount = input.toDoubleOrNull() ?: return@setOnClickListener
            manager.addExpense(amount)
            updateUI()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            manager.reset()
            updateUI()
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun updateUI() {
        val spent = manager.getTotalSpent()
        val limit = manager.getDailyLimit()
        val status = if (manager.isLocked()) "🔒 مقفل" else "✅ مفتوح"
        findViewById<TextView>(R.id.tvStatus).text =
            "المصروف: ${spent.toInt()} / ${limit.toInt()} ريال\nالحالة: $status"
    }
}