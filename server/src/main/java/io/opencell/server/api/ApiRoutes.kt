package io.opencell.server.api

import io.opencell.platform.capabilities.CapabilityEngine
import io.opencell.platform.contacts.ContactEngine
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.events.EventEngine
import io.opencell.platform.messaging.MessagingEngine
import io.opencell.platform.telecom.CallEngine
import io.opencell.server.auth.AuthenticationService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ApiRoutes is the bridge between the HTTP layer (Ktor routes) and the platform engines.
 * All route handlers call methods on this class instead of directly calling engines,
 * so each engine is injected once here and shared across all route files.
 */
@Singleton
class ApiRoutes @Inject constructor(
    val deviceEngine: DeviceEngine,
    val callEngine: CallEngine,
    val messagingEngine: MessagingEngine,
    val capabilityEngine: CapabilityEngine,
    val contactEngine: ContactEngine,
    val eventEngine: EventEngine,
    val authService: AuthenticationService
)
