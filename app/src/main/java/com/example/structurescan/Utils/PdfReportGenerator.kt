package com.example.structurescan.Utils
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class PdfAssessmentData(
    val assessmentName: String,
    val date: String,
    val overallRisk: String,
    val totalIssues: Int,
    val crackHighCount: Int,
    val crackModerateCount: Int,
    val crackLowCount: Int,
    val paintCount: Int,
    val algaeCount: Int,
    val buildingType: String = "",
    val constructionYear: String = "",
    val renovationYear: String = "",
    val floors: String = "",
    val material: String = "",
    val foundation: String = "",
    val environment: String = "",
    val previousIssues: String = "",
    val occupancy: String = "",
    val environmentalRisks: String = "",
    val notes: String = "",
    val imageUrls: List<String> = emptyList()
)

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40
    private const val LINE_HEIGHT = 20

    suspend fun generatePdfReport(
        context: Context,
        data: PdfAssessmentData
    ): String? = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var yPosition = MARGIN

            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            yPosition = drawHeader(canvas, data.assessmentName, data.date, yPosition)
            yPosition += 20

            yPosition = drawRiskBadge(canvas, data.overallRisk, yPosition)
            yPosition += 30

            yPosition = drawSummaryText(canvas, data, yPosition)
            yPosition += 30

            if (data.buildingType.isNotEmpty() || data.material.isNotEmpty()) {
                yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 150, ++pageNumber)
                if (yPosition == MARGIN) {
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                }

                yPosition = drawSectionHeader(canvas, "Building Information", yPosition)
                yPosition += 10
                yPosition = drawBuildingInfo(canvas, data, yPosition)
                yPosition += 30
            }

            yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 150, ++pageNumber)
            if (yPosition == MARGIN) {
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
            }

            yPosition = drawSectionHeader(canvas, "Detection Summary", yPosition)
            yPosition += 10
            yPosition = drawDetectionSummary(canvas, data, yPosition)
            yPosition += 30

            yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 200, ++pageNumber)
            if (yPosition == MARGIN) {
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
            }

            yPosition = drawSectionHeader(canvas, "Recommendations", yPosition)
            yPosition += 10
            yPosition = drawRecommendations(canvas, data, yPosition)
            yPosition += 30

            if (data.imageUrls.isNotEmpty()) {
                pdfDocument.finishPage(page)

                data.imageUrls.forEachIndexed { index, imageUrl ->
                    if (imageUrl.isNotEmpty()) {
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        drawImagePage(canvas, imageUrl, index + 1, data.imageUrls.size, context)
                        pdfDocument.finishPage(page)
                    }
                }
            } else {
                pdfDocument.finishPage(page)
            }

            val fileName = "Assessment_${data.assessmentName.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    pdfDocument.close()
                    Log.d("PdfGenerator", "PDF saved to Downloads: $fileName")

                    val tempFile = File(context.cacheDir, fileName)
                    tempFile.absolutePath
                } else {
                    throw Exception("Failed to create file in Downloads")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                pdfDocument.close()

                Log.d("PdfGenerator", "PDF saved to Downloads: ${file.absolutePath}")
                file.absolutePath
            }

        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error generating PDF: ${e.message}", e)
            null
        }
    }

    private fun checkAndCreateNewPage(
        pdfDocument: PdfDocument,
        currentPage: PdfDocument.Page,
        canvas: Canvas,
        currentY: Int,
        requiredSpace: Int,
        newPageNumber: Int
    ): Int {
        return if (currentY + requiredSpace > PAGE_HEIGHT - MARGIN) {
            pdfDocument.finishPage(currentPage)
            MARGIN
        } else {
            currentY
        }
    }

    private fun drawHeader(canvas: Canvas, title: String, date: String, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#0288D1")
        }

        canvas.drawText(title, MARGIN.toFloat(), yPos.toFloat(), paint)
        yPos += 30

        paint.apply {
            textSize = 14f
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.GRAY
        }
        canvas.drawText(date, MARGIN.toFloat(), yPos.toFloat(), paint)
        yPos += LINE_HEIGHT

        return yPos
    }

    private fun drawRiskBadge(canvas: Canvas, riskLevel: String, startY: Int): Int {
        val paint = Paint()
        val color = when (riskLevel) {
            "High Risk" -> android.graphics.Color.parseColor("#D32F2F")
            "Moderate Risk" -> android.graphics.Color.parseColor("#F57C00")
            else -> android.graphics.Color.parseColor("#388E3C")
        }

        paint.color = color
        val rect = Rect(MARGIN, startY, MARGIN + 200, startY + 40)
        canvas.drawRoundRect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat(),
            10f, 10f, paint
        )

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(riskLevel, (MARGIN + 20).toFloat(), (startY + 27).toFloat(), paint)

        return startY + 50
    }

    private fun drawSummaryText(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }

        val summaryText = buildString {
            append("Analysis completed. ")
            if (data.totalIssues > 0) {
                append("${data.totalIssues} areas of concern detected. ")
                val details = mutableListOf<String>()
                // ✅ FIXED: Show ALL crack types
                if (data.crackHighCount > 0) details.add("${data.crackHighCount} serious concrete damage")
                if (data.crackModerateCount > 0) details.add("${data.crackModerateCount} large cracks")
                if (data.crackLowCount > 0) details.add("${data.crackLowCount} small hairline cracks")
                if (data.paintCount > 0) details.add("${data.paintCount} paint damage areas")
                if (data.algaeCount > 0) details.add("${data.algaeCount} algae/moss areas")
                if (details.isNotEmpty()) append(details.joinToString(", ") + ".")
            } else {
                append("No significant issues detected.")
            }
        }

        val lines = wrapText(summaryText, paint, PAGE_WIDTH - 2 * MARGIN)
        lines.forEach { line ->
            canvas.drawText(line, MARGIN.toFloat(), yPos.toFloat(), paint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, startY: Int): Int {
        val paint = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        canvas.drawText(title, MARGIN.toFloat(), startY.toFloat(), paint)
        return startY + 25
    }

    private fun drawBuildingInfo(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val labelPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.GRAY
        }
        val valuePaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }

        if (data.buildingType.isNotEmpty()) {
            canvas.drawText("Type:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.buildingType, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.material.isNotEmpty()) {
            canvas.drawText("Material:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.material, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.constructionYear.isNotEmpty()) {
            canvas.drawText("Built:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.constructionYear, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.floors.isNotEmpty()) {
            canvas.drawText("Floors:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.floors, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.foundation.isNotEmpty()) {
            canvas.drawText("Foundation:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.foundation, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.environment.isNotEmpty()) {
            canvas.drawText("Environment:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.environment, (MARGIN + 150).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.notes.isNotEmpty()) {
            yPos += 10
            canvas.drawText("Additional Notes:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT
            val noteLines = wrapText(data.notes, valuePaint, PAGE_WIDTH - 2 * MARGIN)
            noteLines.forEach { line ->
                canvas.drawText(line, MARGIN.toFloat(), yPos.toFloat(), valuePaint)
                yPos += LINE_HEIGHT
            }
        }

        return yPos
    }

    private fun drawDetectionSummary(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }

        if (data.crackHighCount + data.crackModerateCount + data.crackLowCount > 0) {
            canvas.drawText("Crack Damage:", MARGIN.toFloat(), yPos.toFloat(), paint)
            canvas.drawText(
                "High: ${data.crackHighCount}  Mod: ${data.crackModerateCount}  Low: ${data.crackLowCount}",
                (MARGIN + 200).toFloat(), yPos.toFloat(), paint
            )
            yPos += LINE_HEIGHT
        }

        if (data.paintCount > 0) {
            canvas.drawText("Paint Damage:", MARGIN.toFloat(), yPos.toFloat(), paint)
            canvas.drawText(
                "Total: ${data.paintCount}",
                (MARGIN + 200).toFloat(), yPos.toFloat(), paint
            )
            yPos += LINE_HEIGHT
        }

        if (data.algaeCount > 0) {
            canvas.drawText("Algae/Moss:", MARGIN.toFloat(), yPos.toFloat(), paint)
            canvas.drawText(
                "Total: ${data.algaeCount}",
                (MARGIN + 200).toFloat(), yPos.toFloat(), paint
            )
            yPos += LINE_HEIGHT
        }

        if (data.crackHighCount + data.crackModerateCount + data.crackLowCount == 0 &&
            data.paintCount == 0 &&
            data.algaeCount == 0) {
            canvas.drawText("No damage detected", MARGIN.toFloat(), yPos.toFloat(), paint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    private fun drawRecommendations(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val titlePaint = Paint().apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val confidencePaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
        }

        val recommendations = mutableListOf<Triple<String, Int, String>>()

        if (data.crackHighCount > 0) recommendations.add(Triple("Serious Concrete Damage", data.crackHighCount, "HIGH"))
        if (data.crackModerateCount > 0) recommendations.add(Triple("Large Crack Found", data.crackModerateCount, "HIGH"))
        if (data.crackLowCount > 0) recommendations.add(Triple("Small Hairline Crack/s", data.crackLowCount, "LOW"))
        if (data.paintCount > 0) recommendations.add(Triple("Paint Peeling or Flaking", data.paintCount, "LOW"))
        if (data.algaeCount > 0) recommendations.add(Triple("Algae/Moss Growth", data.algaeCount, "MODERATE"))

        if (recommendations.isEmpty()) {
            canvas.drawText("✓ No Issues Detected", MARGIN.toFloat(), yPos.toFloat(), titlePaint)
            yPos += LINE_HEIGHT
            canvas.drawText("Your structure is in good condition.", (MARGIN + 10).toFloat(), yPos.toFloat(), textPaint)
            yPos += LINE_HEIGHT
            return yPos
        }

        recommendations.forEach { (title, count, severity) ->
            canvas.drawText("• $title", MARGIN.toFloat(), yPos.toFloat(), titlePaint)
            yPos += LINE_HEIGHT

            if (count > 1) {
                canvas.drawText("  Detected in $count locations", (MARGIN + 10).toFloat(), yPos.toFloat(), confidencePaint)
                yPos += LINE_HEIGHT
            }

            val actions = getRecommendationActions(title)
            actions.forEach { action ->
                val wrappedLines = wrapText("- $action", textPaint, PAGE_WIDTH - 2 * MARGIN - 20)
                wrappedLines.forEach { line ->
                    canvas.drawText("  $line", (MARGIN + 10).toFloat(), yPos.toFloat(), textPaint)
                    yPos += LINE_HEIGHT
                }
            }
            yPos += 5
        }

        return yPos
    }

    private fun getRecommendationActions(title: String): List<String> {
        return when (title) {
            "Serious Concrete Damage" -> listOf(
                "Call a structural engineer or building expert within 2-3 days",
                "Take clear photos of the damaged area from different angles",
                "Check if you can see any metal bars (rebar) showing through - avoid using this area",
                "Measure the damage - if deeper than 1 inch or larger than your hand, it needs professional repair",
                "Tap around the area gently - if it sounds hollow, more concrete might be loose",
                "Look for what's causing it: water leaks, cracks, or drainage problems",
                "Professional will: remove damaged concrete, clean metal bars, fill with repair cement",
                "After repair: seal the surface to protect it from water and prevent future damage"
            )

            "Large Crack Found" -> listOf(
                "Contact a structural engineer or building expert within 1-2 weeks",
                "Put markers on both sides of the crack to see if it's getting bigger",
                "Measure and photograph the crack - note how wide, how long, and where it is",
                "Check if doors or windows are sticking, or if floors are sloping",
                "Look for water problems: check gutters, downspouts, and drainage around your building",
                "Notice the crack direction: straight up (settling), sideways (pressure), or diagonal (twisting)",
                "Expert may inject special material to fill the crack or strengthen the structure",
                "Fix the root cause: improve drainage, stabilize foundation, or reduce soil pressure",
                "Seal the crack after repair to keep water out and prevent freeze damage"
            )

            "Small Hairline Crack/s" -> listOf(
                "Check these cracks once or twice a year during regular building inspections",
                "Watch if the crack gets bigger over 6-12 months - mark the ends and take photos with a ruler",
                "Fill the cracks during your next scheduled maintenance to stop water getting in",
                "Use flexible crack filler that works for indoor or outdoor use",
                "Make sure water drains properly away from your building",
                "If the crack grows wider than 2mm (about 1/16 inch), call a building expert",
                "Keep notes and photos of where the crack is and what it looks like",
                "No need to worry - these small cracks are normal in concrete and brick buildings"
            )

            "Paint Peeling or Flaking" -> listOf(
                "Plan to repaint within 12-24 months during regular maintenance",
                "Find and fix the water problem FIRST: look for leaks, bad drainage, or too much humidity",
                "Proper fix steps: scrape off loose paint, clean the surface, apply primer, then paint",
                "Make sure the surface is completely dry before repainting",
                "Choose the right paint: mildew-resistant for bathrooms/kitchens, weather-resistant for outside",
                "Add better airflow in damp areas (install fans or open windows more often)",
                "For outside: keep gutters clean, make sure wood isn't touching the ground",
                "Use bonding primer so new paint sticks properly",
                "Seal gaps and joints with good quality sealant after painting",
                "This is a cosmetic issue - no safety concerns, just maintenance needed"
            )

            "Algae/Moss Growth" -> listOf(
                "Clean the area within 1-2 months using algae remover or cleaning solution",
                "Cleaning method: gently wash with garden hose and soft brush - DON'T use pressure washer on delicate surfaces",
                "Cleaning solutions you can use: bleach mixed with water (50/50) OR vinegar solution (2 gallons water + 2-3 cups white vinegar)",
                "Let the cleaning solution sit for 15-20 minutes, gently scrub, then rinse well",
                "Find and fix why it's wet: improve drainage, fix gutters, repair any roof leaks",
                "Cut back trees and bushes so more sunlight reaches the wall and air can flow",
                "Make sure ground slopes away from building so water runs off",
                "You can apply special coating to prevent algae from growing back",
                "Check again in 6-12 months to make sure the moisture problem is fixed",
                "If algae keeps coming back, you may need to seal the surface with breathable, water-repellent coating"
            )

            else -> listOf(
                "Continue regular maintenance schedule (annual or bi-annual inspections)",
                "Monitor during routine inspections for any emerging issues",
                "Maintain proper drainage and moisture control measures",
                "Keep gutters and downspouts clear and functional",
                "Ensure vegetation is trimmed back from building surfaces",
                "Address any new cracks, stains, or deterioration promptly",
                "No immediate action required - building surface in good condition"
            )
        }
    }

    private fun drawImagePage(canvas: Canvas, imageUrl: String, imageNumber: Int, totalImages: Int, context: Context) {
        try {
            val bitmap = when {
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                    val url = URL(imageUrl)
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())
                }
                imageUrl.startsWith("content://") -> {
                    val uri = android.net.Uri.parse(imageUrl)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bmp
                }
                else -> {
                    throw Exception("Invalid image URL format: $imageUrl")
                }
            }

            if (bitmap == null) {
                throw Exception("Failed to decode bitmap from: $imageUrl")
            }

            val titlePaint = Paint().apply {
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.BLACK
            }
            canvas.drawText("Analyzed Image $imageNumber of $totalImages", MARGIN.toFloat(), 60f, titlePaint)

            val maxWidth = PAGE_WIDTH - 2 * MARGIN
            val maxHeight = PAGE_HEIGHT - 150

            val scaleFactor = minOf(
                maxWidth.toFloat() / bitmap.width,
                maxHeight.toFloat() / bitmap.height
            )

            val scaledWidth = (bitmap.width * scaleFactor).toInt()
            val scaledHeight = (bitmap.height * scaleFactor).toInt()

            val left = (PAGE_WIDTH - scaledWidth) / 2
            val top = 100

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)

            bitmap.recycle()
            scaledBitmap.recycle()

        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error loading image from URL: $imageUrl", e)
            val paint = Paint().apply {
                textSize = 14f
                color = android.graphics.Color.RED
            }
            canvas.drawText("Failed to load image", MARGIN.toFloat(), 150f, paint)
            canvas.drawText("Error: ${e.message}", MARGIN.toFloat(), 170f, paint)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)

            if (width > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = testLine
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }
}