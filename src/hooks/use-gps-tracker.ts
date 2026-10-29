import { useCallback, useRef, useState } from 'react';
import * as Location from 'expo-location';
import { Platform } from 'react-native';

import { haversineMeters } from '@/lib/geo';
import { GpsConsolidator, type ConsolidatedPoint } from '@/lib/gps-filter';
import { setGpsTaskListener, startGpsUpdates, stopGpsUpdates } from '@/lib/gps-task';

export type LivePoint = ConsolidatedPoint;

export type GpsStatus = 'idle' | 'requesting' | 'denied' | 'tracking';

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

const LIVE_DECIMATE_M = 8;
const MAX_PLAUSIBLE_KMH = 120;

export function useGpsTracker() {
  const [status, setStatus] = useState<GpsStatus>('idle');
  const [state, setState] = useState<GpsState>(INITIAL);
  const [livePath, setLivePath] = useState<LivePoint[]>([]);

  const subRef = useRef<Location.LocationSubscription | null>(null);
  const usingTaskRef = useRef(false);
  const consolidatorRef = useRef<GpsConsolidator | null>(null);
  const pointsRef = useRef<LivePoint[]>([]);
  const flushedCountRef = useRef(0);
  const livePathRef = useRef<LivePoint[]>([]);
  const lastLiveRef = useRef<LivePoint | null>(null);
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
      accRef.current = { ...accRef.current, accuracyM: accuracy ?? null };
      setState({ ...accRef.current });
      return;
    }

    if (pausedRef.current) return;

    const point = result.point;
    let { distanceM, maxSpeedKmh, elevationGainM } = accRef.current;
    distanceM += result.deltaDistanceM;
    elevationGainM += result.deltaElevationGainM;

    const instSpeed = point.speedKmh ?? 0;
    if (instSpeed > maxSpeedKmh && instSpeed <= MAX_PLAUSIBLE_KMH) maxSpeedKmh = instSpeed;

    pointsRef.current.push(point);

    const lastLive = lastLiveRef.current;
    if (lastLive == null || haversineMeters(lastLive, point) >= LIVE_DECIMATE_M) {
      livePathRef.current = [...livePathRef.current, point];
      lastLiveRef.current = point;
      lastLiveEmitRef.current = point.ts;
      setLivePath(livePathRef.current);
    } else if (point.ts - lastLiveEmitRef.current >= 2500) {
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

  const takeUnflushed = useCallback((): LivePoint[] => {
    const all = pointsRef.current;
    const from = flushedCountRef.current;
    if (from >= all.length) return [];
    const slice = all.slice(from);
    flushedCountRef.current = all.length;
    return slice;
  }, []);

  const allPoints = useCallback((): LivePoint[] => pointsRef.current.slice(), []);

  const stop = useCallback(() => {
    subRef.current?.remove();
    subRef.current = null;
    if (usingTaskRef.current) {
      usingTaskRef.current = false;
      setGpsTaskListener(null);
      stopGpsUpdates();
    }
    setStatus('idle');
    return {
      points: pointsRef.current.slice(),
      ...accRef.current,
    };
  }, []);

  return { status, ...state, livePath, start, stop, setPaused, takeUnflushed, allPoints };
}
