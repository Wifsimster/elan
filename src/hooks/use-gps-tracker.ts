// Suivi GPS d'une sortie vélo (premier plan).
// Calcule distance, vitesse instantanée/max et dénivelé positif.
import { useCallback, useRef, useState } from 'react';
import * as Location from 'expo-location';

import { haversineMeters } from '@/lib/geo';

export type LivePoint = {
  ts: number;
  lat: number;
  lon: number;
  altitude: number | null;
  speedKmh: number | null;
};

export type GpsStatus = 'idle' | 'requesting' | 'denied' | 'tracking';

type GpsState = {
  distanceM: number;
  speedKmh: number;
  maxSpeedKmh: number;
  elevationGainM: number;
  pointCount: number;
  accuracyM: number | null;
};

const INITIAL: GpsState = {
  distanceM: 0,
  speedKmh: 0,
  maxSpeedKmh: 0,
  elevationGainM: 0,
  pointCount: 0,
  accuracyM: null,
};

/** Distance minimale entre deux points conservés dans le tracé affiché en live. */
const LIVE_DECIMATE_M = 8;

export function useGpsTracker() {
  const [status, setStatus] = useState<GpsStatus>('idle');
  const [state, setState] = useState<GpsState>(INITIAL);
  // Tracé allégé pour l'affichage temps réel (réactif : déclenche le rendu de la carte).
  const [livePath, setLivePath] = useState<LivePoint[]>([]);

  const subRef = useRef<Location.LocationSubscription | null>(null);
  const pointsRef = useRef<LivePoint[]>([]);
  const livePathRef = useRef<LivePoint[]>([]);
  const lastLiveRef = useRef<LivePoint | null>(null);
  // Horodatage de la dernière émission du curseur de tête (throttle du rendu carte).
  const lastLiveEmitRef = useRef(0);
  const lastRef = useRef<LivePoint | null>(null);
  const lastAltRef = useRef<number | null>(null);
  const pausedRef = useRef(false);
  const accRef = useRef<GpsState>(INITIAL);

  const handleFix = useCallback((loc: Location.LocationObject) => {
    const { latitude, longitude, altitude, speed, accuracy } = loc.coords;
    // On ignore les points trop imprécis pour éviter de gonfler la distance.
    if (accuracy != null && accuracy > 30) {
      accRef.current = { ...accRef.current, accuracyM: accuracy };
      setState({ ...accRef.current });
      return;
    }

    const point: LivePoint = {
      ts: loc.timestamp,
      lat: latitude,
      lon: longitude,
      altitude: altitude ?? null,
      speedKmh: speed != null && speed >= 0 ? speed * 3.6 : null,
    };

    if (pausedRef.current) {
      lastRef.current = point;
      lastAltRef.current = altitude ?? lastAltRef.current;
      return;
    }

    const prev = lastRef.current;
    let { distanceM, maxSpeedKmh, elevationGainM } = accRef.current;

    if (prev) {
      const d = haversineMeters(prev, point);
      if (d >= 2) distanceM += d; // seuil anti-dérive GPS à l'arrêt
    }

    // Dénivelé positif (lissé pour réduire le bruit altimétrique).
    if (altitude != null) {
      const lastAlt = lastAltRef.current;
      if (lastAlt != null && altitude - lastAlt > 1) {
        elevationGainM += altitude - lastAlt;
      }
      if (lastAlt == null || Math.abs(altitude - lastAlt) > 1) {
        lastAltRef.current = altitude;
      }
    }

    const instSpeed = point.speedKmh ?? 0;
    if (instSpeed > maxSpeedKmh) maxSpeedKmh = instSpeed;

    pointsRef.current.push(point);
    lastRef.current = point;

    // Alimente le tracé live décimé : on fige un point d'ancrage dès qu'il s'est
    // assez éloigné du précédent (le départ reste donc immuable). La position
    // courante est ajoutée en tête provisoire pour que le tracé colle à la réalité.
    const lastLive = lastLiveRef.current;
    if (lastLive == null || haversineMeters(lastLive, point) >= LIVE_DECIMATE_M) {
      livePathRef.current = [...livePathRef.current, point];
      lastLiveRef.current = point;
      lastLiveEmitRef.current = point.ts;
      setLivePath(livePathRef.current);
    } else if (point.ts - lastLiveEmitRef.current >= 2500) {
      // Curseur de tête provisoire : la ligne figée (branche d'ancrage) reste
      // exacte ; on throttle le re-rendu du dernier segment « vivant » à ~1/2,5 s
      // pour éviter une recopie O(n) du tracé à chaque fix sur une longue sortie.
      lastLiveEmitRef.current = point.ts;
      setLivePath([...livePathRef.current, point]);
    }

    accRef.current = {
      distanceM,
      speedKmh: instSpeed,
      maxSpeedKmh,
      elevationGainM,
      pointCount: pointsRef.current.length,
      accuracyM: accuracy ?? null,
    };
    setState({ ...accRef.current });
  }, []);

  const start = useCallback(async (): Promise<boolean> => {
    setStatus('requesting');
    const { status: perm } = await Location.requestForegroundPermissionsAsync();
    if (perm !== 'granted') {
      setStatus('denied');
      return false;
    }
    pointsRef.current = [];
    livePathRef.current = [];
    lastLiveRef.current = null;
    lastLiveEmitRef.current = 0;
    lastRef.current = null;
    lastAltRef.current = null;
    pausedRef.current = false;
    accRef.current = INITIAL;
    setState(INITIAL);
    setLivePath([]);

    subRef.current = await Location.watchPositionAsync(
      {
        // `High` (~10 m) reste sous les seuils de distance/vitesse (2 m / 8 m) et
        // alimente la même précision de tracé que `BestForNavigation`, tout en
        // épargnant le palier GNSS le plus gourmand sur les sorties de plusieurs heures.
        accuracy: Location.Accuracy.High,
        distanceInterval: 5,
        timeInterval: 1000,
      },
      handleFix,
    );
    setStatus('tracking');
    return true;
  }, [handleFix]);

  const setPaused = useCallback((paused: boolean) => {
    pausedRef.current = paused;
    if (!paused) lastRef.current = null; // évite un saut de distance à la reprise
  }, []);

  const stop = useCallback(() => {
    subRef.current?.remove();
    subRef.current = null;
    setStatus('idle');
    return {
      points: pointsRef.current.slice(),
      ...accRef.current,
    };
  }, []);

  return { status, ...state, livePath, start, stop, setPaused };
}
