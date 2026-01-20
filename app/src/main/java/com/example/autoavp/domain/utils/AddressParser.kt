package com.example.autoavp.domain.utils

import com.google.mlkit.vision.text.Text
import kotlin.math.abs

object AddressParser {

    private val CIVILITY_KEYWORDS = listOf("M.", "MM.", "MME", "MLLE", "MONSIEUR", "MADAME", "SOCIETE", "ETS", "CHEZ")
    private val FORBIDDEN_KEYWORDS = listOf("EXPEDITEUR", "RETOUR", "SERVICE", "CLIENT", "CEDEX", "TSA", "CS")

    fun parse(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        // Recherche de l'Ancre (CP + Ville)
        var anchorIndex = -1
        val cpCityRegex = Regex("^(?:F-?)?\\s*\\d{5}\\s+[A-Z0-9\\s-]{2,}$", RegexOption.IGNORE_CASE)
        val franceRegex = Regex("^FRANCE$", RegexOption.IGNORE_CASE)

        for (i in lines.lastIndex downTo 0) {
            val line = lines[i]
            if (cpCityRegex.matches(line)) {
                anchorIndex = i
                break
            }
            if (franceRegex.matches(line) && i > 0) {
                if (cpCityRegex.matches(lines[i - 1])) {
                    anchorIndex = i - 1
                    break
                }
            }
        }

        if (anchorIndex != -1) {
            // On prend tout du début jusqu'à l'ancre (incluse)
            // + éventuellement "FRANCE" juste après
            val endIndex = if (anchorIndex + 1 < lines.size && franceRegex.matches(lines[anchorIndex + 1])) {
                anchorIndex + 1
            } else {
                anchorIndex
            }
            
            return lines.subList(0, endIndex + 1).joinToString("\n")
        }
        
        // Si pas de CP trouvé, on renvoie tout le texte filtré par le ROI tel quel
        // car l'utilisateur a visé spécifiquement cette zone
        return text.trim()
    }

    /**
     * Analyse les blocs de texte ML Kit pour identifier le meilleur candidat "Adresse Destinataire".
     * Si le nom est dans un bloc séparé juste au-dessus, tente de le fusionner.
     */
    fun parse(visionText: Text): String? {
        val candidates = mutableListOf<BlockCandidate>()

        // 1. Identification du Bloc Principal (celui qui contient le CP/Ville)
        for (block in visionText.textBlocks) {
            val lines = block.lines
            if (lines.isEmpty()) continue

            // Recherche de l'Ancre (CP + Ville)
            var anchorIndex = -1
            val cpCityRegex = Regex("^(?:F-?)?\\s*\\d{5}\\s+[A-Z0-9\\s-]{2,}$", RegexOption.IGNORE_CASE)
            val franceRegex = Regex("^FRANCE$", RegexOption.IGNORE_CASE)

            val searchLimit = (lines.size - 3).coerceAtLeast(0)
            
            for (i in lines.lastIndex downTo searchLimit) {
                val text = lines[i].text.trim()
                if (cpCityRegex.matches(text)) {
                    anchorIndex = i
                    break
                }
                // Cas "FRANCE"
                if (franceRegex.matches(text) && i > 0) {
                    val prevText = lines[i - 1].text.trim()
                    if (cpCityRegex.matches(prevText)) {
                        anchorIndex = i - 1
                        break
                    }
                }
            }

            if (anchorIndex != -1) {
                val score = calculateScore(block, lines)
                if (score > 0) {
                    candidates.add(BlockCandidate(block, anchorIndex, score))
                }
            }
        }

        val bestCandidate = candidates.maxByOrNull { it.score } ?: return null
        
        // 2. Tentative de Fusion Verticale (Recoudre le Nom)
        // On cherche un bloc qui serait juste au-dessus du bloc principal
        val headerBlock = findHeaderBlock(visionText.textBlocks, bestCandidate.block)
        
        val bodyText = extractRelevantText(bestCandidate.block.lines, bestCandidate.anchorIndex)
        
        return if (headerBlock != null) {
            // On fusionne : Header + \n + Body
            "${headerBlock.text}\n$bodyText"
        } else {
            bodyText
        }
    }

