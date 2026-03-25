package com.droidoffice.core.license

import com.droidoffice.core.exception.LicenseExpiredException
import com.droidoffice.core.exception.LicenseRequiredException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.interfaces.RSAPublicKey
import java.time.LocalDate
import java.time.ZoneId
import java.util.logging.Logger

/**
 * Validates JWT license keys offline.
 * Shared across all DroidOffice products.
 */
class LicenseValidator(
    private val publicKey: RSAPublicKey,
    private val productId: String,
) {
    private val logger = Logger.getLogger(LicenseValidator::class.java.name)

    private var cachedKey: LicenseKey? = null

    /**
     * Initialize with a license key string (JWT).
     * Call this once at app startup for commercial use.
     */
    fun initialize(licenseKeyJwt: String) {
        cachedKey = validate(licenseKeyJwt)
    }

    /**
     * Check the current license status. Called before protected operations.
     * - No license set → assume personal/free use (no-op)
     * - Valid license → no-op
     * - Grace period → log warning
     * - Expired beyond grace → throw LicenseExpiredException
     */
    fun checkLicense() {
        val key = cachedKey ?: return // No license = personal use

        when {
            key.isGracePeriodExceeded -> {
                throw LicenseExpiredException(
                    "License expired on ${key.expiresAt}. " +
                        "Grace period of ${LicenseKey.GRACE_PERIOD_DAYS} days has passed. " +
                        "Please renew your license."
                )
            }
            key.isInGracePeriod -> {
                logger.warning(
                    "DroidOffice license expired on ${key.expiresAt}. " +
                        "You have ${LicenseKey.GRACE_PERIOD_DAYS} days grace period to renew."
                )
            }
        }
    }

    /**
     * Validates a JWT license key string and returns the decoded LicenseKey.
     */
    fun validate(licenseKeyJwt: String): LicenseKey {
        val signedJwt = try {
            SignedJWT.parse(licenseKeyJwt)
        } catch (e: Exception) {
            throw LicenseRequiredException("Invalid license key format: ${e.message}")
        }

        // Verify signature
        if (signedJwt.header.algorithm != JWSAlgorithm.RS256) {
            throw LicenseRequiredException("Unsupported JWT algorithm: ${signedJwt.header.algorithm}")
        }

        val verifier: JWSVerifier = RSASSAVerifier(publicKey)
        if (!signedJwt.verify(verifier)) {
            throw LicenseRequiredException("License key signature verification failed")
        }

        val claims = signedJwt.jwtClaimsSet
        return extractLicenseKey(claims)
    }

    private fun extractLicenseKey(claims: JWTClaimsSet): LicenseKey {
        val product = claims.getStringClaim("product")
            ?: throw LicenseRequiredException("License key missing 'product' claim")

        if (product != productId) {
            throw LicenseRequiredException(
                "License key is for product '$product', expected '$productId'"
            )
        }

        val planStr = claims.getStringClaim("plan")
            ?: throw LicenseRequiredException("License key missing 'plan' claim")

        val plan = try {
            LicensePlan.valueOf(planStr.uppercase())
        } catch (e: IllegalArgumentException) {
            throw LicenseRequiredException("Unknown license plan: $planStr")
        }

        val expirationDate = claims.expirationTime
            ?: throw LicenseRequiredException("License key missing expiration date")

        val expiresAt = expirationDate.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val licensee = claims.subject ?: "Unknown"

        return LicenseKey(
            plan = plan,
            expiresAt = expiresAt,
            product = product,
            licensee = licensee,
        )
    }
}
