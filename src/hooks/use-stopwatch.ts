// Chronomètre simple : temps actif écoulé en secondes, avec pause.
import { useCallback, useEffect, useRef, useState } from 'react';

export function useStopwatch() {
  const [elapsedSec, setElapsedSec] = useState(0);
  const [running, setRunning] = useState(false);
  const accumulatedRef = useRef(0); // secondes figées avant la pause en cours
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

  // Pré-charge un temps écoulé (reprise d'une séance en pause) sans démarrer :
  // le prochain `start()` enchaînera à partir de cette base.
  const seed = useCallback((sec: number) => {
    const base = Math.max(0, sec);
    accumulatedRef.current = base;
    startedAtRef.current = null;
    setElapsedSec(Math.floor(base));
    setRunning(false);
  }, []);

  return { elapsedSec, running, start, pause, reset, seed };
}
