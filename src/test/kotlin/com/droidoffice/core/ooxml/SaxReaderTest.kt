package com.droidoffice.core.ooxml

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals

class SaxReaderTest {

    @Test
    fun `parseRelationships extracts relationships`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
            </Relationships>
        """.trimIndent()

        val rels = parseRelationships(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(2, rels.size)
        assertEquals("rId1", rels[0].id)
        assertEquals(RelationshipTypes.WORKSHEET, rels[0].type)
        assertEquals("worksheets/sheet1.xml", rels[0].target)
        assertEquals("rId2", rels[1].id)
        assertEquals(RelationshipTypes.SHARED_STRINGS, rels[1].type)
    }

    @Test
    fun `parseContentTypes extracts defaults and overrides`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            </Types>
        """.trimIndent()

        val ct = parseContentTypes(ByteArrayInputStream(xml.toByteArray()))

        assertEquals("application/vnd.openxmlformats-package.relationships+xml", ct.defaults["rels"])
        assertEquals("application/xml", ct.defaults["xml"])
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
            ct.overrides["xl/workbook.xml"]
        )
    }
}
