package com.opencell.agent

import com.opencell.ai.AiSettings

/** Hard safety rules for when the assistant may act. Pure, so they're unit tested. */
object AgentRules {

    const val MAX_AI_REPLIES_PER_HOUR = 3

    private val otp = Regex("""(\b\d{4,8}\b.*\b(otp|code|verification|verify|passcode|pin)\b)|(\b(otp|code|verification|verify|passcode|pin)\b.*\b\d{4,8}\b)""", RegexOption.IGNORE_CASE)

    /** Real people have phone numbers; banks, OTP senders and shops use short codes or names ("JM-HDFCBK"). */
    fun isPersonalNumber(address: String): Boolean {
        if (address.any { it.isLetter() }) return false
        return address.count(Char::isDigit) >= 7
    }

    fun looksLikeOtp(body: String) = otp.containsMatchIn(body)

    /** Null = the SMS agent may draft a reply; otherwise the reason it must not. */
    fun smsSkipReason(
        from: String,
        body: String,
        settings: AiSettings,
        isContact: Boolean,
        isScreeningThread: Boolean,
        aiRepliesLastHour: Int,
    ): String? = when {
        !settings.smsAgent && !isScreeningThread -> "SMS replies are off"
        settings.primary == null -> "no AI provider configured"
        !isPersonalNumber(from) -> "business or short-code sender"
        looksLikeOtp(body) -> "looks like a one-time code"
        settings.smsAgentContactsOnly && !isContact && !isScreeningThread -> "sender is not in contacts"
        aiRepliesLastHour >= MAX_AI_REPLIES_PER_HOUR -> "reply limit reached for this conversation"
        else -> null
    }

    fun shouldLearn(from: String, body: String, settings: AiSettings, isContact: Boolean): Boolean =
        settings.learnFromSms && settings.primary != null && isContact && isPersonalNumber(from) && !looksLikeOtp(body)

    fun shouldScreenCall(number: String?, settings: AiSettings, isContact: Boolean, isAllowlisted: Boolean): Boolean =
        settings.screenUnknownCalls && settings.primary != null && number != null && isPersonalNumber(number) && !isContact && !isAllowlisted
}
