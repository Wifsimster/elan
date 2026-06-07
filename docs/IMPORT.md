# Importer vos anciennes sorties

Ce document explique comment **reprendre vos activités passées** dans Élan à
partir de fichiers. Il s'adresse à l'utilisateur qui arrive depuis Strava ou une
autre application de sport.

## En bref

- L'import se fait **à partir d'un fichier** que vous choisissez, pas par
  connexion à un compte.
- Formats pris en charge : **GPX**, **TCX** et **FIT** (y compris compressés en
  `.gz`).
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

1. Ouvrez **Réglages → Importer des activités**.
2. Sélectionnez un ou plusieurs fichiers.
3. Un récapitulatif s'affiche : importées, doublons, ignorées, erreurs.

## Ce qui est importé

- Seules les activités de type **vélo** sont conservées (les autres sports sont
  ignorés).
- Données reprises quand elles sont présentes : tracé GPS, distance, vitesse,
  dénivelé, fréquence cardiaque, cadence.
- Les **calories** sont recalculées localement à partir de votre profil.

> **Détail technique.** Une **clé unique** (empreinte du contenu) est dérivée de
> chaque activité pour garantir un import idempotent : le même fichier réimporté
> est reconnu comme doublon. Les parseurs GPX/TCX et FIT sont écrits en
> JavaScript pur, sans module natif, et durcis contre les fichiers malveillants
> (taille plafonnée, entités XML externes refusées).
