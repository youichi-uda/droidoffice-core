package com.droidoffice.core.ooxml

import com.droidoffice.core.exception.PasswordException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES encryption/decryption for OOXML files.
 *
 * Format: MAGIC(8) | salt(16) | originalSize(4) | encryptedData(...)
 * The encrypted data contains: verifier(16) | verifierHash(32) | packageBytes
 */
object EncryptedPackage {

    private const val MAGIC = "DXLSENC1"
    private const val AES_KEY_SIZE = 256
    private const val AES_BLOCK_SIZE = 16
    private const val ITERATION_COUNT = 50000
    private const val SALT_SIZE = 16
    private const val VERIFIER_SIZE = 16

    fun encrypt(packageBytes: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        // Plaintext: verifier(16) + verifierHash(32) + packageBytes
        val verifier = ByteArray(VERIFIER_SIZE).also { SecureRandom().nextBytes(it) }
        val verifierHash = sha256(verifier)
        val plaintext = verifier + verifierHash + packageBytes

        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, salt)
        val encryptedData = cipher.doFinal(plaintext)

        val output = ByteArrayOutputStream()
        output.write(MAGIC.toByteArray(Charsets.US_ASCII))
        output.write(salt)
        output.write(intToBytes(packageBytes.size))
        output.write(encryptedData)
        return output.toByteArray()
    }

    fun decrypt(encryptedBytes: ByteArray, password: String): ByteArray {
        val input = ByteArrayInputStream(encryptedBytes)

        val magic = ByteArray(8)
        input.read(magic)
        if (String(magic, Charsets.US_ASCII) != MAGIC) {
            throw PasswordException("Not a DroidOffice encrypted file")
        }

        val salt = ByteArray(SALT_SIZE)
        input.read(salt)
        val originalSize = bytesToInt(readNBytes(input, 4))
        val encryptedData = input.readBytes()

        val key = deriveKey(password, salt)
        val cipher = createCipher(Cipher.DECRYPT_MODE, key, salt)
        val plaintext = try {
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            throw PasswordException("Incorrect password or corrupted file", e)
        }

        // Verify: plaintext = verifier(16) + verifierHash(32) + packageBytes
        if (plaintext.size < VERIFIER_SIZE + 32) {
            throw PasswordException("Incorrect password")
        }
        val verifier = plaintext.copyOfRange(0, VERIFIER_SIZE)
        val storedHash = plaintext.copyOfRange(VERIFIER_SIZE, VERIFIER_SIZE + 32)
        val computedHash = sha256(verifier)
        if (!storedHash.contentEquals(computedHash)) {
            throw PasswordException("Incorrect password")
        }

        return plaintext.copyOfRange(VERIFIER_SIZE + 32, VERIFIER_SIZE + 32 + originalSize)
    }

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return String(bytes, 0, 8, Charsets.US_ASCII) == MAGIC
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_16LE)
        var hash = sha256(salt + passwordBytes)
        for (i in 0 until ITERATION_COUNT) {
            hash = sha256(intToBytes(i) + hash)
        }
        hash = sha256(hash + intToBytes(0))
        return hash.copyOf(AES_KEY_SIZE / 8)
    }

    private fun createCipher(mode: Int, key: ByteArray, salt: ByteArray): Cipher {
        val iv = sha256(salt).copyOf(AES_BLOCK_SIZE)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun bytesToInt(bytes: ByteArray): Int =
        (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)

    private fun readNBytes(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read <= 0) break
            offset += read
        }
        return buf
    }
}
