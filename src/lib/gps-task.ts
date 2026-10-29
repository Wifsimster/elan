import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { Platform } from 'react-native';

export const GPS_TASK = 'elan-gps-tracking';

type GpsTaskListener = (locations: Location.LocationObject[]) => void;

let listener: GpsTaskListener | null = null;

export function setGpsTaskListener(next: GpsTaskListener | null): void {
  listener = next;
}

if (Platform.OS !== 'web') {
  TaskManager.defineTask(GPS_TASK, async ({ data, error }) => {
    if (error || !data) return;
    const { locations } = data as { locations: Location.LocationObject[] };
    if (locations?.length) listener?.(locations);
  });
}

export async function startGpsUpdates(): Promise<void> {
  await Location.startLocationUpdatesAsync(GPS_TASK, {
    accuracy: Location.Accuracy.BestForNavigation,
    timeInterval: 1000,
    distanceInterval: 0,
    activityType: Location.ActivityType.Fitness,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Sortie en cours',
      notificationBody: 'Élan enregistre ton tracé GPS.',
      notificationColor: '#0A0C10',
      killServiceOnDestroy: true,
    },
  });
}

export async function reconcileOrphanGpsTask(): Promise<void> {
  await stopGpsUpdates();
}

export async function stopGpsUpdates(): Promise<void> {
  if (Platform.OS === 'web') return;
  try {
    if (await Location.hasStartedLocationUpdatesAsync(GPS_TASK)) {
      await Location.stopLocationUpdatesAsync(GPS_TASK);
    }
  } catch {
  }
}
