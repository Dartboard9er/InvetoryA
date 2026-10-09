package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

object SampleScanGenerator {

    data class SampleHouseholdObject(
        val name: String,
        val brand: String,
        val model: String,
        val modelNumber: String,
        val serialNumber: String,
        val category: String,
        val colorHex: Int,
        val specs: String,
        val barcodeDigits: String,
        val description: String
    )

    val SAMPLE_OBJECTS = listOf(
        SampleHouseholdObject(
            name = "DeWalt 20V Max XR Brushless Compact Drill",
            brand = "DeWalt",
            model = "DCD791B 20V MAX XR",
            modelNumber = "DCD791B",
            serialNumber = "DW-2024-88921",
            category = "Tools",
            colorHex = Color.parseColor("#F59E0B"), // DeWalt yellow
            specs = "20V MAX DC • 0-2000 RPM • 460 UWO BRUSHLESS",
            barcodeDigits = "088591148291",
            description = "DeWalt 20V Max XR Brushless Cordless Compact Drill / Driver with ergonomic rubber grip and LED work light."
        ),
        SampleHouseholdObject(
            name = "Sony Bravia 65-Inch 4K OLED Smart TV",
            brand = "Sony",
            model = "Bravia XR-65A80L",
            modelNumber = "XR-65A80L",
            serialNumber = "S01-8894102-L",
            category = "Electronics",
            colorHex = Color.parseColor("#1E293B"), // Deep slate
            specs = "120V 60Hz 385W • 4K 120Hz HDMI 2.1 • OLED HDR",
            barcodeDigits = "027242925182",
            description = "Sony Bravia XR 65-inch Class A80L 4K HDR OLED Smart Google TV with Cognitive Processor XR."
        ),
        SampleHouseholdObject(
            name = "HP LaserJet Pro M404dn Laser Printer",
            brand = "HP",
            model = "LaserJet Pro M404dn",
            modelNumber = "W1A53A",
            serialNumber = "CND4829182",
            category = "Office",
            colorHex = Color.parseColor("#E2E8F0"), // Off white
            specs = "110-127V 50/60Hz 495W • 40 PPM DUPLEX USB/ETHERNET",
            barcodeDigits = "192018047918",
            description = "HP LaserJet Pro M404dn high-speed monochrome office printer with automatic two-sided printing and built-in Gigabit Ethernet."
        ),
        SampleHouseholdObject(
            name = "Panasonic Eneloop Pro AA Rechargeable Batteries",
            brand = "Panasonic Eneloop",
            model = "Eneloop Pro 2550mAh AA",
            modelNumber = "BK-3HCCA4BA",
            serialNumber = "23-11-ENL-94",
            category = "Storage",
            colorHex = Color.parseColor("#0284C7"), // Eneloop blue
            specs = "Ni-MH 1.2V 2550mAh • PRE-CHARGED SOLAR • 500 CYCLES",
            barcodeDigits = "073096901844",
            description = "Panasonic Eneloop Pro 4-pack high capacity Ni-MH rechargeable AA batteries for professional tools and electronics."
        ),
        SampleHouseholdObject(
            name = "Southwire Outdoor Heavy Duty 50ft Extension Cord",
            brand = "Southwire",
            model = "Outdoor 12/3 SJTW 50-Foot",
            modelNumber = "2588SW0002",
            serialNumber = "SW-50FT-9912",
            category = "Hardware",
            colorHex = Color.parseColor("#EA580C"), // Industrial orange
            specs = "125V 15A 1875W • 12/3 SJTW WATER RESISTANT • LIGHTED END",
            barcodeDigits = "029892025884",
            description = "Southwire 50-foot 12/3 gauge heavy-duty cold-weather outdoor extension cord with lighted power-indicator receptacle."
        ),
        SampleHouseholdObject(
            name = "Craftsman Versastack 71-Pc Mechanics Tool Set",
            brand = "Craftsman",
            model = "Versastack 71-Pc Tool Set",
            modelNumber = "CMMT45710",
            serialNumber = "CM-71PC-84201",
            category = "Tools",
            colorHex = Color.parseColor("#DC2626"), // Craftsman red
            specs = "CHROME VANADIUM STEEL • 72-TOOTH RATCHET 1/4 & 3/8 DRIVE",
            barcodeDigits = "885911634710",
            description = "Craftsman Versastack 71-piece mechanics tool box kit with full polish chrome sockets, ratchets, and heavy duty blow-molded storage case."
        )
    )

