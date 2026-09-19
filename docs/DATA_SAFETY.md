# Sécurité des données — réponses Play Console (Élan 2.0)

Ce document fournit les réponses prêtes à l'emploi pour le questionnaire
**Sécurité des données** de la Play Console. Il s'adresse à la personne qui
publie l'application. Il est dérivé des permissions du manifeste
(`app/src/main/AndroidManifest.xml`) et des flux réels du code ; à mettre à
jour si l'un ou l'autre évolue.

Réponses à reporter dans **Play Console → Contenu de l'application →
Sécurité des données**.

## Flux de données réels

Élan stocke **tout en local** (base Room `elan.db`, secrets dans l'Android
KeyStore) et **n'envoie rien à l'éditeur** : pas de compte, pas de serveur,
pas d'analytics, pas de rapport de plantage, pas de SDK publicitaire. Trois
flux, tous **optionnels et désactivés par défaut**, font sortir des données de
l'appareil :

| Flux | Déclenchement | Destinataire | Données |
|------|---------------|--------------|---------|
| Sauvegarde S3 (`data/backup/`, `data/remote/`) | Interrupteur « Sauvegarde automatique » ou bouton « Sauvegarder maintenant » | **Le serveur de l'utilisateur** (endpoint HTTPS qu'il saisit) | Instantané JSON complet : séances, points GPS, séries, pesées, réglages (sans identifiants ni URL de carte) |
| Fond de carte (`maps/`) | Interrupteur « Fond de carte en ligne » | OpenFreeMap (`tiles.openfreemap.org`) par défaut, ou le serveur de tuiles de l'utilisateur | Requêtes de tuiles : zone géographique du parcours affiché + adresse IP ; aucun tracé envoyé |
| Health Connect (`health/`) | Interrupteur « Exporter les séances » | Le magasin Health Connect **sur l'appareil** (aucun réseau) | Session d'exercice, distance, calories actives, fréquence cardiaque — écriture seule |

S'y ajoutent les **partages** que l'utilisateur déclenche lui-même via la
feuille de partage d'Android (GPX, bilan coach, JSON, image de séance) : la
destination est son choix, l'application ne transmet rien d'elle-même.

