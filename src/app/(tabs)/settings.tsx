import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Pressable,
  ScrollView,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Chip } from '@/components/chip';
import { PressableScale } from '@/components/pressable-scale';
import { Radius, Type } from '@/constants/theme';
import { clearAllData, getProfile, saveProfile } from '@/lib/db';
import { formatDateTime } from '@/lib/format';
import { getMapStyleUrl, setMapStyleUrl } from '@/lib/map';
import {
  applyNotifications,
  DEFAULT_NOTIFICATION_CONFIG,
  getNotificationConfig,
  requestNotificationPermission,
  saveNotificationConfig,
  type NotificationConfig,
} from '@/lib/notifications';
import {
  DEFAULT_WEEK_PLAN,
  getEffectiveWeekPlan,
  resetCustomWeekPlan,
  saveCustomWeekPlan,
  type PlannedSession,
} from '@/lib/program';
import type { Profile } from '@/lib/types';
import { matchWheelSize, WHEEL_SIZES } from '@/lib/wheel-sizes';
import { useBackup } from '@/hooks/use-backup';
import { useCadenceSpeed } from '@/hooks/use-cadence-speed';
import { useDataExport } from '@/hooks/use-data-export';
import { useHeartRate } from '@/hooks/use-heart-rate';
import { useStravaImport } from '@/hooks/use-strava-import';
import { useTheme } from '@/hooks/use-theme';

