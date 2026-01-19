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

    // --- CONFIGURATION DES ZONES (Format AVP : 210 x 99 mm) ---
    private val TRACKING_X_MM = 22f
    private val TRACKING_Y_MM = 52f

    private val ADDR_X_MM = 22f
    private val ADDR_Y_MM = 66f
    private val ADDR_MAX_WIDTH_MM = 85f

    private val INSTANCE_X_MM = 122f
    private val INSTANCE_Y_MM = 55f
    private val INSTANCE_WIDTH_MM = 68f
    private val INSTANCE_HEIGHT_MM = 32f
    
    // --- FIN CONFIGURATION ---

    fun printSession(items: List<MailItemEntity>, office: InstanceOfficeEntity, orientation: PrintOrientation = PrintOrientation.HORIZONTAL) {
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
                
                items.forEachIndexed { index, item ->
                    // Dimensions AVP (DL) : 210mm (595 pts) x 99mm (281 pts)
                    val width = if (orientation == PrintOrientation.HORIZONTAL) 595 else 281
                    val height = if (orientation == PrintOrientation.HORIZONTAL) 281 else 595
                    
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    if (orientation == PrintOrientation.VERTICAL) {
                        // Rotation pour impression verticale (insertion par le petit côté)
                        canvas.translate(width.toFloat(), 0f)
                        canvas.rotate(90f)
                    }

                    drawAvpOverlay(canvas, item, office)

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
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val lineHeight = textPaint.descent() - textPaint.ascent()

        // 1. Numéro de Suivi
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(item.trackingNumber ?: "", PrintUtils.mmToPoints(TRACKING_X_MM), PrintUtils.mmToPoints(TRACKING_Y_MM), textPaint)

        // 2. Adresse
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val addrX = PrintUtils.mmToPoints(ADDR_X_MM)
        var addrY = PrintUtils.mmToPoints(ADDR_Y_MM)
        (item.recipientAddress ?: "").split("\n").take(4).forEach { line ->
            if (line.isNotBlank()) {
                canvas.drawText(line.trim(), addrX, addrY, textPaint)
                addrY += lineHeight * 0.9f
            }
        }

        // 3. Bureau d'Instance
        val instX = PrintUtils.mmToPoints(INSTANCE_X_MM)
        val instY = PrintUtils.mmToPoints(INSTANCE_Y_MM)
        val instW = PrintUtils.mmToPoints(INSTANCE_WIDTH_MM)
        val instH = PrintUtils.mmToPoints(INSTANCE_HEIGHT_MM)

        val bgPaint = Paint().apply {
            color = try { Color.parseColor(office.colorHex) } catch (e: Exception) { Color.parseColor("#FFCE00") }
            style = Paint.Style.FILL
        }
        canvas.drawRect(instX, instY - 10f, instX + instW, instY + instH - 10f, bgPaint)

        textPaint.color = Color.BLACK
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 9f
        var currentY = instY + 2f
        canvas.drawText(office.name, instX + 5f, currentY, textPaint)
        
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 7.5f
        office.address.split("\n").take(3).forEach { line ->
            currentY += lineHeight * 0.65f
            canvas.drawText(line.trim(), instX + 5f, currentY, textPaint)
        }
        currentY += lineHeight * 0.75f
        canvas.drawText(office.openingHours, instX + 5f, currentY, textPaint)
    }
}