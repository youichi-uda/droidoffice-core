package com.droidoffice.core.ooxml

import com.droidoffice.core.exception.InvalidFileException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes OOXML packages (ZIP archives containing XML parts).
 * Used by DroidXLS (.xlsx), DroidDoc (.docx), and DroidSlide (.pptx).
 */
class OoxmlPackage private constructor(
    private val parts: MutableMap<String, ByteArray>,
) {
    /**
     * Returns the raw bytes for a given part path (e.g. "xl/workbook.xml").
     */
    fun getPart(path: String): ByteArray? = parts[normalizePath(path)]

    /**
     * Returns an InputStream for a given part.
     */
    fun getPartAsStream(path: String): InputStream? =
        getPart(path)?.inputStream()

    /**
     * Sets or replaces a part in the package.
     */
    fun setPart(path: String, data: ByteArray) {
        parts[normalizePath(path)] = data
    }

    /**
     * Removes a part from the package.
     */
    fun removePart(path: String) {
        parts.remove(normalizePath(path))
    }

    /**
     * Returns all part paths in this package.
     */
    fun partNames(): Set<String> = parts.keys.toSet()

    /**
     * Returns true if the package contains the given part.
     */
    fun hasPart(path: String): Boolean = parts.containsKey(normalizePath(path))

    /**
     * Writes the package as a ZIP archive to the output stream.
     */
    fun writeTo(output: OutputStream) {
        ZipOutputStream(output).use { zos ->
            for ((path, data) in parts) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    private fun normalizePath(path: String): String =
        path.removePrefix("/")

    companion object {
        /**
         * Creates an empty OOXML package.
         */
        fun create(): OoxmlPackage = OoxmlPackage(mutableMapOf())

        /**
         * Reads an OOXML package from a ZIP input stream.
         * Loads all parts into memory.
         */
        fun open(input: InputStream): OoxmlPackage {
            val parts = mutableMapOf<String, ByteArray>()
            try {
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val buffer = ByteArrayOutputStream()
                            zis.copyTo(buffer)
                            parts[entry.name] = buffer.toByteArray()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                throw InvalidFileException("Failed to read OOXML package: ${e.message}", e)
            }

            if (parts.isEmpty()) {
                throw InvalidFileException("Empty or invalid OOXML package")
            }

            return OoxmlPackage(parts)
        }
    }
}
