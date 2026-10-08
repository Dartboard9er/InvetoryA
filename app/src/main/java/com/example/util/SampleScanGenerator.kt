package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

object SampleScanGenerator {

    data class SampleHouseholdObject(
        val name: String,
        val brand: String,
        val model: String,
        val category: String,
        val colorHex: Int,
        val labelText: String
    )

    val SAMPLE_OBJECTS = listOf(
        SampleHouseholdObject(
            name = "Cordless Drill DCD791",
            brand = "DeWalt",
            model = "DCD791B 20V MAX XR",
            category = "Tools",
            colorHex = Color.parseColor("#F59E0B"), // DeWalt yellow
            labelText = "DEWALT 20V MAX XR BRUSHLESS"
        ),
        SampleHouseholdObject(
            name = "Bravia 65-Inch 4K OLED TV",
            brand = "Sony",
            model = "XR-65A80L",
            category = "Electronics",
            colorHex = Color.parseColor("#1E293B"),
            labelText = "SONY BRAVIA OLED 4K HDR"
        ),
        SampleHouseholdObject(
            name = "LaserJet Pro Printer",
            brand = "HP",
            model = "M404dn",
            category = "Office",
            colorHex = Color.parseColor("#E2E8F0"),
            labelText = "HP LaserJet Pro M404dn"
        ),
        SampleHouseholdObject(
            name = "Rechargeable AA Batteries (4-Pack)",
            brand = "Eneloop",
            model = "BK-3MCCA",
            category = "Storage",
            colorHex = Color.parseColor("#0284C7"),
            labelText = "panasonic eneloop 2000mAh AA"
        ),
        SampleHouseholdObject(
            name = "Heavy Duty 50ft Extension Cord",
            brand = "Southwire",
            model = "Outdoor 12/3 Gauge",
            category = "Hardware",
            colorHex = Color.parseColor("#EA580C"),
            labelText = "OUTDOOR 12/3 SJTW 50FT"
        ),
        SampleHouseholdObject(
            name = "Mechanics Tool Box Set",
            brand = "Craftsman",
            model = "Versastack 71-Pc",
            category = "Tools",
            colorHex = Color.parseColor("#DC2626"),
            labelText = "CRAFTSMAN VERSASTACK 71PC"
        )
    )

    fun generateSampleImageFile(targetFile: File, sampleIndex: Int = 0): File {
        val obj = SAMPLE_OBJECTS[sampleIndex % SAMPLE_OBJECTS.size]
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background studio workbench texture
        val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Surface table
        val tablePaint = Paint().apply { color = Color.parseColor("#1E293B") }
        canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), tablePaint)

        // Subtle grid lines on surface
        val gridPaint = Paint().apply {
            color = Color.parseColor("#334155")
            strokeWidth = 3f
        }
        for (y in (height * 0.55f).toInt()..height step 80) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

        // 2. Main Object Body
        val objPaint = Paint().apply {
            color = obj.colorHex
            isAntiAlias = true
        }
        val objRect = RectF(220f, 380f, 860f, 1020f)
        canvas.drawRoundRect(objRect, 48f, 48f, objPaint)

        // Shadow under object
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#050811")
            alpha = 180
            isAntiAlias = true
        }
        canvas.drawOval(RectF(180f, 980f, 900f, 1060f), shadowPaint)

        // 3. Brand Accent / Label Plate
        val platePaint = Paint().apply {
            color = Color.parseColor("#000000")
            alpha = 200
            isAntiAlias = true
        }
        val plateRect = RectF(280f, 520f, 800f, 720f)
        canvas.drawRoundRect(plateRect, 24f, 24f, platePaint)

        // Brand & Model Text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 52f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(obj.brand.uppercase(), width / 2f, 600f, textPaint)

        val modelPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 34f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(obj.model, width / 2f, 660f, modelPaint)

        // Barcode / Serial simulation sticker
        val stickerPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(320f, 780f, 760f, 880f), 12f, 12f, stickerPaint)

        val barcodeBarPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
        }
        var x = 360f
        while (x < 720f) {
            canvas.drawLine(x, 800f, x, 850f, barcodeBarPaint)
            x += (8..24).random()
        }

        val footerTextPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 30f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HOME AI TEST SCAN • PHYSICAL OBJECT PHOTO", width / 2f, height - 80f, footerTextPaint)

        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return targetFile
    }
}
