package com.example.autoavp.ui.scan

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.autoavp.domain.model.ScannedData
import com.example.autoavp.domain.model.TrackingType
import com.example.autoavp.domain.model.ValidationStatus
import com.example.autoavp.domain.utils.AddressParser
import com.example.autoavp.domain.utils.TrackingParser
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import javax.inject.Inject

class AutoAvpAnalyzer @Inject constructor(
    private val barcodeScanner: BarcodeScanner,
    private val textRecognizer: TextRecognizer
) : ImageAnalysis.Analyzer {

    var onResult: ((ScannedData, Boolean) -> Unit)? = null
    var onDetectionUpdate: ((String?, String?) -> Unit)? = null
    
    private var isManualCaptureRequested = false

    fun requestManualCapture() {
        isManualCaptureRequested = true
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)
            val isManual = isManualCaptureRequested
            isManualCaptureRequested = false

            val barcodeTask = barcodeScanner.process(image)
            val textTask = textRecognizer.process(image)

            Tasks.whenAllComplete(barcodeTask, textTask)
                .addOnCompleteListener {
                    val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()
                    val visionText = if (textTask.isSuccessful) textTask.result else null
                    val ocrText = visionText?.text ?: ""

                    val liveTracking = barcodes.firstOrNull { it.format != Barcode.FORMAT_QR_CODE }?.rawValue
                    val liveOcr = ocrText.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
                    onDetectionUpdate?.invoke(liveTracking, if (liveOcr.isBlank()) null else liveOcr)

                    if (isManual || barcodes.isNotEmpty() || ocrText.contains("RECOMMAND", ignoreCase = true)) {
                        val data = extractData(barcodes, ocrText, isManual)
                        if (data != null) {
                            onResult?.invoke(data, isManual)
                        }
                    }
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun processHighQualityImage(image: InputImage, onComplete: (ScannedData?) -> Unit) {
        val barcodeTask = barcodeScanner.process(image)
        val textTask = textRecognizer.process(image)

        Tasks.whenAllComplete(barcodeTask, textTask).addOnCompleteListener {
            val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()
            val visionText = if (textTask.isSuccessful) textTask.result else null
            val ocrText = visionText?.text ?: ""
            onComplete(extractData(barcodes, ocrText, isManual = true))
        }
    }

    private fun extractData(barcodes: List<Barcode>, ocrText: String, isManual: Boolean): ScannedData? {
        var trackingNumber: String? = null
        var trackingType: TrackingType? = null

        val smartData = barcodes.find { it.format == Barcode.FORMAT_DATA_MATRIX }
        val otherCode = barcodes.firstOrNull { it.format != Barcode.FORMAT_QR_CODE }

        if (smartData != null) {
            val result = TrackingParser.parseTrackingNumber(smartData.rawValue ?: "", isDataMatrix = true)
            // parseTrackingNumber renvoie déjà 15 chars (14+clé calculée par défaut)
            trackingNumber = result.first
            trackingType = result.second
        } else if (otherCode != null) {
            val result = TrackingParser.parseTrackingNumber(otherCode.rawValue ?: "", isDataMatrix = false)
            trackingNumber = result.first
            trackingType = result.second
        }

        // --- VERIFICATION CROISÉE (Trust but Verify) ---
        var validationStatus = ValidationStatus.CALCULATED
        var finalTrackingNumber = trackingNumber
        var lKey: String? = null
        var iKey: String? = null
        var oKey: String? = null

        if (trackingNumber != null && trackingNumber.length == 15) {
            val core14 = trackingNumber.substring(0, 14)
            lKey = TrackingParser.calculateLaPosteKey(core14)
            iKey = TrackingParser.calculateIso7064Key(core14)
            
            // Recherche de ce numéro dans l'OCR pour voir la clé "réelle"
            val ocrMatch = Regex("$core14\\s?([0-9A-Z])").find(ocrText)
            
            if (ocrMatch != null) {
                oKey = ocrMatch.groupValues[1]

                when (oKey) {
                    iKey, lKey -> {
                        validationStatus = ValidationStatus.VERIFIED
                        finalTrackingNumber = core14 + oKey 
                    }
                    else -> {
                        validationStatus = ValidationStatus.WARNING
                        finalTrackingNumber = core14 + oKey 
                    }
                }
            } else {
                validationStatus = ValidationStatus.CALCULATED
            }
        }

        val addressResult = AddressParser.parse(ocrText)

        if (isManual || (finalTrackingNumber != null && addressResult != null)) {
            return ScannedData(
                trackingNumber = finalTrackingNumber ?: trackingNumber,
                trackingType = trackingType,
                recipientName = addressResult?.name,
                rawText = addressResult?.fullAddress ?: ocrText,
                addressCandidates = emptyList(),
                confidenceStatus = validationStatus,
                luhnKey = lKey,
                isoKey = iKey,
                ocrKey = oKey
            )
        }
        return null
    }
}