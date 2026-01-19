package com.example.autoavp.domain.utils

object AddressParser {

    data class AddressResult(
        val name: String?,
        val fullAddress: String
    )

    /**
     * Extrait l'adresse structurée (Nom + Voie + CP/Ville) depuis un texte brut OCR.
     * Utilise une stratégie "Bottom-Up" basée sur la ligne CP + Ville.
     */
    fun parse(rawText: String): AddressResult? {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // 1. Trouver l'index de la ligne CP + Ville (Ligne N)
        // Regex : 5 chiffres (CP) suivis d'au moins 3 caractères (Ville)
        val cpCityRegex = Regex("^\\d{5}\\s+[A-Z0-9\\s-]{3,}$")
        
        // On prend la dernière occurrence qui matche (souvent le destinataire est en bas)
        // Attention : l'expéditeur peut aussi avoir un CP/Ville, mais il est souvent plus haut.
        // ML Kit lit souvent de haut en bas, gauche à droite.
        val anchorIndex = lines.indexOfLast { cpCityRegex.matches(it) }

        if (anchorIndex == -1) return null // Pas d'adresse valide trouvée

        // 2. Construire le bloc adresse en remontant
        val addressBlock = mutableListOf<String>()
        addressBlock.add(lines[anchorIndex]) // Ajout Ligne N (Ville)

        var nameCandidate: String? = null
        
        // On remonte jusqu'à 3 lignes au-dessus maximum (N-1, N-2, N-3)
        // N-1 : Voie (souvent)
        // N-2 : Complément ou Nom
        // N-3 : Nom (si N-2 est complément)
        val maxLookback = 3
        var linesAdded = 0

        for (i in 1..maxLookback) {
            val currentIndex = anchorIndex - i
            if (currentIndex < 0) break

            val line = lines[currentIndex]
            
            // Filtres d'exclusion (Intrus)
            if (isNoise(line)) continue

            addressBlock.add(0, line) // On insère au début
            linesAdded++

            // Heuristique pour le nom : la ligne la plus haute retenue est le candidat Nom
            // Sauf si elle ressemble trop à une voie
            if (i == linesAdded) { // C'est la ligne la plus haute du bloc courant
                nameCandidate = line
            }
        }

        // Si on a identifié un nom, on l'enlève du bloc adresse "physique" pour le champ dédié
        // ou on le garde dans l'adresse complète ? 
        // Pour l'AVP, on veut souvent "Mme Dupont" en première ligne de l'adresse.
        // Donc fullAddress contient tout le bloc.
        
        return AddressResult(
            name = nameCandidate,
            fullAddress = addressBlock.joinToString("\n")
        )
    }

    private fun isNoise(line: String): Boolean {
        val upper = line.uppercase()
        return upper.contains("EXPEDITEUR") ||
               upper.contains("RETOUR") ||
               upper.contains("PRIORITAIRE") ||
               upper.contains("RECOMMANDE") ||
               upper.contains("LA POSTE") ||
               upper.matches(Regex("^SD\\s?:.*")) // Ligne SmartData textuelle
    }
}