    fun generateSampleImageFile(targetFile: File, sampleIndex: Int = 0): File {
        val obj = SAMPLE_OBJECTS[sampleIndex % SAMPLE_OBJECTS.size]
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Dark Studio Workbench Background
        val bgPaint = Paint().apply { color = Color.parseColor("#0B1120") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Workbench Table Surface
        val tablePaint = Paint().apply { color = Color.parseColor("#1E293B") }
        canvas.drawRect(0f, height * 0.48f, width.toFloat(), height.toFloat(), tablePaint)

        // Surface Grid lines
        val gridPaint = Paint().apply {
            color = Color.parseColor("#334155")
            strokeWidth = 2f
        }
        for (y in (height * 0.48f).toInt()..height step 70) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

        // 2. Main Physical Product Body / Casing
        val objShadowPaint = Paint().apply {
            color = Color.parseColor("#020617")
            alpha = 190
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(140f, 210f, 940f, 1270f), 36f, 36f, objShadowPaint)

        val objPaint = Paint().apply {
            color = obj.colorHex
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(150f, 200f, 930f, 1250f), 32f, 32f, objPaint)

        // Dark accent chassis section
        val chassisPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(180f, 230f, 900f, 520f), 24f, 24f, chassisPaint)

        // 3. Brand & Model Emblem on Object
        val brandPaint = Paint().apply {
            color = Color.WHITE
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(obj.brand.uppercase(), width / 2f, 320f, brandPaint)

        val modelHeaderPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(obj.model, width / 2f, 390f, modelHeaderPaint)

        val subtextPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(obj.name, width / 2f, 450f, subtextPaint)

        // 4. Industrial Rating Plate / Serial Specification Label (High contrast metallic white/silver sticker)
        val plateBorderPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        val plateBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val plateRect = RectF(180f, 560f, 900f, 1180f)
        canvas.drawRoundRect(plateRect, 20f, 20f, plateBgPaint)
        canvas.drawRoundRect(plateRect, 20f, 20f, plateBorderPaint)

        // Plate Title Header bar
        val plateHeaderBg = Paint().apply {
            color = Color.parseColor("#0F172A")
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(190f, 570f, 890f, 650f), 12f, 12f, plateHeaderBg)

        val plateHeaderTitle = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PRODUCT RATING & SERIAL IDENTIFIER", width / 2f, 622f, plateHeaderTitle)

        // Specification text lines inside the label
        val labelKeyPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val labelValPaint = Paint().apply {
            color = Color.parseColor("#0369A1") // Vibrant blue for values
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        var textY = 710f
        val startX = 220f

        // BRAND
        canvas.drawText("BRAND / MFR:", startX, textY, labelKeyPaint)
        canvas.drawText(obj.brand, startX + 240f, textY, labelValPaint)

        textY += 60f
        // MODEL
        canvas.drawText("MODEL NAME:", startX, textY, labelKeyPaint)
        canvas.drawText(obj.model, startX + 240f, textY, labelValPaint)

        textY += 60f
        // MODEL NUMBER
        canvas.drawText("MODEL NO:", startX, textY, labelKeyPaint)
        canvas.drawText(obj.modelNumber, startX + 240f, textY, labelValPaint)

        textY += 60f
        // SERIAL NUMBER (Highlighted)
        val serialHighlightBg = Paint().apply {
            color = Color.parseColor("#FEF08A") // Soft yellow highlight box
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(startX - 10f, textY - 40f, 870f, textY + 16f), 8f, 8f, serialHighlightBg)
        val serialKeyPaint = Paint().apply {
            color = Color.parseColor("#854D0E")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val serialValPaint = Paint().apply {
            color = Color.parseColor("#B45309")
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("SERIAL NO:", startX, textY, serialKeyPaint)
        canvas.drawText(obj.serialNumber, startX + 240f, textY, serialValPaint)

        textY += 66f
        // SPECS
        val specsSmallPaint = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("SPECS: " + obj.specs, startX, textY, specsSmallPaint)

        textY += 50f
        // CATEGORY & RATING
        canvas.drawText("CATEGORY: " + obj.category.uppercase() + " • CERTIFIED HOME INVENTORY", startX, textY, specsSmallPaint)

        // 5. Crisp Barcode Graphic with Digits
        val barcodeAreaTop = textY + 30f
        val barPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
        }
        var bx = 260f
        val barPattern = listOf(6, 12, 6, 18, 8, 14, 6, 10, 16, 8, 12, 6, 20, 8, 10, 14, 6, 12, 8, 16, 6, 10, 14, 8, 12, 6)
        var pIdx = 0
        while (bx < 820f) {
            val step = barPattern[pIdx % barPattern.size]
            canvas.drawLine(bx, barcodeAreaTop, bx, barcodeAreaTop + 60f, barPaint)
            bx += step.toFloat()
            pIdx++
        }

        val barcodeDigitsPaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("BARCODE: " + obj.barcodeDigits, width / 2f, barcodeAreaTop + 95f, barcodeDigitsPaint)

        // Footer banner
        val footerPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HOME AI VISUAL OBJECT SCAN • OPTICAL INSPECTION MODE", width / 2f, height - 60f, footerPaint)

        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return targetFile
    }
}
