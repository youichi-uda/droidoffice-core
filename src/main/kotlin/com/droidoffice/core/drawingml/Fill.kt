package com.droidoffice.core.drawingml

/**
 * Fill (background) definitions shared across Office formats.
 */
sealed class Fill {
    data object None : Fill()
    data class Solid(val color: OfficeColor) : Fill()
    data class Pattern(val patternType: PatternType, val foreground: OfficeColor?, val background: OfficeColor?) : Fill()
}

enum class PatternType {
    NONE,
    SOLID,
    GRAY_125,
    GRAY_0625,
    DARK_GRAY,
    MEDIUM_GRAY,
    LIGHT_GRAY,
    DARK_HORIZONTAL,
    DARK_VERTICAL,
    DARK_DOWN,
    DARK_UP,
    DARK_GRID,
    DARK_TRELLIS,
    LIGHT_HORIZONTAL,
    LIGHT_VERTICAL,
    LIGHT_DOWN,
    LIGHT_UP,
    LIGHT_GRID,
    LIGHT_TRELLIS,
}
