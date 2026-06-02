# PULSE — Design System

**PULSE** est le langage visuel d'Élan : un système **cinétique, sombre par défaut et orienté effort**, dans la lignée de Material 3 Expressive et des apps de sport modernes (WHOOP, Strava, Apple Fitness). Trois partis pris le résument :

1. **L'énergie passe par la couleur et le dégradé.** Chaque activité a sa teinte vive et son dégradé ; les actions principales rayonnent (ombre teintée), pas juste « remplies ».
2. **La profondeur passe par l'ombre, pas par la bordure.** Les surfaces flottent au-dessus d'un fond quasi noir ; les bordures dures sont réservées aux champs et séparateurs.
3. **Tout ce qui réagit au doigt bouge.** Appui = compression élastique + retour haptique. Le mouvement est physique (ressorts), jamais linéaire.

> Source de vérité : **`src/constants/theme.ts`**. Aucune valeur de style en dur dans les écrans — on pioche les tokens ici (couleurs via `useTheme()`, le reste via les exports statiques `Radius` / `Elevation` / `Type` / `Motion` / `Gradients`).

---

## 1. Couleur

Palette **sombre par défaut** (le mode clair suit le système). Les couleurs dépendantes du thème se lisent via `useTheme()`.

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
| `textMuted` | Texte/icône tertiaire (chevrons) | `#6B7484` | `#8A93A1` |
| `accent` | Marque, liens, focus | `#5B7CFF` | `#3B5BFF` |
| `accentSoft` | Fond teinté accent | `#1B2236` | `#E5EAFF` |

### Couleurs d'activité (sémantiques)

| Token | Sens | Sombre | Clair |
| --- | --- | --- | --- |
| `velo` | Vélo / cardio GPS | `#22D3C5` | `#0BA59B` |
| `muscu` | Musculation / volume | `#A78BFA` | `#7C3AED` |
| `heart` | Fréquence cardiaque (data viz) | `#FF5C7A` | `#F43F5E` |
| `danger` | Action destructrice (supprimer) | `#FF4D4D` | `#DC2626` |
| `success` | GPS précis, validé | `#34D399` | `#10B981` |
| `warning` | Calories, alerte douce | `#FBBF24` | `#E08600` |

**Règles d'usage**
- Une teinte d'activité = un sens. Ne jamais peindre du vélo en violet.
- Fonds teintés : superposer la teinte à `~13 %` (`color + '22'`) pour les pastilles d'icône, `~24 %` pour les puces.
- **`heart` (rose) ne sert qu'à la donnée cardio** (badge FC, chiffres FC), jamais à un bouton.
- **Boutons : le primaire est bleu (`accent`) ou la teinte d'activité (vélo/muscu) ; seul le destructif est rouge (`danger`).** Pas de bouton rose.

### Dégradés (`Gradients`)

Vifs et **identiques en clair/sombre** (ils se posent toujours sur une surface colorée). Diagonale par défaut.

| Nom | Couleurs | Emploi |
| --- | --- | --- |
| `accent` | `#6478FF → #7A3BFF` | Action principale neutre, barres du graphe |
| `velo` | `#2DE0C0 → #0BA9B5` | Bouton/héros vélo |
| `muscu` | `#B07BFF → #7A3BFF` | Bouton/héros muscu |
| `heart` | `#FF6B8B → #F43F5E` | Cardio (jauges, accents data) |
| `danger` | `#FF5A5A → #D61F2E` | Bouton destructif (supprimer) |
| `fire` | `#FFB020 → #FF6B35` | Calories |
| `success` | `#4ADE80 → #10B981` | Validation |
| `scrim` | transparent → noir | Voile sous une image/héros |

Rendu via `<Gradient>` (composant maison sur `react-native-svg` — **100 % local, aucune dépendance réseau**).

---

## 2. Typographie (`Type`)

Police système (rounded sur iOS via `Fonts`). Chiffres d'effort en **`tabular-nums`** pour ne pas « danser » en direct. Tracking négatif sur les grands titres pour un rendu compact et moderne.

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

Usage : `style={{ ...Type.headline, color: theme.text }}`.

---

## 3. Espacement, rayons, élévation

**Espacement** — grille 4 pt (`Spacing`). Marges d'écran : `16`. Espace inter-cartes : `16`. Gouttière intra-carte : `12`.

**Rayons** (`Radius`) — coins **continus** (squircle, `borderCurve: 'continuous'`) partout :

