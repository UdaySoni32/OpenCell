package io.opencell.platform.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.events.EventEngine
import io.opencell.platform.messaging.MessagingEngine
import io.opencell.platform.telecom.CallEngine
import io.opencell.platform.telecom.CallUiDelegate

/**
 * Hilt EntryPoint for platform services that are instantiated by the Android system
 * (ConnectionService, InCallService, SmsReceiver) and cannot use @Inject directly.
 *
 * Usage:
 *   val entryPoint = EntryPointAccessors.fromApplication(
 *       applicationContext,
 *       PlatformEntryPoint::class.java
 *   )
 *   val callEngine = entryPoint.callEngine()
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlatformEntryPoint {
    fun callEngine(): CallEngine
    fun messagingEngine(): MessagingEngine
    fun deviceEngine(): DeviceEngine
    fun eventEngine(): EventEngine
    fun callUiDelegate(): CallUiDelegate
}
