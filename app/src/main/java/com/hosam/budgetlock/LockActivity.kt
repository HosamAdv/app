package com.hosam.budgetlock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        val manager = BudgetManager(applicationContext)
        val spent = manager.getTotalSpent()
        val limit = manager.getDailyLimit()
        findViewById<TextView>(R.id.tvLockMessage).text =
            "وصلت للحد اليومي\n${spent.toInt()} / ${limit.toInt()} ريال"
        findViewById<Button>(R.id.btnClose).setOnClickListener {
            moveTaskToBack(true)
            finish()
        }
    }
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}