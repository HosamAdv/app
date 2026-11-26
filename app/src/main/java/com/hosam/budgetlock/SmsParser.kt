package com.hosam.budgetlock

/**
 * يطلع مبلغ المصروف من رسائل البنوك (شراء، سحب، حوالة صادرة، سداد...).
 * يتجاهل الإيداعات والحوالات الواردة والاسترداد ورموز التحقق.
 */
object SmsParser {

    private val SPEND = Regex(
        "شراء|سحب|حوالة صادرة|تحويل صادر|حوالة محلية|حوالة دولية|خصم|سداد|مدفوعات|دفع|" +
            "purchase|withdraw|pos|debit|payment|paid|outgoing|transfer to",
        RegexOption.IGNORE_CASE
    )

    private val IGNORE = Regex(
        "إيداع|ايداع|واردة|وارد|استرداد|مسترد|إرجاع|ارجاع|رمز التحقق|رمز التفعيل|كلمة المرور|" +
            "لا تشارك|عرض|عروض|استمتع|احصل|%|otp|verification|deposit|credited|refund|reversal|incoming|received|offer",
        RegexOption.IGNORE_CASE
    )

    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)"
    private const val CUR = "(?:SAR|SR|ر\\.?\\s?س|ريال)"

    // "SAR 25.00" أو "مبلغ: SAR 25" أو "25.00 SAR" أو "25 ريال"
    private val AMOUNT_BEFORE = Regex("$CUR\\s*:?\\s*$NUM", RegexOption.IGNORE_CASE)
    private val AMOUNT_AFTER = Regex("$NUM\\s*$CUR", RegexOption.IGNORE_CASE)
    // "مبلغ: 25.00" بدون عملة
    private val AMOUNT_LABEL = Regex("(?:مبلغ|المبلغ|بمبلغ|amount|amt)\\s*:?\\s*$NUM", RegexOption.IGNORE_CASE)

    /** يرجع المبلغ إذا الرسالة مصروف، وإلا null. */
    fun parseExpense(body: String): Double? {
        val text = normalizeDigits(body)
        if (IGNORE.containsMatchIn(text)) return null
        if (!SPEND.containsMatchIn(text)) return null

        val raw = AMOUNT_BEFORE.find(text)?.groupValues?.get(1)
            ?: AMOUNT_AFTER.find(text)?.groupValues?.get(1)
            ?: AMOUNT_LABEL.find(text)?.groupValues?.get(1)
            ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull() ?: return null
        return if (amount > 0 && amount < 1_000_000) amount else null
    }

    /** رسائل البنوك تجي من أسماء (AlRajhiBank) مو أرقام جوال. */
    fun isLikelyBankSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        return sender.any { it.isLetter() }
    }

    private fun normalizeDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> c
                }
            )
        }
    }
}
