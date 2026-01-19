package com.example.autoavp.ui.print

import android.content.Context
import android.print.PrintAttributes

object PrintUtils {
    // 1 pouce = 72 points = 25.4 mm
    private const val POINTS_PER_MM = 72f / 25.4f

    fun mmToPoints(mm: Float): Float {
        return mm * POINTS_PER_MM
    }

    fun pointsToMm(points: Float): Float {
        return points / POINTS_PER_MM
    }
    
    // Taille standard A4 pour référence
    val A4_WIDTH_MM = 210f
    val A4_HEIGHT_MM = 297f
}
