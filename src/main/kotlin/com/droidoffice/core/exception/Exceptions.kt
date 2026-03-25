package com.droidoffice.core.exception

/**
 * Base exception for all DroidOffice products.
 */
open class DroidOfficeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The file is corrupted or not a valid OOXML file.
 */
class InvalidFileException(
    message: String,
    cause: Throwable? = null,
) : DroidOfficeException(message, cause)

/**
 * The file format is not supported (e.g. .xls, .doc, .ppt binary formats).
 */
class UnsupportedFormatException(
    message: String,
    cause: Throwable? = null,
) : DroidOfficeException(message, cause)

/**
 * Password is incorrect or encryption/decryption failed.
 */
class PasswordException(
    message: String,
    cause: Throwable? = null,
) : DroidOfficeException(message, cause)

/**
 * Base for license-related exceptions.
 */
open class LicenseException(
    message: String,
    cause: Throwable? = null,
) : DroidOfficeException(message, cause)

/**
 * Commercial use detected but no valid license key provided.
 */
class LicenseRequiredException(
    message: String = "A valid license key is required for commercial use.",
) : LicenseException(message)

/**
 * License key has expired and the 30-day grace period has passed.
 */
class LicenseExpiredException(
    message: String = "License key has expired. Please renew your license.",
) : LicenseException(message)
