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

/** Résultat d'une demande de démarrage du suivi (distingue le refus « position
 *  approximative » d'un refus total, pour un message dédié). */
export type GpsStartResult = 'granted' | 'denied' | 'coarse';

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
/** Vitesse instantanée plafond retenue pour la vitesse max (km/h) : au-delà,
 *  c'est un fix Doppler glitché, pas un vrai record (« 173 km/h à vélo »). */
const MAX_PLAUSIBLE_KMH = 120;

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
  // Curseur du flush incrémental : index du premier point pas encore écrit en
  // base (cf. use-gps-tracker → velo.tsx, persistance au fil de l'eau).
  const flushedCountRef = useRef(0);
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
    // Plafond de plausibilité : un fix Doppler aberrant ne s'octroie pas la
    // vitesse max de la sortie.
    if (instSpeed > maxSpeedKmh && instSpeed <= MAX_PLAUSIBLE_KMH) maxSpeedKmh = instSpeed;

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

  const start = useCallback(async (): Promise<GpsStartResult> => {
    setStatus('requesting');
    const perm = await Location.requestForegroundPermissionsAsync();
    if (perm.status !== 'granted') {
      setStatus('denied');
      return 'denied';
    }
    // Android 12+ : l'utilisateur peut n'accorder que la position APPROXIMATIVE.
    // À vélo elle est inexploitable — chaque fix dépasse la porte de précision
    // (50 m, gps-filter) et est rejeté en silence, laissant une session vide et
    // « En attente de déplacement ». On refuse explicitement, avec un message
    // dédié côté écran (position précise requise).
    if (perm.android?.accuracy === 'coarse') {
      setStatus('denied');
      return 'coarse';
    }
    consolidatorRef.current = new GpsConsolidator();
    pointsRef.current = [];
    flushedCountRef.current = 0;
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
    return 'granted';
  }, [handleFix]);

  const setPaused = useCallback((paused: boolean) => {
    pausedRef.current = paused;
  }, []);

  // Renvoie les points accumulés depuis le dernier appel et avance le curseur.
  // Sert au flush incrémental : l'écran écrit ces points en base au fil de l'eau
  // (survie à un crash) sans re-persister ce qui l'a déjà été.
  const takeUnflushed = useCallback((): LivePoint[] => {
    const all = pointsRef.current;
    const from = flushedCountRef.current;
    if (from >= all.length) return [];
    const slice = all.slice(from);
    flushedCountRef.current = all.length;
    return slice;
  }, []);

  // Tous les points capturés (pour recalculer les agrégats à l'enregistrement).
  const allPoints = useCallback((): LivePoint[] => pointsRef.current.slice(), []);

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

  return { status, ...state, livePath, start, stop, setPaused, takeUnflushed, allPoints };
}
