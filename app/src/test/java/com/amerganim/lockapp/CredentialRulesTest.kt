package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the PIN length rules shared by the keypad and the setup screen. */
class CredentialRulesTest {

    @Test
    fun rejectsPinsShorterThanTheMinimum() {
        for (length in 0 until CredentialManager.PIN_MIN_LENGTH) {
            assertFalse("length $length", CredentialManager.isValidPinLength(length))
        }
    }

    @Test
    fun acceptsEveryLengthInRange() {
        for (length in CredentialManager.PIN_MIN_LENGTH..CredentialManager.PIN_MAX_LENGTH) {
            assertTrue("length $length", CredentialManager.isValidPinLength(length))
        }
    }

    @Test
    fun rejectsPinsLongerThanTheMaximum() {
        assertFalse(CredentialManager.isValidPinLength(CredentialManager.PIN_MAX_LENGTH + 1))
        assertFalse(CredentialManager.isValidPinLength(64))
    }

    /** 4-8 is the advertised range; widening it silently would change the setup copy. */
    @Test
    fun rangeIsFourToEight() {
        assertTrue(CredentialManager.PIN_MIN_LENGTH == 4 && CredentialManager.PIN_MAX_LENGTH == 8)
    }
}
