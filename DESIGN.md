# Sillage — Design System d'Élan

**Élan** est un carnet d'entraînement qui ne quitte pas ton téléphone. Son
identité tient en une phrase : **« Ton effort, ta trace. »** Chaque sortie
laisse un sillage — la trace GPS, la courbe de la semaine, la charge qui monte —
et ce sillage t'appartient, sans compte ni cloud.

**Sillage** est le langage visuel qui porte cette idée. Quatre partis pris :

1. **Encre et papier.** Des neutres chauds — une encre verdâtre en sombre, un
   papier crème en clair — plutôt que le bleu-noir froid des apps de sport. La
   profondeur passe par **le ton** (fond → carte → sélection), pas par l'ombre.
2. **Un seul Volt, et il se mérite.** La couleur de marque (`brand`, un
   jaune-vert électrique) marque *l'action principale et le maintenant* :
   bouton principal, onglet actif, filtre choisi, barre d'aujourd'hui. Jamais
   de décor.
3. **Les chiffres sont les héros.** Archivo Condensed ExtraBold, tabulaire,
   grand. Les libellés s'effacent devant la valeur.
4. **Le trait d'élan.** L'accent du « é » — un trait penché vers l'avant — est
   le motif de la marque : logo, étiquettes de section, élément actif.

Et, hérité de PULSE : **tout ce qui réagit au doigt bouge** (ressort +
haptique), une teinte d'activité = un sens, 100 % local.

> Source de vérité : **`app/src/main/java/ovh/battistella/elan/ui/theme/`**
> (`Tokens.kt`, `Theme.kt`, `Type.kt`). Aucune valeur de style en dur dans les
> écrans — couleurs via `ElanTheme.colors`, le reste via `Radius` / `Spacing` /
> `Elevation` / `ControlSize` / `ElanType` / `Motion` / `ElanGradients`.
> Correspondance token → Kotlin au [§ 8](#8-implémentation-compose).

---

## 1. Marque

| Élément | Fichier | Règle |
| --- | --- | --- |
| **Logotype « élan »** | `res/drawable/brand_wordmark.xml` + `brand_wordmark_accent.xml`, composable `ElanWordmark` | Minuscules, Archivo Condensed ExtraBold Italic. Lettres à l'encre (`text`), accent à la marque (`accent`). Hauteur mini 18 dp. |
| **Icône « é »** | `ic_launcher_{foreground,background,monochrome}.xml`, `docs/brand/elan-mark.svg` | « é » papier sur aplat Encre, accent Volt. Jamais sur un autre fond. |
| **Trait d'élan** | `ElanTick` (`components/Brand.kt`) | Quadrilatère penché (227 × 132, bord droit plus raide). Seul, il signale « ici » : étiquette de section (`SectionLabel`). |
| **Accroche** | fiche Play, feature graphic | « Ton effort, ta trace. » — tutoiement, phrases courtes, pas de superlatif. |

Les vecteurs sont les contours exacts de la police embarquée, générés par
`scripts/gen-brand-assets.py` ; le feature graphic par
`scripts/feature-graphic.mjs`. Ne pas les retoucher à la main.

**Voix.** Tutoiement, français simple, verbes d'action (« Démarrer »,
« Reprendre »). On parle d'effort et de progrès, jamais de performance
comparée aux autres. Pas d'emoji dans l'interface, sauf le salut de l'accueil.

---

## 2. Couleur

Sombre par défaut (Encre), le clair (Papier) suit le système. Les couleurs se
lisent via `ElanTheme.colors` (`ElanColors.Light` / `ElanColors.Dark`).

### Neutres

| Token | Rôle | Encre (sombre) | Papier (clair) |
| --- | --- | --- | --- |
| `background` | Fond d'écran | `#0D0E0B` | `#F3F2EC` |
| `backgroundElement` | Carte, barre d'onglets | `#171914` | `#FFFFFF` |
| `surfaceHigh` | Feuille, menu | `#1F221B` | `#FFFFFF` |
| `backgroundSelected` | Puce au repos, piste de jauge, sélection | `#292D24` | `#E8E7DF` |
| `border` | Bordure de champ | `#30352A` | `#DAD9CF` |
| `hairline` | Séparateur, rail de graphe | `#22261E` | `#EAE9E2` |
| `text` | Texte principal | `#F3F4EE` | `#151712` |
| `textSecondary` | Texte secondaire | `#A7AB9E` | `#595C52` |
| `textMuted` | Tertiaire, chevrons, onglet inactif | `#8B8F82` | `#6B6E64` |

