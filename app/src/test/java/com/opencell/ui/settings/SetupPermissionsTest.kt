package com.opencell.ui.settings

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPermissionsTest {
    @Test
    fun `setup does not request notification permission before API 33`() {
        for (sdk in 30..32) {
            val permissions = requiredPermissions(sdk).toList()
            assertFalse(Manifest.permission.POST_NOTIFICATIONS in permissions)
            assertTrue(Manifest.permission.CALL_PHONE in permissions)
            assertTrue(Manifest.permission.RECEIVE_SMS in permissions)
        }
    }

    @Test
    fun `setup adds notification permission at API 33 while preserving existing permissions`() {
        val base = requiredPermissions(32).toList()
        for (sdk in 33..36) {
            assertEquals(base + Manifest.permission.POST_NOTIFICATIONS, requiredPermissions(sdk).toList())
        }
    }
}