| `sm` 12 | `md` 16 | `lg` 22 | `xl` 28 | `pill` 999 |
| --- | --- | --- | --- | --- |
| pastilles, champs | icône détail | **Card, bouton** | héros | puces, badges |

**Élévation** (`Elevation`) — ombres douces et diffuses (`sm` / `md` / `lg`). `elevation` couvre Android, `shadow*` couvre iOS. La profondeur ne se signale **jamais** par une bordure dure. Les actions principales ajoutent une **ombre teintée** à leur couleur (`shadowColor: accent`, opacité ~0.45) pour « rayonner ».

---

## 4. Mouvement & haptique (`Motion`, `lib/haptics`)

Le ressenti « cinétique » vient d'ici. Bâti sur `react-native-reanimated` + `expo-haptics`.

**Ressorts** (`Motion.spring`) : `snappy` (appui), `bouncy` (relâchement, léger rebond), `gentle` (entrées/sorties).

**Appui** : toute surface tactile passe par **`<PressableScale>`** → compression à `0.96` (`Motion.pressScale`) puis rebond. C'est la brique de base ; ne pas utiliser `Pressable` nu pour un élément interactif visible.

**Haptique** (`haptics`) — un retour par interaction marquante :

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

Tous dans `src/components/`, thémés via `useTheme()` + tokens.

| Composant | Rôle | Points clés |
| --- | --- | --- |
| **`Gradient`** | Fond en dégradé linéaire | `colors` = nom de `Gradients` ou liste ; SVG local |
| **`PressableScale`** | Surface tactile élastique | `scaleTo`, `haptic` ; base de toute interaction |
| **`Button`** | Action | `primary`/`secondary`/`danger`/`ghost`, `size` md/lg ; le primaire est un dégradé à ombre teintée |
| **`Card`** | Surface de contenu | `elevated` (défaut, ombre) / `inset` (champ) / `plain` |
| **`Chip`** | Filtre / suggestion | sélectionnable, teinte d'activité |
| **`StatTile`** | Métrique « bento » | pastille d'icône teintée + grand chiffre tabulaire |
| **`BarChart`** | Mini-graphe | barres en dégradé vertical, coins arrondis |
| **`HrBadge`** | État cardio | halo (`shadow`) coloré quand la ceinture émet |
| **`EmptyState`** | Vide | icône + titre + sous-titre centrés |
| **`RouteMap`** | Tracé GPS | SVG normalisé, aucun fond cartographique (offline) |

### Anatomie des écrans
- **Accueil** : en-tête (salut + titre `title` + `HrBadge`) → deux gros boutons d'action `size="lg"` (dégradés vélo/muscu) → cartes résumé & graphe → liste récente.
- **Séance live (vélo/muscu)** : chrono `metricLg` centré, label `overline` à la teinte d'activité, stats en grille bento, **barre de contrôle flottante** (`Elevation.lg`) collée en bas.
- **Détail de séance** : en-tête icône+titre, tracé GPS, grille de stats, ventilation muscu, notes.

---

## 6. Faire / Ne pas faire

✅ Lire les valeurs depuis `theme.ts` (couleurs via `useTheme()`, reste via tokens).
✅ Envelopper tout interactif dans `PressableScale` + haptique adapté.
✅ Action principale = `Button` primaire (dégradé + ombre teintée).
✅ Profondeur par l'ombre (`Elevation`) ; coins `continuous`.
✅ Chiffres d'effort en `tabular-nums` (déjà dans `Type.metric*`).

❌ Pas de couleur, taille de police, rayon ni ombre codés en dur dans un écran.
❌ Pas de bordure dure pour signifier une surface flottante.
❌ Ne pas mélanger les sens des teintes d'activité.
❌ Pas de dépendance réseau/cloud (dégradés, carte et icônes restent 100 % locaux).
❌ Pas de mémoïsation manuelle qui combat le React Compiler (activé).

---

## 7. Stack & contraintes

- **Expo SDK 56 / React Native 0.85 / React 19**, React Compiler activé.
- Style par **objets inline** (pas NativeWind) + `useTheme()`.
- Briques d'animation/effet déjà présentes : `react-native-reanimated`, `expo-haptics`, `react-native-svg`, `expo-glass-effect` (verre iOS, optionnel/secondaire).
- Copie d'interface et commentaires en **français**.
- 100 % local et hors-ligne — c'est un invariant produit, pas seulement visuel.
