// Tâche de localisation en arrière-plan (expo-task-manager + expo-location).
//
// `watchPositionAsync` ne reçoit plus de positions dès que l'écran s'éteint ou
// que l'app passe en arrière-plan : sur une sortie réelle cela trouait le tracé
// (lignes droites, distance et dénivelé sous-estimés). Comme Strava & co, on
// enregistre via un service de premier plan Android (notification persistante)
// qui maintient le GPS actif écran éteint — sans exiger la permission
// « localisation en arrière-plan » (cf. LocationModule.kt : un service démarré
// app au premier plan suffit).
import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

export const GPS_TASK = 'elan-gps-tracking';

type GpsTaskListener = (locations: Location.LocationObject[]) => void;

// Un seul consommateur : l'unique session active (use-gps-tracker).
let listener: GpsTaskListener | null = null;

export function setGpsTaskListener(next: GpsTaskListener | null): void {
  listener = next;
}

// Doit être défini au chargement du module (hors composant), avant tout start.
if (Platform.OS !== 'web') {
  TaskManager.defineTask(GPS_TASK, async ({ data, error }) => {
    if (error || !data) return;
    const { locations } = data as { locations: Location.LocationObject[] };
    if (locations?.length) listener?.(locations);
  });
}

/**
 * Démarre le suivi GPS continu (1 Hz) adossé à un service de premier plan.
 * À appeler app visible (Android interdit le démarrage du service sinon).
 */
export async function startGpsUpdates(): Promise<void> {
  await Location.startLocationUpdatesAsync(GPS_TASK, {
    accuracy: Location.Accuracy.BestForNavigation,
    // Flux régulier à ~1 Hz sans filtre de déplacement natif : le lissage et
    // l'anti-dérive à l'arrêt sont gérés côté JS (lib/gps-filter), qui a besoin
    // d'échantillons continus pour fonctionner.
    timeInterval: 1000,
    distanceInterval: 0,
    // iOS (secondaire) : optimise le GNSS pour le sport en extérieur.
    activityType: Location.ActivityType.Fitness,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Sortie vélo en cours',
      notificationBody: 'Élan enregistre votre tracé GPS.',
      notificationColor: '#0A0C10',
      killServiceOnDestroy: true,
    },
  });
}

export async function stopGpsUpdates(): Promise<void> {
  if (Platform.OS === 'web') return;
  try {
    if (await Location.hasStartedLocationUpdatesAsync(GPS_TASK)) {
      await Location.stopLocationUpdatesAsync(GPS_TASK);
    }
  } catch {
    // Tâche déjà arrêtée/jamais enregistrée : rien à faire.
  }
}
