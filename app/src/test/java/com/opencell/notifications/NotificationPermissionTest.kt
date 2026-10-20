package com.opencell.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionTest {
    @Test
    fun `API 30 through 32 never query the nonexistent runtime permission`() {
        for (sdk in 30..32) {
            assertFalse(requiresNotificationPermission(sdk))
            assertTrue(notificationPermissionGranted(sdk) { error("Must not query permission on API $sdk") })
        }
    }

    @Test
    fun `API 33 and newer require a granted notification permission`() {
        for (sdk in 33..36) {
            assertTrue(requiresNotificationPermission(sdk))
            assertFalse(notificationPermissionGranted(sdk) { false })
            assertTrue(notificationPermissionGranted(sdk) { true })
        }
    }
}
