/**
 * Sons de l'app. Pendant des retours haptiques (`lib/haptics`) : volontairement
 * « fire and forget », un échec (web, lecteur non chargé) est ignoré. 100 %
 * local — le fichier audio est embarqué dans l'app, aucune connexion réseau.
 *
 * Le lecteur est créé paresseusement et réutilisé : on se contente de revenir
 * au début puis de relancer la lecture à chaque déclenchement.
 */
import { createAudioPlayer, setAudioModeAsync, type AudioPlayer } from 'expo-audio';

// Petit carillon ascendant joué à la fin d'un repos inter-séries.
const REST_DONE = require('../../assets/sounds/rest-done.wav');

let restPlayer: AudioPlayer | null = null;
let audioModeReady = false;

/**
 * Configure une seule fois le mode audio : le carillon doit s'entendre même
 * en mode silencieux (iOS) et se superposer à une musique en cours plutôt que
 * de la couper, sans prendre la main en arrière-plan.
 */
function ensureAudioMode() {
  if (audioModeReady) return;
  audioModeReady = true;
  setAudioModeAsync({
    playsInSilentMode: true,
    interruptionMode: 'mixWithOthers',
    shouldPlayInBackground: false,
  }).catch(() => {});
}

function ensureRestPlayer(): AudioPlayer | null {
  if (restPlayer) return restPlayer;
  try {
    restPlayer = createAudioPlayer(REST_DONE);
  } catch {
    restPlayer = null;
  }
  return restPlayer;
}

export const sounds = {
  /** Carillon de fin de repos : invite à reprendre la série suivante. */
  restDone: () => {
    try {
      ensureAudioMode();
      const player = ensureRestPlayer();
      if (!player) return;
      // Repart du début à chaque fois (le lecteur garde sa position de fin).
      player.seekTo(0).catch(() => {});
      player.play();
    } catch {
      // Lecture impossible : on se contente du retour haptique.
    }
  },
};
