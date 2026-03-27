package com.droidoffice.core.ooxml.cfb

import com.droidoffice.core.exception.InvalidFileException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal OLE2/CFB (Compound File Binary Format) reader.
 * Supports V3 (512-byte sectors) and V4 (4096-byte sectors).
 */
object CfbReader {

    private const val HEADER_SIZE = 512
    private const val DIR_ENTRY_SIZE = 128
    private const val ENDOFCHAIN = -2      // 0xFFFFFFFE
    private const val FATSECT = -3         // 0xFFFFFFFD
    private const val FREESECT = -1        // 0xFFFFFFFF
    private const val NOSTREAM = -1

    private val MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

    fun isCfb(data: ByteArray): Boolean {
        if (data.size < 8) return false
        return data[0] == MAGIC[0] && data[1] == MAGIC[1] &&
            data[2] == MAGIC[2] && data[3] == MAGIC[3] &&
            data[4] == MAGIC[4] && data[5] == MAGIC[5] &&
            data[6] == MAGIC[6] && data[7] == MAGIC[7]
    }

    /**
     * Read all named streams from a CFB container.
     * Returns a map of stream name → stream data.
     */
    fun read(data: ByteArray): Map<String, ByteArray> {
        if (!isCfb(data)) throw InvalidFileException("Not a valid CFB file")

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Parse header
        buf.position(26)
        val majorVersion = buf.getShort().toInt() and 0xFFFF
        buf.position(30)
        val sectorSizePower = buf.getShort().toInt() and 0xFFFF
        val sectorSize = 1 shl sectorSizePower

        buf.position(44)
        val totalFatSectors = buf.getInt()
        val firstDirSector = buf.getInt()
        buf.position(60)
        val firstDifatSector = buf.getInt()
        val totalDifatSectors = buf.getInt()

        // Determine header area size (V4 header occupies one full sector)
        val headerAreaSize = if (majorVersion == 4) sectorSize else HEADER_SIZE

        // Read DIFAT to collect FAT sector locations
        val fatSectorIds = mutableListOf<Int>()

        // First 109 DIFAT entries in header (bytes 76-511)
        buf.position(76)
        for (i in 0 until 109) {
            val id = buf.getInt()
            if (id == FREESECT || id < 0) break
            fatSectorIds.add(id)
        }

        // Additional DIFAT sectors (if any)
        var difatSector = firstDifatSector
        while (difatSector >= 0 && difatSector != ENDOFCHAIN) {
            val pos = sectorOffset(difatSector, sectorSize, headerAreaSize)
            buf.position(pos)
            for (i in 0 until (sectorSize / 4) - 1) {
                val id = buf.getInt()
                if (id == FREESECT || id < 0) break
                fatSectorIds.add(id)
            }
            difatSector = buf.getInt() // Last entry is next DIFAT sector
        }

        // Read FAT
        val fat = mutableListOf<Int>()
        for (fatSectorId in fatSectorIds) {
            val pos = sectorOffset(fatSectorId, sectorSize, headerAreaSize)
            buf.position(pos)
            for (i in 0 until sectorSize / 4) {
                fat.add(buf.getInt())
            }
        }

        // Read directory entries by following chain from firstDirSector
        val dirData = readStream(data, firstDirSector, fat, sectorSize, headerAreaSize)
        val dirBuf = ByteBuffer.wrap(dirData).order(ByteOrder.LITTLE_ENDIAN)
        val entryCount = dirData.size / DIR_ENTRY_SIZE

        data class DirEntry(val name: String, val type: Int, val startSector: Int, val size: Long)

        val entries = mutableListOf<DirEntry>()
        for (i in 0 until entryCount) {
            dirBuf.position(i * DIR_ENTRY_SIZE)

            // Name (UTF-16LE)
            val nameBytes = ByteArray(64)
            dirBuf.get(nameBytes)
            val nameSize = dirBuf.getShort().toInt() and 0xFFFF
            val name = if (nameSize > 2) {
                String(nameBytes, 0, nameSize - 2, Charsets.UTF_16LE)
            } else ""

            val type = dirBuf.get().toInt() and 0xFF
            dirBuf.get() // color
            dirBuf.getInt() // left sibling
            dirBuf.getInt() // right sibling
            dirBuf.getInt() // child

            dirBuf.position(i * DIR_ENTRY_SIZE + 116) // skip CLSID + state + timestamps
            val startSector = dirBuf.getInt()
            // V3: size is 4 bytes (uint32). V4: 8 bytes (uint64).
            val sizeLow = dirBuf.getInt().toLong() and 0xFFFFFFFFL
            val sizeHigh = if (majorVersion == 4) (dirBuf.getInt().toLong() and 0xFFFFFFFFL) else { dirBuf.getInt(); 0L }
            val size = sizeLow or (sizeHigh shl 32)

            entries.add(DirEntry(name, type, startSector, size))
        }

        // Read stream data for each non-root entry
        val result = mutableMapOf<String, ByteArray>()
        for (entry in entries) {
            if (entry.type == 2 && entry.name.isNotEmpty()) { // type 2 = stream
                val streamData = readStream(data, entry.startSector, fat, sectorSize, headerAreaSize)
                // Trim to actual size
                val trimmed = if (entry.size < streamData.size) {
                    streamData.copyOf(entry.size.toInt())
                } else {
                    streamData
                }
                result[entry.name] = trimmed
            }
        }

        return result
    }

    private fun readStream(
        data: ByteArray,
        startSector: Int,
        fat: List<Int>,
        sectorSize: Int,
        headerAreaSize: Int,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        var sector = startSector
        while (sector >= 0 && sector != ENDOFCHAIN) {
            val pos = sectorOffset(sector, sectorSize, headerAreaSize)
            val len = minOf(sectorSize, data.size - pos)
            if (len > 0) output.write(data, pos, len)
            sector = if (sector < fat.size) fat[sector] else ENDOFCHAIN
        }
        return output.toByteArray()
    }

    private fun sectorOffset(sectorId: Int, sectorSize: Int, headerAreaSize: Int): Int {
        return headerAreaSize + sectorId * sectorSize
    }
}
