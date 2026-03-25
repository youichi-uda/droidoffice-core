package com.droidoffice.core.ooxml

/**
 * Represents the [Content_Types].xml structure.
 */
class ContentTypes {
    val defaults = mutableMapOf<String, String>()
    val overrides = mutableMapOf<String, String>()

    fun contentTypeFor(partName: String): String? {
        // Check overrides first (exact path match)
        overrides[partName]?.let { return it }

        // Fall back to default by extension
        val ext = partName.substringAfterLast('.', "").lowercase()
        return defaults[ext]
    }
}

/**
 * Common OOXML content type strings.
 */
object ContentTypeValues {
    const val WORKSHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    const val SHARED_STRINGS = "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"
    const val STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"
    const val WORKBOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
    const val RELATIONSHIPS = "application/vnd.openxmlformats-package.relationships+xml"
}
