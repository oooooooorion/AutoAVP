package com.example.autoavp.ui.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class AutoAvpAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val barcodeScanner: BarcodeScanner,
    private val textRecognizer: TextRecognizer
) : ImageAnalysis.Analyzer {

    var onResult: ((ScannedData, Boolean) -> Unit)? = null
    var onDetectionUpdate: ((String?, String?, List<RectF>?, Int, Int) -> Unit)? = null
    
    private var viewportRatio: Float = 9f/16f // Ratio par défaut "standard" en portrait

    fun updateViewport(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            viewportRatio = width.toFloat() / height.toFloat()
        }
    }

    @SuppressLint("SuspiciousIndentation")
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)
            val isManual = false

            val barcodeTask = barcodeScanner.process(image)
            val textTask = textRecognizer.process(image)

            Tasks.whenAllComplete(barcodeTask, textTask)
                .addOnCompleteListener {
                    val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()
                    val visionText = if (textTask.isSuccessful) textTask.result else null
                    val ocrText = visionText?.text ?: ""

                    val liveTracking = barcodes.firstOrNull { it.format != Barcode.FORMAT_QR_CODE }?.rawValue
                    val liveOcr = ocrText.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")

                    val extractionResult = extractData(barcodes, ocrText, visionText, isManual, image.width, image.height)
                    val data = extractionResult.first
                    val detectedBlocks = extractionResult.second

                    onDetectionUpdate?.invoke(liveTracking, if (liveOcr.isBlank()) null else liveOcr, detectedBlocks, image.width, image.height)

                    if (data != null) {
                        if (isManual || barcodes.isNotEmpty() || ocrText.contains("RECOMMAND", ignoreCase = true)) {
                            // Sauvegarde de l'image uniquement si on a une donnée valide
                            val imagePath = saveImageToDisk(imageProxy)
                            onResult?.invoke(data.copy(imagePath = imagePath), isManual)
                        }
                    }
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun processHighQualityImage(image: InputImage, imagePath: String? = null, onComplete: (ScannedData?) -> Unit) {
        val barcodeTask = barcodeScanner.process(image)
        val textTask = textRecognizer.process(image)

        Tasks.whenAllComplete(barcodeTask, textTask).addOnCompleteListener {
            val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else emptyList()
            val visionText = if (textTask.isSuccessful) textTask.result else null
            val ocrText = visionText?.text ?: ""
            
            val result = extractData(barcodes, ocrText, visionText, isManual = true, image.width, image.height)
            onComplete(result.first?.copy(imagePath = imagePath))
        }
    }

    private fun saveImageToDisk(imageProxy: ImageProxy): String? {
        try {
            val bitmap = imageProxy.toBitmap() // Extension androidx.camera.core
            val rotation = imageProxy.imageInfo.rotationDegrees
            val finalBitmap = if (rotation != 0) {
                 val matrix = Matrix()
                 matrix.postRotate(rotation.toFloat())
                 Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                 bitmap
            }
            
            val filename = "scan_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, filename)
            val out = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.close()
            return file.absolutePath
        } catch (e: Exception) {
            Log.e("AutoAvpAnalyzer", "Failed to save image", e)
            return null
        }
    }

    private fun extractData(
        barcodes: List<Barcode>, 
        ocrText: String, 
        visionText: com.google.mlkit.vision.text.Text?, 
        isManual: Boolean, 
        imageWidth: Int, 
        imageHeight: Int
    ): Pair<ScannedData?, List<RectF>?> {
        
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

        // 2. Extraction Explicite depuis l'OCR
        val ocrTracking = TrackingParser.extractFromOcrLabel(ocrText)

        // 3. Consolidation et Choix du Numéro
        var finalTrackingNumber: String? = null
        var validationStatus = ValidationStatus.CALCULATED
        var iKey: String? = null
        var oKey: String? = null

        if (ocrTracking != null) {
            val ocrCore14 = ocrTracking.take(14)
            oKey = ocrTracking.takeLast(1)
            
            if (barcodeTracking != null) {
                val barcodeCore14 = barcodeTracking.take(14)
                if (ocrCore14 == barcodeCore14) {
                    finalTrackingNumber = ocrTracking
                    validationStatus = ValidationStatus.VERIFIED
                } else {
                    finalTrackingNumber = ocrTracking
                    validationStatus = ValidationStatus.WARNING
                }
            } else {
                finalTrackingNumber = ocrTracking
                validationStatus = ValidationStatus.VERIFIED 
                trackingType = TrackingType.SMARTDATA_DATAMATRIX
            }
            
            iKey = TrackingParser.calculateIso7064Key(ocrCore14)

        } else if (barcodeTracking != null) {
            val core14 = barcodeTracking.take(14)
            iKey = TrackingParser.calculateIso7064Key(core14)
            
            val fuzzyMatch = Regex("$core14\\s?([0-9A-Z])").find(ocrText)
            
            if (fuzzyMatch != null) {
                oKey = fuzzyMatch.groupValues[1]
                finalTrackingNumber = core14 + oKey
                
                if (oKey == iKey) {
                    validationStatus = ValidationStatus.VERIFIED
                } else {
                    validationStatus = ValidationStatus.WARNING
                }
            } else {
                finalTrackingNumber = barcodeTracking
                validationStatus = ValidationStatus.CALCULATED
                if (barcodeTracking.length == 15) {
                    val usedKey = barcodeTracking.last().toString()
                    if (usedKey == iKey) validationStatus = ValidationStatus.CALCULATED 
                }
            }
        }

        // 4. Extraction de l'Adresse : Stratégie "Ancrage & Zones d'Exclusion"
        
        // A. Définition de l'Ancre (SmartData)
        val smartDataBox = smartData?.boundingBox
        val anchorRightLimit = if (smartDataBox != null) {
            // Tout ce qui est à droite du BORD GAUCHE de la SmartData est exclu (Logos, Affranchissement)
            smartDataBox.left.toFloat()
        } else {
            // Fallback : On exclut le tiers droit de l'image (zone timbre standard)
            imageWidth * 0.66f
        }

        // B. Zones d'Exclusion La Poste (Normalisées par rapport à la hauteur image)
        // Zone Indexation (Haut) : ~15% (40mm sur ~220mm)
        val exclusionTop = imageHeight * 0.15f
        // Zone Codage (Bas) : ~10% (20mm sur ~220mm)
        val exclusionBottom = imageHeight * 0.90f

        val detectedRects = mutableListOf<RectF>()
        
        // On filtre les blocs OCR en amont
        val filteredBlocks = visionText?.textBlocks?.filter { block ->
            val box = block.boundingBox ?: return@filter false
            val cx = box.centerX()
            val cy = box.centerY()

            // 1. Filtre Horizontal (Droite de l'Ancre)
            if (cx > anchorRightLimit) return@filter false
            
            // 2. Filtre Vertical (Zones La Poste)
            if (cy < exclusionTop) return@filter false // Trop haut (Indexation)
            if (cy > exclusionBottom) return@filter false // Trop bas (Codage)

            // 3. Filtre Expéditeur (Coin Haut-Gauche extrême)
            // Si on est dans le quart haut-gauche strict (hors zone indexation)
            if (cx < imageWidth * 0.35f && cy < imageHeight * 0.30f) return@filter false

            true
        } ?: emptyList()

        val finalAddressBlock = if (filteredBlocks.isNotEmpty()) {
            // On affiche les rects retenus
            filteredBlocks.forEach { block ->
                val box = block.boundingBox
                if (box != null) {
                    detectedRects.add(
                        RectF(
                            box.left.toFloat() / imageWidth,
                            box.top.toFloat() / imageHeight,
                            box.right.toFloat() / imageWidth,
                            box.bottom.toFloat() / imageHeight
                        )
                    )
                }
            }
            // On passe les blocs pré-filtrés au parser pour le choix final (structure)
            AddressParser.parseFilteredBlocks(filteredBlocks)
        } else {
            // Fallback si rien n'est trouvé (ex: zoom trop fort sur l'adresse seule)
            AddressParser.parse(ocrText)
        }

        if (isManual || (finalTrackingNumber != null && finalAddressBlock != null)) {
            val scannedData = ScannedData(
                trackingNumber = finalTrackingNumber ?: barcodeTracking, 
                trackingType = trackingType,
                recipientName = null,
                rawText = finalAddressBlock ?: ocrText,
                addressCandidates = emptyList(),
                confidenceStatus = validationStatus,
                isoKey = iKey,
                ocrKey = oKey
            )
            return scannedData to detectedRects
        }
        
        // Même si on ne valide pas l'objet complet, on renvoie les rects détectés pour l'UI
        return null to detectedRects.takeIf { it.isNotEmpty() }
    }
}