export default function SettingsScreen() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const hr = useHeartRate();
  const csc = useCadenceSpeed();
  const strava = useStravaImport();
  const backup = useBackup();
  const dataExport = useDataExport();

  const [profile, setProfile] = useState<Profile | null>(null);
  const [mapUrl, setMapUrl] = useState('');

  useEffect(() => {
    getProfile().then(setProfile);
    getMapStyleUrl().then(setMapUrl);
  }, []);

  const updateMapUrl = (url: string) => {
    setMapUrl(url);
    setMapStyleUrl(url);
  };

  const patchProfile = (patch: Partial<Profile>) => {
    setProfile((prev) => {
      if (!prev) return prev;
      const next = { ...prev, ...patch };
      saveProfile(next);
      return next;
    });
  };

  const runStravaImport = async () => {
    const res = await strava.pickAndImport();
    if (!res) return;
    Alert.alert(
      'Import Strava',
      `${res.imported} séance(s) importée(s)\n` +
        `${res.duplicates} doublon(s) ignoré(s)\n` +
        `${res.skipped} activité(s) ignorée(s)` +
        (res.errors ? `\n${res.errors} fichier(s) en erreur` : ''),
    );
  };

  const confirmClear = () => {
    Alert.alert(
      'Effacer toutes les données ?',
      'Toutes les séances enregistrées seront supprimées définitivement. Le profil et la ceinture appairée sont conservés.',
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Tout effacer', style: 'destructive', onPress: () => clearAllData() },
      ],
    );
  };

  const confirmRestore = () => {
    Alert.alert(
      'Restaurer la sauvegarde ?',
      'Les données locales actuelles seront REMPLACÉES par celles du serveur. Cette action est irréversible.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Restaurer',
          style: 'destructive',
          onPress: async () => {
            const count = await backup.restore();
            if (count != null) {
              Alert.alert('Restauration terminée', `${count} séance(s) restaurée(s) depuis le serveur.`);
            }
          },
        },
      ],
    );
  };

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 40,
        paddingHorizontal: 16,
        gap: 16,
      }}>
      <Text style={{ ...Type.title, color: theme.text }}>Réglages</Text>

      {/* Ceinture cardiaque */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="heart-pulse" size={22} color={theme.heart} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
            Ceinture cardiaque
          </Text>
        </View>

        <HrStatusLine />

        {hr.status === 'connected' ? (
          <Button
            title="Déconnecter"
            icon="bluetooth-off"
            variant="secondary"
            color={theme.accent}
            onPress={hr.disconnect}
          />
        ) : (
          <Button
            title={hr.status === 'scanning' ? 'Recherche en cours…' : 'Rechercher une ceinture'}
            icon="bluetooth"
            color={theme.accent}
            loading={hr.status === 'scanning' || hr.status === 'connecting'}
            onPress={hr.startScan}
          />
        )}

        {hr.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{hr.error}</Text>
        ) : null}

        {hr.status === 'scanning' && hr.scanned.length === 0 ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <ActivityIndicator color={theme.textSecondary} />
            <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
              {"Activez votre ceinture et portez-la pour qu'elle soit détectée."}
            </Text>
          </View>
        ) : null}

        {hr.scanned.map((d) => (
          <PressableScale
            key={d.id}
            onPress={() => hr.connect(d.id)}
            haptic="light"
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 10,
              paddingVertical: 10,
              borderTopWidth: 1,
              borderTopColor: theme.hairline,
            }}>
            <MaterialCommunityIcons name="heart-flash" size={20} color={theme.heart} />
            <Text style={{ color: theme.text, flex: 1, fontWeight: '600' }}>{d.name}</Text>
            <MaterialCommunityIcons name="chevron-right" size={20} color={theme.textMuted} />
          </PressableScale>
        ))}

        {hr.status === 'unsupported' ? (
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {"Le Bluetooth n'est disponible que sur l'application Android/iOS (development build)."}
          </Text>
        ) : null}
      </Card>

      {/* Capteurs vélo (cadence / vitesse) */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="rotate-right" size={22} color={theme.velo} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
            Capteurs vélo
          </Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {'Cadence et vitesse via le profil BLE standard (iGPSPORT CAD70 / SPD70, ou équivalent). Vous pouvez en connecter deux à la fois.'}
        </Text>

        {/* Capteurs connectés */}
        {csc.devices.map((d) => (
          <View
            key={d.id}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 10,
              paddingVertical: 8,
              borderTopWidth: 1,
              borderTopColor: theme.hairline,
            }}>
            <View style={{ width: 9, height: 9, borderRadius: 5, backgroundColor: theme.success }} />
            <Text style={{ color: theme.text, flex: 1, fontWeight: '600' }}>{d.name}</Text>
            {csc.cadenceRpm != null || csc.speedKmh != null ? (
              <Text style={{ color: theme.velo, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
                {csc.cadenceRpm != null ? `${csc.cadenceRpm} tr/min` : `${(csc.speedKmh ?? 0).toFixed(1)} km/h`}
              </Text>
            ) : null}
            <Pressable onPress={() => csc.disconnect(d.id)} hitSlop={8}>
              <MaterialCommunityIcons name="bluetooth-off" size={20} color={theme.textMuted} />
            </Pressable>
          </View>
        ))}

        <Button
          title={csc.status === 'scanning' ? 'Recherche en cours…' : 'Rechercher un capteur'}
          icon="bluetooth"
          color={theme.velo}
          loading={csc.status === 'scanning' || csc.status === 'connecting'}
          onPress={csc.startScan}
        />

        {csc.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{csc.error}</Text>
        ) : null}

        {csc.status === 'scanning' && csc.scanned.length === 0 ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <ActivityIndicator color={theme.textSecondary} />
            <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
              {'Réveillez le capteur (faites tourner la roue ou la manivelle) pour qu\'il soit détecté.'}
            </Text>
          </View>
        ) : null}

        {csc.scanned.map((d) => (
          <PressableScale
            key={d.id}
            onPress={() => csc.connect(d.id)}
            haptic="light"
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 10,
              paddingVertical: 10,
              borderTopWidth: 1,
              borderTopColor: theme.hairline,
            }}>
            <MaterialCommunityIcons name="bike-fast" size={20} color={theme.velo} />
            <Text style={{ color: theme.text, flex: 1, fontWeight: '600' }}>{d.name}</Text>
            <MaterialCommunityIcons name="chevron-right" size={20} color={theme.textMuted} />
          </PressableScale>
        ))}

        {/* Taille de pneu → circonférence (mm) pour le calcul de vitesse/distance */}
        {csc.status !== 'unsupported' ? (
          <View style={{ borderTopWidth: 1, borderTopColor: theme.hairline, paddingTop: 12, gap: 12 }}>
            <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>Taille de pneu</Text>
            <WheelSizePicker valueMm={csc.wheelCircumferenceMm} onSelect={csc.setWheelCircumferenceMm} />
            <SettingStepper
              label="Circonférence"
              value={csc.wheelCircumferenceMm}
              unit="mm"
              step={1}
              min={1000}
              max={2400}
              onChange={csc.setWheelCircumferenceMm}
            />
            <Text style={{ color: theme.textSecondary, fontSize: 12 }}>
              {'Choisissez votre pneu, ou ajustez la circonférence au mm près si vous l\'avez mesurée.'}
            </Text>
          </View>
        ) : null}

        {csc.status === 'unsupported' ? (
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {"Le Bluetooth n'est disponible que sur l'application Android/iOS (development build)."}
          </Text>
        ) : null}
      </Card>

      {/* Carte (fond MapLibre auto-hébergé) */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="map-outline" size={22} color={theme.velo} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Carte</Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {'URL du style MapLibre servi par ton homelab. Renseignée, les sorties vélo affichent un vrai fond de carte (rues) ; sinon, un tracé sur fond uni. Les tuiles ne sont demandées qu’à ton serveur.'}
        </Text>
        <Field
          label="URL du style"
          placeholder="https://tiles.battistella.ovh/styles/basic/style.json"
          value={mapUrl}
          onChangeText={updateMapUrl}
          keyboardType="url"
        />
      </Card>

      {/* Profil */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="account-outline" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Profil</Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          Utilisé pour estimer les calories et les zones cardio.
        </Text>

        <SettingStepper
          label="Poids"
          value={profile?.weightKg ?? 70}
          unit="kg"
          step={1}
          min={30}
          max={200}
          onChange={(v) => patchProfile({ weightKg: v })}
        />
        <SettingStepper
          label="FC max"
          value={profile?.maxHr ?? 190}
          unit="bpm"
          step={1}
          min={120}
          max={220}
          onChange={(v) => patchProfile({ maxHr: v })}
        />
      </Card>

      {/* Planning hebdomadaire personnalisable */}
      <WeekPlanCard />

      {/* Rappels du soir (opt-in) */}
      <NotificationsCard />

      {/* Données */}
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="database-outline" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Données</Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
          <MaterialCommunityIcons name="lock-outline" size={18} color={theme.success} />
          <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1 }}>
            {"Toutes vos données restent sur cet appareil (base SQLite locale). Rien n'est envoyé sur internet."}
          </Text>
        </View>
        <Button
          title="Effacer toutes les séances"
          icon="trash-can-outline"
          variant="danger"
          onPress={confirmClear}
        />
      </Card>

      {/* Exporter mes données (bilan IA / brut) */}
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="export-variant" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
            Exporter mes données
          </Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {"Génère un bilan lisible de ton programme, de ta progression et de ton historique, ou un export brut complet. À déposer dans l'outil de ton choix : coach IA, tableur, sauvegarde personnelle… Tout est généré sur l'appareil, le partage se fait via la feuille système (Drive, mail, fichier…)."}
        </Text>
        <Button
          title="Exporter le bilan (Markdown)"
          icon="file-document-outline"
          color={theme.accent}
          loading={dataExport.exporting === 'markdown'}
          onPress={dataExport.exportMarkdown}
        />
        <Button
          title="Exporter les données brutes (JSON)"
          icon="code-json"
          variant="secondary"
          color={theme.accent}
          loading={dataExport.exporting === 'json'}
          onPress={dataExport.exportJson}
        />
        {dataExport.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{dataExport.error}</Text>
        ) : null}
      </Card>

      {/* Import Strava */}
      <Card style={{ gap: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="cloud-download-outline" size={22} color={theme.velo} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>Import Strava</Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {"Exportez depuis Strava (page de l'activité → « Exporter GPX », ou « Télécharger vos données » dans les réglages du compte), puis importez le fichier ici. Formats acceptés : GPX, TCX et FIT, y compris compressés (.gz) — comme dans l'export en masse. Tout est traité sur l'appareil."}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8 }}>
          <MaterialCommunityIcons name="information-outline" size={18} color={theme.textMuted} />
          <Text style={{ color: theme.textMuted, fontSize: 12, flex: 1 }}>
            {"Pas de synchronisation automatique avec votre compte Strava : cela demanderait un serveur, incompatible avec le fonctionnement 100 % hors-ligne de l'app. La ré-importation d'un même fichier ne crée pas de doublon."}
          </Text>
        </View>

        <Button
          title="Importer un fichier (GPX/TCX/FIT)"
          icon="file-import-outline"
          color={theme.velo}
          loading={strava.importing}
          onPress={runStravaImport}
        />

        {strava.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{strava.error}</Text>
        ) : null}

        {strava.result ? (
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {`Dernier import : ${strava.result.imported} importée(s), ${strava.result.duplicates} doublon(s), ${strava.result.skipped} ignorée(s)` +
              (strava.result.errors ? `, ${strava.result.errors} erreur(s)` : '')}
          </Text>
        ) : null}
      </Card>

      {/* Sauvegarde homelab (S3) */}
      <Card style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <MaterialCommunityIcons name="cloud-upload-outline" size={22} color={theme.accent} />
          <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
            Sauvegarde homelab
          </Text>
        </View>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {'Backup automatique de toutes tes données vers ton stockage S3 (MinIO). Elles restent sur ton serveur — rien chez un tiers.'}
        </Text>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>
            Sauvegarde automatique
          </Text>
          <Switch
            value={backup.config?.enabled ?? false}
            onValueChange={(v) => backup.update({ enabled: v })}
            trackColor={{ true: theme.accent }}
          />
        </View>

        <BackupStatusLine />

        <Field
          label="Endpoint"
          placeholder="https://minio.mon-homelab.tld"
          value={backup.config?.endpoint ?? ''}
          onChangeText={(t) => backup.update({ endpoint: t })}
          keyboardType="url"
        />
        <View style={{ flexDirection: 'row', gap: 12 }}>
          <View style={{ flex: 2 }}>
            <Field
              label="Bucket"
              placeholder="suivi-sport"
              value={backup.config?.bucket ?? ''}
              onChangeText={(t) => backup.update({ bucket: t })}
            />
          </View>
          <View style={{ flex: 1 }}>
            <Field
              label="Région"
              placeholder="us-east-1"
              value={backup.config?.region ?? ''}
              onChangeText={(t) => backup.update({ region: t })}
            />
          </View>
        </View>
        <Field
          label="Access key"
          placeholder="Clé d'accès"
          value={backup.config?.accessKeyId ?? ''}
          onChangeText={(t) => backup.update({ accessKeyId: t })}
        />
        <Field
          label="Secret key"
          placeholder="Clé secrète"
          value={backup.config?.secretAccessKey ?? ''}
          onChangeText={(t) => backup.update({ secretAccessKey: t })}
          secureTextEntry
        />
        <Field
          label="Nom de l'objet"
          placeholder="suivi-sport-backup.json"
          value={backup.config?.objectKey ?? ''}
          onChangeText={(t) => backup.update({ objectKey: t })}
        />

        {backup.error ? (
          <Text style={{ color: theme.heart, fontSize: 13 }}>{backup.error}</Text>
        ) : null}

        <Button
          title="Sauvegarder maintenant"
          icon="cloud-upload"
          color={theme.accent}
          loading={backup.status === 'saving'}
          disabled={!backup.ready}
          onPress={backup.backupNow}
        />
        <Button
          title="Restaurer depuis le serveur"
          icon="cloud-download-outline"
          variant="secondary"
          color={theme.accent}
          loading={backup.status === 'restoring'}
          disabled={!backup.ready}
          onPress={confirmRestore}
        />
      </Card>
    </ScrollView>
  );
}

