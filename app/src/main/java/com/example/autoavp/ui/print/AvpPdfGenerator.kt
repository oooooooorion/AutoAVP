package com.example.autoavp.ui.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import com.example.autoavp.data.local.entities.InstanceOfficeEntity
import com.example.autoavp.data.local.entities.MailItemEntity
import java.io.FileOutputStream
import java.io.IOException

class AvpPdfGenerator(private val context: Context) {

    // Dimensions physiques strictes de l'AVP
    private val AVP_WIDTH_MM = 210f
    private val AVP_HEIGHT_MM = 99f

    // Dimensions A4 pour forcer Android à ne pas redimensionner
    private val A4_WIDTH_MM = 210f
    private val A4_HEIGHT_MM = 297f

    fun printSession(
        items: List<MailItemEntity>,
        office: InstanceOfficeEntity,
        orientation: PrintOrientation = PrintOrientation.HORIZONTAL,
        calibrationX: Float = 0f,
        calibrationY: Float = 0f
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "AutoAVP_${System.currentTimeMillis()}"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                val info = android.print.PrintDocumentInfo.Builder(jobName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(items.size)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                val pdfDocument = PdfDocument()
                
                val ptsWidthA4 = PrintUtils.mmToPoints(A4_WIDTH_MM).toInt()
                val ptsHeightA4 = PrintUtils.mmToPoints(A4_HEIGHT_MM).toInt()

                items.forEachIndexed { index, item ->
                    val pageInfo = PdfDocument.PageInfo.Builder(ptsWidthA4, ptsHeightA4, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    if (orientation == PrintOrientation.VERTICAL) {
                        canvas.save()
                        canvas.translate(PrintUtils.mmToPoints(calibrationX), PrintUtils.mmToPoints(calibrationY))
                        val centeringMarginMm = (A4_WIDTH_MM - AVP_HEIGHT_MM) / 2f
                        canvas.translate(PrintUtils.mmToPoints(centeringMarginMm + AVP_HEIGHT_MM), 0f)
                        canvas.rotate(90f)
                        drawAvpOverlay(canvas, item, office)
                        canvas.restore()
                    } else {
                        canvas.save()
                        canvas.translate(PrintUtils.mmToPoints(calibrationX), PrintUtils.mmToPoints(calibrationY))
                        drawAvpOverlay(canvas, item, office)
                        canvas.restore()
                    }

                    pdfDocument.finishPage(page)
                }

                try {
                    pdfDocument.writeTo(FileOutputStream(destination?.fileDescriptor))
                } catch (e: IOException) {
                    callback?.onWriteFailed(e.toString())
                } finally {
                    pdfDocument.close()
                }
                callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            }
        }, null)
    }

    private fun drawAvpOverlay(canvas: Canvas, item: MailItemEntity, office: InstanceOfficeEntity) {
        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        // 1. Numéro de Suivi
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val trackingText = item.trackingNumber ?: ""
        val maxTrackingWidth = PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_W)
        while (textPaint.measureText(trackingText) > maxTrackingWidth && textPaint.textSize > 6f) {
            textPaint.textSize -= 0.5f
        }
        canvas.drawText(trackingText, PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_X), PrintUtils.mmToPoints(PrintConfig.TRACKING_BOX_Y + 5.5f), textPaint)

        // 2. Adresse
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val addrX = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_X)
        val startAddrY = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_Y + 4.5f)
        val addrBoxBottom = PrintUtils.mmToPoints(PrintConfig.ADDR_BOX_Y + PrintConfig.ADDR_BOX_H)
        val lineHeight = textPaint.descent() - textPaint.ascent()
        val lineSpacing = lineHeight * 0.9f
        val linesAddr = (item.recipientAddress ?: "").split("\n")
        var currentAddrY = startAddrY
        for (line in linesAddr) {
            if (currentAddrY + textPaint.descent() > addrBoxBottom) break
            if (line.isNotBlank()) canvas.drawText(line.trim(), addrX, currentAddrY, textPaint)
            currentAddrY += lineSpacing
        }

        // 3. Bureau d'Instance
        val instX = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_X)
        val instY = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_Y)
        val instW = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_W)
        val instH = PrintUtils.mmToPoints(PrintConfig.INSTANCE_BOX_H)

        val bgPaint = Paint().apply {
            color = try { Color.parseColor(office.colorHex) } catch (e: Exception) { Color.parseColor("#FFCE00") }
            alpha = 100
            style = Paint.Style.FILL
        }
        canvas.drawRect(instX, instY, instX + instW, instY + instH, bgPaint)

        // Préparation du paragraphe centré
        val paragraphLines = mutableListOf<Pair<String, Boolean>>()
        
        // Phrase d'introduction
        val introText = "Votre objet sera disponible à partir de la date et de l'heure indiquées sur l'avis à l'emplacement suivant"
        textPaint.textSize = 6.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paragraphLines.addAll(wrapText(introText, instW - 10f, textPaint).map { it to false })
        
        // Bureau (Gras)
        paragraphLines.add(office.name to true)
        
        // Adresse (limitée pour ne pas exploser le cadre)
        office.address.split("\n").take(2).forEach { paragraphLines.add(it to false) }
        
        // Horaires
        paragraphLines.add(office.openingHours to false)

        val spacing = 8.5f
        val totalHeight = paragraphLines.size * spacing
        var currentY = instY + (instH - totalHeight) / 2f + 6f
        
        textPaint.textAlign = Paint.Align.CENTER
        val centerX = instX + instW / 2f

        paragraphLines.forEach { (text, isBold) ->
            textPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = if (isBold) 8.5f else 6.5f
            canvas.drawText(text.trim(), centerX, currentY, textPaint)
            currentY += spacing
        }
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}