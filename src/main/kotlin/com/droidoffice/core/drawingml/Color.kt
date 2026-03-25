package com.droidoffice.core.drawingml

/**
 * Represents a color in DroidOffice documents.
 * Supports RGB, theme colors, and indexed colors.
 */
sealed class OfficeColor {
    data class Rgb(val red: Int, val green: Int, val blue: Int) : OfficeColor() {
        init {
            require(red in 0..255) { "Red must be 0-255, got $red" }
            require(green in 0..255) { "Green must be 0-255, got $green" }
            require(blue in 0..255) { "Blue must be 0-255, got $blue" }
        }

        fun toHex(): String = "%02X%02X%02X".format(red, green, blue)

        companion object {
            fun fromHex(hex: String): Rgb {
                val h = hex.removePrefix("#")
                require(h.length == 6) { "Hex color must be 6 characters, got: $hex" }
                return Rgb(
                    red = h.substring(0, 2).toInt(16),
                    green = h.substring(2, 4).toInt(16),
                    blue = h.substring(4, 6).toInt(16),
                )
            }
        }
    }

    data class Theme(val themeIndex: Int, val tint: Double = 0.0) : OfficeColor()
    data class Indexed(val index: Int) : OfficeColor()
    data object Auto : OfficeColor()

    companion object {
        val BLACK = Rgb(0, 0, 0)
        val WHITE = Rgb(255, 255, 255)
        val RED = Rgb(255, 0, 0)
        val GREEN = Rgb(0, 128, 0)
        val BLUE = Rgb(0, 0, 255)
        val LIGHT_BLUE = Rgb(173, 216, 230)
        val LIGHT_GRAY = Rgb(211, 211, 211)
    }
}