### Marque — un Volt, trois usages

Le Volt pur (`#D4F545`) éclate sur l'encre mais disparaît sur le papier
(1,1:1). D'où trois jetons :

| Token | Usage | Encre | Papier |
| --- | --- | --- | --- |
| `brand` / `onBrand` | **Aplat** (bouton principal, onglet actif, puce choisie), toujours sous l'encre `onBrand` | `#D4F545` / `#12140F` | idem |
| `accent` | **Trait** : barres, jauges, icônes, curseurs, trait d'élan (≥ 3:1) | `#D4F545` | `#5A7F00` |
| `link` | **Texte** : « Voir tout », `TextButton` (≥ 4,5:1) | `#D4F545` | `#4A6600` |
| `accentSoft` | Fond teinté de marque | `#252B12` | `#EFF7CC` |

`fillFor(color)` (`ElanButton.kt`) convertit une teinte « accent » en aplat
`brand` ; `inkOn(fill)` choisit l'encre (`onBrand` ou blanc) au contraste.

### Teintes d'activité (sémantiques)

Vives sur l'encre (aplat sous encre sombre), approfondies sur le papier (aplat
sous blanc, texte ≥ 4,5:1).

| Token | Sens | Encre | Papier |
| --- | --- | --- | --- |
| `velo` | Vélo | `#35D6E6` | `#007584` |
| `course` | Course à pied | `#FF8B4D` | `#B8420B` |
| `marche` | Marche | `#86A8FF` | `#2F5BD0` |
| `muscu` | Musculation, volume | `#B899FF` | `#7440DB` |
| `heart` | Fréquence cardiaque — **données uniquement** | `#FF5E8A` | `#CF2358` |
| `warning` | Calories, alerte douce | `#FFC247` | `#9A5B00` |
| `success` | Validé, GPS précis, tendance ↑ | `#5BDF8E` | `#137A3B` |
| `danger` | Action destructrice | `#FF5D52` | `#C4261C` |