// Libellés courts pour chaque jour de la semaine, lundi en tête (index 0 = lundi).
const WEEK_DAY_LABELS = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];

/** Option proposée pour un jour : repos, vélo, ou muscu avec un template précis. */
type WeekPlanOption =
  | { kind: 'repos'; label: string }
  | { kind: 'velo'; label: string }
  | { kind: 'muscu'; label: string; templateId: 'fullbody-a' | 'fullbody-b' };

const WEEK_PLAN_OPTIONS: WeekPlanOption[] = [
  { kind: 'repos', label: 'Repos' },
  { kind: 'velo', label: 'Vélo' },
  { kind: 'muscu', label: 'Muscu A', templateId: 'fullbody-a' },
  { kind: 'muscu', label: 'Muscu B', templateId: 'fullbody-b' },
];

/** Convertit une option d'UI en `PlannedSession` à persister. */
function optionToPlanned(opt: WeekPlanOption): PlannedSession {
  if (opt.kind === 'repos') return { kind: 'repos' };
  if (opt.kind === 'velo') return { kind: 'velo', label: 'Vélo' };
  return {
    kind: 'muscu',
    label: opt.templateId === 'fullbody-a' ? 'Full-body A' : 'Full-body B',
    templateId: opt.templateId,
  };
}

/** Détermine si une option correspond à la séance planifiée pour ce jour. */
function isOptionActive(opt: WeekPlanOption, entry: PlannedSession): boolean {
  if (opt.kind !== entry.kind) return false;
  if (opt.kind === 'muscu' && entry.kind === 'muscu') {
    return opt.templateId === entry.templateId;
  }
  return true;
}

