package com.opencell.assistant

/** Overdue pending reminders fire immediately after boot; future reminders keep their time. */
internal fun remindersToRestore(reminders: List<AssistantReminder>, now: Long): List<AssistantReminder> =
    reminders.filter { it.status == ReminderStatus.PENDING }
        .map { it.copy(triggerAtMillis = maxOf(it.triggerAtMillis, now)) }
