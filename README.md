# OpenDrop S23 📱

Prototype Android open source de partage de fichiers entre un **Galaxy S23** et Safari sur iPhone/iPad/Mac. L'application est conçue pour Android moderne et dispose d'un module de recherche **AWDL expérimental volontairement inactif**.

> **État actuel : v0.2.0, code non encore validé sur appareils physiques.**
> Ce n'est **pas** un client AirDrop natif : aucun appareil iOS ne verra le S23 dans le menu AirDrop.

## Fonctionnalités implémentées dans le code

- Application Kotlin / Jetpack Compose avec interface de transfert.
- Serveur HTTP local actif seulement après appui sur « Activer le partage », maintenu par un service au premier plan.
- Association depuis Safari par QR code contenant un secret aléatoire.
- **Galaxy → iPhone** : choisir des fichiers, les télécharger dans Safari.
- **iPhone → Galaxy** : choisir des fichiers dans Safari, puis accepter **chaque** réception sur le S23.
- Réception dans **Téléchargements/OpenDrop S23**, limite de **500 Mio par fichier**.
- Cible du menu « Partager » Android pour les fichiers et les contacts transmis en vCard.
- Informations matérielles en lecture seule ; aucun accès root n'est demandé.
- Workflow GitHub Actions permettant de compiler un **APK debug**.

## Installer / tester

1. Dans l'onglet [Actions](../../actions), ouvrir un workflow « Build Android APK » **réussi**.
2. Ouvrir « Artifacts », télécharger `OpenDrop-S23-debug`, extraire l'APK puis l'installer sur le S23. Autoriser l'installation pour l'application utilisée si Android le demande.
3. Connecter le Galaxy et l'iPhone au **même Wi-Fi privé**, sans isolation des clients.
4. Ouvrir OpenDrop S23 et toucher **Activer le partage**.
5. Sur l'iPhone, scanner le QR code avec l'appareil photo et ouvrir le lien dans Safari.
6. Pour **envoyer vers l'iPhone** : choisir des fichiers sur le Galaxy, puis toucher « Actualiser la liste » et « Télécharger » dans Safari.
7. Pour **recevoir depuis l'iPhone** : sélectionner un fichier dans Safari, toucher « Envoyer », puis garder l'application Android visible pour **accepter** la demande.

Si l'iPhone ne peut pas joindre le Galaxy, désactiver le VPN, vérifier que les deux appareils sont bien sur le même réseau et vérifier la configuration d'isolation Wi-Fi du routeur.

## ⚠️ Sécurité

**La connexion utilise HTTP sans TLS : elle n'est pas chiffrée.** Même si les requêtes nécessitent un secret d'association aléatoire et l'acceptation des fichiers reçus, les fichiers et le secret restent accessibles à quelqu'un capable de surveiller le réseau. **Ne pas l'utiliser pour des documents privés ou sensibles, ni sur des réseaux publics.** Pas de redirection de port, pas d'exposition Internet.

Les fichiers proposés sont accessibles à toute personne disposant du QR code tant que le serveur est actif. Appuyer sur **Arrêter le partage** pour invalider le secret. Ne pas utiliser comme coffre-fort ou outil de sauvegarde.

## KernelSU et AWDL

L'application ne requiert **aucune** permission root pour les transferts actuels. Le répertoire `app/src/main/java/dev/territoireantoine/appleshare/experimental/` réserve une interface pour un futur moteur AWDL, mais n'implémente **pas** AirDrop. Avant un prototype natif, il faut vérifier si le pilote Wi-Fi de la variante exacte du S23 permet les trames et la synchronisation nécessaires. KernelSU ne garantit pas ce point.

Voir [l'architecture](docs/ARCHITECTURE.md).

## Build / contribution

- Android Gradle Plugin **8.7.3**
- Gradle **8.9**, JDK **17**, Android SDK **35**
- Exécution : `gradle :app:assembleDebug`
- L'APK généré par CI est un debug build, pas une version de production.

Licence à définir avant contributions externes. N'utilisez pas les logos ou visuels propriétaires d'Apple dans les redistributions.
