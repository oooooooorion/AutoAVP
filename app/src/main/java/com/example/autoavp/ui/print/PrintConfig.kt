package com.example.autoavp.ui.print

object PrintConfig {
    // --- CONFIGURATION DES ZONES (Extraites de AVP Rempli.pptx) ---
    // Echelle calculée sur base AVP 210mm de large
    
    // Zone 1 : Numéro de Suivi
    const val TRACKING_BOX_X = 5.8f
    const val TRACKING_BOX_Y = 46.5f
    const val TRACKING_BOX_W = 74.4f
    const val TRACKING_BOX_H = 7.6f

    // Zone 2 : Destinataire
    const val ADDR_BOX_X = 5.8f
    const val ADDR_BOX_Y = 57.5f
    const val ADDR_BOX_W = 74.4f
    const val ADDR_BOX_H = 16.2f

    // Zone 3 : Bureau d'Instance
    const val INSTANCE_BOX_X = 123.0f
    const val INSTANCE_BOX_Y = 52.0f
    const val INSTANCE_BOX_W = 62.1f
    const val INSTANCE_BOX_H = 22.0f
}
