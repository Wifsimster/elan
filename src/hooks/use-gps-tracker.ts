// Suivi GPS d'une sortie vélo.
// Les positions arrivent via un service de premier plan Android (lib/gps-task),
// qui continue d'enregistrer écran éteint / app en arrière-plan — c'est ce qui
// manquait et trouait les tracés. Chaque fix passe par lib/gps-filter
// (porte de précision, rejet d'aberrations, Kalman, hystérésis d'altitude)
// avant d'alimenter distance, vitesse et dénivelé.
import { useCallback, useRef, useState } from 'react';
import * as Location from 'expo-location';
import { Platform } from 'react-native';

import { haversineMeters } from '@/lib/geo';
import { GpsConsolidator, type ConsolidatedPoint } from '@/lib/gps-filter';
import { setGpsTaskListener, startGpsUpdates, stopGpsUpdates } from '@/lib/gps-task';

export type LivePoint = ConsolidatedPoint;

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
  // Suivi actif via le service d'arrière-plan (sinon repli watchPositionAsync).
  const usingTaskRef = useRef(false);
  const consolidatorRef = useRef<GpsConsolidator | null>(null);
  const pointsRef = useRef<LivePoint[]>([]);
  const livePathRef = useRef<LivePoint[]>([]);
  const lastLiveRef = useRef<LivePoint | null>(null);
  // Horodatage de la dernière émission du curseur de tête (throttle du rendu carte).
  const lastLiveEmitRef = useRef(0);
  const pausedRef = useRef(false);
  const accRef = useRef<GpsState>(INITIAL);

  const handleFix = useCallback((loc: Location.LocationObject) => {
    const { latitude, longitude, altitude, altitudeAccuracy, speed, accuracy } = loc.coords;
    const consolidator = consolidatorRef.current;
    if (!consolidator) return;

    const result = consolidator.process({
      ts: loc.timestamp,
      lat: latitude,
      lon: longitude,
      altitude: altitude ?? null,
      accuracy: accuracy ?? null,
      altitudeAccuracy: altitudeAccuracy ?? null,
      speed: speed != null && speed >= 0 ? speed : null,
    });

    if (result.point == null) {
      // Fix rejeté (imprécis ou aberrant) : on n'actualise que l'indicateur de précision.
      accRef.current = { ...accRef.current, accuracyM: accuracy ?? null };
      setState({ ...accRef.current });
      return;
    }

    // En pause : le filtre reste alimenté (pas de saut à la reprise) mais on ne
    // cumule ni distance ni dénivelé et on n'enregistre pas le point.
    if (pausedRef.current) return;

    const point = result.point;
    let { distanceM, maxSpeedKmh, elevationGainM } = accRef.current;
    distanceM += result.deltaDistanceM;
    elevationGainM += result.deltaElevationGainM;

    const instSpeed = point.speedKmh ?? 0;
    if (instSpeed > maxSpeedKmh) maxSpeedKmh = instSpeed;

    pointsRef.current.push(point);

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
    consolidatorRef.current = new GpsConsolidator();
    pointsRef.current = [];
    livePathRef.current = [];
    lastLiveRef.current = null;
    lastLiveEmitRef.current = 0;
    pausedRef.current = false;
    accRef.current = INITIAL;
    setState(INITIAL);
    setLivePath([]);

    // Service de premier plan (notification persistante) : le GPS continue
    // écran éteint, comme Strava. Repli sur watchPositionAsync si indisponible
    // (web, service refusé par l'OS) — suivi limité au premier plan dans ce cas.
    usingTaskRef.current = false;
    if (Platform.OS !== 'web') {
      try {
        setGpsTaskListener((locations) => locations.forEach(handleFix));
        await startGpsUpdates();
        usingTaskRef.current = true;
      } catch {
        setGpsTaskListener(null);
      }
    }
    if (!usingTaskRef.current) {
      subRef.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.BestForNavigation,
          distanceInterval: 0,
          timeInterval: 1000,
        },
        handleFix,
      );
    }
    setStatus('tracking');
    return true;
  }, [handleFix]);

  const setPaused = useCallback((paused: boolean) => {
    pausedRef.current = paused;
  }, []);

  const stop = useCallback(() => {
    subRef.current?.remove();
    subRef.current = null;
    if (usingTaskRef.current) {
      usingTaskRef.current = false;
      setGpsTaskListener(null);
      stopGpsUpdates(); // best-effort : coupe le service et sa notification
    }
    setStatus('idle');
    return {
      points: pointsRef.current.slice(),
      ...accRef.current,
    };
  }, []);

  return { status, ...state, livePath, start, stop, setPaused };
}
