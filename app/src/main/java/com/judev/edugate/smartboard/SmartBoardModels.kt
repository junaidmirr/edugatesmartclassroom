package com.judev.edugate.smartboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

enum class Tool { PEN, MARKER, ERASER, TEXT, SHAPE, EMOJI, SELECT }

enum class ShapeType { RECTANGLE, CIRCLE, LINE, TRIANGLE, ARROW }

/**
 * All coordinates stored in NORMALISED space (0f..1f relative to canvas size).
 * This makes the board fully resolution-independent across devices.
 */
sealed class BoardElement {
    data class PathElement(
        val path: Path,
        val color: Color,
        val strokeWidth: Float,
        val isEraser: Boolean,
        val points: List<Offset> = emptyList(),
        val opacity: Float = 1f,
        val id: Long = System.nanoTime()
    ) : BoardElement()

    data class ShapeElement(
        val id: Long = System.nanoTime(),
        val type: ShapeType,
        val offset: Offset,          // normalised
        val size: Size,              // normalised
        val color: Color,
        val fillColor: Color = Color.Transparent,
        val strokeWidth: Float = 5f,
        val rotation: Float = 0f,
        val opacity: Float = 1f
    ) : BoardElement()

    data class TextElement(
        val id: Long = System.nanoTime(),
        val text: String,
        val offset: Offset,          // normalised
        val fontSize: Float = 40f,
        val color: Color = Color.Black,
        val rotation: Float = 0f,
        val opacity: Float = 1f,
        val bold: Boolean = false
    ) : BoardElement()

    data class EmojiElement(
        val id: Long = System.nanoTime(),
        val emoji: String,
        val offset: Offset,          // normalised
        val size: Float = 40f,
        val rotation: Float = 0f
    ) : BoardElement()
}
