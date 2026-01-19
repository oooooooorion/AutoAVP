package com.example.autoavp.ui.scan

import android.annotation.SuppressLint
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

    @SuppressLint("SuspiciousIndentation")
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

                                    val data = extractData(barcodes, ocrText, visionText, isManual)

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

                        onComplete(extractData(barcodes, ocrText, visionText, isManual = true))

                    }

                }

            

                private fun extractData(barcodes: List<Barcode>, ocrText: String, visionText: com.google.mlkit.vision.text.Text?, isManual: Boolean): ScannedData? {

                    var barcodeTracking: String? = null

                    var trackingType: TrackingType? = null

            

                    // 1. Extraction depuis le Code-Barres / DataMatrix

                    val smartData = barcodes.find { it.format == Barcode.FORMAT_DATA_MATRIX }

                    val otherCode = barcodes.firstOrNull { it.format != Barcode.FORMAT_QR_CODE }

            

                    if (smartData != null) {

                        val result = TrackingParser.parseTrackingNumber(smartData.rawValue ?: "", isDataMatrix = true)

                        barcodeTracking = result.first

                        trackingType = result.second

                    } else if (otherCode != null) {

                        val result = TrackingParser.parseTrackingNumber(otherCode.rawValue ?: "", isDataMatrix = false)

                        barcodeTracking = result.first

                        trackingType = result.second

                    }

            

                    // 2. Extraction Explicite depuis l'OCR (Format "SD : ...")

                    val ocrTracking = TrackingParser.extractFromOcrLabel(ocrText)

            

                    // 3. Consolidation et Choix du Numéro

                    var finalTrackingNumber: String? = null

                    var validationStatus = ValidationStatus.CALCULATED

                    var lKey: String? = null

                    var iKey: String? = null

                    var oKey: String? = null

            

                    if (ocrTracking != null) {

                        // Cas A : On a lu un numéro complet "SD : ..." via OCR

                        val ocrCore14 = ocrTracking.take(14)

                        oKey = ocrTracking.takeLast(1)

                        

                        if (barcodeTracking != null) {

                            // On a aussi un code-barres, on compare

                            val barcodeCore14 = barcodeTracking.take(14)

                            if (ocrCore14 == barcodeCore14) {

                                // Match parfait Core 14 -> On fait confiance à la clé écrite (OCR)

                                finalTrackingNumber = ocrTracking

                                validationStatus = ValidationStatus.VERIFIED

                            } else {

                                // Mismatch (Le code dit A, le texte dit B) -> Warning, on privilégie souvent le code-barres ou on garde OCR ?

                                // L'utilisateur dit : "le numéro issu de l'OCR prime... si l'OCR a réussi à lire tous les autres numéros"

                                // On suppose que si OCR a lu 15 chars valides, c'est ce qui est écrit.

                                finalTrackingNumber = ocrTracking

                                validationStatus = ValidationStatus.WARNING

                            }

                        } else {

                            // Pas de code-barres, on prend l'OCR seul

                            finalTrackingNumber = ocrTracking

                            validationStatus = ValidationStatus.VERIFIED // "Visuellement lu"

                            trackingType = TrackingType.SMARTDATA_DATAMATRIX // Supposition

                        }

                        

                        // Calculs théoriques pour info

                        lKey = TrackingParser.calculateLaPosteKey(ocrCore14)

                        iKey = TrackingParser.calculateIso7064Key(ocrCore14)

            

                    } else if (barcodeTracking != null) {

                        // Cas B : Pas de label "SD : ..." clair, on se base sur le code-barres + recherche heuristique de clé

                        val core14 = barcodeTracking.take(14)

                        

                        lKey = TrackingParser.calculateLaPosteKey(core14)

                        iKey = TrackingParser.calculateIso7064Key(core14)

                        

                        // Recherche "floue" de la clé dans le texte alentour (ancienne méthode)

                        // On cherche le Core14 suivi d'un caractère isolé

                        val fuzzyMatch = Regex("$core14\\s?([0-9A-Z])").find(ocrText)

                        

                        if (fuzzyMatch != null) {

                            oKey = fuzzyMatch.groupValues[1]

                            finalTrackingNumber = core14 + oKey

                            

                            if (oKey == iKey || oKey == lKey) {

                                validationStatus = ValidationStatus.VERIFIED

                            } else {

                                validationStatus = ValidationStatus.WARNING

                            }

                        } else {

                            // Pas de clé lue, on garde celle calculée par défaut (qui est déjà dans barcodeTracking si parseTrackingNumber l'a ajoutée)

                            finalTrackingNumber = barcodeTracking

                            validationStatus = ValidationStatus.CALCULATED

                            // Si barcodeTracking a 15 chars, le dernier est la clé calculée/par défaut

                            if (barcodeTracking.length == 15) {

                                // On essaie de deviner laquelle c'est pour l'affichage technique

                                val usedKey = barcodeTracking.last().toString()

                                if (usedKey == iKey) validationStatus = ValidationStatus.CALCULATED // "Match ISO"

                                // etc.

                            }

                        }

                    }

            

                    val fullAddressBlock = if (visionText != null) AddressParser.parse(visionText) else null

            

                    if (isManual || (finalTrackingNumber != null && fullAddressBlock != null)) {

                        return ScannedData(

                            trackingNumber = finalTrackingNumber ?: barcodeTracking, // Fallback ultime

                            trackingType = trackingType,

                            recipientName = null, // Fusionné dans le bloc adresse

                            rawText = fullAddressBlock ?: ocrText,

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

    

            

    