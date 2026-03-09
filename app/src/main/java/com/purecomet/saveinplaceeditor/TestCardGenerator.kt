package com.purecomet.saveinplaceeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TestCardGenerator {

    fun generate(): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Black background
        canvas.drawColor(Color.BLACK)

        // Color bar strip at top (~32px tall, 4 equal bands)
        val barHeight = 32f
        val bandWidth = size / 4f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        barPaint.color = Color.RED
        canvas.drawRect(0f, 0f, bandWidth, barHeight, barPaint)

        barPaint.color = Color.GREEN
        canvas.drawRect(bandWidth, 0f, bandWidth * 2, barHeight, barPaint)

        barPaint.color = Color.BLUE
        canvas.drawRect(bandWidth * 2, 0f, bandWidth * 3, barHeight, barPaint)

        barPaint.color = Color.YELLOW
        canvas.drawRect(bandWidth * 3, 0f, size.toFloat(), barHeight, barPaint)

        // Centered date/time text
        val now = LocalDateTime.now()
        val dateLine = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val timeLine = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = 56f
            textAlign = Paint.Align.CENTER
        }

        val cx = size / 2f
        val lineSpacing = textPaint.textSize * 1.3f
        val totalHeight = lineSpacing * 2
        val startY = (size + totalHeight) / 2f - lineSpacing

        canvas.drawText(dateLine, cx, startY, textPaint)
        canvas.drawText(timeLine, cx, startY + lineSpacing, textPaint)

        return bitmap
    }
}
