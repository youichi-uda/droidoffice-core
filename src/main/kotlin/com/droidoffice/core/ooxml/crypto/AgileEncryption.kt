package com.droidoffice.core.ooxml.crypto

import com.droidoffice.core.exception.PasswordException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * MS-OFFCRYPTO Standard Encryption (v3.2) implementation.
 * Uses AES-128-ECB with SHA-1 key derivation.
 * Compatible with Microsoft Office 2007+ and LibreOffice.
 *
 * Ref: [MS-OFFCRYPTO] Section 2.3.6 (Standard Encryption)
 */
object AgileEncryption {

    private const val SPIN_COUNT = 50000
    private const val KEY_BITS = 128
    private const val KEY_BYTES = KEY_BITS / 8  // 16
    private const val SALT_SIZE = 16
    private const val VERIFIER_SIZE = 16

    // Standard Encryption algorithm IDs
    private const val ALG_AES128 = 0x0000660E
    private const val ALG_HASH_SHA1 = 0x00008004
    private const val PROVIDER_AES = 0x00000018

    // CSP Name (UTF-16LE)
    private val CSP_NAME = "Microsoft Enhanced RSA and AES Cryptographic Provider"

    /**
     * Encrypt OOXML package bytes using Standard Encryption.
     * @return Pair(EncryptionInfo stream bytes, EncryptedPackage stream bytes)
     */
    fun encrypt(packageBytes: ByteArray, password: String): Pair<ByteArray, ByteArray> {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }

        // Derive encryption key from password
        val key = deriveKey(password, salt)

        // Generate verifier and its hash
        val verifier = ByteArray(VERIFIER_SIZE).also { random.nextBytes(it) }
        val verifierHash = sha1(verifier)

        // Encrypt verifier and verifier hash with the derived key
        val encVerifier = aesEcbEncrypt(key, verifier)
        val encVerifierHash = aesEcbEncrypt(key, padTo32(verifierHash))

        // Build EncryptionInfo stream (binary format)
        val encInfo = buildEncryptionInfo(salt, encVerifier, encVerifierHash)

        // Encrypt the package: LE_UINT64(size) + AES-ECB encrypted data
        // Pad plaintext to at least 4088 bytes so EncryptedPackage stream >= 4096
        // (avoids CFB mini-stream which we don't implement)
        val minPlainSize = 4096 - 8  // 4088, rounded up to block size = 4096
        val paddedPackage = padToBlockSize(
            if (packageBytes.size < minPlainSize) packageBytes.copyOf(minPlainSize) else packageBytes, 16
        )
        val encryptedData = aesEcbEncrypt(key, paddedPackage)
        val pkgStream = ByteBuffer.allocate(8 + encryptedData.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(packageBytes.size.toLong())
            .put(encryptedData)
            .array()

        return encInfo to pkgStream
    }

