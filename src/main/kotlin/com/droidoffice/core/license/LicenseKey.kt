package com.droidoffice.core.license

import java.time.LocalDate

/**
 * Decoded license key information.
 */
data class LicenseKey(
    val plan: LicensePlan,
    val expiresAt: LocalDate,
    val product: String,
    val licensee: String,
) {
    val isExpired: Boolean
        get() = LocalDate.now().isAfter(expiresAt)

    val isInGracePeriod: Boolean
        get() {
            val now = LocalDate.now()
            return now.isAfter(expiresAt) && !now.isAfter(expiresAt.plusDays(GRACE_PERIOD_DAYS))
        }

    val isGracePeriodExceeded: Boolean
        get() = LocalDate.now().isAfter(expiresAt.plusDays(GRACE_PERIOD_DAYS))

    companion object {
        const val GRACE_PERIOD_DAYS = 30L
    }
}
