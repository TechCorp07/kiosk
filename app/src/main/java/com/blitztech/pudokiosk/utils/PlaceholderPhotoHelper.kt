package com.blitztech.pudokiosk.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

object PlaceholderPhotoHelper {

    /**
     * Generates a tiny (<10kb) fallback photo to bypass backend limits
     * when the actual kiosk camera fails or is offline.
     */
    fun createFallbackPhoto(context: Context, referenceId: String): File {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        val paint = Paint().apply {
            color = Color.DKGRAY
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Fallback Photo", size / 2f, size / 2f - 20, paint)
        canvas.drawText("Ref: $referenceId", size / 2f, size / 2f + 20, paint)

        val file = File(context.cacheDir, "fallback_$referenceId.jpg")
        FileOutputStream(file).use { out ->
            // Quality 50 to keep it very small
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out)
        }
        return file
    }
}
