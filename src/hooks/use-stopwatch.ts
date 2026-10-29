import { useCallback, useEffect, useRef, useState } from 'react';

export function useStopwatch() {
  const [elapsedSec, setElapsedSec] = useState(0);
  const [running, setRunning] = useState(false);
  const accumulatedRef = useRef(0);
  const startedAtRef = useRef<number | null>(null);

  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => {
      const base = accumulatedRef.current;
      const since = startedAtRef.current ? (Date.now() - startedAtRef.current) / 1000 : 0;
      setElapsedSec(Math.floor(base + since));
    }, 250);
    return () => clearInterval(id);
  }, [running]);

  const getElapsedSec = useCallback(() => {
    const base = accumulatedRef.current;
    const since = startedAtRef.current ? (Date.now() - startedAtRef.current) / 1000 : 0;
    return Math.floor(base + since);
  }, []);

  const start = useCallback(() => {
    startedAtRef.current = Date.now();
    setRunning(true);
  }, []);

  const pause = useCallback(() => {
    if (startedAtRef.current) {
      accumulatedRef.current += (Date.now() - startedAtRef.current) / 1000;
      startedAtRef.current = null;
    }
    setRunning(false);
  }, []);

  const reset = useCallback(() => {
    accumulatedRef.current = 0;
    startedAtRef.current = null;
    setElapsedSec(0);
    setRunning(false);
  }, []);

  const seed = useCallback((sec: number) => {
    const base = Math.max(0, sec);
    accumulatedRef.current = base;
    startedAtRef.current = null;
    setElapsedSec(Math.floor(base));
    setRunning(false);
  }, []);

  return { elapsedSec, running, start, pause, reset, seed, getElapsedSec };
}
