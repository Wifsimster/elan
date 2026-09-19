# Importer vos anciennes sorties

Ce document explique comment **reprendre vos activités passées** dans Élan à
partir de fichiers. Il s'adresse à l'utilisateur qui arrive depuis Strava ou une
autre application de sport.

> Vous venez d'**Élan 1.x** ? Rien à importer : vos séances sont reprises
> automatiquement à la première ouverture de la 2.0
> (voir [MIGRATION-1.x.md](MIGRATION-1.x.md)).

## En bref

- L'import se fait **à partir d'un fichier** que vous choisissez, pas par
  connexion à un compte.
- Formats pris en charge : **GPX**, **TCX** et **FIT** (y compris compressés en
  `.gz`), plusieurs fichiers à la fois, 30 Mo maximum par fichier.
- L'opération est **100 % hors-ligne** : aucun appel réseau.
- Les doublons sont **détectés et ignorés** : réimporter un fichier ne crée pas
  de séance en double.

> **Pourquoi pas de synchronisation Strava automatique ?** C'est un choix
> assumé. Une synchronisation par compte (OAuth) imposerait une dépendance
> réseau et cloud contraire à la philosophie locale d'Élan. L'import par fichier
> garde vos données sous votre contrôle.

## Comment ça marche

```mermaid
graph LR
    A[Vous choisissez un fichier] --> B[Lecture locale]
    B --> C{Format détecté}
    C -->|GPX / TCX| D[Lecture du texte]
    C -->|FIT binaire| E[Décodage binaire]
    D --> F[Normalisation & contrôle]
    E --> F
    F --> G{Déjà importé ?}
    G -->|Non| H[Ajout à l'historique]
    G -->|Oui| I[Ignoré]
```

L'application lit le fichier, détecte son format, en extrait les points (GPS,
altitude, fréquence cardiaque, cadence), nettoie les valeurs aberrantes, puis
ajoute la séance — sauf si elle existe déjà.

## Où trouver vos fichiers

| Source | Fichier à exporter |
|--------|--------------------|
| Strava (une activité) | « Exporter GPX » ou « Exporter l'original » (FIT) |
| Strava (tout l'historique) | Archive d'export en masse (fichiers `.gz`) |
| Montre / capteur | Fichier `.fit` produit par l'appareil |
| Autre application | Export GPX ou TCX |

## Utilisation

1. Ouvrez **Réglages → Import Strava**.
2. Touchez « Importer un fichier (GPX/TCX/FIT) » et sélectionnez un ou
   plusieurs fichiers dans le sélecteur de documents d'Android.
3. Un récapitulatif s'affiche : importées, doublons, ignorées, erreurs. Le
   dernier résultat reste visible dans la carte.

## Ce qui est importé

- Le **type d'activité** dépend du format :

  | Format | Sport détecté | Séance créée |
  |--------|---------------|--------------|
  | TCX | `Biking` / `Running` / `Walking` (ou `Other`) | vélo / course / marche (ignorée) |
  | FIT | `cycling` / `running` / `walking`, `hiking` | vélo / course / marche ; les autres sports sont ignorés |
  | GPX | `<type>` du tracé (`cycling`, `running`, `walking`, `hiking`) | vélo / course / marche ; sans `<type>` reconnu : vélo |

- Données reprises quand elles sont présentes : tracé GPS, altitude, fréquence
  cardiaque, cadence, distance et calories déclarées par le fichier.
- Distance (si absente du fichier), vitesse moyenne/max, temps en mouvement et
  dénivelé sont recalculés depuis les points ; les segments à plus de 160 km/h
  sont écartés.
- Les **calories** sont estimées localement à partir de votre profil si le
  fichier n'en fournit pas.
- Une activité sans horodatage exploitable ou de durée nulle est ignorée ; une
  activité **sans GPS** (home-trainer avec cardio/cadence) est acceptée.

> **Détail technique.** Le pipeline est `data/export/StravaImporter.kt`
> (sélecteur SAF multi-fichiers, limite `MAX_BYTES` de 30 Mio) →
> `domain/strava/StravaDecoder.kt` (gzip, puis signature `.FIT`, sinon XML) →
> `domain/strava/FitDecoder.kt` (décodeur binaire maison, messages `record` et
> `session`) ou `domain/strava/GpxTcxParser.kt` (SAX, `DOCTYPE`/`ENTITY`
> refusés) → `domain/strava/StravaImport.kt` (normalisation, `externalId`
> stable = empreinte SHA-256 de la date, la durée, la distance et du premier
> point, préfixée `strava-`) → `SessionRepository.insertImportedSession`, dont
> l'index unique sur `externalId` renvoie `Duplicate` sans rien écrire. Le
> dernier bilan est mémorisé dans le réglage `strava_last_import`.
