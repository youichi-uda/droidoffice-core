package com.droidoffice.core.ooxml

import com.droidoffice.core.exception.PasswordException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedPackageTest {

    @Test
    fun `encrypt and decrypt round trip`() {
        val original = "Hello, encrypted world!".toByteArray()
        val password = "s3cret!"

        val encrypted = EncryptedPackage.encrypt(original, password)
        assertTrue(EncryptedPackage.isEncrypted(encrypted))

        val decrypted = EncryptedPackage.decrypt(encrypted, password)
        assertEquals(String(original), String(decrypted))
    }

    @Test
    fun `wrong password throws PasswordException`() {
        val original = "Secret data".toByteArray()
        val encrypted = EncryptedPackage.encrypt(original, "correct")

        assertThrows<PasswordException> {
            EncryptedPackage.decrypt(encrypted, "wrong")
        }
    }

    @Test
    fun `non-encrypted data is not detected as encrypted`() {
        assertFalse(EncryptedPackage.isEncrypted("PK\u0003\u0004".toByteArray()))
        assertFalse(EncryptedPackage.isEncrypted(ByteArray(4)))
    }

    @Test
    fun `large data round trip`() {
        val original = ByteArray(100_000) { (it % 256).toByte() }
        val password = "test123"

        val encrypted = EncryptedPackage.encrypt(original, password)
        val decrypted = EncryptedPackage.decrypt(encrypted, password)

        assertTrue(original.contentEquals(decrypted))
    }
}
