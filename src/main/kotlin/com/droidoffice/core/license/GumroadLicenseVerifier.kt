package com.droidoffice.core.license

import com.droidoffice.core.exception.LicenseExpiredException
import com.droidoffice.core.exception.LicenseRequiredException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Verifies license keys directly against the Gumroad API.
 * No intermediate server required.
 *
 * Flow:
 * 1. First call: POST to Gumroad /v2/licenses/verify
 * 2. Cache result locally (via [LicenseCache] callback)
 * 3. Subsequent calls use cache, re-verify every [REVERIFY_DAYS] days
 */
class GumroadLicenseVerifier(
    private val productPermalink: String,
    private val cache: LicenseCache? = null,
) {
    private val logger = Logger.getLogger(GumroadLicenseVerifier::class.java.name)

    private var verifiedLicense: VerifiedLicense? = null

    /**
     * Initialize with a Gumroad license key.
     * Tries cache first, falls back to API call.
     */
    fun initialize(licenseKey: String) {
        // Try cache first
        val cached = cache?.load()
        if (cached != null && cached.licenseKey == licenseKey && !cached.needsReverify()) {
            verifiedLicense = cached
            logger.info("DroidOffice license loaded from cache (valid until ${cached.expiresAt})")
            return
        }

        // Verify against Gumroad API
        val result = verifyWithGumroad(licenseKey)
        verifiedLicense = result
        cache?.save(result)
    }

    /**
     * Check current license status. Call before protected operations.
     */
    fun checkLicense() {
        val license = verifiedLicense ?: return // No license = personal use

        if (license.isGracePeriodExceeded) {
            throw LicenseExpiredException(
                "License expired on ${license.expiresAt}. " +
                    "Grace period of ${LicenseKey.GRACE_PERIOD_DAYS} days has passed. " +
                    "Please renew your license."
            )
        }

        if (license.isExpired) {
            logger.warning(
                "DroidOffice license expired on ${license.expiresAt}. " +
                    "You have ${LicenseKey.GRACE_PERIOD_DAYS} days grace period to renew."
            )
        }

        // Trigger background re-verify if stale
        if (license.needsReverify()) {
            logger.info("License cache is stale, will re-verify on next network opportunity.")
        }
    }

    val isLicensed: Boolean get() = verifiedLicense != null
    val currentLicense: VerifiedLicense? get() = verifiedLicense

    private fun verifyWithGumroad(licenseKey: String): VerifiedLicense {
        val url = URL(GUMROAD_VERIFY_URL)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = "product_permalink=$productPermalink&license_key=$licenseKey"
            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                val error = BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                throw LicenseRequiredException("License verification failed (HTTP $responseCode): $error")
            }

            return parseGumroadResponse(responseBody, licenseKey)
        } catch (e: LicenseRequiredException) {
            throw e
        } catch (e: Exception) {
            // Network error — check if we have a valid cache to fall back on
            val cached = cache?.load()
            if (cached != null && cached.licenseKey == licenseKey && !cached.isGracePeriodExceeded) {
                logger.warning("Network unavailable, using cached license verification.")
                return cached
            }
            throw LicenseRequiredException("Unable to verify license: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseGumroadResponse(json: String, licenseKey: String): VerifiedLicense {
        // Simple JSON parsing without external dependency
        val success = json.contains("\"success\":true")
        if (!success) {
            throw LicenseRequiredException("Invalid license key")
        }

        val email = extractJsonString(json, "email") ?: "unknown"
        val plan = determinePlan(json)

        // Gumroad subscriptions: check if subscription is active
        val cancelled = json.contains("\"cancelled\":true")
        val endDate = extractJsonString(json, "subscription_ended_at")
            ?: extractJsonString(json, "subscription_failed_at")

        val expiresAt = if (endDate != null && endDate != "null") {
            try {
                LocalDate.parse(endDate.substring(0, 10))
            } catch (_: Exception) {
                LocalDate.now().plusYears(1)
            }
        } else {
            // Active subscription or lifetime — set expiry far ahead
            LocalDate.now().plusYears(1)
        }

        return VerifiedLicense(
            licenseKey = licenseKey,
            email = email,
            plan = plan,
            expiresAt = expiresAt,
            verifiedAt = LocalDate.now(),
        )
    }

    private fun determinePlan(json: String): LicensePlan {
        val variants = extractJsonString(json, "variants")?.lowercase() ?: ""
        val price = extractJsonInt(json, "price")

        return when {
            variants.contains("enterprise") -> LicensePlan.ENTERPRISE
            variants.contains("startup") || (price != null && price >= 39900) -> LicensePlan.STARTUP
            variants.contains("indie") || (price != null && price >= 9900) -> LicensePlan.INDIE
            else -> LicensePlan.PERSONAL
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        const val GUMROAD_VERIFY_URL = "https://api.gumroad.com/v2/licenses/verify"
        const val REVERIFY_DAYS = 30L
    }
}

/**
 * Cached verification result.
 */
data class VerifiedLicense(
    val licenseKey: String,
    val email: String,
    val plan: LicensePlan,
    val expiresAt: LocalDate,
    val verifiedAt: LocalDate,
) {
    val isExpired: Boolean get() = LocalDate.now().isAfter(expiresAt)

    val isGracePeriodExceeded: Boolean
        get() = LocalDate.now().isAfter(expiresAt.plusDays(LicenseKey.GRACE_PERIOD_DAYS))

    fun needsReverify(): Boolean =
        LocalDate.now().isAfter(verifiedAt.plusDays(GumroadLicenseVerifier.REVERIFY_DAYS))
}

/**
 * Interface for persisting license verification results.
 * Implement with SharedPreferences on Android.
 */
interface LicenseCache {
    fun save(license: VerifiedLicense)
    fun load(): VerifiedLicense?
}
