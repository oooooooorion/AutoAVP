package com.example.autoavp.domain.utils

import com.google.mlkit.vision.text.Text
import kotlin.math.abs

object AddressParser {

    private val CIVILITY_KEYWORDS = listOf("M.", "MM.", "MME", "MLLE", "MONSIEUR", "MADAME", "SOCIETE", "ETS", "CHEZ")
    private val FORBIDDEN_KEYWORDS = listOf("EXPEDITEUR", "RETOUR", "SERVICE", "CLIENT", "CEDEX", "TSA", "CS")

    /**
     * Analyse une liste de blocs déjà filtrés spatialement.
     * Sélectionne le meilleur candidat basé sur la structure (Civilité, CP).
     */
    fun parseFilteredBlocks(blocks: List<Text.TextBlock>): String? {
        val candidates = mutableListOf<BlockCandidate>()

        for (block in blocks) {
            val lines = block.lines
            if (lines.isEmpty()) continue
            
            // On cherche n'importe quel indice structurel fort
            var anchorIndex = -1
            val cpRegex = Regex("(?<!\\d)\\d{5}") // CP simple
            
            // On scanne les lignes
            for (i in lines.indices) {
                if (cpRegex.containsMatchIn(lines[i].text)) {
                    anchorIndex = i
                }
            }
            
            // Si pas de CP trouvé, on prend la dernière ligne comme ancre par défaut
            // car les blocs filtrés sont censés être pertinents
            if (anchorIndex == -1) anchorIndex = lines.lastIndex

            val score = calculateStructureScore(block)
            candidates.add(BlockCandidate(block, anchorIndex, score))
        }

        val bestCandidate = candidates.maxByOrNull { it.score } ?: return null
        
        // Tentative de fusion avec le bloc du dessus (Nom/Raison Sociale)
        // On cherche parmi les blocs filtrés s'il y en a un juste au-dessus
        val headerBlock = findHeaderBlock(blocks, bestCandidate.block)
        
        val bodyText = extractAddressFromBlock(bestCandidate.block, bestCandidate.anchorIndex)
        
        return if (headerBlock != null) {
            "${headerBlock.text}\n$bodyText"
        } else {
            bodyText
        }
    }

    private fun findHeaderBlock(allBlocks: List<Text.TextBlock>, primaryBlock: Text.TextBlock): Text.TextBlock? {
        val primaryBox = primaryBlock.boundingBox ?: return null
        
        // Estimation hauteur ligne
        val primaryLineCount = primaryBlock.lines.size.coerceAtLeast(1)
        val primaryAvgHeight = primaryBox.height().toFloat() / primaryLineCount
        
        var bestHeader: Text.TextBlock? = null
        var maxScore = -100

        for (other in allBlocks) {
            if (other == primaryBlock) continue
            val otherBox = other.boundingBox ?: continue
            
            // 1. Position Verticale
            // Doit être globalement au-dessus.
            // On accepte un léger chevauchement (overlap) si ML Kit a mal découpé
            // Condition : Le centre du header doit être au-dessus du haut du bloc principal
            // ou le bas du header doit être proche du haut du bloc principal.
            if (otherBox.centerY() > primaryBox.top + primaryAvgHeight) continue // Trop bas (c'est le corps)

            val gap = primaryBox.top - otherBox.bottom
            // Tolérance : Chevauchement jusqu'à 1 ligne ou écart jusqu'à 3 lignes vides
            val minGapAllowed = -primaryAvgHeight * 1.0 
            val maxGapAllowed = primaryAvgHeight * 3.5
            
            if (gap < minGapAllowed || gap > maxGapAllowed) continue

            // 2. Position Horizontale
            // Vérification de l'intersection des intervalles X
            val intersectionLeft = maxOf(primaryBox.left, otherBox.left)
            val intersectionRight = minOf(primaryBox.right, otherBox.right)
            
            val overlapWidth = (intersectionRight - intersectionLeft).toFloat()
            val otherWidth = otherBox.width().toFloat()
            
            // Si pas d'intersection ou intersection trop faible (< 30% de la largeur du header)
            if (overlapWidth <= 0 || overlapWidth < otherWidth * 0.3f) continue

            // --- CALCUL DU SCORE ---
            var score = 0
            
            val gapD = gap.toDouble()
            val avgH = primaryAvgHeight.toDouble()

            // Proximité Verticale (Plus c'est proche, mieux c'est)
            if (gapD in 0.0..avgH) score += 50
            else if (gapD < 0) score += 30 // Chevauchement : bon signe de continuité
            else score += 20 // Un peu d'écart

            // Alignement Horizontal (Gauche strict = bonus)
            if (abs(otherBox.left - primaryBox.left) < 50) score += 40

            // Contenu (Civilité = Jackpot)
            val textUpper = other.text.uppercase()
            if (CIVILITY_KEYWORDS.any { textUpper.contains(it) }) score += 150
            
            // Taille de Police (Cohérence)
            val otherLineCount = other.lines.size.coerceAtLeast(1)
            val otherAvgHeight = otherBox.height().toFloat() / otherLineCount
            val ratio = otherAvgHeight / primaryAvgHeight
            
            if (ratio in 0.8..1.2) score += 30
            else if (ratio in 0.6..1.5) score += 10
            else score -= 20 // Police très différente

            // Pénalités
            if (FORBIDDEN_KEYWORDS.any { textUpper.contains(it) }) score -= 1000
            
            // Mise à jour du meilleur candidat
            if (score > maxScore && score > 0) {
                maxScore = score
                bestHeader = other
            }
        }

        return bestHeader
    }

    private fun calculateStructureScore(block: Text.TextBlock): Int {
        var score = 100 
        val fullText = block.text.uppercase()

        // Civilité = Indice très fort
        if (CIVILITY_KEYWORDS.any { fullText.contains(it) }) score += 100
        
        // Structure multiligne
        val lineCount = block.lines.size
        if (lineCount in 3..7) score += 50
        if (lineCount < 2) score -= 50

        // CP + Ville (Regex stricte)
        if (Regex("(?<!\\d)\\d{5}\\s+[A-Z]{2,}").containsMatchIn(fullText)) score += 80

        // Bruit résiduel
        if (fullText.contains("LA POSTE") || fullText.contains("SD:")) score -= 200

        return score
    }

    private fun extractAddressFromBlock(block: Text.TextBlock, anchorIndex: Int): String {
        val lines = block.lines
        val startIndex = (anchorIndex - 5).coerceAtLeast(0)
        
        return lines.subList(startIndex, anchorIndex + 1)
            .joinToString("\n") { it.text }
    }

    private data class BlockCandidate(
        val block: Text.TextBlock, 
        val anchorIndex: Int, 
        val score: Int
    )

    // Garde l'ancienne méthode simple pour le fallback texte brut (si pas de visionText dispo)
    fun parse(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val cpRegex = Regex("(?<!\\d)\\d{5}(?!\\d)") 
        var anchor = -1
        
        for (i in lines.lastIndex downTo 0) {
            if (cpRegex.containsMatchIn(lines[i])) {
                anchor = i
                break
            }
        }
        
        if (anchor != -1) {
            val start = (anchor - 5).coerceAtLeast(0)
            return lines.subList(start, anchor + 1).joinToString("\n")
        }
        return text.takeIf { it.isNotEmpty() }
    }
}
