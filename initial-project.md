#Projet initial

Ceci est un projet d'application Android en Kotlin à destination des facteurs de La Poste en France. Il permet de scanner des enveloppes, d'en extraire le nom et l'adresse de l'expéditeur et le numéro de suivi de la lettre pour imprimer des avis de passage de la Poste, plutôt que de les remplir à la main.

L'idée est de mettre des avis de passage vierges dans une imprimante et que l'application permette d'imprimer par dessus les informations pertinentes pour le destinataire (nom et adresse du destinataire, numéro de suivi de l'objet et bureau d'instance) 

Terminologie :
* L'avis de passage (ou AVP) est le document que le facteur met en boite à lettre lorsqu'il ne peut pas distribuer un courrier recommandé ou un colis.
* Le numéro de suivi est la suite de caractères alphanumériques propre à chaque objet suivi.
* L'objet suivi est la lettre suivie, le petit paquet international, la lettre recommandée avec ou sans accusé de reception (française ou étrangère).
* Le destinataire est celui à qui est destiné l'objet.
* Une instance est un objet mis à disposition de l'usager dans un bureau de poste car sa distribution n'a pas pu aboutir.

Dans "documentation/" tu trouveras des documents pour t'aider dans ta tâche. L'AVP signifie "Avis de passage".
Dans ce dossier tu retrouveras :
* "231024_CIG_ sous SD Présentation Générale du Dispositif Technique.pdf" : aide pour comprendre le formatage des enveloppes à La Poste.
* "Guide_Cour_Indust_smart_data_2025.pdf" : un dispositif pour comprendre comment est encodé la "Smartdata", un format de datamatrix utilisé par La Poste, duquel tu récupereras le numéro de suivi.
* "AVP Type.PNG" : un avis de passage vierge 
* "AVP Commenté.jpg" : les zones que tu devras remplir dans l'avis de passage.

Dans "idées interface/" tu trouveras un brouillon de l'interface de l'application dont tu pourras t'inspirer pour comprendre ce dont j'ai besoin.

Les fonctionnalités attendues de l'application sont les suivantes :
* une page principale où apparaitront les différents courriers scannés avant d'être envoyés à l'impression ; chaque courrier apparaitra par son numéro de suivi et s'il y a la place
* en cliquant sur chaque lettre, je veux pouvoir apercevoir les informations scannées et les modifier si nécessaire ;
* une page pour lancer la caméra afin de scanner les lettres ;
* une page pour éditer les bureaux d'instance (nom, adresse, horaires d'ouverture, couleur) ; l'instance est le bureau de Poste dans lequel la lettre sera disponible si le facteur ne peut pas la distribuer ; je veux pouvoir y indiquer, entre autres, le nom du bureau, son adresse, ses heures d'ouverture, dans un champ de texte ;
* une page pour lancer l'impression des avis de passage vers l'imprimante ou en format pdf ; c'est sur cette page que je choisirai le bureau d'instance ; il sera précisé sur cette page que seuls les avis de passage dernière génération sont compatibles avec l'application. 

Les champs à remplir sur l'avis de passage :
* "Zone n° suivi" : le numéro de suivi, extrait soit de la smartdata, soit d'un code barre plus classique ;
* "Zone information destinataire" : Les nom et prénom du destinataire et son adresse complète extraits grâce à l'OCR ;
* "Zone bureau d'instance" : Les informations du bureau d'instance écrites en noir sur un rectangle de couleur (chaque bureau d'instance dispose de sa propre couleur).