function WeekPlanCard() {
  const theme = useTheme();
  const [plan, setPlan] = useState<PlannedSession[]>(DEFAULT_WEEK_PLAN);

  useEffect(() => {
    getEffectiveWeekPlan().then(setPlan);
  }, []);

  const updateDay = (dayIndex: number, opt: WeekPlanOption) => {
    setPlan((prev) => {
      const next = prev.slice();
      next[dayIndex] = optionToPlanned(opt);
      saveCustomWeekPlan(next).then(() => applyNotifications());
      return next;
    });
  };

  const reset = () => {
    Alert.alert(
      'Réinitialiser le planning ?',
      'Le planning par défaut sera restauré (Vélo lundi, Full-body A mardi, Full-body B vendredi, repos les autres jours).',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Réinitialiser',
          style: 'destructive',
          onPress: async () => {
            await resetCustomWeekPlan();
            setPlan(DEFAULT_WEEK_PLAN);
            await applyNotifications();
          },
        },
      ],
    );
  };

  return (
    <Card style={{ gap: 14 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <MaterialCommunityIcons name="calendar-week" size={22} color={theme.accent} />
        <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
          Planning hebdomadaire
        </Text>
      </View>
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Choisissez la séance prévue pour chaque jour. L'accueil propose la séance du jour selon ce planning."}
      </Text>

      {WEEK_DAY_LABELS.map((dayLabel, i) => (
        <View
          key={dayLabel}
          style={{
            gap: 8,
            paddingTop: i === 0 ? 0 : 10,
            borderTopWidth: i === 0 ? 0 : 1,
            borderTopColor: theme.hairline,
          }}>
          <Text style={{ ...Type.label, color: theme.textSecondary }}>{dayLabel}</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {WEEK_PLAN_OPTIONS.map((opt) => {
              const active = isOptionActive(opt, plan[i]);
              const color =
                opt.kind === 'velo'
                  ? theme.velo
                  : opt.kind === 'muscu'
                    ? theme.muscu
                    : theme.textSecondary;
              return (
                <Chip
                  key={opt.label}
                  label={opt.label}
                  selected={active}
                  color={color}
                  onPress={() => updateDay(i, opt)}
                />
              );
            })}
          </View>
        </View>
      ))}

      <Button
        title="Réinitialiser le planning"
        icon="restore"
        variant="secondary"
        color={theme.accent}
        onPress={reset}
      />
    </Card>
  );
}

function NotificationsCard() {
  const theme = useTheme();
  const [cfg, setCfg] = useState<NotificationConfig>(DEFAULT_NOTIFICATION_CONFIG);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getNotificationConfig().then(setCfg);
  }, []);

  const toggleEnabled = async (next: boolean) => {
    setError(null);
    if (next) {
      const granted = await requestNotificationPermission();
      if (!granted) {
        setError(
          "Permission refusée. Activez les notifications de l'app dans les réglages système pour utiliser les rappels.",
        );
        return;
      }
    }
    const updated: NotificationConfig = { ...cfg, enabled: next };
    setCfg(updated);
    await saveNotificationConfig(updated);
    await applyNotifications();
  };

  const changeHour = async (hour: number) => {
    const updated: NotificationConfig = { ...cfg, hour };
    setCfg(updated);
    await saveNotificationConfig(updated);
    if (updated.enabled) await applyNotifications();
  };

  return (
    <Card style={{ gap: 14 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <MaterialCommunityIcons name="bell-outline" size={22} color={theme.accent} />
        <Text style={{ color: theme.text, fontSize: 17, fontWeight: '800' }}>
          Rappels du soir
        </Text>
      </View>
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {"Un rappel local la veille au soir d'une séance prévue, pour penser à préparer le matériel. Aucune notification les jours de repos, et aucune relance si la séance est manquée. 100 % local — aucune connexion réseau."}
      </Text>

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>
          Activer les rappels
        </Text>
        <Switch
          value={cfg.enabled}
          onValueChange={toggleEnabled}
          trackColor={{ true: theme.accent }}
        />
      </View>

      <SettingStepper
        label="Heure du rappel"
        value={cfg.hour}
        unit="h"
        step={1}
        min={17}
        max={23}
        onChange={changeHour}
      />

      {error ? <Text style={{ color: theme.heart, fontSize: 13 }}>{error}</Text> : null}
    </Card>
  );
}

function BackupStatusLine() {
  const theme = useTheme();
  const { last, status } = useBackup();

  let color: string = theme.textSecondary;
  let label = 'Aucune sauvegarde pour l’instant';
  if (status === 'saving') {
    color = theme.warning;
    label = 'Sauvegarde en cours…';
  } else if (status === 'restoring') {
    color = theme.warning;
    label = 'Restauration en cours…';
  } else if (last?.ok) {
    color = theme.success;
    label = `Dernière sauvegarde : ${formatDateTime(last.at)}`;
  } else if (last && !last.ok) {
    color = theme.heart;
    label = `Échec le ${formatDateTime(last.at)}`;
  }

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
      <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: color }} />
      <Text style={{ color: theme.textSecondary, fontSize: 13, flex: 1 }}>{label}</Text>
    </View>
  );
}

