/**
 * Tokens du design system **PULSE** (voir DESIGN.md).
 *
 * Tout le style de l'app dérive de ce fichier : couleurs (clair/sombre),
 * dégradés d'activité, rayons, élévation, échelle typographique et ressorts
 * d'animation. Les écrans ne codent jamais une valeur en dur — ils piochent ici
 * via `useTheme()` (couleurs dépendantes du thème) ou via les exports statiques
 * ci-dessous (Radius / Elevation / Type / Motion / Gradients, indépendants du thème).
 */

import '@/global.css';

import { Platform, type TextStyle } from 'react-native';

export const Colors = {
  light: {
    text: '#0B0E13',
    textSecondary: '#5A6472',
    textMuted: '#8A93A1',
    background: '#F3F5FA',
    backgroundElement: '#FFFFFF',
    surfaceHigh: '#FFFFFF',
    backgroundSelected: '#E9ECF4',
    border: '#E4E8F0',
    hairline: '#EDF0F6',
    accent: '#3B5BFF',
    accentSoft: '#E5EAFF',
    velo: '#0BA59B',
    muscu: '#7C3AED',
    heart: '#F43F5E',
    success: '#10B981',
    warning: '#E08600',
  },
  dark: {
    text: '#F4F7FB',
    textSecondary: '#9AA3B0',
    textMuted: '#6B7484',
    background: '#0A0C10',
    backgroundElement: '#14181F',
    surfaceHigh: '#1B202A',
    backgroundSelected: '#232A35',
    border: '#222934',
    hairline: '#1A1F28',
    accent: '#5B7CFF',
    accentSoft: '#1B2236',
    velo: '#22D3C5',
    muscu: '#A78BFA',
    heart: '#FF5C7A',
    success: '#34D399',
    warning: '#FBBF24',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

/**
 * Dégradés d'activité — volontairement vifs et identiques en clair/sombre car
 * ils se posent toujours sur une surface colorée (boutons, héros, jauges).
 * Ordre : [début, fin], dégradé en diagonale par défaut.
 */
export const Gradients = {
  accent: ['#6478FF', '#7A3BFF'],
  velo: ['#2DE0C0', '#0BA9B5'],
  muscu: ['#B07BFF', '#7A3BFF'],
  heart: ['#FF6B8B', '#F43F5E'],
  fire: ['#FFB020', '#FF6B35'],
  success: ['#4ADE80', '#10B981'],
  /** Voile sombre posé en bas d'une image/héros pour asseoir le texte. */
  scrim: ['rgba(10,12,16,0)', 'rgba(10,12,16,0.85)'],
} as const;

export type GradientName = keyof typeof Gradients;

/** Rayons — coins « continus » (squircle iOS) appliqués partout. */
export const Radius = {
  sm: 12,
  md: 16,
  lg: 22,
  xl: 28,
  pill: 999,
} as const;

/** Grille d'espacement 4 pt. */
export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

/**
 * Élévation par ombres douces et diffuses (jamais de bordure dure pour signifier
 * la profondeur). `elevation` couvre Android, les `shadow*` couvrent iOS.
 */
export const Elevation = {
  none: {},
  sm: {
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  md: {
    shadowColor: '#000',
    shadowOpacity: 0.16,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
  lg: {
    shadowColor: '#000',
    shadowOpacity: 0.26,
    shadowRadius: 34,
    shadowOffset: { width: 0, height: 18 },
    elevation: 18,
  },
} as const;

/**
 * Échelle typographique. `display`/`metric` portent le poids visuel des chiffres
 * d'effort ; le reste cadence titres et corps. Les métriques sont en
 * `tabular-nums` pour ne pas « danser » quand elles changent en direct.
 */
export type TypeToken =
  | 'display'
  | 'metric'
  | 'metricLg'
  | 'title'
  | 'headline'
  | 'subtitle'
  | 'body'
  | 'label'
  | 'caption'
  | 'overline';

export const Type: Record<TypeToken, TextStyle> = {
  display: { fontSize: 44, fontWeight: '800', letterSpacing: -1.2, lineHeight: 48 },
  metric: { fontSize: 30, fontWeight: '800', letterSpacing: -0.5, fontVariant: ['tabular-nums'] },
  metricLg: { fontSize: 64, fontWeight: '800', letterSpacing: -2, fontVariant: ['tabular-nums'] },
  title: { fontSize: 28, fontWeight: '800', letterSpacing: -0.6, lineHeight: 32 },
  headline: { fontSize: 20, fontWeight: '800', letterSpacing: -0.3, lineHeight: 26 },
  subtitle: { fontSize: 16, fontWeight: '700', letterSpacing: -0.2 },
  body: { fontSize: 15, fontWeight: '500', lineHeight: 22 },
  label: { fontSize: 13, fontWeight: '600' },
  caption: { fontSize: 12, fontWeight: '600' },
  overline: { fontSize: 12, fontWeight: '700', letterSpacing: 1.4, textTransform: 'uppercase' },
};

/**
 * Mouvement — ressorts (react-native-reanimated `withSpring`) et facteur d'échelle
 * d'appui. Le mouvement est physique, jamais linéaire : tout ce qui réagit au
 * doigt « ressort ».
 */
export const Motion = {
  spring: {
    snappy: { damping: 20, stiffness: 320, mass: 0.7 },
    gentle: { damping: 24, stiffness: 170, mass: 1 },
    bouncy: { damping: 13, stiffness: 240, mass: 0.8 },
  },
  pressScale: 0.96,
} as const;

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: 'var(--font-display)',
    serif: 'var(--font-serif)',
    rounded: 'var(--font-rounded)',
    mono: 'var(--font-mono)',
  },
});

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
