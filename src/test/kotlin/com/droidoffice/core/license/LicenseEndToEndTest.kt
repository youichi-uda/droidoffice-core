package com.droidoffice.core.license

import com.droidoffice.core.exception.LicenseExpiredException
import com.droidoffice.core.exception.LicenseRequiredException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseEndToEndTest {

    private val keyPair = LicenseKeyGenerator.generateKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey
    private val privateKey = keyPair.private as RSAPrivateKey

    @Test
    fun `generate and validate a license key`() {
        val jwt = LicenseKeyGenerator.generateLicenseKey(
            privateKey = privateKey,
            product = "droidxls",
            plan = LicensePlan.INDIE,
            licensee = "test@example.com",
            expiresAt = LocalDate.now().plusYears(1),
        )

        val validator = LicenseValidator(publicKey, "droidxls")
        val key = validator.validate(jwt)

        assertEquals(LicensePlan.INDIE, key.plan)
        assertEquals("test@example.com", key.licensee)
        assertEquals("droidxls", key.product)
        assertFalse(key.isExpired)
    }

    @Test
    fun `initialize and check license flow`() {
        val jwt = LicenseKeyGenerator.generateLicenseKey(
            privateKey = privateKey,
            product = "droidxls",
            plan = LicensePlan.STARTUP,
            licensee = "company@example.com",
            expiresAt = LocalDate.now().plusMonths(6),
        )

        val validator = LicenseValidator(publicKey, "droidxls")
        validator.initialize(jwt)
        // Should not throw
        validator.checkLicense()
    }

    @Test
    fun `expired key beyond grace period throws`() {
        val jwt = LicenseKeyGenerator.generateLicenseKey(
            privateKey = privateKey,
            product = "droidxls",
            plan = LicensePlan.INDIE,
            licensee = "expired@example.com",
            expiresAt = LocalDate.now().minusDays(31),
        )

        val validator = LicenseValidator(publicKey, "droidxls")
        validator.initialize(jwt)

        assertThrows<LicenseExpiredException> {
            validator.checkLicense()
        }
    }

    @Test
    fun `wrong product throws`() {
        val jwt = LicenseKeyGenerator.generateLicenseKey(
            privateKey = privateKey,
            product = "droiddoc",
            plan = LicensePlan.INDIE,
            licensee = "test@example.com",
            expiresAt = LocalDate.now().plusYears(1),
        )

        val validator = LicenseValidator(publicKey, "droidxls")

        assertThrows<LicenseRequiredException> {
            validator.validate(jwt)
        }
    }

    @Test
    fun `public key encode and decode round trip`() {
        val encoded = LicenseKeyGenerator.encodePublicKey(publicKey)
        val decoded = LicenseKeyGenerator.decodePublicKey(encoded)
        assertEquals(publicKey, decoded)
    }

    @Test
    fun `no license set means personal use - checkLicense is no-op`() {
        val validator = LicenseValidator(publicKey, "droidxls")
        // No initialize() called — should not throw
        validator.checkLicense()
    }
}
