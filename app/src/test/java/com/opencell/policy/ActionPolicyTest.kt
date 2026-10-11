package com.opencell.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPolicyTest {

    private val device = Actor.Device(1, "laptop", Scope.DEFAULT_DEVICE)
    private fun sms(actor: Actor = device, to: String = "+91 98765 43210") = ActionRequest(actor, ActionType.SEND_SMS, to, "hi")

    @Test
    fun `user is always allowed even with kill switch`() {
        assertEquals(Decision.Allow, ActionPolicy.decide(sms(Actor.User), PolicySettings(killSwitch = true), 999))
    }

    @Test
    fun `kill switch denies everyone else`() {
        assertTrue(ActionPolicy.decide(sms(), PolicySettings(killSwitch = true), 0) is Decision.Deny)
        assertTrue(ActionPolicy.decide(sms(Actor.Ai("sms")), PolicySettings(killSwitch = true), 0) is Decision.Deny)
    }

    @Test
    fun `device without scope is denied`() {
        val readOnly = Actor.Device(2, "pi", setOf(Scope.READ))
        assertEquals(Decision.Deny("missing scope sms:send"), ActionPolicy.decide(sms(readOnly), PolicySettings(), 0))
    }

    @Test
    fun `ask mode asks`() {
        assertEquals(Decision.Ask, ActionPolicy.decide(sms(), PolicySettings(mode = PolicyMode.ASK), 0))
    }

    @Test
    fun `allowlist matches across number formats`() {
        val s = PolicySettings(mode = PolicyMode.ALLOWLIST, allowlist = setOf("09876543210"))
        assertEquals(Decision.Allow, ActionPolicy.decide(sms(), s, 0))
        assertEquals(Decision.Ask, ActionPolicy.decide(sms(to = "+15550001111"), s, 0))
    }

    @Test
    fun `full auto respects daily limit`() {
        val s = PolicySettings(mode = PolicyMode.FULL_AUTO, dailyLimit = 2)
        assertEquals(Decision.Allow, ActionPolicy.decide(sms(), s, 1))
        assertEquals(Decision.Ask, ActionPolicy.decide(sms(), s, 2))
    }

    @Test
    fun `auto-approve scope skips asking under limit`() {
        val trusted = Actor.Device(3, "agent", Scope.DEFAULT_DEVICE + Scope.AUTO_APPROVE)
        assertEquals(Decision.Allow, ActionPolicy.decide(sms(trusted), PolicySettings(), 0))
        assertEquals(Decision.Ask, ActionPolicy.decide(sms(trusted), PolicySettings(dailyLimit = 1), 1))
    }

    @Test
    fun `hanging up an existing call needs only the scope`() {
        val req = ActionRequest(device, ActionType.HANGUP_CALL, "call-0")
        assertEquals(Decision.Allow, ActionPolicy.decide(req, PolicySettings(mode = PolicyMode.ASK), 999))
    }

    @Test
    fun `scope csv round trip`() {
        val scopes = setOf(Scope.READ, Scope.AUTO_APPROVE)
        assertEquals(scopes, Scope.parse(Scope.format(scopes)))
        assertEquals(setOf(Scope.READ), Scope.parse("read, bogus"))
    }
}
