package com.droidoffice.core.drawingml

/**
 * Common font properties shared across Office formats.
 */
data class FontProperties(
    val name: String? = null,
    val size: Double? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: UnderlineStyle = UnderlineStyle.NONE,
    val strikethrough: Boolean = false,
    val color: OfficeColor? = null,
)

enum class UnderlineStyle {
    NONE,
    SINGLE,
    DOUBLE,
    SINGLE_ACCOUNTING,
    DOUBLE_ACCOUNTING,
}
