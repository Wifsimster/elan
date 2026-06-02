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

export function useGpsTracker() {
  const [status, setStatus] = useState<GpsStatus>('idle');
  const [state, setState] = useState<GpsState>(INITIAL);

  const subRef = useRef<Location.LocationSubscription | null>(null);
  const pointsRef = useRef<LivePoint[]>([]);
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
    lastRef.current = null;
    lastAltRef.current = null;
    pausedRef.current = false;
    accRef.current = INITIAL;
    setState(INITIAL);

    subRef.current = await Location.watchPositionAsync(
      {
        accuracy: Location.Accuracy.BestForNavigation,
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

  return { status, ...state, start, stop, setPaused };
}