function Field({
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry,
  keyboardType,
}: {
  label: string;
  value: string;
  onChangeText: (t: string) => void;
  placeholder?: string;
  secureTextEntry?: boolean;
  keyboardType?: 'default' | 'url';
}) {
  const theme = useTheme();
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: theme.textSecondary, fontSize: 13, fontWeight: '600' }}>{label}</Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={theme.textMuted}
        secureTextEntry={secureTextEntry}
        keyboardType={keyboardType}
        autoCapitalize="none"
        autoCorrect={false}
        style={{
          color: theme.text,
          backgroundColor: theme.backgroundSelected,
          borderRadius: Radius.sm,
          borderCurve: 'continuous',
          paddingHorizontal: 12,
          paddingVertical: 10,
          fontSize: 15,
          borderWidth: 1,
          borderColor: theme.border,
        }}
      />
    </View>
  );
}

function HrStatusLine() {
  const theme = useTheme();
  const { status, bpm, device } = useHeartRate();

  const map: Record<string, { label: string; color: string }> = {
    connected: { label: device ? `Connectée · ${device.name}` : 'Connectée', color: theme.success },
    connecting: { label: 'Connexion…', color: theme.warning },
    scanning: { label: 'Recherche…', color: theme.warning },
    error: { label: 'Erreur', color: theme.heart },
    idle: { label: 'Non connectée', color: theme.textSecondary },
    unsupported: { label: 'Non disponible', color: theme.textSecondary },
  };
  const s = map[status] ?? map.idle;

  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: s.color }} />
        <Text style={{ color: theme.text, fontWeight: '600' }}>{s.label}</Text>
      </View>
      {status === 'connected' ? (
        <Text style={{ color: theme.heart, fontWeight: '800', fontVariant: ['tabular-nums'] }}>
          {bpm != null ? `${bpm} bpm` : '··'}
        </Text>
      ) : null}
    </View>
  );
}

