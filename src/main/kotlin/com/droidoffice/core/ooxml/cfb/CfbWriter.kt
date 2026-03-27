package com.droidoffice.core.ooxml.cfb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal OLE2/CFB (Compound File Binary Format) writer.
 * Produces a Version 3 CFB with 512-byte sectors (most compatible).
 */
object CfbWriter {

    private const val SECTOR_SIZE = 512
    private const val HEADER_SIZE = 512
    private const val DIR_ENTRY_SIZE = 128
    private const val ENTRIES_PER_SECTOR = SECTOR_SIZE / DIR_ENTRY_SIZE  // 4
    private const val FAT_ENTRIES_PER_SECTOR = SECTOR_SIZE / 4          // 128
    private const val ENDOFCHAIN = -2           // 0xFFFFFFFE
    private const val FATSECT = -3              // 0xFFFFFFFD
    private const val FREESECT = -1             // 0xFFFFFFFF
    private const val NOSTREAM = -1             // 0xFFFFFFFF

    private val MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
    )

    fun write(streams: Map<String, ByteArray>): ByteArray {
        val streamEntries = streams.entries.toList()

        // Phase 1: Calculate data sectors needed
        // Sector 0: Directory
        // Sector 1..N: stream data
        // Streams < 4096 bytes are padded to 4096 to avoid mini-stream
        // (directory entry records actual size for correct trimming on read)
        val MINI_CUTOFF = 4096
        var nextDataSector = 1
        val streamSectors = mutableListOf<Triple<Int, Int, Int>>() // startSector, sectorCount, reportedSize
        for ((_, data) in streamEntries) {
            // Streams < 4096 get padded sectors but reported size >= 4096
            // so CFB readers use regular FAT instead of mini-stream
            val reportedSize = if (data.isEmpty()) 0 else maxOf(data.size, MINI_CUTOFF)
            val count = if (reportedSize == 0) 0 else ceilDiv(reportedSize, SECTOR_SIZE)
            streamSectors.add(Triple(nextDataSector, count, reportedSize))
            nextDataSector += count
        }

        // Phase 2: Calculate how many FAT sectors we need
        // FAT sectors go after data sectors. Each FAT sector holds 128 entries.
        // We need enough FAT entries to cover: 1 dir + data sectors + FAT sectors themselves.
        var numFatSectors = 1
        while (true) {
            val totalSectors = nextDataSector + numFatSectors
            val fatCapacity = numFatSectors * FAT_ENTRIES_PER_SECTOR
            if (fatCapacity >= totalSectors) break
            numFatSectors++
        }

        val totalSectors = nextDataSector + numFatSectors

        // Phase 3: Build FAT entries
        val fatEntries = IntArray(numFatSectors * FAT_ENTRIES_PER_SECTOR) { FREESECT }
        fatEntries[0] = ENDOFCHAIN // directory sector
        for ((startSector, count, _) in streamSectors) {
            for (i in 0 until count) {
                fatEntries[startSector + i] = if (i < count - 1) startSector + i + 1 else ENDOFCHAIN
            }
        }
        for (f in 0 until numFatSectors) {
            fatEntries[nextDataSector + f] = FATSECT
        }

        // === BUILD OUTPUT ===
        val output = ByteArrayOutputStream()

        // --- HEADER ---
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put(MAGIC)
        header.put(ByteArray(16))                  // CLSID
        header.putShort(0x003E)                    // minor version
        header.putShort(0x0003)                    // major version (V3)
        header.putShort(0xFFFE.toShort())          // byte order
        header.putShort(9)                         // sector size power (2^9=512)
        header.putShort(6)                         // mini sector size power
        header.put(ByteArray(6))                   // reserved
        header.putInt(0)                           // total directory sectors
        header.putInt(numFatSectors)               // total FAT sectors
        header.putInt(0)                           // first directory sector
        header.putInt(0)                           // transaction signature
        header.putInt(0x00001000)                  // mini stream cutoff (4096)
        header.putInt(ENDOFCHAIN)                  // first mini FAT sector
        header.putInt(0)                           // total mini FAT sectors
        header.putInt(ENDOFCHAIN)                  // first DIFAT sector
        header.putInt(0)                           // total DIFAT sectors

        // DIFAT array (109 entries in header)
        for (f in 0 until minOf(numFatSectors, 109)) {
            header.putInt(nextDataSector + f)
        }
        repeat(109 - minOf(numFatSectors, 109)) { header.putInt(FREESECT) }

        output.write(header.array())

        // --- SECTOR 0: DIRECTORY ---
        val dir = ByteBuffer.allocate(SECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        writeDirectoryEntry(dir, "Root Entry", 5, NOSTREAM, NOSTREAM,
            if (streamEntries.isNotEmpty()) 1 else NOSTREAM, ENDOFCHAIN, 0)

        for ((i, entry) in streamEntries.withIndex()) {
            val (name, _) = entry
            val (startSector, count, reportedSize) = streamSectors[i]
            val actualStart = if (count > 0) startSector else ENDOFCHAIN
            val rightSibling = if (i + 1 < streamEntries.size) i + 2 else NOSTREAM
            writeDirectoryEntry(dir, name, 2, NOSTREAM, rightSibling, NOSTREAM,
                actualStart, reportedSize.toLong())
        }

        repeat(ENTRIES_PER_SECTOR - 1 - streamEntries.size) { writeEmptyDirectoryEntry(dir) }
        output.write(dir.array())

        // --- DATA SECTORS ---
        for ((i, entry) in streamEntries.withIndex()) {
            val (_, data) = entry
            val (_, sectorCount, _) = streamSectors[i]
            if (sectorCount == 0) continue
            val totalBytes = sectorCount * SECTOR_SIZE
            // Write data, then zero-pad to fill all allocated sectors
            output.write(data)
            if (data.size < totalBytes) {
                output.write(ByteArray(totalBytes - data.size))
            }
        }

        // --- FAT SECTORS ---
        for (f in 0 until numFatSectors) {
            val fatBuf = ByteBuffer.allocate(SECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val base = f * FAT_ENTRIES_PER_SECTOR
            for (i in 0 until FAT_ENTRIES_PER_SECTOR) {
                fatBuf.putInt(fatEntries[base + i])
            }
            output.write(fatBuf.array())
        }

        return output.toByteArray()
    }

    private fun writeDirectoryEntry(
        buf: ByteBuffer, name: String, type: Int,
        leftSibling: Int, rightSibling: Int, child: Int,
        startSector: Int, size: Long,
    ) {
        val nameBytes = name.toByteArray(Charsets.UTF_16LE)
        val nameField = ByteArray(64)
        nameBytes.copyInto(nameField, 0, 0, minOf(nameBytes.size, 62))
        buf.put(nameField)
        buf.putShort(if (name.isEmpty()) 0 else ((name.length + 1) * 2).toShort())
        buf.put(type.toByte())
        buf.put(1) // black
        buf.putInt(leftSibling)
        buf.putInt(rightSibling)
        buf.putInt(child)
        buf.put(ByteArray(16)) // CLSID
        buf.putInt(0) // state bits
        buf.putLong(0) // creation time
        buf.putLong(0) // modification time
        buf.putInt(startSector)
        buf.putInt(size.toInt()) // size low (V3)
        buf.putInt(0) // size high
    }

    private fun writeEmptyDirectoryEntry(buf: ByteBuffer) {
        buf.put(ByteArray(64))
        buf.putShort(0)
        buf.put(0); buf.put(0)
        buf.putInt(NOSTREAM); buf.putInt(NOSTREAM); buf.putInt(NOSTREAM)
        buf.put(ByteArray(16))
        buf.putInt(0)
        buf.putLong(0); buf.putLong(0)
        buf.putInt(ENDOFCHAIN)
        buf.putInt(0); buf.putInt(0)
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
