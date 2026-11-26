package com.hosam.budgetlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.Locale

/** يسمع رسائل SMS، وإذا كانت عملية صرف من البنك يسجلها تلقائياً. */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val manager = BudgetManager(context.applicationContext)
        if (!manager.isSmsAuto() || !manager.hasPlan()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // الرسالة الطويلة توصل مقسمة؛ نجمعها حسب المرسل
        messages.groupBy { it.originatingAddress }.forEach { (sender, parts) ->
            if (!SmsParser.isLikelyBankSender(sender)) return@forEach
            val body = parts.joinToString("") { it.messageBody ?: "" }
            val amount = SmsParser.parseExpense(body) ?: return@forEach
            val key = "$sender|$body|${parts.first().timestampMillis}"
            if (!manager.markSmsProcessed(key)) return@forEach

            manager.addExpense(amount)
            val shown = String.format(Locale.US, "%.2f", amount).removeSuffix(".00")
            manager.setLastAuto("آخر عملية من الرسائل: $shown ريال ($sender)")
        }
    }
}