    /**
     * Decrypt OOXML package using Standard Encryption.
     */
    fun decrypt(encryptionInfoBytes: ByteArray, encryptedPackageBytes: ByteArray, password: String): ByteArray {
        val buf = ByteBuffer.wrap(encryptionInfoBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Parse EncryptionInfo header
        val vMajor = buf.getShort().toInt() and 0xFFFF
        val vMinor = buf.getShort().toInt() and 0xFFFF
        val flags = buf.getInt()
        val headerSize = buf.getInt()

        // Parse EncryptionHeader
        val headerStart = buf.position()
        val hdrFlags = buf.getInt()
        val sizeExtra = buf.getInt()
        val algId = buf.getInt()
        val algIdHash = buf.getInt()
        val keySize = buf.getInt()

        // Skip to salt in EncryptionVerifier
        buf.position(headerStart + headerSize)
        val saltSize = buf.getInt()
        val salt = ByteArray(saltSize).also { buf.get(it) }
        val encVerifier = ByteArray(16).also { buf.get(it) }
        val verifierHashSize = buf.getInt()
        val encVerifierHash = ByteArray(32).also { buf.get(it) }

        // Derive key
        val derivedKeyBits = if (keySize > 0) keySize else KEY_BITS
        val key = deriveKey(password, salt, derivedKeyBits / 8)

        // Verify password
        val decVerifier = aesEcbDecrypt(key, encVerifier)
        val decVerifierHash = aesEcbDecrypt(key, encVerifierHash)
        val computedHash = sha1(decVerifier)

        if (!computedHash.contentEquals(decVerifierHash.copyOf(20))) {
            throw PasswordException("Incorrect password")
        }

        // Decrypt package (ECB mode, 8-byte size header)
        val pkgBuf = ByteBuffer.wrap(encryptedPackageBytes).order(ByteOrder.LITTLE_ENDIAN)
        val originalSize = pkgBuf.getLong()

        val rawEncData = ByteArray(encryptedPackageBytes.size - 8)
        pkgBuf.get(rawEncData)
        val blockAlignedSize = (rawEncData.size / 16) * 16
        val encData = if (blockAlignedSize < rawEncData.size) rawEncData.copyOf(blockAlignedSize) else rawEncData

        val decrypted = aesEcbDecrypt(key, encData)
        return decrypted.copyOf(originalSize.toInt())
    }

    // --- EncryptionInfo binary format ---

    private fun buildEncryptionInfo(
        salt: ByteArray,
        encVerifier: ByteArray,
        encVerifierHash: ByteArray,
    ): ByteArray {
        val cspNameBytes = CSP_NAME.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0) // null terminator
        val headerSize = 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + cspNameBytes.size // 32 + cspName

        // Total: version(4) + flags(4) + headerSize field(4) + EncryptionHeader(headerSize)
        //        + EncryptionVerifier: saltSize(4) + salt(16) + encVerifier(16) + hashSize(4) + encVerifierHash(32)
        val totalSize = 4 + 4 + 4 + headerSize + 4 + SALT_SIZE + 16 + 4 + 32
        val buf = ByteBuffer.allocate(totalSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        // Version
        buf.putShort(0x0003) // vMajor
        buf.putShort(0x0002) // vMinor
        buf.putInt(0x00000024) // Flags: fCryptoAPI | fAES

        // HeaderSize
        buf.putInt(headerSize)

        // EncryptionHeader
        buf.putInt(0x00000024)  // Flags
        buf.putInt(0)           // SizeExtra
        buf.putInt(ALG_AES128)  // AlgID
        buf.putInt(ALG_HASH_SHA1) // AlgIDHash
        buf.putInt(KEY_BITS)    // KeySize
        buf.putInt(PROVIDER_AES) // ProviderType
        buf.putInt(0)           // Reserved1
        buf.putInt(0)           // Reserved2
        buf.put(cspNameBytes)   // CSPName

        // EncryptionVerifier
        buf.putInt(SALT_SIZE)   // SaltSize
        buf.put(salt)           // Salt
        buf.put(encVerifier)    // EncryptedVerifier (16 bytes)
        buf.putInt(20)          // VerifierHashSize (SHA-1 = 20)
        buf.put(encVerifierHash) // EncryptedVerifierHash (32 bytes, AES block padded)

        val pos = buf.position()
        // Pad to at least 4096 bytes to ensure the CFB stream
        // exceeds the mini-stream cutoff (4096 bytes)
        val resultSize = maxOf(pos, 4096)
        val result = ByteArray(resultSize)
        buf.flip()
        buf.get(result, 0, pos)
        return result
    }

    // --- Key Derivation (MS-OFFCRYPTO Section 2.3.6.2) ---

    private fun deriveKey(password: String, salt: ByteArray, keyBytes: Int = KEY_BYTES): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_16LE)

        // H0 = SHA-1(salt + password)
        var hash = sha1(salt + passwordBytes)

        // Iterate: Hn = SHA-1(iterator + H(n-1))
        for (i in 0 until SPIN_COUNT) {
            hash = sha1(leInt32(i) + hash)
        }

        // HFinal = SHA-1(Hn + blockKey=0)
        val hFinal = sha1(hash + leInt32(0))
        val cbHash = 20

        // Always use X1/X2 derivation (MS-OFFCRYPTO Section 2.3.6.2)
        val buf1 = ByteArray(64) { 0x36 }
        for (i in hFinal.indices) buf1[i] = (buf1[i].toInt() xor hFinal[i].toInt()).toByte()
        val x1 = sha1(buf1)

        val buf2 = ByteArray(64) { 0x5C }
        for (i in hFinal.indices) buf2[i] = (buf2[i].toInt() xor hFinal[i].toInt()).toByte()
        val x2 = sha1(buf2)

        return (x1 + x2).copyOf(keyBytes)
    }

    // --- AES helpers ---

    private fun aesEcbEncrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(padToBlockSize(data, 16))
    }

    private fun aesEcbDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun padToBlockSize(data: ByteArray, blockSize: Int): ByteArray {
        val padded = (data.size + blockSize - 1) / blockSize * blockSize
        return if (data.size == padded) data else data.copyOf(padded)
    }

    private fun padTo32(data: ByteArray): ByteArray =
        if (data.size >= 32) data.copyOf(32) else data.copyOf(32)

    // --- Hash ---

    private fun sha1(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(data)

    private fun leInt32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