> **Recommandation : déclaration prudente.** Comme une transmission hors-appareil
> est possible (sauvegarde opt-in vers le serveur de l'utilisateur), on déclare
> les données ci-dessous comme « collectées » mais **jamais partagées avec des
> tiers** et **facultatives**. C'est l'option la plus sûre vis-à-vis des règles
> Google, même si l'app n'envoie rien par défaut.

## Permissions déclarées

| Permission | Pourquoi | À déclarer |
|------------|----------|------------|
| `INTERNET` | Sauvegarde S3 et tuiles de carte, uniquement si l'utilisateur les active | — |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Tracé GPS d'une sortie, **au premier plan** ; aussi exigée par Android ≤ 11 pour le scan BLE | Position précise |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK` | Service de premier plan `tracking/TrackingService.kt` (type `location`) pour que l'enregistrement continue écran éteint. **Pas** de `ACCESS_BACKGROUND_LOCATION` | Déclaration de service de premier plan |
| `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT` ; `BLUETOOTH`, `BLUETOOTH_ADMIN` (≤ Android 11) | Ceinture cardiaque et capteurs cadence/vitesse | — (matériel `bluetooth_le` non requis) |
| `POST_NOTIFICATIONS` | Notification de séance en cours, rappels planifiés, progression du programme | — |
| `RECEIVE_BOOT_COMPLETED` | Re-planifier les rappels après un redémarrage | — |
| `android.permission.health.WRITE_EXERCISE`, `WRITE_DISTANCE`, `WRITE_ACTIVE_CALORIES_BURNED`, `WRITE_HEART_RATE` | Export Health Connect opt-in, écriture seule | Déclaration Health Connect |

### Permissions retirées par rapport à Élan 1.x

`CAMERA` (le QR de configuration S3 passe par le lecteur de codes de Google Play
Services, dont l'interface appartient au système), `RECORD_AUDIO`,
`MODIFY_AUDIO_SETTINGS`, `READ/WRITE_EXTERNAL_STORAGE` (partage via
`FileProvider` sur `cache/share/` uniquement) et les composants
Firebase/datatransport que le runtime Expo embarquait. Si la fiche 1.x les
déclarait, retirer les entrées correspondantes.

## Questions de haut niveau

| Question | Réponse |
|---|---|
| Votre application collecte-t-elle ou partage-t-elle des types de données utilisateur requis ? | **Oui** (par prudence, à cause de la sauvegarde opt-in) |
| Toutes les données collectées sont-elles **chiffrées en transit** ? | **Oui** (la sauvegarde S3 et les tuiles exigent HTTPS ; une URL `http://` est refusée) |
| Fournissez-vous un moyen de **demander la suppression** des données ? | **Oui** (Réglages → Données → « Effacer toutes les séances » ou « Tout réinitialiser (profil + réglages) » ; suppression séance par séance ; la désinstallation efface tout, `allowBackup="false"` empêche toute copie par la sauvegarde Android) |

## Types de données à déclarer

Pour **chaque** type ci-dessous :
- **Collectée** : Oui · **Partagée** : **Non** (aucun tiers)
- **Traitement** : facultatif pour l'utilisateur (sauf position, voir note)
- **Finalité** : *Fonctionnalité de l'application* (uniquement)

| Catégorie Google | Type de donnée | Collectée | Partagée | Finalité |
|---|---|---|---|---|
| Position | **Position précise** (GPS) | Oui | Non | Fonctionnalité de l'app (tracé des sorties vélo, course, marche) |
| Infos de santé et de remise en forme | **Infos de remise en forme** (FC, distance, vitesse, cadence, calories, séries/charges, pesées) | Oui | Non | Fonctionnalité de l'app |
| Infos personnelles | **Autres infos** (poids, taille, FC max, sexe, objectif) | Oui | Non | Fonctionnalité de l'app |
| Messages | **Autres messages in-app** (notes de séance libres) | Oui | Non | Fonctionnalité de l'app |

### Notes par catégorie

- **Position précise** : utilisée **au premier plan uniquement** pendant une
  sortie active, via un service de premier plan de type `location`. Pas de
  localisation en arrière-plan (`ACCESS_BACKGROUND_LOCATION` non demandée). À
  déclarer comme **requise** pour la fonction de suivi GPS. Le scan Bluetooth
  est déclaré `neverForLocation`.
- **Toutes les autres** : **facultatives** (l'utilisateur les saisit ou branche
  un capteur s'il le souhaite).
- **Health Connect** : la Play Console demande une déclaration séparée
  (Contenu de l'application → Health Connect) : types **écrits** = session
  d'exercice, distance, calories actives, fréquence cardiaque ; aucune lecture ;
  finalité = enregistrer les séances de l'utilisateur dans son propre magasin de
  santé ; la justification est affichée dans l'app (`ui/screens/health/HealthRationaleScreen.kt`).

## Ce qu'il NE faut PAS déclarer

Aucune de ces données n'est présente dans Élan — laisser **non coché** :

- Identifiants utilisateur, e-mail, nom, téléphone (pas de compte).
- Informations financières / de paiement.
- Identifiants d'appareil ou publicitaires, **analytics**, rapports de plantage.
- Historique de navigation/recherche, contacts, photos, fichiers, audio, caméra.
- **Aucun SDK tiers de collecte** (pas de pub, pas de télémétrie). Les seules
  bibliothèques réseau sont OkHttp (client S3 signé sur l'appareil) et MapLibre
  (tuiles, opt-in). Les journaux `Log.v/d/i` sont supprimés des builds release
  (`app/proguard-rules.pro`).

## Pratiques de sécurité (cases à cocher)

| Pratique | Réponse |
|---|---|
| Les données sont chiffrées en transit | **Oui** (HTTPS obligatoire pour la sauvegarde S3 et le fond de carte ; sinon tout reste local) |
| L'utilisateur peut demander la suppression de ses données | **Oui** |
| Engagement envers les règles « Familles » de Google Play | **Non** (l'app ne cible pas les enfants) |
| L'app a fait l'objet d'un examen de sécurité indépendant | **Non** (facultatif) |

## Phrase de justification (si Google demande des précisions)

> Élan est une application de suivi sportif **locale**. Les données restent sur
> l'appareil (base de données locale) et **ne sont jamais transmises à l'éditeur
> ni à un tiers**. Une sauvegarde **optionnelle**, désactivée par défaut, permet
> à l'utilisateur d'envoyer ses propres données vers **son propre serveur**
> compatible S3, via HTTPS ; les identifiants saisis sont chiffrés dans le
> KeyStore Android et ne sont jamais inclus dans la sauvegarde. Un fond de carte
> en ligne, lui aussi optionnel, ne transmet que des requêtes de tuiles.
> L'application ne contient ni publicité, ni traceur, ni outil de mesure
> d'audience.
