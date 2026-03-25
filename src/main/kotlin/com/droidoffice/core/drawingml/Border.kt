package com.droidoffice.core.drawingml

/**
 * Border style definitions shared across Office formats.
 */
data class BorderProperties(
    val style: BorderStyle = BorderStyle.NONE,
    val color: OfficeColor? = null,
    val width: Double? = null,
)

enum class BorderStyle {
    NONE,
    THIN,
    MEDIUM,
    THICK,
    DASHED,
    DOTTED,
    DOUBLE,
    HAIR,
    MEDIUM_DASHED,
    DASH_DOT,
    MEDIUM_DASH_DOT,
    DASH_DOT_DOT,
    MEDIUM_DASH_DOT_DOT,
    SLANT_DASH_DOT,
}
