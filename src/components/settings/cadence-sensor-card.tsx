import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, Text, View } from 'react-native';

import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { PressableScale } from '@/components/pressable-scale';
import { SettingCardHeader } from '@/components/setting-card-header';
import { SettingStepper } from '@/components/settings/setting-stepper';
import { Radius } from '@/constants/theme';
import { matchWheelSize, WHEEL_SIZES } from '@/lib/wheel-sizes';
import { useCadenceSpeed } from '@/hooks/use-cadence-speed';
import { useTheme } from '@/hooks/use-theme';

/** Carte Réglages : capteurs vélo BLE (cadence / vitesse) + taille de pneu. */
export function CadenceSensorCard() {
  const theme = useTheme();
  const csc = useCadenceSpeed();

  return (
    <Card style={{ gap: 14 }}>
      <SettingCardHeader icon="rotate-right" color={theme.velo} title="Capteurs vélo" />
      <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
        {'Cadence et vitesse via le profil BLE standard (iGPSPORT CAD70 / SPD70, ou équivalent). Tu peux en connecter deux à la fois.'}
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
          <Pressable
            onPress={() => csc.disconnect(d.id)}
            hitSlop={14}
            accessibilityRole="button"
            accessibilityLabel={`Déconnecter ${d.name}`}>
            <MaterialCommunityIcons name="bluetooth-off" size={20} color={theme.textMuted} />
          </Pressable>
        </View>
      ))}

      <Button
        title={
          csc.status === 'scanning'
            ? 'Recherche en cours…'
            : csc.status === 'reconnecting'
              ? 'Reconnexion…'
              : 'Rechercher un capteur'
        }
        icon="bluetooth"
        color={theme.velo}
        loading={
          csc.status === 'scanning' || csc.status === 'connecting' || csc.status === 'reconnecting'
        }
        onPress={csc.startScan}
      />

      {csc.error ? <Text style={{ color: theme.danger, fontSize: 13 }}>{csc.error}</Text> : null}

      {csc.status === 'scanning' && csc.scanned.length === 0 ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <ActivityIndicator color={theme.textSecondary} />
          <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
            {'Réveille le capteur (fais tourner la roue ou la manivelle) pour qu\'il soit détecté.'}
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
            {'Choisis ton pneu, ou ajuste la circonférence au mm près si tu l\'as mesurée.'}
          </Text>
        </View>
      ) : null}

      {csc.status === 'unsupported' ? (
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          {"Le Bluetooth n'est disponible que sur l'application Android/iOS (development build)."}
        </Text>
      ) : null}
    </Card>
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
