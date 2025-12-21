
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
import android.content.res.Resources.getSystem
import com.example.structurescan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ✅ UPDATED: Area summary for PDF (NO areaType)
data class AreaSummary(
    val areaName: String = "",
    val areaRisk: String = "Low Risk",
    val avgRiskPoints: Float = 0f,
    val imageCount: Int = 0,
    val structuralAnalysisEnabled: Boolean = false,
    val detectedIssues: List<String> = emptyList(),
    val maxTiltAngle: Double? = null,
    val maxTiltSeverity: String? = null
)

// ✅ NEW: Image with metadata
data class ImageDetail(
    val imageUrl: String = "",
    val imageName: String = "",
    val areaName: String = ""
)

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
    // ✅ BUILDING INFO
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
    // ✅ LOCATION INFO
    val address: String = "",
    val footprintArea: String = "",
    val typeOfConstruction: String = "",
    // ✅ AREA DATA WITH RISK POINTS
    val areasData: List<AreaSummary> = emptyList(),
    // ✅ IMAGES WITH METADATA
    val imageDetails: List<ImageDetail> = emptyList()
)

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40
    private const val LINE_HEIGHT = 16
    private const val SMALL_LINE_HEIGHT = 14

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

            // ========== COVER PAGE ==========
// Line ~95 - CHANGE:
            yPosition = drawHeader(canvas, data.assessmentName, data.date, yPosition, context)
            yPosition += 20
            yPosition = drawRiskBadge(canvas, data.overallRisk, yPosition)
            yPosition += 30
            yPosition = drawSummaryText(canvas, data, yPosition)
            yPosition += 40

            // ========== LOCATION & ADDRESS INFO ==========
            yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 120, ++pageNumber)
            if (yPosition == MARGIN) {
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
            }
            yPosition = drawSectionHeader(canvas, "Location Information", yPosition)
            drawLogo(canvas, context)  // ✅ ADD THIS
            yPosition += 8
            yPosition = drawLocationInfo(canvas, data, yPosition)
            yPosition += 25

            // ========== BUILDING INFORMATION ==========
            if (data.buildingType.isNotEmpty() || data.material.isNotEmpty()) {
                yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 180, ++pageNumber)
                if (yPosition == MARGIN) {
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                }
                yPosition = drawSectionHeader(canvas, "Building Information", yPosition)
                yPosition += 8
                yPosition = drawBuildingInfo(canvas, data, yPosition)
                yPosition += 25
            }

            // ========== AREAS & RISK SUMMARY ==========
            if (data.areasData.isNotEmpty()) {
                yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 150, ++pageNumber)
                if (yPosition == MARGIN) {
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                }
                yPosition = drawSectionHeader(canvas, "Area Assessment Summary", yPosition)
                yPosition += 8
                yPosition = drawAreasSummary(canvas, data.areasData, yPosition)
                yPosition += 25
            }

            // ========== DAMAGE DETECTION SUMMARY ==========
            yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 150, ++pageNumber)
            if (yPosition == MARGIN) {
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
            }
            yPosition = drawSectionHeader(canvas, "Damage Detection Summary", yPosition)
            yPosition += 8
            yPosition = drawDetectionSummary(canvas, data, yPosition)
            yPosition += 25

            // ========== DETAILED RECOMMENDATIONS ==========
            yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 160, ++pageNumber)
            if (yPosition == MARGIN) {
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
            }
            yPosition = drawSectionHeader(canvas, "Detailed Recommendations & Actions", yPosition)
            yPosition += 8
            yPosition = drawRecommendations(canvas, data, yPosition)
            yPosition += 25

            // ========== ASSESSMENT NOTES ==========
            if (data.notes.isNotEmpty()) {
                yPosition = checkAndCreateNewPage(pdfDocument, page, canvas, yPosition, 120, ++pageNumber)
                if (yPosition == MARGIN) {
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                }
                yPosition = drawSectionHeader(canvas, "Additional Notes", yPosition)
                yPosition += 8
                yPosition = drawNotesSection(canvas, data.notes, yPosition)
                yPosition += 25
            }

            // ========== IMAGE PAGES WITH METADATA ==========
            if (data.imageDetails.isNotEmpty()) {
                pdfDocument.finishPage(page)

                data.imageDetails.forEachIndexed { index, imageDetail ->
                    if (imageDetail.imageUrl.isNotEmpty()) {
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas

                        drawImagePage(
                            canvas,
                            imageDetail.imageUrl,
                            imageDetail.imageName,
                            imageDetail.areaName,
                            index + 1,
                            data.imageDetails.size,
                            context
                        )
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

    // ✅ FIXED: Perfect 1:1 square logo (no stretching!)
    private fun drawLogo(canvas: Canvas, context: Context): Int {
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo)

            if (logo != null) {
                // ✅ SINGLE SCALE FACTOR for perfect 1:1 square
                val logoSize = 60f  // Perfect size for 115x115 logo
                val scale = logoSize / logo.width.toFloat()  // SAME for width + height

                val matrix = android.graphics.Matrix().apply {
                    postScale(scale, scale)  // ✅ SAME scale → no stretch!
                }

                val scaledLogo = Bitmap.createBitmap(
                    logo, 0, 0, logo.width, logo.height, matrix, true
                )

                // Position top-right
                val rightMargin = PAGE_WIDTH - MARGIN.toFloat()
                canvas.drawBitmap(scaledLogo, rightMargin - logoSize - 10f, 25f, null)

                logo.recycle()
                scaledLogo.recycle()
            }
        } catch (e: Exception) {
            Log.w("PdfLogo", "Logo error: ${e.message}")
        }
        return 0
    }



    private fun drawHeader(canvas: Canvas, title: String, date: String, startY: Int, context: Context): Int {
        drawLogo(canvas, context)

        var yPos = startY
        val paint = Paint().apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#0288D1")
        }

        canvas.drawText(title, MARGIN.toFloat(), yPos.toFloat(), paint)
        yPos += 35

        paint.apply {
            textSize = 13f
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.GRAY
        }
        canvas.drawText("Report Date: $date", MARGIN.toFloat(), yPos.toFloat(), paint)
        yPos += LINE_HEIGHT

        return yPos
    }


    private fun drawRiskBadge(canvas: Canvas, riskLevel: String, startY: Int): Int {
        val paint = Paint()
        val (color, displayText) = when (riskLevel) {
            "UNSAFE", "High Risk" -> Pair(android.graphics.Color.parseColor("#D32F2F"), "🔴 UNSAFE - IMMEDIATE ACTION REQUIRED")
            "RESTRICTED", "Moderate Risk" -> Pair(android.graphics.Color.parseColor("#F57C00"), "🟠 RESTRICTED USE - CAUTION ADVISED")
            "INSPECTED", "Low Risk" -> Pair(android.graphics.Color.parseColor("#388E3C"), "🟢 INSPECTED - SAFE TO OCCUPY")
            else -> Pair(android.graphics.Color.parseColor("#388E3C"), "🟢 INSPECTED - SAFE TO OCCUPY")
        }

        paint.color = color
        val rect = Rect(MARGIN, startY, MARGIN + 515, startY + 45)
        canvas.drawRoundRect(
            rect.left.toFloat(),
            rect.top.toFloat(),
            rect.right.toFloat(),
            rect.bottom.toFloat(),
            8f, 8f, paint
        )

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(displayText, (MARGIN + 15).toFloat(), (startY + 30).toFloat(), paint)

        return startY + 55
    }

    // ✅ Location Information Section
    private fun drawLocationInfo(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val labelPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.GRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }

        if (data.address.isNotEmpty()) {
            canvas.drawText("Address:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            val addressLines = wrapText(data.address, valuePaint, PAGE_WIDTH - 2 * MARGIN - 100)
            addressLines.forEachIndexed { index, line ->
                canvas.drawText(line, (MARGIN + 110).toFloat(), (yPos + (index * LINE_HEIGHT)).toFloat(), valuePaint)
            }
            yPos += (addressLines.size * LINE_HEIGHT) + 5
        }

        if (data.footprintArea.isNotEmpty()) {
            canvas.drawText("Footprint Area:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.footprintArea, (MARGIN + 110).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        if (data.typeOfConstruction.isNotEmpty()) {
            canvas.drawText("Construction Type:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            canvas.drawText(data.typeOfConstruction, (MARGIN + 110).toFloat(), yPos.toFloat(), valuePaint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    private fun drawSummaryText(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
        }

        val summaryText = buildString {
            append("Assessment Summary: ")
            if (data.totalIssues > 0) {
                append("${data.totalIssues} areas of concern detected. ")
                val details = mutableListOf<String>()
                if (data.crackHighCount > 0) details.add("${data.crackHighCount} serious concrete damage")
                if (data.crackModerateCount > 0) details.add("${data.crackModerateCount} large cracks")
                if (data.crackLowCount > 0) details.add("${data.crackLowCount} small hairline cracks")
                if (data.paintCount > 0) details.add("${data.paintCount} paint damage areas")
                if (data.algaeCount > 0) details.add("${data.algaeCount} algae/moss areas")
                if (details.isNotEmpty()) append(details.joinToString(", ") + ".")
            } else {
                append("No significant structural issues detected.")
            }
        }

        val lines = wrapText(summaryText, paint, PAGE_WIDTH - 2 * MARGIN)
        lines.forEach { line ->
            canvas.drawText(line, MARGIN.toFloat(), yPos.toFloat(), paint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    // ✅ UPDATED: Areas Summary with user-friendly risk scores (NO areaType)
    private fun drawAreasSummary(canvas: Canvas, areas: List<AreaSummary>, startY: Int): Int {
        var yPos = startY
        val headerPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.BLACK
        }
        val riskPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }

        areas.forEachIndexed { _, area ->
            // Area Header
            canvas.drawText("▸ ${area.areaName}", MARGIN.toFloat(), yPos.toFloat(), headerPaint)
            yPos += LINE_HEIGHT

            // Risk Level
            canvas.drawText("Risk: ${area.areaRisk}", (MARGIN + 15).toFloat(), yPos.toFloat(), textPaint)
            yPos += SMALL_LINE_HEIGHT

            // ✅ USER-FRIENDLY Risk Score (LOW/MEDIUM/HIGH instead of numbers)
            val riskLevelText = when {
                area.avgRiskPoints < 1.0f -> "LOW"
                area.avgRiskPoints < 2.0f -> "MEDIUM"
                else -> "HIGH"
            }
            val riskColor = when {
                area.avgRiskPoints < 1.0f -> android.graphics.Color.parseColor("#388E3C")
                area.avgRiskPoints < 2.0f -> android.graphics.Color.parseColor("#F57C00")
                else -> android.graphics.Color.parseColor("#D32F2F")
            }
            val riskColorPaint = Paint().apply { color = riskColor }
            canvas.drawCircle((MARGIN + 20).toFloat(), (yPos - 3).toFloat(), 3f, riskColorPaint)
            riskPaint.color = riskColor
            canvas.drawText("Risk Score: $riskLevelText (${String.format("%.1f", area.avgRiskPoints)}/3.0 points)", (MARGIN + 30).toFloat(), yPos.toFloat(), riskPaint)
            yPos += SMALL_LINE_HEIGHT

            // Image Count
            canvas.drawText("Photos Analyzed: ${area.imageCount}", (MARGIN + 30).toFloat(), yPos.toFloat(), textPaint)
            yPos += SMALL_LINE_HEIGHT

            // ✅ TILT: Show MAX tilt across all photos in this area
            if (area.structuralAnalysisEnabled && area.maxTiltAngle != null) {
                canvas.drawText("Worst Tilt: ${String.format("%.1f", area.maxTiltAngle)}° (${area.maxTiltSeverity})", (MARGIN + 30).toFloat(), yPos.toFloat(), textPaint)
                yPos += SMALL_LINE_HEIGHT
            } else if (area.structuralAnalysisEnabled) {
                canvas.drawText("Structural Tilt: No issues detected", (MARGIN + 30).toFloat(), yPos.toFloat(), textPaint)
                yPos += SMALL_LINE_HEIGHT
            }

            // Issues
            if (area.detectedIssues.isNotEmpty()) {
                val issuesText = area.detectedIssues.joinToString(", ")
                val issueLines = wrapText("Issues: $issuesText", textPaint, PAGE_WIDTH - 2 * MARGIN - 30)
                issueLines.forEach { line ->
                    canvas.drawText(line, (MARGIN + 30).toFloat(), yPos.toFloat(), textPaint)
                    yPos += SMALL_LINE_HEIGHT
                }
            }

            yPos += 8 // Spacing between areas
        }

        return yPos
    }

    private fun drawSectionHeader(canvas: Canvas, title: String, startY: Int): Int {
        val paint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#0288D1")
        }
        canvas.drawText(title, MARGIN.toFloat(), startY.toFloat(), paint)

        // Underline
        val linePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0288D1")
            strokeWidth = 1.5f
        }
        canvas.drawLine(
            MARGIN.toFloat(),
            (startY + 5).toFloat(),
            (PAGE_WIDTH - MARGIN).toFloat(),
            (startY + 5).toFloat(),
            linePaint
        )

        return startY + 20
    }

    private fun drawBuildingInfo(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val labelPaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.GRAY
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }

        val info = listOf(
            "Building Type" to data.buildingType,
            "Construction Year" to data.constructionYear,
            "Last Renovation" to data.renovationYear,
            "Number of Floors" to data.floors,
            "Primary Material" to data.material,
            "Foundation Type" to data.foundation,
            "Environment" to data.environment,
            "Occupancy Status" to data.occupancy
        )

        info.forEach { (label, value) ->
            if (value.isNotEmpty()) {
                canvas.drawText("$label:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
                canvas.drawText(value, (MARGIN + 160).toFloat(), yPos.toFloat(), valuePaint)
                yPos += LINE_HEIGHT
            }
        }

        if (data.previousIssues.isNotEmpty()) {
            yPos += 5
            canvas.drawText("Previous Issues:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT
            val issueLines = wrapText(data.previousIssues, valuePaint, PAGE_WIDTH - 2 * MARGIN - 20)
            issueLines.forEach { line ->
                canvas.drawText(line, (MARGIN + 15).toFloat(), yPos.toFloat(), valuePaint)
                yPos += LINE_HEIGHT
            }
        }

        if (data.environmentalRisks.isNotEmpty()) {
            yPos += 5
            canvas.drawText("Environmental Risks:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT
            val riskLines = wrapText(data.environmentalRisks, valuePaint, PAGE_WIDTH - 2 * MARGIN - 20)
            riskLines.forEach { line ->
                canvas.drawText(line, (MARGIN + 15).toFloat(), yPos.toFloat(), valuePaint)
                yPos += LINE_HEIGHT
            }
        }

        return yPos
    }

    private fun drawDetectionSummary(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val labelPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.GRAY
        }

        if (data.crackHighCount + data.crackModerateCount + data.crackLowCount > 0) {
            canvas.drawText("Crack Damage Detection:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT

            if (data.crackHighCount > 0) {
                canvas.drawText("  • Serious Concrete Damage: ${data.crackHighCount} locations (HIGH severity)", (MARGIN + 10).toFloat(), yPos.toFloat(), paint)
                yPos += SMALL_LINE_HEIGHT
            }
            if (data.crackModerateCount > 0) {
                canvas.drawText("  • Large Cracks: ${data.crackModerateCount} locations (MODERATE severity)", (MARGIN + 10).toFloat(), yPos.toFloat(), paint)
                yPos += SMALL_LINE_HEIGHT
            }
            if (data.crackLowCount > 0) {
                canvas.drawText("  • Hairline Cracks: ${data.crackLowCount} locations (LOW severity)", (MARGIN + 10).toFloat(), yPos.toFloat(), paint)
                yPos += SMALL_LINE_HEIGHT
            }
            yPos += 5
        }

        if (data.paintCount > 0) {
            canvas.drawText("Paint Damage Detection:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT
            canvas.drawText("  • Paint Peeling/Flaking: ${data.paintCount} locations", (MARGIN + 10).toFloat(), yPos.toFloat(), paint)
            yPos += SMALL_LINE_HEIGHT
            yPos += 5
        }

        if (data.algaeCount > 0) {
            canvas.drawText("Biological Growth Detection:", MARGIN.toFloat(), yPos.toFloat(), labelPaint)
            yPos += LINE_HEIGHT
            canvas.drawText("  • Algae/Moss: ${data.algaeCount} locations", (MARGIN + 10).toFloat(), yPos.toFloat(), paint)
            yPos += SMALL_LINE_HEIGHT
        }

        if (data.crackHighCount + data.crackModerateCount + data.crackLowCount == 0 &&
            data.paintCount == 0 && data.algaeCount == 0) {
            canvas.drawText("✓ No structural damage detected", MARGIN.toFloat(), yPos.toFloat(), paint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    // ✅ Detailed Recommendations
// ✅ FIXED: Shows ALL recommendations - no truncation
    private fun drawRecommendations(canvas: Canvas, data: PdfAssessmentData, startY: Int): Int {
        var yPos = startY
        val titlePaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.BLACK
        }
        val severityPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.parseColor("#D32F2F")
        }

        val recommendations = mutableListOf<Triple<String, Int, String>>()

        if (data.crackHighCount > 0) recommendations.add(Triple("Serious Concrete Damage", data.crackHighCount, "CRITICAL"))
        if (data.crackModerateCount > 0) recommendations.add(Triple("Large Cracks", data.crackModerateCount, "HIGH"))
        if (data.crackLowCount > 0) recommendations.add(Triple("Hairline Cracks", data.crackLowCount, "LOW"))
        if (data.paintCount > 0) recommendations.add(Triple("Paint Peeling", data.paintCount, "LOW"))
        if (data.algaeCount > 0) recommendations.add(Triple("Algae/Moss Growth", data.algaeCount, "MODERATE"))

        if (recommendations.isEmpty()) {
            canvas.drawText("✓ No Issues Detected", MARGIN.toFloat(), yPos.toFloat(), titlePaint)
            yPos += LINE_HEIGHT
            canvas.drawText("Your structure is in good condition. Continue routine maintenance.", (MARGIN + 10).toFloat(), yPos.toFloat(), textPaint)
            yPos += LINE_HEIGHT
            return yPos
        }

        recommendations.forEach { (title, count, severity) ->
            canvas.drawText("• $title", MARGIN.toFloat(), yPos.toFloat(), titlePaint)
            canvas.drawText("[$severity]", (MARGIN + 200).toFloat(), yPos.toFloat(), severityPaint)
            yPos += LINE_HEIGHT

            if (count > 1) {
                canvas.drawText("Detected in $count locations", (MARGIN + 15).toFloat(), yPos.toFloat(), textPaint)
                yPos += SMALL_LINE_HEIGHT
            }

            // ✅ SHOW ALL RECOMMENDATIONS - NO TRUNCATION
            val actions = getRecommendationActions(title)
            actions.forEach { action ->
                val wrappedLines = wrapText("→ $action", textPaint, PAGE_WIDTH - 2 * MARGIN - 30)
                wrappedLines.forEach { line ->
                    canvas.drawText(line, (MARGIN + 15).toFloat(), yPos.toFloat(), textPaint)
                    yPos += SMALL_LINE_HEIGHT
                }
            }

            yPos += 8  // Extra spacing between recommendation sections
        }

        return yPos
    }

    // ✅ Notes Section
    private fun drawNotesSection(canvas: Canvas, notes: String, startY: Int): Int {
        var yPos = startY
        val paint = Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }

        val noteLines = wrapText(notes, paint, PAGE_WIDTH - 2 * MARGIN)
        noteLines.forEach { line ->
            canvas.drawText(line, MARGIN.toFloat(), yPos.toFloat(), paint)
            yPos += LINE_HEIGHT
        }

        return yPos
    }

    // ✅ UPDATED: Image Page with location name and image filename
    private fun drawImagePage(
        canvas: Canvas,
        imageUrl: String,
        imageName: String,
        areaName: String,
        imageNumber: Int,
        totalImages: Int,
        context: Context
    ) {
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
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.BLACK
            }
            val metadataPaint = Paint().apply {
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = android.graphics.Color.parseColor("#0288D1")
            }

            // Page number
            canvas.drawText("Analyzed Image $imageNumber of $totalImages", MARGIN.toFloat(), 50f, titlePaint)
            drawLogo(canvas, context)  // ✅ ADD LOGO HERE

            // ✅ Area name and image filename
            canvas.drawText("📍 $areaName - $imageName", MARGIN.toFloat(), 70f, metadataPaint)

            val maxWidth = PAGE_WIDTH - 2 * MARGIN
            val maxHeight = PAGE_HEIGHT - 150

            val scaleFactor = minOf(
                maxWidth.toFloat() / bitmap.width,
                maxHeight.toFloat() / bitmap.height
            )

            val scaledWidth = (bitmap.width * scaleFactor).toInt()
            val scaledHeight = (bitmap.height * scaleFactor).toInt()

            val left = (PAGE_WIDTH - scaledWidth) / 2
            val top = 95

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

    private fun getRecommendationActions(title: String): List<String> {
        return when (title) {
            "Serious Concrete Damage" -> listOf(
                "Call a structural engineer or building expert within 2-3 days",
                "Take clear photos of the damaged area from different angles",
                "Check if you can see any metal bars (rebar) showing through",
                "Measure the damage - if deeper than 1 inch, it needs professional repair",
                "Tap around the area gently - if it sounds hollow, more concrete might be loose",
                "Look for what's causing it: water leaks, cracks, or drainage problems",
                "Professional will: remove damaged concrete, clean metal bars, fill with repair cement",
                "After repair: seal the surface to protect it from water and prevent future damage"
            )

            "Large Cracks" -> listOf(
                "Contact a structural engineer or building expert within 1-2 weeks",
                "Put markers on both sides of the crack to see if it's getting bigger",
                "Measure and photograph the crack - note how wide, how long, and where it is",
                "Check if doors or windows are sticking, or if floors are sloping",
                "Look for water problems: check gutters, downspouts, and drainage",
                "Notice the crack direction: straight up (settling), sideways (pressure), or diagonal (twisting)",
                "Expert may inject special material to fill the crack or strengthen the structure",
                "Fix the root cause: improve drainage, stabilize foundation, or reduce soil pressure",
                "Seal the crack after repair to keep water out and prevent freeze damage"
            )

            "Hairline Cracks" -> listOf(
                "Check these cracks once or twice a year during regular building inspections",
                "Watch if the crack gets bigger over 6-12 months - mark the ends and take photos",
                "Fill the cracks during your next scheduled maintenance to stop water getting in",
                "Use flexible crack filler that works for indoor or outdoor use",
                "Make sure water drains properly away from your building",
                "If the crack grows wider than 2mm, call a building expert",
                "Keep notes and photos of where the crack is and what it looks like",
                "No need to worry - these small cracks are normal in concrete and brick buildings"
            )

            "Paint Peeling" -> listOf(
                "Plan to repaint within 12-24 months during regular maintenance",
                "Find and fix the water problem FIRST: look for leaks, bad drainage, or humidity",
                "Proper fix: scrape off loose paint, clean the surface, apply primer, then paint",
                "Make sure the surface is completely dry before repainting",
                "Choose the right paint: mildew-resistant for bathrooms, weather-resistant for outside",
                "Add better airflow in damp areas (install fans or open windows more often)",
                "For outside: keep gutters clean, make sure wood isn't touching the ground",
                "Use bonding primer so new paint sticks properly",
                "Seal gaps and joints with good quality sealant after painting",
                "This is a cosmetic issue - no safety concerns, just maintenance needed"
            )

            "Algae/Moss Growth" -> listOf(
                "Clean the area within 1-2 months using algae remover or cleaning solution",
                "Cleaning: gently wash with garden hose and soft brush - avoid pressure washer",
                "Cleaning solutions: bleach mixed with water (50/50) OR vinegar solution",
                "Let the cleaning solution sit for 15-20 minutes, gently scrub, then rinse well",
                "Find and fix why it's wet: improve drainage, fix gutters, repair any roof leaks",
                "Cut back trees and bushes so more sunlight reaches the wall and air can flow",
                "Make sure ground slopes away from building so water runs off",
                "You can apply special coating to prevent algae from growing back",
                "Check again in 6-12 months to make sure the moisture problem is fixed",
                "If algae keeps coming back, apply breathable, water-repellent coating"
            )

            else -> listOf(
                "Continue regular maintenance schedule (annual or bi-annual inspections)",
                "Monitor during routine inspections for any emerging issues",
                "Maintain proper drainage and moisture control measures",
                "Keep gutters and downspouts clear and functional",
                "Ensure vegetation is trimmed back from building surfaces"
            )
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