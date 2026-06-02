# Suivi Sport 🚴‍♂️🏋️

Tracker d'activité physique **personnel et 100 % local** pour Android.
Conçu pour le **vélo** (suivi GPS) et la **musculation**, avec prise en charge des
capteurs externes : **GPS** et **ceinture cardiaque Bluetooth**.

> 🔒 **Vos données ne quittent jamais votre téléphone.** Tout est stocké dans une base
> SQLite locale. Aucune connexion à un serveur, aucun compte, aucun cloud.

---

## Fonctionnalités

- **Séance Vélo en direct** — chronomètre, distance, vitesse instantanée et max,
  dénivelé positif, tracé GPS, fréquence cardiaque et calories estimées.
- **Séance Muscu en direct** — exercices, séries (répétitions × charge), volume total
  soulevé, durée et fréquence cardiaque.
- **Ceinture cardiaque Bluetooth** — connexion au profil GATT standard *Heart Rate*
  (service `0x180D`), reconnexion automatique à la dernière ceinture.
- **Tableau de bord** — résumé de la semaine et graphe d'activité sur 7 jours.
- **Historique** — toutes les séances, filtrables par type, avec page de détail
  (tracé du parcours, répartition des exercices).
- **Profil** — poids et FC max, utilisés pour estimer calories et zones cardio.

## Pile technique

| Domaine | Choix |
| --- | --- |
| Framework | [Expo](https://expo.dev) SDK 56 · React Native 0.85 · React 19 |
| Navigation | Expo Router (typed routes) |
| Stockage | `expo-sqlite` (base locale, migrations versionnées) |
| GPS | `expo-location` (premier plan) |
| Cardio BLE | `react-native-ble-plx` |
| Tracé | `react-native-svg` (rendu vectoriel, sans fond cartographique) |
| Icônes | `@expo/vector-icons` (MaterialCommunityIcons) |

## ⚠️ Development build requis

L'accès Bluetooth (`react-native-ble-plx`) **n'est pas disponible dans Expo Go**.
Il faut générer un *development build* :

```bash
# Sur une machine avec Android Studio / SDK Android
npx expo run:android
```

Ou via EAS Build (cloud) :

```bash
npm install -g eas-cli
eas build --profile development --platform android
```

Le GPS et le stockage fonctionnent aussi en *development build*.

## Démarrage

```bash
npm install
npx expo run:android   # development build sur appareil/émulateur connecté
```

Puis, dans l'app : **Réglages → Rechercher une ceinture** pour appairer votre
capteur cardiaque, et renseignez votre poids pour l'estimation des calories.

## Structure du projet

```
src/
  app/                       # Routes (Expo Router)
    _layout.tsx              # Stack racine + HeartRateProvider + thème
    (tabs)/                  # Onglets : Accueil, Historique, Réglages
      index.tsx              #   Tableau de bord
      history.tsx            #   Historique des séances
      settings.tsx           #   Profil, ceinture, données
    velo.tsx                 # Séance vélo en direct (plein écran)
    muscu.tsx                # Séance muscu en direct (plein écran)
    session/[id].tsx         # Détail d'une séance
  components/                # UI réutilisable (Card, Button, StatTile, RouteMap…)
  hooks/
    use-heart-rate.tsx       # Contexte BLE partagé (ceinture cardiaque)
    use-gps-tracker.ts       # Suivi GPS (distance, vitesse, dénivelé)
    use-stopwatch.ts         # Chronomètre actif avec pause
    use-theme.ts             # Thème clair/sombre
  lib/
    db.ts                    # Accès SQLite + migrations + requêtes
    ble.ts                   # BleManager + parsing trame Heart Rate
    geo.ts                   # Distance haversine
    calories.ts              # Estimation MET + zones cardio
    format.ts                # Formatage (durée, distance, dates…)
    types.ts                 # Types du domaine
```

## Modèle de données (SQLite)

- `sessions` — une séance (type vélo/muscu, durée, cardio, et pour le vélo :
  distance, vitesse, dénivelé, calories).
- `track_points` — points GPS d'un tracé vélo (lat/lon, altitude, vitesse, FC).
- `muscu_sets` — séries de musculation (exercice, n° de série, reps, charge).
- `settings` — réglages clé/valeur (profil, dernière ceinture appairée).

## Notes

- Le suivi GPS est en **premier plan** : gardez l'app ouverte pendant la sortie
  (l'écran reste allumé via `expo-keep-awake`).
- L'estimation des calories repose sur la méthode des MET : c'est un ordre de
  grandeur, pas une mesure médicale.

## Scripts

```bash
npm run android      # lance le development build
npx tsc --noEmit     # vérification de types
npx expo lint        # linter
```
