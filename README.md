# AutoAVP

**AutoAVP** est une application Android conçue pour les facteurs de La Poste. Elle automatise le remplissage et l'impression des avis de passage (AVP) en numérisant les informations directement depuis les enveloppes.

L'application combine la reconnaissance optique de caractères (OCR) et la lecture de codes-barres (SmartData/Datamatrix) pour garantir une fiabilité maximale des données avant l'impression.

## Fonctionnalités clés

*   **Scanner hybride intelligent** :
    *   Lecture simultanée des codes-barres (Datamatrix, Code 128) et du texte (OCR).
    *   **SmartData** : Décodage avancé des Datamatrix La Poste (extraction positionnelle stricte des 14 chiffres de suivi).
    *   **Validation croisée** : Le numéro de suivi n'est validé que si la clé de contrôle (15ème caractère) lue par l'OCR correspond à la clé théorique calculée (Algorithmes Luhn ou ISO 7064).
*   **Reconnaissance d'adresse avancée** :
    *   Algorithme de regroupement et scoring pour isoler le bloc adresse parmi les autres textes de l'enveloppe.
    *   **Fusion verticale** : Récupération intelligente du nom ou de la raison sociale s'ils sont séparés de l'adresse (détection par proximité et alignement).
*   **Interface tête haute (HUD)** :
    *   Retour visuel en temps réel sur l'écran de scan.
    *   Liste de vérification (Suivi, Clé, Adresse) avant enregistrement.
*   **Modes de travail** :
    *   **Automatique** : Enregistrement instantané dès que toutes les données sont complètes et vérifiées.
    *   **Manuel** : Possibilité de forcer la capture via une prise de photo si l'automatisme échoue.
*   **Impression AVP** :
    *   Génération de PDF vectoriels calés au millimètre près sur les formulaires AVP officiels (Format DL). 
    *   Support de l'impression Bluetooth/WiFi via le service d'impression Android.
    *   Gestion des bureaux d'instance (couleur de fond dynamique, horaires, adresse), même si c'est amené à être amélioré. La gestion des instances n'est pas encore tout à fait satisfaisante.

## Stack technique

Le projet respecte les standards modernes du développement Android (2025/2026).

### Architecture & Langage
*   **Langage** : [Kotlin](https://kotlinlang.org/)
*   **UI Toolkit** : [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material Design 3)
*   **Architecture** : MVVM (Model-View-ViewModel) + Clean Architecture simplifiée.
*   **Injection de dépendances** : [Hilt](https://dagger.dev/hilt/) (Dagger)
*   **Asynchronisme** : Coroutines & Kotlin Flow.

### Noyau Fonctionnel (Scan & ML)
*   **Caméra** : [CameraX](https://developer.android.com/training/camerax) (Gestion simultanée de `Preview`, `ImageAnalysis` et `ImageCapture`).
*   **Machine Learning** : [Google ML Kit](https://developers.google.com/ml-kit)
    *   *Text Recognition v2* (OCR Latin)
    *   *Barcode Scanning* (Format DataMatrix & Code 128)

### Données & Persistance
*   **Base de données** : [Room](https://developer.android.com/training/data-storage/room) (SQLite abstraction).
*   **Format de données** : Entités relationnelles (`Session` -> `MailItems`).

### Build & Outils
*   **Build System** : Gradle (Kotlin DSL).
*   **Gestion des versions** : Version Catalog (`libs.versions.toml`).
*   **JDK** : Java 17.

## 🧠 Algorithmes spécifiques

### 1. Parsing SmartData
L'application n'utilise pas le contenu brut du DataMatrix aveuglément.
*   **Extraction** : Elle isole strictement les caractères aux index **9 à 22** (longueur 14) du flux binaire.
*   **Calcul de clé** : Elle recalcule la clé de contrôle manquante selon le préfixe :
    *   `869...` : Algorithme ISO/IEC 7064 mod 37/36.
    *   Autres (`865...`) : Algorithme Luhn pondéré (GS1).
Toutefois, cette fonctionnalité n'est pas encore au point, d'où la préférence pour l'OCR quant à la clé de contrôle. Effectivement, la clé de contrôle est générée par La Poste selon un algorithme secret.

### 2. Détection d'adresse (Scoring)
Pour éviter de lire l'adresse de l'expéditeur ou des publicités :
1.  **Regroupement** : Les lignes de texte sont groupées en blocs visuels.
2.  **Ancrage** : Chaque bloc est analysé pour trouver une ligne "Code Postal + Ville" (Regex 5 chiffres).
3.  **Scoring** : Les blocs reçoivent des points (Bonus pour "Monsieur/Madame", Malus pour "Expéditeur", Bonus pour la taille de police).
4.  **Fusion** : Le bloc gagnant absorbe les lignes situées juste au-dessus (Nom) si elles sont alignées verticalement.

## Installation et configuration

### Prérequis
*   Android Studio Ladybug ou plus récent.
*   Device Android physique recommandé (pour la caméra et le flash).
*   Minimum SDK : 26 (Android 8.0).

### Compilation
1.  Cloner le dépôt.
2.  Ouvrir dans Android Studio.
3.  Synchroniser le projet Gradle (Java 17 requis).
4.  Compiler et déployer : `Run 'app'`.

## Guide de calage impression
Les coordonnées d'impression sont définies en millimètres dans `AvpPdfGenerator.kt`.
Pour ajuster l'alignement sur vos imprimantes :
1.  Ouvrir `ui/print/AvpPdfGenerator.kt`.
2.  Modifier les constantes `TRACKING_X_MM`, `ADDR_Y_MM`, etc.
3.  Tester via l'écran "Aperçu avant impression".

Je compte me pencher là dessus pour ne pas avoir à le faire manuellement.

## Licence
Projet interne - Tous droits réservés (pour l'instant).