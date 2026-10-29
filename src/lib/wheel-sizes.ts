export type WheelSize = {
  label: string;
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

export function matchWheelSize(mm: number): WheelSize | undefined {
  return WHEEL_SIZES.find((w) => w.mm === mm);
}
