# AutoAVP

AutoAVP est une application Android destinée à assister les facteurs de La Poste dans le traitement des avis de passage. Elle permet de numériser les informations présentes sur les courriers (numéro de suivi et adresse du destinataire) afin d'automatiser le remplissage des avis de passage.

## Fonctionnalités

### Numérisation et reconnaissance
L'application utilise l'appareil photo du terminal pour capturer les données des courriers.
*   **Modes de scan** : permet un traitement unitaire ou par lots (scan multiple) pour préparer une tournée.
*   **Lecture hybride** : combine la lecture des codes-barres (SmartData et standards) et la reconnaissance optique de caractères (OCR) pour extraire l'adresse du destinataire.
*   **Validation** : vérifie la cohérence des numéros de suivi via le calcul des clés de contrôle (algorithmes Luhn et ISO).

### Gestion des sessions
Les courriers scannés sont regroupés par sessions de travail.
*   **Historique** : permet de consulter, reprendre ou supprimer les sessions précédentes.
*   **Modification** : offre la possibilité de corriger manuellement les informations d'un courrier (numéro ou adresse) avant l'impression.

### Impression des avis de passage
L'application génère des documents PDF formatés pour être imprimés directement sur les avis de passage vierges officiels.
*   **Mise en page précise** : positionne rigoureusement les informations dans les zones réservées (numéro de suivi, adresse destinataire, bureau d'instance).
*   **Calibration** : dispose d'un module de réglage permettant de définir un décalage horizontal et vertical pour compenser les marges spécifiques de chaque imprimante.
*   **Gestion des débordements** : adapte automatiquement la taille du texte pour le numéro de suivi et tronque intelligemment l'adresse si elle dépasse la zone d'impression.
*   **Transparence** : applique une transparence au fond coloré du bureau d'instance pour améliorer la lisibilité et tolérer les légers décalages d'impression.

### Gestion des bureaux d'instance
L'utilisateur peut configurer plusieurs bureaux de poste où les courriers seront mis en instance.
*   **Personnalisation** : chaque bureau est défini par son nom, son adresse, ses horaires et une couleur distinctive qui sera imprimée en fond sur l'avis.

## Architecture technique

Le projet est développé en langage Kotlin et suit les recommandations modernes de développement Android.

*   **Interface utilisateur** : réalisée avec Jetpack Compose (Material Design 3).
*   **Architecture** : suit le modèle MVVM (Model-View-ViewModel) et les principes de la Clean Architecture.
*   **Base de données** : utilise la bibliothèque Room pour la persistance locale des données.
*   **Analyse d'image** : s'appuie sur CameraX et ML Kit pour la détection des codes-barres et la reconnaissance de texte.
*   **Injection de dépendances** : gérée par Hilt.

## Utilisation

1.  **Configuration** : définir au moins un bureau d'instance dans le menu dédié. Si nécessaire, calibrer l'imprimante via les paramètres.
2.  **Scan** : lancer une nouvelle session de scan. Viser le courrier pour détecter automatiquement le numéro de suivi et l'adresse.
3.  **Vérification** : contrôler la liste des courriers scannés sur l'écran d'accueil.
4.  **Impression** : connecter l'imprimante, sélectionner le bureau d'instance souhaité et lancer l'impression. Le document PDF généré respecte le format A4 pour garantir le respect des dimensions lors de l'impression.
