package com.droidoffice.core.ooxml.cfb

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CfbRoundTripTest {

    @Test
    fun `isCfb detects CFB magic bytes`() {
        val cfb = CfbWriter.write(mapOf("test" to byteArrayOf(1, 2, 3)))
        assertTrue(CfbReader.isCfb(cfb))
    }

    @Test
    fun `isCfb rejects non-CFB data`() {
        assertFalse(CfbReader.isCfb(byteArrayOf(0x50, 0x4B, 3, 4)))
        assertFalse(CfbReader.isCfb(byteArrayOf()))
    }

    @Test
    fun `two streams round trip`() {
        val infoData = "Hello, EncryptionInfo!".toByteArray()
        val pkgData = ByteArray(5000) { it.toByte() } // > 4096 to avoid padding

        val cfb = CfbWriter.write(mapOf(
            "EncryptionInfo" to infoData,
            "EncryptedPackage" to pkgData,
        ))

        val streams = CfbReader.read(cfb)
        assertEquals(2, streams.size)
        // Small stream gets padded to 4096, but content starts correctly
        val readInfo = streams["EncryptionInfo"]!!
        assertTrue(readInfo.size >= infoData.size)
        assertTrue(readInfo.copyOf(infoData.size).contentEquals(infoData))
        // Large stream matches exactly
        assertTrue(pkgData.contentEquals(streams["EncryptedPackage"]!!))
    }

    @Test
    fun `large stream spanning multiple sectors`() {
        val largeData = ByteArray(10000) { (it % 256).toByte() }
        val cfb = CfbWriter.write(mapOf("Large" to largeData))
        val streams = CfbReader.read(cfb)
        assertTrue(largeData.contentEquals(streams["Large"]!!))
    }

    @Test
    fun `CFB header magic is correct`() {
        val cfb = CfbWriter.write(mapOf("test" to byteArrayOf(1)))
        assertEquals(0xD0.toByte(), cfb[0])
        assertEquals(0xCF.toByte(), cfb[1])
        assertEquals(0x11.toByte(), cfb[2])
        assertEquals(0xE0.toByte(), cfb[3])
    }
}
