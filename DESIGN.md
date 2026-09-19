# PULSE — Design System

**PULSE** est le langage visuel d'Élan : un système **cinétique, sombre par défaut et orienté effort**, dans la lignée de Material 3 Expressive et des apps de sport modernes (WHOOP, Strava, Apple Fitness). Trois partis pris le résument :

1. **L'énergie passe par la couleur et le dégradé.** Chaque activité a sa teinte vive et son dégradé ; les actions principales rayonnent (ombre teintée), pas juste « remplies ».
2. **La profondeur passe par l'ombre, pas par la bordure.** Les surfaces flottent au-dessus d'un fond quasi noir ; les bordures dures sont réservées aux champs et séparateurs.
3. **Tout ce qui réagit au doigt bouge.** Appui = compression élastique + retour haptique. Le mouvement est physique (ressorts), jamais linéaire.

> Source de vérité : **`app/src/main/java/ovh/battistella/elan/ui/theme/`** (`Tokens.kt`, `Theme.kt`, `Type.kt`). Aucune valeur de style en dur dans les écrans — on pioche les tokens ici (couleurs via `ElanTheme.colors`, le reste via les objets `Radius` / `Spacing` / `Elevation` / `PulseType` / `Motion` / `PulseGradients`). La correspondance token → Kotlin est détaillée au [§ 7](#7-implémentation-compose).

---

## 1. Couleur

Palette **sombre par défaut** (le mode clair suit le système). Les couleurs dépendantes du thème se lisent via `ElanTheme.colors` (`PulseColors.Light` / `PulseColors.Dark`).

### Rôles

| Token | Rôle | Sombre | Clair |
| --- | --- | --- | --- |
| `background` | Fond d'écran | `#0A0C10` | `#F3F5FA` |
| `backgroundElement` | Surface (Card, barres, tab bar) | `#14181F` | `#FFFFFF` |
| `surfaceHigh` | Surface surélevée (popover, sélection) | `#1B202A` | `#FFFFFF` |
| `backgroundSelected` | État sélectionné | `#232A35` | `#E9ECF4` |
| `border` | Bordure visible (champ, contour) | `#222934` | `#E4E8F0` |
| `hairline` | Séparateur ultra-discret | `#1A1F28` | `#EDF0F6` |
| `text` | Texte principal | `#F4F7FB` | `#0B0E13` |
| `textSecondary` | Texte secondaire | `#9AA3B0` | `#5A6472` |
| `textMuted` | Texte/icône tertiaire (chevrons) | `#868FA0` | `#6B7280` |
| `accent` | Marque, liens, focus | `#5B7CFF` | `#3B5BFF` |
| `accentSoft` | Fond teinté accent | `#1B2236` | `#E5EAFF` |

### Couleurs d'activité (sémantiques)

| Token | Sens | Sombre | Clair |
| --- | --- | --- | --- |
| `velo` | Vélo / cardio GPS | `#22D3C5` | `#0BA59B` |
| `muscu` | Musculation / volume | `#A78BFA` | `#7C3AED` |
| `course` | Course à pied | `#38BDF8` | `#0284C7` |
| `marche` | Marche | `#A3E635` | `#65A30D` |
| `heart` | Fréquence cardiaque (data viz) | `#FF5C7A` | `#F43F5E` |
| `danger` | Action destructrice (supprimer) | `#FF4D4D` | `#DC2626` |
| `success` | GPS précis, validé | `#34D399` | `#10B981` |
| `warning` | Calories, alerte douce | `#FBBF24` | `#E08600` |

**Règles d'usage**
- Une teinte d'activité = un sens. Ne jamais peindre du vélo en violet.
- Fonds teintés : superposer la teinte à `~13 %` (`color + '22'`) pour les pastilles d'icône, `~24 %` pour les puces.
- **`heart` (rose) ne sert qu'à la donnée cardio** (badge FC, chiffres FC), jamais à un bouton.
- **Boutons : le primaire est bleu (`accent`) ou la teinte d'activité (vélo/muscu) ; seul le destructif est rouge (`danger`).** Pas de bouton rose.

### Dégradés (`PulseGradients`)

Vifs et **identiques en clair/sombre** (ils se posent toujours sur une surface colorée). Diagonale par défaut.

| Nom | Couleurs | Emploi |
| --- | --- | --- |
| `accent` | `#6478FF → #7A3BFF` | Action principale neutre, barres du graphe |
| `velo` | `#2DE0C0 → #0BA9B5` | Bouton/héros vélo |
| `muscu` | `#B07BFF → #7A3BFF` | Bouton/héros muscu |
| `course` | `#5AC8FF → #3B7BFF` | Bouton/héros course |
| `marche` | `#C7F04F → #5FB828` | Bouton/héros marche |
| `heart` | `#FF6B8B → #F43F5E` | Cardio (jauges, accents data) |
| `danger` | `#FF5A5A → #D61F2E` | Bouton destructif (supprimer) |
| `fire` | `#FFB020 → #FF6B35` | Calories |
| `success` | `#4ADE80 → #10B981` | Validation |
| `scrim` | transparent → noir | Voile sous une image/héros |

Rendu via `Brush.linearGradient` de Compose à partir de `PulseGradients.<nom>` — **100 % local, aucune dépendance réseau**. `PulseGradients.inkOn(gradient)` choisit l'encre (`OnBright` sur les dégradés clairs `velo`/`success`/`fire`/`marche`, blanc sinon).

---

## 2. Typographie (`PulseType`)

Police système (`FontFamily.Default`, aucune police embarquée). Chiffres d'effort en **tabulaire** (`fontFeatureSettings = "tnum"`) pour ne pas « danser » en direct. Tracking négatif sur les grands titres pour un rendu compact et moderne.

| Token | Taille / graisse | Emploi |
| --- | --- | --- |
| `metricLg` | 64 / 800, tabular | Le chronomètre de séance |
| `display` | 44 / 800 | Très grand chiffre héros |
| `title` | 28 / 800 | Titre d'écran (« Historique », « Réglages ») |
| `metric` | 30 / 800, tabular | Valeur de StatTile |
| `headline` | 20 / 800 | Titre de section / de Card |
| `subtitle` | 16 / 700 | Titre de ligne (séance, exercice) |
| `body` | 15 / 500 | Texte courant |
| `label` | 13 / 600 | Libellé de métrique |
| `caption` | 12 / 600 | Légende, unité |
| `overline` | 12 / 700, +1.4, MAJUSCULES | Étiquette de section (« DURÉE », « NOTES ») |

Usage : `Text(text, style = PulseType.headline, color = ElanTheme.colors.text)`.

---

## 3. Espacement, rayons, élévation

**Espacement** — grille 4 pt (`Spacing`). Marges d'écran : `16`. Espace inter-cartes : `16`. Gouttière intra-carte : `12`.

**Rayons** (`Radius`) — coins arrondis (`RoundedCornerShape`) partout :

| `sm` 12 | `md` 16 | `lg` 22 | `xl` 28 | `pill` 999 |
| --- | --- | --- | --- | --- |
| pastilles, champs | icône détail | **Card, bouton** | héros | puces, badges |

**Élévation** (`Elevation`) — ombres douces et diffuses (`sm` / `md` / `lg`). `Modifier.shadow(Elevation.x)`. La profondeur ne se signale **jamais** par une bordure dure. Les actions principales ajoutent une **ombre teintée** à leur couleur (`ambientColor`/`spotColor` = teinte du bouton) pour « rayonner ».

---

## 4. Mouvement & haptique (`Motion`, `ui/haptics/Haptics.kt`)

Le ressenti « cinétique » vient d'ici. Bâti sur les `spring()` de Compose Animation et `HapticFeedbackConstants` (aucune bibliothèque tierce).

**Ressorts** (`Motion`) : `snappy` (appui), `bouncy` (relâchement, léger rebond), `gentle` (entrées/sorties).

**Appui** : toute surface tactile passe par **`Modifier.pressableScale()`** → compression à `0.96` (`Motion.pressScale`) puis rebond. C'est la brique de base ; ne pas utiliser `clickable` nu pour un élément interactif visible. Le mouvement réduit du système (`LocalReducedMotion`) supprime l'échelle mais garde l'haptique.

**Haptique** (`HapticKind`, `rememberHaptics()`) — un retour par interaction marquante :

| Geste | Retour |
| --- | --- |
| Appui contrôle secondaire / chip / sélection | `selection` |
| Appui action principale | `light` |
| Démarrer / mettre en pause un effort | `medium` |
| Séance enregistrée, objectif atteint | `success` |
| Action refusée / erreur | `error` |

« Fire and forget » : un appareil sans moteur haptique (ou le web) ignore silencieusement.

---

## 5. Composants

Tous dans `ui/components/`, thémés via `ElanTheme.colors` + tokens.

| Composant | Rôle | Points clés |
| --- | --- | --- |
| **`Modifier.pressableScale()`** | Surface tactile élastique | `scaleTo`, `haptic` ; base de toute interaction |
| **`PulseButton`** | Action | `ButtonVariant` Primary/Secondary/Danger/Ghost, `ButtonSize` Md/Lg ; le primaire est un dégradé à ombre teintée |
| **`PulseCard`** | Surface de contenu | `CardVariant` Elevated (défaut, ombre) / Inset (champ) / Plain |
| **`PulseChip`** | Filtre / suggestion | sélectionnable, teinte d'activité |
| **`StatTile`** | Métrique « bento » | pastille d'icône teintée + grand chiffre tabulaire, tendance |
| **`BarChart`** / **`LineChart`** | Graphes | barres en dégradé vertical ; aire façon Strava avec moyenne pointillée |
| **`HrBadge`** | État cardio | halo (`shadow`) coloré quand la ceinture émet |
| **`EmptyState`** | Vide | icône + titre + sous-titre centrés, action secondaire optionnelle |
| **`RouteMap`** / **`RouteCanvas`** | Tracé GPS | `RouteMap` choisit MapLibre si un style est configuré, sinon `RouteCanvas` (Canvas normalisé, aucun fond cartographique, offline) |

### Anatomie des écrans
- **Accueil** : en-tête (salut + titre `title` + `HrBadge`) → grille 2×2 de boutons d'action `ButtonSize.Lg` (dégradés vélo/muscu/course/marche) → cartes résumé & graphe → liste récente.
- **Séance live (vélo/muscu)** : chrono `metricLg` centré, label `overline` à la teinte d'activité, stats en grille bento, **barre de contrôle flottante** (`Elevation.lg`) collée en bas.
- **Détail de séance** : en-tête icône+titre, tracé GPS, grille de stats, ventilation muscu, notes.

---

## 6. Faire / Ne pas faire

✅ Lire les valeurs depuis `ui/theme/` (couleurs via `ElanTheme.colors`, reste via les objets de tokens).
✅ Envelopper tout interactif dans `Modifier.pressableScale()` + haptique adapté.
✅ Action principale = `PulseButton` Primary (dégradé + ombre teintée).
✅ Profondeur par l'ombre (`Elevation`) ; coins arrondis `Radius`.
✅ Chiffres d'effort en tabulaire (`tnum`, déjà dans `PulseType.metric*`).

❌ Pas de couleur, taille de police, rayon ni ombre codés en dur dans un écran.
❌ Pas de bordure dure pour signifier une surface flottante.
❌ Ne pas mélanger les sens des teintes d'activité.
❌ Pas de dépendance réseau/cloud (dégradés, carte et icônes restent 100 % locaux).
❌ Pas de `Color(0x…)`, de `.sp` ni de `.dp` de style inventés dans un écran : si un token manque, on l'ajoute à `Tokens.kt`.

---

## 7. Implémentation Compose

PULSE est implémenté en **Kotlin + Jetpack Compose** (Material 3 Expressive) dans `app/src/main/java/ovh/battistella/elan/ui/`. Material fournit la mécanique (thème, composants de base, `MotionScheme.expressive()`), PULSE fournit les valeurs.

### Tokens → Kotlin

| Section de ce document | Kotlin (`ui/theme/`) | Accès |
| --- | --- | --- |
| §1 Rôles et couleurs d'activité | `Tokens.kt` → `@Immutable data class PulseColors`, instances `PulseColors.Light` / `PulseColors.Dark` | `ElanTheme.colors.accent`, `.velo`, … (`Theme.kt`) ; `PulseColors.forKey("velo")` (`ColorUtils.kt`) pour une clé sérialisée |
| §1 Zones cardiaques | `Tokens.kt` → `object HrZoneColors` (`Light` / `Dark`, 5 couleurs) | `ElanTheme.hrZones` |
| §1 Dégradés | `Tokens.kt` → `object PulseGradients` (`accent`, `velo`, `muscu`, `course`, `marche`, `heart`, `danger`, `fire`, `success`, `scrim`), `BRIGHT_GRADIENTS`, `OnBright`, `inkOn()` | `Brush.linearGradient(PulseGradients.velo)` |
| §2 Typographie | `Type.kt` → `object PulseType` (`display`, `metricLg`, `metric`, `title`, `headline`, `sectionTitle`, `subtitle`, `body`, `label`, `caption`, `overline`) ; police système, `tnum` sur les métriques | `Text(style = PulseType.headline)` |
| §3 Rayons | `Tokens.kt` → `object Radius` (`sm` 12, `md` 16, `lg` 22, `xl` 28, `pill` 999) | `RoundedCornerShape(Radius.lg)` |
| §3 Espacement | `Tokens.kt` → `object Spacing` (`half` 2, `one` 4, `two` 8, `three` 16, `four` 24, `five` 32, `six` 64) et `MaxContentWidth` (800 dp) | `Modifier.screenContent()` (`components/ScreenContent.kt`) applique marges, insets et largeur max |
| §3 Élévation | `Tokens.kt` → `object Elevation` (`sm` 3, `md` 8, `lg` 18 dp) | `Modifier.shadow(Elevation.md, …)` avec `ambientColor`/`spotColor` teintés pour les actions primaires |
| §4 Ressorts | `Tokens.kt` → `object Motion` (`snappy`, `bouncy`, `gentle` en `spring()` Compose, `pressScale` 0.96) | `Modifier.pressableScale()` (`components/PressableScale.kt`) |
| §4 Haptique | `ui/haptics/Haptics.kt` → `enum HapticKind` (Selection, Light, Medium, Heavy, Success, Error), `rememberHaptics()` | `val haptics = rememberHaptics(); haptics(HapticKind.Success)` |
| §4 Son | `ui/sound/Sounds.kt` → `Sounds.restDone(context)` (`res/raw/rest_done.wav`) | minuteur de repos uniquement |
| Mouvement réduit | `ui/theme/Accessibility.kt` → `LocalReducedMotion`, `rememberSystemReducedMotion()` | fourni par `ElanTheme` |
| Icônes | `ui/icons/MdiIcons.kt` → `object MdiIcons` (`MdiIcons.HeartPulse` = `R.drawable.mdi_heart_pulse`, `MdiIcons.byName("heart-pulse")`), généré par `scripts/gen-mdi-icons.mjs` | `Icon(painterResource(MdiIcons.Fire), tint = …)` |

### Thème Material

`ElanTheme(darkTheme = isSystemInDarkTheme()) { … }` (`Theme.kt`) fournit `LocalPulseColors`, `LocalHrZoneColors` et `LocalReducedMotion`, puis enveloppe `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive())`. **Pas de couleur dynamique** (Material You) : la marque est fixe. Le `colorScheme` est dérivé des tokens (`primary` = `accent`, `primaryContainer` = `accentSoft`, `secondary` = `link`, `tertiary` = `success`, `background`/`surface`/`outline`/`error`/`scrim` = tokens homonymes) pour que les composants Material non stylés (dialogues, `Switch`, `ModalBottomSheet`) restent dans la palette.

Le bord à bord est activé par `enableEdgeToEdgeCompat()` (`ui/EdgeToEdge.kt`), sans les API `Window.setStatusBarColor` / `setNavigationBarColor` obsolètes que la Play Console signale ; les barres transparentes viennent de `Theme.Elan` (`res/values/themes.xml`).

### Composants

| PULSE (§5) | Compose (`ui/components/`) | Fichier |
| --- | --- | --- |
| PressableScale | `Modifier.pressableScale(enabled, scaleTo, haptic, onClick)` | `PressableScale.kt` |
| Button | `PulseButton(title, onClick, variant, size, color, gradient, icon, loading, enabled)` | `PulseButton.kt` |
| Card | `PulseCard(variant = CardVariant.Elevated) { … }` | `PulseCard.kt` |
| Chip | `PulseChip(label, selected, onClick, color)` | `Chip.kt` |
| StatTile | `StatTile(label, value, unit, icon, color, compact, hero, trend)` | `StatTile.kt` |
| BarChart / LineChart | `BarChart(data: List<BarPoint>, gradient)`, `LineChart(data: List<ChartPoint>, color, avg)` | `BarChart.kt`, `LineChart.kt` |
| HrBadge | `HrBadge(bpm, connected, onClick, connecting)` | `HrBadge.kt` |
| EmptyState | `EmptyState(icon, title, subtitle, tint, action)` | `EmptyState.kt` |
| ErrorNotice | `ErrorNotice(message)` | `ErrorNotice.kt` |
| RouteMap | `RouteMap(points, color, height, live, interactive, fill)` + `LocalMapRenderer` ; `RouteCanvas(...)`, `MapPlaceholder(status)` | `RouteMap.kt`, `RouteCanvas.kt` |
| GpsStatusPill, HrZonesCard, RestTimerBar, SessionRow, ShareCard | composables homonymes | fichiers homonymes |
| SettingCardHeader, SettingField, SettingStepper, Stepper | composables homonymes (cartes de réglages, saisie) | fichiers homonymes |
| ExerciseCatalog, ExerciseDetailSheet, ExerciseInfoSheet, ExerciseIllustration | catalogue d'exercices et fiches (photos embarquées, `ExerciseImages.kt`) | `ExerciseCatalog.kt`, `ExerciseInfoSheet.kt`, `ExerciseIllustration.kt` |
| QrScanButton | bouton secondaire qui ouvre le lecteur de codes Play Services | `QrScanButton.kt` |

### Contraintes

- Copie d'interface (`res/values/strings*.xml`) et commentaires en **français**.
- Tout composant reçoit ses couleurs depuis `ElanTheme.colors` ; les seuls littéraux tolérés sont `Color.White`/`OnBright` comme encre sur dégradé.
- 100 % local et hors-ligne — c'est un invariant produit, pas seulement visuel : photos, icônes, sons et dégradés sont embarqués.