    private fun findHeaderBlock(allBlocks: List<Text.TextBlock>, primaryBlock: Text.TextBlock): Text.TextBlock? {
        val primaryBox = primaryBlock.boundingBox ?: return null
        // On estime la hauteur d'une ligne moyenne dans le bloc principal
        val avgLineHeight = if (primaryBlock.lines.isNotEmpty()) primaryBox.height() / primaryBlock.lines.size else 20
        
        // Critères de recherche :
        // 1. Le bloc doit être au-dessus (bottom < top)
        // 2. Pas trop loin (écart < 2.5 * hauteur de ligne)
        // 3. Aligné horizontalement (gauche ou centre)
        
        var bestHeader: Text.TextBlock? = null
        var minGap = Int.MAX_VALUE

        for (other in allBlocks) {
            if (other == primaryBlock) continue
            val otherBox = other.boundingBox ?: continue
            
            // Vérifions si 'other' est au-dessus de 'primary'
            if (otherBox.bottom <= primaryBox.top) {
                val gap = primaryBox.top - otherBox.bottom
                
                // Tolérance d'écart vertical augmentée (4 lignes vides max)
                val maxGap = avgLineHeight * 4 
                
                if (gap in 0..maxGap) {
                    val otherTextUpper = other.text.uppercase()
                    val hasCivility = CIVILITY_KEYWORDS.any { otherTextUpper.contains(it) }

                    // Vérification Alignement Horizontal
                    // On tolère un décalage si c'est centré ou aligné gauche
                    // Si on a une civilité explicite, on est beaucoup plus tolérant sur l'alignement
                    val alignmentTolerance = if (hasCivility) 300 else 150
                    
                    val horizontalOverlap = checkHorizontalAlignment(otherBox, primaryBox, alignmentTolerance)
                    
                    if (horizontalOverlap) {
                        // On garde le plus proche
                        if (gap < minGap) {
                            minGap = gap
                            bestHeader = other
                        }
                    }
                }
            }
        }
        
        // Filtre final sur le header trouvé : est-ce un parasite ?
        if (bestHeader != null) {
             val text = bestHeader.text.uppercase()
             if (FORBIDDEN_KEYWORDS.any { text.contains(it) }) return null
        }

        return bestHeader
    }

    private fun checkHorizontalAlignment(boxA: android.graphics.Rect, boxB: android.graphics.Rect, tolerance: Int): Boolean {
        // Alignement Gauche (avec tolérance dynamique)
        if (abs(boxA.left - boxB.left) < tolerance) return true
        
        // Alignement Centre
        val centerA = boxA.centerX()
        val centerB = boxB.centerX()
        if (abs(centerA - centerB) < tolerance) return true
        
        // Intersection significative en X
        val intersectionLeft = maxOf(boxA.left, boxB.left)
        val intersectionRight = minOf(boxA.right, boxB.right)
        
        if (intersectionRight > intersectionLeft) {
            val overlapWidth = intersectionRight - intersectionLeft
            val minWidth = minOf(boxA.width(), boxB.width())
            // Si ils se chevauchent sur au moins 50% de la largeur du plus petit
            if (overlapWidth > minWidth * 0.5) return true
        }
        
        return false
    }

    private fun calculateScore(block: Text.TextBlock, lines: List<Text.Line>): Int {
        var score = 100 
        val fullText = block.text.uppercase()

        if (FORBIDDEN_KEYWORDS.any { fullText.contains(it) }) return -500

        if (CIVILITY_KEYWORDS.any { fullText.contains(it) }) score += 50

        val avgHeight = block.boundingBox?.height() ?: 0
        score += avgHeight / 2 

        if (lines.size in 3..6) score += 20
        else if (lines.size < 2) score -= 30

        return score
    }

    private fun extractRelevantText(lines: List<Text.Line>, anchorIndex: Int): String {
        val sb = StringBuilder()
        val endIndex = if (anchorIndex + 1 < lines.size && lines[anchorIndex + 1].text.trim().equals("FRANCE", ignoreCase = true)) {
            anchorIndex + 1
        } else {
            anchorIndex
        }

        for (i in 0..endIndex) {
            sb.append(lines[i].text).append("\n")
        }
        return sb.toString().trim()
    }

    private data class BlockCandidate(
        val block: Text.TextBlock, 
        val anchorIndex: Int, 
        val score: Int
    )
}
