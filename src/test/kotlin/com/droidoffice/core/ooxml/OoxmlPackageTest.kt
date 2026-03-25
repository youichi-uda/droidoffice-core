package com.droidoffice.core.ooxml

import com.droidoffice.core.exception.InvalidFileException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OoxmlPackageTest {

    @Test
    fun `create empty package and add parts`() {
        val pkg = OoxmlPackage.create()
        assertNull(pkg.getPart("test.xml"))

        pkg.setPart("test.xml", "<root/>".toByteArray())
        assertNotNull(pkg.getPart("test.xml"))
        assertEquals("<root/>", pkg.getPart("test.xml")!!.decodeToString())
    }

    @Test
    fun `round-trip write and read`() {
        val original = OoxmlPackage.create()
        original.setPart("xl/workbook.xml", "<workbook/>".toByteArray())
        original.setPart("xl/worksheets/sheet1.xml", "<worksheet/>".toByteArray())
        original.setPart("[Content_Types].xml", "<Types/>".toByteArray())

        // Write to bytes
        val buffer = ByteArrayOutputStream()
        original.writeTo(buffer)

        // Read back
        val restored = OoxmlPackage.open(ByteArrayInputStream(buffer.toByteArray()))

        assertEquals(3, restored.partNames().size)
        assertEquals("<workbook/>", restored.getPart("xl/workbook.xml")!!.decodeToString())
        assertEquals("<worksheet/>", restored.getPart("xl/worksheets/sheet1.xml")!!.decodeToString())
    }

    @Test
    fun `normalize paths removes leading slash`() {
        val pkg = OoxmlPackage.create()
        pkg.setPart("/xl/test.xml", "data".toByteArray())
        assertNotNull(pkg.getPart("xl/test.xml"))
    }

    @Test
    fun `open invalid data throws InvalidFileException`() {
        val garbage = ByteArrayInputStream("not a zip file".toByteArray())
        assertThrows<InvalidFileException> {
            OoxmlPackage.open(garbage)
        }
    }

    @Test
    fun `removePart works`() {
        val pkg = OoxmlPackage.create()
        pkg.setPart("a.xml", "a".toByteArray())
        pkg.setPart("b.xml", "b".toByteArray())
        assertEquals(2, pkg.partNames().size)

        pkg.removePart("a.xml")
        assertEquals(1, pkg.partNames().size)
        assertTrue(pkg.hasPart("b.xml"))
    }
}
