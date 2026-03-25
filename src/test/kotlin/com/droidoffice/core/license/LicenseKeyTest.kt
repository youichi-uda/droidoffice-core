package com.droidoffice.core.license

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseKeyTest {

    @Test
    fun `valid license is not expired`() {
        val key = LicenseKey(
            plan = LicensePlan.COMMERCIAL,
            expiresAt = LocalDate.now().plusDays(30),
            product = "droidxls",
            licensee = "test@example.com",
        )
        assertFalse(key.isExpired)
        assertFalse(key.isInGracePeriod)
        assertFalse(key.isGracePeriodExceeded)
    }

    @Test
    fun `expired license within grace period`() {
        val key = LicenseKey(
            plan = LicensePlan.COMMERCIAL,
            expiresAt = LocalDate.now().minusDays(15),
            product = "droidxls",
            licensee = "test@example.com",
        )
        assertTrue(key.isExpired)
        assertTrue(key.isInGracePeriod)
        assertFalse(key.isGracePeriodExceeded)
    }

    @Test
    fun `expired license beyond grace period`() {
        val key = LicenseKey(
            plan = LicensePlan.COMMERCIAL,
            expiresAt = LocalDate.now().minusDays(31),
            product = "droidxls",
            licensee = "test@example.com",
        )
        assertTrue(key.isExpired)
        assertFalse(key.isInGracePeriod)
        assertTrue(key.isGracePeriodExceeded)
    }
}
