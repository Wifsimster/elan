// Vérifie le contrat de l'orchestrateur post-enregistrement (lib/session-finalize.ts) :
// il déclenche la sauvegarde homelab ET le miroir Health Connect, en transmettant
// les données de séance. Les deux dépendances sont moquées (effets best-effort).
import { finalizeSavedSession } from '@/lib/session-finalize';
import { autoBackup } from '@/lib/backup';
import { exportSessionToHealthConnect } from '@/lib/health-connect';

jest.mock('@/lib/backup', () => ({ autoBackup: jest.fn() }));
jest.mock('@/lib/health-connect', () => ({ exportSessionToHealthConnect: jest.fn() }));

describe('finalizeSavedSession', () => {
  beforeEach(() => jest.clearAllMocks());

  it('déclenche la sauvegarde et l’export Health Connect avec les données', () => {
    const data = {
      type: 'velo' as const,
      startedAt: 1000,
      endedAt: 2000,
      distanceM: 12345,
      calories: 400,
      hrSamples: [{ ts: 1000, hr: 120 }],
    };
    finalizeSavedSession(data);
    expect(autoBackup).toHaveBeenCalledTimes(1);
    expect(exportSessionToHealthConnect).toHaveBeenCalledTimes(1);
    expect(exportSessionToHealthConnect).toHaveBeenCalledWith(data);
  });
});
