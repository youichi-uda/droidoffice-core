package com.droidoffice.core.ooxml

import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Lightweight SAX-based XML reader for OOXML parts.
 * Provides a callback-based API for memory-efficient streaming reads.
 */
object SaxReader {

    private val factory: SAXParserFactory by lazy {
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Disable external entities for security
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    }

    /**
     * Parse an XML input stream using the given handler.
     */
    fun parse(input: InputStream, handler: DefaultHandler) {
        val parser = factory.newSAXParser()
        val reader = parser.xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.parse(InputSource(input))
    }
}

/**
 * Convenience base handler that tracks current element text.
 */
abstract class OoxmlHandler : DefaultHandler() {

    private val textBuffer = StringBuilder()

    /**
     * Current accumulated character data.
     */
    protected val currentText: String get() = textBuffer.toString()

    override fun characters(ch: CharArray, start: Int, length: Int) {
        textBuffer.append(ch, start, length)
    }

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        textBuffer.clear()
        onStartElement(localName, attributes)
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        onEndElement(localName)
    }

    protected abstract fun onStartElement(name: String, attrs: Attributes)
    protected abstract fun onEndElement(name: String)
}

/**
 * Parses a .rels file (relationship part) and returns the list of relationships.
 */
fun parseRelationships(input: InputStream): List<Relationship> {
    val relationships = mutableListOf<Relationship>()

    SaxReader.parse(input, object : DefaultHandler() {
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (localName == "Relationship" || qName == "Relationship") {
                val id = attributes.getValue("Id") ?: return
                val type = attributes.getValue("Type") ?: return
                val target = attributes.getValue("Target") ?: return
                relationships.add(Relationship(id, type, target))
            }
        }
    })

    return relationships
}

/**
 * Parses [Content_Types].xml and returns a ContentTypes object.
 */
fun parseContentTypes(input: InputStream): ContentTypes {
    val ct = ContentTypes()

    SaxReader.parse(input, object : DefaultHandler() {
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when {
                localName == "Default" || qName == "Default" -> {
                    val ext = attributes.getValue("Extension") ?: return
                    val contentType = attributes.getValue("ContentType") ?: return
                    ct.defaults[ext.lowercase()] = contentType
                }
                localName == "Override" || qName == "Override" -> {
                    val partName = attributes.getValue("PartName") ?: return
                    val contentType = attributes.getValue("ContentType") ?: return
                    ct.overrides[partName.removePrefix("/")] = contentType
                }
            }
        }
    })

    return ct
}
