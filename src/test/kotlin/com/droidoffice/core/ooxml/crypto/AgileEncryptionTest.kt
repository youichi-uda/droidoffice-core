package com.droidoffice.core.ooxml.crypto

import com.droidoffice.core.exception.PasswordException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgileEncryptionTest {

    @Test
    fun `encrypt and decrypt round trip`() {
        val data = "Hello, MS-OFFCRYPTO!".toByteArray()
        val (encInfo, encPkg) = AgileEncryption.encrypt(data, "password123")
        val decrypted = AgileEncryption.decrypt(encInfo, encPkg, "password123")
        assertTrue(data.contentEquals(decrypted))
    }

    @Test
    fun `wrong password throws PasswordException`() {
        val data = "Secret data".toByteArray()
        val (encInfo, encPkg) = AgileEncryption.encrypt(data, "correct")
        assertThrows<PasswordException> {
            AgileEncryption.decrypt(encInfo, encPkg, "wrong")
        }
    }

    @Test
    fun `large data round trip`() {
        val data = ByteArray(50000) { (it % 256).toByte() }
        val (encInfo, encPkg) = AgileEncryption.encrypt(data, "test")
        val decrypted = AgileEncryption.decrypt(encInfo, encPkg, "test")
        assertTrue(data.contentEquals(decrypted))
    }

    @Test
    fun `small data round trip`() {
        val data = byteArrayOf(1, 2, 3)
        val (encInfo, encPkg) = AgileEncryption.encrypt(data, "p")
        val decrypted = AgileEncryption.decrypt(encInfo, encPkg, "p")
        assertTrue(data.contentEquals(decrypted))
    }

    @Test
    fun `EncryptionInfo is Standard v3_2 format`() {
        val data = "test".toByteArray()
        val (encInfo, _) = AgileEncryption.encrypt(data, "pw")
        // Check version header: 03 00 02 00
        assertEquals(0x03, encInfo[0].toInt() and 0xFF)
        assertEquals(0x00, encInfo[1].toInt() and 0xFF)
        assertEquals(0x02, encInfo[2].toInt() and 0xFF)
        assertEquals(0x00, encInfo[3].toInt() and 0xFF)
    }

    @Test
    fun `unicode password`() {
        val data = "日本語データ".toByteArray()
        val (encInfo, encPkg) = AgileEncryption.encrypt(data, "パスワード")
        val decrypted = AgileEncryption.decrypt(encInfo, encPkg, "パスワード")
        assertTrue(data.contentEquals(decrypted))
    }
}
