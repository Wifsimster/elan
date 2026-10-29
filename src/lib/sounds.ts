import { createAudioPlayer, setAudioModeAsync, type AudioPlayer } from 'expo-audio';

const REST_DONE = require('../../assets/sounds/rest-done.wav');

let restPlayer: AudioPlayer | null = null;
let audioModeReady = false;

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
  restDone: () => {
    try {
      ensureAudioMode();
      const player = ensureRestPlayer();
      if (!player) return;
      player.seekTo(0).catch(() => {});
      player.play();
    } catch {
    }
  },
};
