import { autoBackup } from '@/lib/backup';
import {
  exportSessionToHealthConnect,
  removeSessionFromHealthConnect,
  type HealthSessionData,
} from '@/lib/health-connect';
import type { ActivityType } from '@/lib/types';

export function finalizeSavedSession(data: HealthSessionData): void {
  autoBackup();
  exportSessionToHealthConnect(data);
}

export function retypeSavedSession(previousType: ActivityType, data: HealthSessionData): void {
  autoBackup();
  void (async () => {
    await removeSessionFromHealthConnect(previousType, data.startedAt);
    await exportSessionToHealthConnect(data);
  })();
}
