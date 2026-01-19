package com.example.autoavp.domain.model

data class ScannedData(
    val trackingNumber: String? = null,
    val trackingType: TrackingType? = null, // Barcode or Smartdata
    val recipientName: String? = null,
    val rawText: String? = null,
    val addressCandidates: List<String> = emptyList(),
    val confidenceStatus: ValidationStatus = ValidationStatus.CALCULATED,
    val luhnKey: String? = null,
    val isoKey: String? = null,
    val ocrKey: String? = null
)

enum class ValidationStatus {
    VERIFIED, // Clé calculée correspond à la clé lue (OCR)
    WARNING,  // Clé lue différente des calculs théoriques
    CALCULATED // Pas de clé lue, valeur théorique utilisée
}

enum class TrackingType {
    BARCODE_1D,
    SMARTDATA_DATAMATRIX,
    UNKNOWN
}
