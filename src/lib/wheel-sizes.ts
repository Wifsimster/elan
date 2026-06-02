// Tailles de pneu courantes et leur circonférence de roue en mm.
// Valeurs issues du barème standard des compteurs vélo (Garmin/iGPSPORT) ;
// la circonférence reste l'unité de calcul (vitesse = tours × circonférence).

export type WheelSize = {
  /** Désignation lisible du pneu (ETRTO / pouces). */
  label: string;
  /** Circonférence de roue correspondante, en mm. */
  mm: number;
};

export const WHEEL_SIZES: readonly WheelSize[] = [
  { label: '700×23c', mm: 2096 },
  { label: '700×25c', mm: 2105 },
  { label: '700×28c', mm: 2136 },
  { label: '700×32c', mm: 2155 },
  { label: '700×38c', mm: 2180 },
  { label: '650b', mm: 2079 },
  { label: '26×1.5', mm: 1985 },
  { label: '26×1.95', mm: 2050 },
  { label: '27.5×2.1', mm: 2148 },
  { label: '29×2.1', mm: 2288 },
  { label: '29×2.25', mm: 2326 },
] as const;

/** Renvoie le preset dont la circonférence correspond exactement, le cas échéant. */
export function matchWheelSize(mm: number): WheelSize | undefined {
  return WHEEL_SIZES.find((w) => w.mm === mm);
}
