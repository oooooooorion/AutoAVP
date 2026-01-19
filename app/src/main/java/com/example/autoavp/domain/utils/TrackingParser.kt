package com.example.autoavp.domain.utils

import com.example.autoavp.domain.model.TrackingType

object TrackingParser {

    /**
     * Tente d'extraire le numéro de suivi d'un contenu brut (Barcode 1D ou DataMatrix).
     */
    fun parseTrackingNumber(rawContent: String, isDataMatrix: Boolean = false): Pair<String, TrackingType> {
        val trimmed = rawContent.trim()
        
        if (isDataMatrix) {
            // Selon les spécifications utilisateur : le numéro de suivi (14 chiffres) 
            // se trouve entre le 9ème et le 22ème caractère inclus.
            // En indexation 0 : de l'index 8 à l'index 22 (exclu).
            
            var extracted14: String? = null

            if (trimmed.length >= 22) {
                extracted14 = trimmed.substring(8, 22)
                // Petite vérification de cohérence (doit souvent commencer par 8)
                if (!extracted14.startsWith("8")) {
                    // Si le découpage positionnel semble étrange, on tente quand même les regex par sécurité
                    val matchPercent = Regex("^%[0-9]{7}([0-9A-Z]{14})").find(trimmed)
                    if (matchPercent != null) extracted14 = matchPercent.groupValues[1]
                }
            } else {
                // Si la chaîne est trop courte pour le standard, on cherche un bloc de 14
                val matchIndustrial = Regex("86[59][0-9]{11}").find(trimmed)
                if (matchIndustrial != null) extracted14 = matchIndustrial.value
            }

            // 2. Calcul et Ajout de la clé théorique
            if (extracted14 != null && extracted14.length == 14) {
                // SÉLECTION DE L'ALGORITHME
                // D'après la documentation Smart Data (CI Premium), la plage "869" utilise ISO 7064 (clé alphanumérique).
                // Les plages plus classiques (ex: "865" de l'utilisateur) utilisent Luhn/GS1 (clé numérique).
                val key = if (extracted14.startsWith("869")) {
                    calculateIso7064Key(extracted14)
                } else {
                    calculateLaPosteKey(extracted14)
                }
                return (extracted14 + key) to TrackingType.SMARTDATA_DATAMATRIX
            }
            
            // Fallback
            if (extracted14 != null) {
                return extracted14 to TrackingType.SMARTDATA_DATAMATRIX
            }
        }

        // Test direct classique
        if (isLikelyTrackingNumber(trimmed)) {
            val type = if (isDataMatrix) TrackingType.SMARTDATA_DATAMATRIX else TrackingType.BARCODE_1D
            return trimmed to type
        }

        return trimmed to if (isDataMatrix) TrackingType.SMARTDATA_DATAMATRIX else TrackingType.BARCODE_1D
    }

    /**
     * Calcule la clé de contrôle selon la norme ISO/IEC 7064 mod 37/36 (Variante Somme Pondérée).
     * D'après la documentation utilisateur :
     * - Somme des produits (Chiffre * Poids)
     * - Poids croissants (2, 3, 4...)
     * - Modulo 37
     * - Table : 0-9, A-Z (0..35)
     */
    fun calculateIso7064Key(number: String): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var sum = 0
        
        // Parcours de DROITE à GAUCHE pour appliquer les poids 2, 3, 4...
        // C'est la convention standard pour que les zéros à gauche n'impactent pas le calcul.
        val reversed = number.reversed()
        for (i in reversed.indices) {
            val char = reversed[i]
            // Conversion char -> valeur (0-9). Si lettre dans le numéro (rare), A=10...
            val valI = if (char.isDigit()) char - '0' else (char - 'A' + 10)
            
            // Poids : 2, 3, 4... (i+2)
            val weight = i + 2
            sum += valI * weight
        }
        
        val remainder = sum % 37
        
        // Résultat = remainder directement mappé ? Ou 37 - remainder ?
        // L'exemple utilisateur dit : "1234 mod 37 = 10 -> A". 
        // Donc c'est DIRECTEMENT le reste.
        
        // Gestion du cas limite (si reste = 36, hors de l'alphabet 0-35 ?)
        // La norme Mod 37/36 garantit normalement que le check digit n'est pas 36.
        // Si ça arrive, on retourne "?" (ou Z par défaut ?)
        return if (remainder in alphabet.indices) alphabet[remainder].toString() else "?"
    }

    /**
     * Calcule la clé de contrôle (Luhn/GS1 Mod 10) pour un numéro de 14 chiffres.
     */
    fun calculateLaPosteKey(number: String): String {
        var sum = 0
        // Parcours de droite à gauche
        // Position 1 (droite) : Poids 3
        // Position 2 (droite) : Poids 1
        val reversed = number.reversed()
        for (i in reversed.indices) {
            val digit = reversed[i].toString().toIntOrNull() ?: 0
            val weight = if ((i + 1) % 2 != 0) 3 else 1
            sum += digit * weight
        }
        
        val remainder = sum % 10
        return if (remainder == 0) "0" else (10 - remainder).toString()
    }

    /**
     * Vérifie si une chaîne ressemble à un numéro de suivi standard (UPU S10 ou interne La Poste)
     */
    fun isLikelyTrackingNumber(text: String): Boolean {
        // Format UPU S10 : 2 lettres + 9 chiffres + 2 lettres (ex: RR123456789FR)
        val upuRegex = Regex("^[A-Z]{2}[0-9]{9}[A-Z]{2}$")
        
        // Format Colissimo/Interne/Industriel : 11 à 15 caractères alphanumériques (ex: 8690000000001Q)
        val generalRegex = Regex("^[A-Z0-9]{11,15}$")
        
        return upuRegex.matches(text) || generalRegex.matches(text)
    }

    /**
     * Tente d'extraire le numéro de suivi complet (15 caractères) depuis une ligne de texte OCR
     * commençant par "SD :".
     * Exemple : "SD : 869 123 456 789 01 X" -> "86912345678901X"
     */
    fun extractFromOcrLabel(ocrText: String): String? {
        // Regex cherche "SD" suivi optionnellement de ":" ou espaces, puis capture 15 chars (chiffres/lettres)
        // en tolérant des espaces entre les blocs de chiffres.
        val pattern = Regex("SD\\s*:?\\s*([0-9A-Z\\s]{14,25})", RegexOption.IGNORE_CASE)
        val match = pattern.find(ocrText) ?: return null
        
        // Nettoyage : on retire les espaces pour avoir le code brut
        val raw = match.groupValues[1].replace(" ", "").trim()
        
        return if (raw.length == 15) raw else null
    }

    /**
     * Tente d'extraire le nom du destinataire si présent dans la SmartData
     */
    fun extractName(rawContent: String): String? {
        // Logique à implémenter si spécifiée dans le Guide Smart Data
        return null
    }
}
