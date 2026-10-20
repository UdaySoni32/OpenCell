package com.opencell.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderRestorationTest {
    @Test fun `only pending reminders restore and overdue reminders schedule immediately`() {
        val overdue = AssistantReminder(id = 1, title = "overdue", triggerAtMillis = 50, createdAtMillis = 0)
        val due = overdue.copy(id = 2, triggerAtMillis = 100)
        val future = overdue.copy(id = 3, triggerAtMillis = 200)
        val restored = remindersToRestore(listOf(overdue, due, future,
            overdue.copy(id = 4, status = ReminderStatus.FIRED),
            overdue.copy(id = 5, status = ReminderStatus.CANCELLED)), now = 100)
        assertEquals(listOf(overdue.copy(triggerAtMillis = 100), due, future), restored)
        assertEquals(50L, overdue.triggerAtMillis) // Restoration does not rewrite Room history.
        assertEquals(emptyList<AssistantReminder>(), remindersToRestore(emptyList(), 100))
    }
}
