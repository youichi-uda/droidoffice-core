package com.droidoffice.core.ooxml

/**
 * Represents a single relationship entry from a .rels file.
 */
data class Relationship(
    val id: String,
    val type: String,
    val target: String,
)

/**
 * Common OOXML relationship type URIs.
 */
object RelationshipTypes {
    private const val BASE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val WORKSHEET = "$BASE/worksheet"
    const val SHARED_STRINGS = "$BASE/sharedStrings"
    const val STYLES = "$BASE/styles"
    const val THEME = "$BASE/theme"
    const val CHART = "$BASE/chart"
    const val IMAGE = "$BASE/image"
    const val HYPERLINK = "$BASE/hyperlink"
    const val DRAWING = "$BASE/drawing"
    const val COMMENTS = "$BASE/comments"
    const val OFFICE_DOCUMENT = "$BASE/officeDocument"

    // Word-specific
    const val HEADER = "$BASE/header"
    const val FOOTER = "$BASE/footer"
    const val NUMBERING = "$BASE/numbering"
    const val SETTINGS = "$BASE/settings"

    // Slide-specific
    const val SLIDE = "$BASE/slide"
    const val SLIDE_LAYOUT = "$BASE/slideLayout"
    const val SLIDE_MASTER = "$BASE/slideMaster"
    const val NOTE_SLIDE = "$BASE/notesSlide"
}