/** Sélecteur de taille de pneu : une liste déroulante qui renseigne la circonférence (mm). */
function WheelSizePicker({
  valueMm,
  onSelect,
}: {
  valueMm: number;
  onSelect: (mm: number) => void;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const current = matchWheelSize(valueMm);

  return (
    <>
      <PressableScale
        haptic="selection"
        onPress={() => setOpen(true)}
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingVertical: 12,
          paddingHorizontal: 14,
          borderRadius: Radius.md,
          borderWidth: 1.5,
          borderColor: theme.border,
          backgroundColor: theme.backgroundElement,
        }}>
        <Text style={{ color: theme.text, fontWeight: '700', fontSize: 14 }}>
          {current ? current.label : 'Personnalisé'}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={22} color={theme.textSecondary} />
      </PressableScale>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          onPress={() => setOpen(false)}
          style={{
            flex: 1,
            backgroundColor: '#00000088',
            justifyContent: 'center',
            padding: 24,
          }}>
          <Pressable
            onPress={(e) => e.stopPropagation()}
            style={{
              backgroundColor: theme.backgroundElement,
              borderRadius: Radius.lg,
              borderWidth: 1,
              borderColor: theme.border,
              overflow: 'hidden',
            }}>
            <Text
              style={{
                color: theme.textSecondary,
                fontSize: 12,
                fontWeight: '700',
                paddingHorizontal: 16,
                paddingTop: 14,
                paddingBottom: 6,
                textTransform: 'uppercase',
              }}>
              Taille de pneu
            </Text>
            <ScrollView style={{ maxHeight: 360 }}>
              {WHEEL_SIZES.map((w) => {
                const active = w.mm === valueMm;
                return (
                  <PressableScale
                    key={w.label}
                    haptic="selection"
                    onPress={() => {
                      onSelect(w.mm);
                      setOpen(false);
                    }}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      paddingVertical: 14,
                      paddingHorizontal: 16,
                      backgroundColor: active ? theme.velo + '22' : 'transparent',
                    }}>
                    <Text
                      style={{
                        color: active ? theme.velo : theme.text,
                        fontWeight: active ? '800' : '600',
                        fontSize: 15,
                      }}>
                      {w.label}
                    </Text>
                    <Text style={{ color: theme.textSecondary, fontSize: 13 }}>{w.mm} mm</Text>
                  </PressableScale>
                );
              })}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

function SettingStepper({
  label,
  value,
  unit,
  step,
  min,
  max,
  onChange,
}: {
  label: string;
  value: number;
  unit: string;
  step: number;
  min: number;
  max: number;
  onChange: (v: number) => void;
}) {
  const theme = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text style={{ color: theme.text, fontSize: 15, fontWeight: '600' }}>{label}</Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        <Pressable onPress={() => onChange(Math.max(min, value - step))} hitSlop={8}>
          <MaterialCommunityIcons name="minus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
        <Text
          style={{
            color: theme.text,
            fontSize: 17,
            fontWeight: '800',
            minWidth: 70,
            textAlign: 'center',
            fontVariant: ['tabular-nums'],
          }}>
          {value} {unit}
        </Text>
        <Pressable onPress={() => onChange(Math.min(max, value + step))} hitSlop={8}>
          <MaterialCommunityIcons name="plus-circle-outline" size={28} color={theme.accent} />
        </Pressable>
      </View>
    </View>
  );
}