**Règles**
- Une teinte = un sens. Le vélo n'est jamais violet ; le Volt n'est jamais une activité.
- Pastilles d'icône : teinte à **16 %** ; bouton secondaire : **14 %**.
- `heart` ne sert qu'à la donnée cardio (badge FC, chiffres FC), jamais à un bouton.
- Un écran = **un** aplat Volt au plus (l'action principale). Les autres actions sont tonales.

### Dégradés « sillage » (`ElanGradients`)

Une teinte qui s'éclaircit en s'éloignant, comme une trace. Réservés aux
surfaces de marque (carte de partage, illustration d'exercice, héros sans
tracé) ; **jamais sur un bouton**. Les graphes utilisent le même principe en
alpha : barre pleine en haut → 45 % à la base, aire de courbe → transparent.

---

## 3. Typographie (`ElanType`)

**Archivo** (Omnibus-Type, OFL), embarquée dans `res/font/` en huit
graisses sous-ensemblées latin (~460 Ko) : `ElanFonts.sans` (400 → 800) et
`ElanFonts.condensed` (700, 800, 800 italique). Aucune police téléchargée.
La typographie Material est recomposée en Archivo : un `Text` sans style ou un
composant Material non stylé hérite de la marque.

| Token | Famille | Taille / graisse | Emploi |
| --- | --- | --- | --- |
| `metricLg` | Condensed | 84 / 800, tnum | Chronomètre de séance |
| `display` | Condensed | 52 / 800, tnum | Chiffre héros |
| `metric` | Condensed | 34 / 800, tnum | Valeur de StatTile |
| `metricSm` | Condensed | 22 / 800, tnum | Valeur de ligne, compteur, bpm |
| `title` | Condensed *italique* | 34 / 800 | Titre d'écran — l'élan du logotype |
| `headline` | Sans | 19 / 700 | Titre de carte |
| `sectionTitle` | Sans | 17 / 700 | Sous-section |
| `subtitle` | Sans | 16 / 600 | Titre de ligne |
| `body` | Sans | 15 / 400 | Texte courant |
| `bodySm` | Sans | 13 / 400 | Date, aide, sous-titre |
| `label` | Sans | 13 / 600 | Libellé de champ |
| `caption` | Sans | 12 / 500 | Légende, unité |
| `micro` | Sans | 11 / 600 | Axe de graphe, onglet |
| `overline` | Condensed | 13 / 700, +1,2, MAJUSCULES | Libellé de métrique, section |
| `button` / `buttonLg` | Sans | 16 / 700 · 17 / 800 | Boutons, liens, tuiles |

Usage : `Text(text, style = ElanType.headline, color = ElanTheme.colors.text)`.
Jamais de `TextStyle(fontSize = …)` nu : sans `fontFamily`, il retombe sur la
police système.

---

## 4. Forme, espace, profondeur

**Rayons** (`Radius`) : `xs` 8 (barres de graphe) · `sm` 12 (champs) ·
`md` 16 (pastilles d'icône) · `lg` 24 (**cartes**, tuiles) · `xl` 32 (héros,
feuilles) · `pill` (**boutons**, puces, badges, jauges).

**Espacement** (`Spacing`, grille 4 pt) : marges d'écran 16, entre cartes 16,
gouttière 12 (`gutter`), padding de carte 16 × 18.

**Tailles de contrôle** (`ControlSize`) : bouton 52, bouton large 60, tuile de
démarrage 104. Toute cible tactile ≥ 48 dp.

**Profondeur** (`Elevation`) : par le ton. `none` pour cartes et barres ;
l'ombre (`md`, `lg`) n'existe que pour ce qui flotte réellement (barre de
contrôle de séance, feuilles). Pas d'ombre teintée, pas de halo.

---

## 5. Mouvement & haptique (`Motion`, `ui/haptics/Haptics.kt`)

**Ressorts** : `snappy` (appui), `bouncy` (relâchement), `gentle`
(entrées/sorties). **Appui** : toute surface tactile passe par
`Modifier.pressableScale()` (compression 0,96 puis rebond). Le mouvement
réduit du système (`LocalReducedMotion`) supprime l'échelle, garde l'haptique.

| Geste | Retour |
| --- | --- |
| Contrôle secondaire, puce, sélection | `selection` |
| Action principale, tuile de démarrage | `light` |
| Démarrer / mettre en pause un effort | `medium` |
| Séance enregistrée, objectif atteint | `success` |
| Action refusée / erreur | `error` |

---

## 6. Composants (`ui/components/`)

| Composant | Rôle | Sillage |
| --- | --- | --- |
| `ElanWordmark`, `ElanTick`, `SectionLabel` | Marque | Logotype bicolore ; trait d'élan ; overline précédée du trait |
| `ElanButton` | Action | Pilule 52/60 dp. `Primary` = aplat Volt (ou teinte d'activité) sous encre calculée ; `Secondary` = tonal 14 % ; `Danger` ; `Ghost` |
| `ElanCard` | Surface | Tonale, `Radius.lg`, sans ombre ; `Inset` = creux bordé |
| `ElanChip` | Filtre | Pilule 40 dp ; repos tonal, choisie = aplat Volt/teinte |
| `StatTile` | Métrique | Icône 16 + overline, valeur Condensed tabulaire |
| `BarChart` / `LineChart` | Graphes | Barres « sillage », la plus récente pleine et libellée à l'encre ; aire qui s'estompe |
| `SessionRow` | Ligne de séance | Pastille 48 teinte 16 %, valeur `metricSm` |
| `HrBadge` | Cardio | Pilule tonale `heart` 16 %, bpm condensé |
| `EmptyState` | Vide | Pastille ronde, titre, action secondaire |
| Barre d'onglets | Navigation | Barre tonale, indicateur Volt sous icône encre |
| `RouteMap` / `RouteCanvas` | Tracé | Teinte d'activité ; hors-ligne par défaut |

### Anatomie des écrans
- **Accueil** : salut + logotype, pastille FC → séance du jour → « Démarrer »
  (`SectionLabel`) et grille 2×2 de **tuiles d'activité** en aplat → semaine,
  objectifs, activité 7 jours → séances récentes.
- **Séance live** : chrono `metricLg` centré, overline à la teinte d'activité,
  métriques en grille, tracé, barre de contrôle flottante (`Elevation.lg`).
- **Détail** : en-tête, records, tracé, grille de métriques, graphes, notes.

---

## 7. Faire / Ne pas faire

✅ Tokens uniquement (`ElanTheme.colors`, `ElanType`, `Radius`, `Spacing`…).
✅ Un seul aplat Volt par écran : l'action principale.
✅ Chiffres en `metric*` (Condensed, tabulaire).
✅ `pressableScale` + haptique sur tout interactif.
✅ Titres d'écran en `ElanType.title`, sections en `SectionLabel`.

❌ Pas de `Color(0x…)`, `.sp`, `.dp` de style ni `TextStyle` nu dans un écran : il manque un token ? on l'ajoute à `Tokens.kt` / `Type.kt`.
❌ Pas de Volt en texte ou en trait sur le papier : `accent` / `link` s'en chargent.
❌ Pas d'ombre pour signifier une carte ; pas de dégradé sur un bouton.
❌ Pas de mélange des sens de teinte ; pas de rose hors cardio.
❌ Rien de réseau : police, icônes, sons et dégradés sont embarqués.

---

## 8. Implémentation Compose

Sillage vit dans `app/src/main/java/ovh/battistella/elan/ui/`. Material 3
Expressive fournit la mécanique (thème, composants de base,
`MotionScheme.expressive()`) ; Sillage fournit les valeurs.

| Section | Kotlin (`ui/theme/`) | Accès |
| --- | --- | --- |
| §2 Couleurs | `Tokens.kt` → `ElanColors` (`Light` / `Dark`) | `ElanTheme.colors.brand`, `.velo`… ; `ElanColors.forKey("velo")` (`ColorUtils.kt`) |
| §2 Zones FC | `Tokens.kt` → `HrZoneColors` | `ElanTheme.hrZones` |
| §2 Dégradés | `Tokens.kt` → `ElanGradients` (`brand`, activités, `heart`, `danger`, `fire`, `success`, `scrim`), `inkOn()` | `Brush.linearGradient(ElanGradients.velo)` |
| §3 Typographie | `Type.kt` → `ElanFonts`, `ElanType` | `Text(style = ElanType.metric)` |
| §4 Forme | `Tokens.kt` → `Radius`, `Spacing`, `ControlSize`, `Elevation`, `MaxContentWidth` | `RoundedCornerShape(Radius.lg)`, `Modifier.screenContent()` |
| §5 Mouvement | `Tokens.kt` → `Motion` ; `ui/haptics/Haptics.kt` ; `Accessibility.kt` | `Modifier.pressableScale()`, `rememberHaptics()` |
| Icônes | `ui/icons/MdiIcons.kt` (généré par `scripts/gen-mdi-icons.mjs`) | `painterResource(MdiIcons.Fire)` |

**Thème Material.** `ElanTheme(darkTheme)` fournit `LocalElanColors`,
`LocalHrZoneColors`, `LocalReducedMotion`, puis `MaterialExpressiveTheme`
avec un `colorScheme` dérivé (`primary` = `accent`, `secondary` = `link`,
surfaces = neutres, `surfaceTint` transparent) et une typographie Archivo. Pas
de couleur dynamique : la marque est fixe.

**Bord à bord** : `enableEdgeToEdgeCompat()` (`ui/EdgeToEdge.kt`) ; fenêtre
de démarrage `@color/elan_background` (Encre).

**Captures.** `ScreenshotTourTest` rend l'application entière (vraie base en
mémoire, semaine type) en sombre et en clair :

```bash
ELAN_SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTourTest*'
# → app/build/screenshots/*.png
```

**Contraintes** : interface et commentaires en français ; seuls littéraux
tolérés hors `ui/theme/` : `Color.White` / `ElanGradients.OnBright` comme
encre calculée ; 100 % local et hors-ligne.
