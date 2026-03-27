package com.droidoffice.core.ooxml

import com.droidoffice.core.exception.PasswordException
import com.droidoffice.core.ooxml.cfb.CfbReader
import com.droidoffice.core.ooxml.cfb.CfbWriter
import com.droidoffice.core.ooxml.crypto.AgileEncryption

/**
 * Handles password protection for OOXML files using MS-OFFCRYPTO Standard Encryption.
 *
 * Encrypted files are stored as OLE2/CFB containers containing:
 * - \EncryptionInfo — encryption parameters (binary)
 * - \EncryptedPackage — the encrypted OOXML ZIP package
 *
 * Compatible with Microsoft Office 2007+ and LibreOffice.
 */
object EncryptedPackage {

    fun encrypt(packageBytes: ByteArray, password: String): ByteArray {
        val (encInfo, encPkg) = AgileEncryption.encrypt(packageBytes, password)
        return CfbWriter.write(mapOf(
            "EncryptionInfo" to encInfo,
            "EncryptedPackage" to encPkg,
        ))
    }

    fun decrypt(encryptedBytes: ByteArray, password: String): ByteArray {
        val streams = CfbReader.read(encryptedBytes)
        val encInfo = streams["EncryptionInfo"]
            ?: throw PasswordException("Missing EncryptionInfo stream")
        val encPkg = streams["EncryptedPackage"]
            ?: throw PasswordException("Missing EncryptedPackage stream")
        return AgileEncryption.decrypt(encInfo, encPkg, password)
    }

    fun isEncrypted(bytes: ByteArray): Boolean = CfbReader.isCfb(bytes)
}
