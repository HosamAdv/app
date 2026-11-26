package com.hosam.budgetlock

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** نوافذ الرمز السري. */
object PinDialog {

    /** يطلب الرمز، وإذا كان صح ينفذ [onOk]. إذا ما فيه رمز ينفذ على طول. */
    fun require(ctx: Context, manager: BudgetManager, title: String, onOk: () -> Unit) {
        if (!manager.hasPin()) { onOk(); return }
        ask(ctx, title, "اكتب الرمز السري") { pin ->
            if (manager.checkPin(pin)) onOk()
            else Toast.makeText(ctx, "الرمز غلط", Toast.LENGTH_SHORT).show()
        }
    }

    /** يحط رمز جديد (يكتبه مرتين). */
    fun setNew(ctx: Context, manager: BudgetManager, onDone: () -> Unit) {
        ask(ctx, "رمز جديد", "4 إلى 8 أرقام") { first ->
            if (first.length < 4) {
                Toast.makeText(ctx, "الرمز لازم 4 أرقام أو أكثر", Toast.LENGTH_SHORT).show()
                return@ask
            }
            ask(ctx, "أكد الرمز", "اكتبه مرة ثانية") { second ->
                if (first == second) {
                    manager.setPin(first)
                    Toast.makeText(ctx, "تم حفظ الرمز 🔐", Toast.LENGTH_SHORT).show()
                    onDone()
                } else {
                    Toast.makeText(ctx, "الرمزين مو نفس بعض", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun ask(ctx: Context, title: String, hint: String, onEntered: (String) -> Unit) {
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val til = TextInputLayout(ctx).apply { this.hint = hint }
        val et = TextInputEditText(til.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            gravity = Gravity.CENTER
            textSize = 22f
        }
        til.addView(et)
        val box = FrameLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(til)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("تم") { _, _ -> onEntered(et.text?.toString().orEmpty()) }
            .setNegativeButton("إلغاء", null)
            .show()
        et.requestFocus()
    }
}
